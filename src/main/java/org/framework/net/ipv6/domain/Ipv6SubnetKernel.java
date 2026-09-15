package org.framework.net.ipv6.domain;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.ipv6.IPv6Address;
import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.ipv6.exception.Ipv6Exception;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Kernel de cálculo de sub-redes IPv6.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> é o equivalente IPv6 do {@code SubnetKernel} do IPv4 — analisa um
 * endereço/prefixo (normalização RFC 5952, expansão, tipo, rede, faixa, quantidade, Interface ID,
 * multicast solicited-node, binário por hexteto) e divide um prefixo em sub-redes menores. Preenche
 * o buraco central do projeto, que só tinha analisador de endereço único de IPv6.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> IPv6 <b>não tem broadcast</b> e a aritmética de "hosts úteis =
 * total − 2" do IPv4 <b>não se aplica</b> — este kernel nunca subtrai rede/broadcast. Contagens de
 * endereços usam {@link BigInteger} (um /0 tem 2^128 endereços, estoura {@code long}). O prefixo
 * alvo de uma divisão é sempre maior (mais específico) que o prefixo base, entre 0 e 128.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> entrada malformada, prefixo fora de 0–128 ou alvo menor
 * que a base lançam {@link Ipv6Exception} com mensagem didática. Nunca propaga exceção crua da
 * biblioteca nem devolve resultado parcial.</p>
 */
@ApplicationScoped
public class Ipv6SubnetKernel {

    /** Faixas especiais IANA, avaliadas em ordem (a primeira que contém o endereço vence). */
    private static final List<FaixaEspecial> FAIXAS = List.of(
            new FaixaEspecial("::1/128", "Loopback", "Host local (equivale ao 127.0.0.1 do IPv4)"),
            new FaixaEspecial("::/128", "Não especificado", "Ausência de endereço (equivale ao 0.0.0.0)"),
            new FaixaEspecial("::ffff:0:0/96", "IPv4-mapped", "Carrega um IPv4 dentro do IPv6 (::ffff:a.b.c.d)"),
            new FaixaEspecial("64:ff9b::/96", "NAT64", "Tradução IPv6↔IPv4 (well-known prefix)"),
            new FaixaEspecial("2001:db8::/32", "Documentação", "Reservado para exemplos e material didático (RFC 3849)"),
            new FaixaEspecial("2001::/32", "Teredo", "Túnel IPv6 sobre UDP/IPv4"),
            new FaixaEspecial("2002::/16", "6to4", "Túnel IPv6 sobre IPv4"),
            new FaixaEspecial("fe80::/10", "Link-local", "Válido só no enlace; não roteável (auto-configuração/NDP)"),
            new FaixaEspecial("fc00::/7", "ULA / Privado", "Uso interno, similar ao RFC 1918 do IPv4 (RFC 4193)"),
            new FaixaEspecial("ff00::/8", "Multicast", "Transmissão para um grupo (IPv6 não tem broadcast)"),
            new FaixaEspecial("2000::/3", "Global unicast", "Roteável na Internet")
    );

    /**
     * Analisa um endereço ou prefixo IPv6 e devolve todos os campos didáticos.
     *
     * @param entrada ex.: {@code 2001:db8::1}, {@code 2001:db8::/48}, {@code fe80::1%eth0}
     */
    public AnaliseIpv6 analisar(String entrada) {
        String bruto = entrada == null ? "" : entrada.strip().replace("\"", "").replace("'", "");
        if (bruto.isEmpty()) {
            throw new Ipv6Exception("Informe um endereço ou prefixo IPv6 (ex.: 2001:db8::/48).");
        }
        // O zone index (%eth0) é informação de escopo, não entra no cálculo.
        String semZona = bruto;
        int idxZona = bruto.indexOf('%');
        if (idxZona >= 0) {
            semZona = bruto.substring(0, idxZona).strip();
        }

        IPAddressString parser = new IPAddressString(semZona);
        if (!parser.isValid() || !parser.isIPv6()) {
            throw new Ipv6Exception("IPv6 inválido: formato não reconhecido (" + bruto + ").");
        }
        IPv6Address addr = parser.getAddress().toIPv6();
        if (addr == null) {
            throw new Ipv6Exception("IPv6 inválido: " + bruto + ".");
        }

        Integer prefixoInformado = addr.getNetworkPrefixLength();
        boolean temPrefixo = prefixoInformado != null;
        int prefixo = temPrefixo ? prefixoInformado : 128;

        IPv6Address host = addr.withoutPrefixLength();
        IPv6Address bloco = prefixoBloco(host, prefixo);

        String comprimido = host.toCompressedString();
        String expandido = host.toFullString();
        // getLower()/getUpper() de um bloco prefixado carregam o /n; withoutPrefixLength() tira o
        // sufixo para exibir só o endereço (senão "rede" sairia como "2001:db8::/48").
        String rede = bloco.getLower().withoutPrefixLength().toCompressedString();
        String primeiro = bloco.getLower().withoutPrefixLength().toCompressedString();
        String ultimo = bloco.getUpper().withoutPrefixLength().toCompressedString();
        BigInteger total = bloco.getCount();

        FaixaEspecial faixa = classificar(host);
        boolean unicastGlobalOuUla = faixa.tipo().equals("Global unicast") || faixa.tipo().equals("ULA / Privado");

        return new AnaliseIpv6(
                bruto,
                comprimido,
                expandido,
                prefixo,
                temPrefixo,
                rede,
                primeiro,
                ultimo,
                total.toString(),
                "2^" + (128 - prefixo),
                interfaceId(host),
                faixa.tipo(),
                faixa.descricao(),
                unicastGlobalOuUla ? solicitedNode(host) : "—",
                host.toReverseDNSLookupString(),
                binarioHextetos(host));
    }

    /**
     * Divide um prefixo base em sub-redes de {@code prefixoAlvo}.
     *
     * @param maxLinhas teto de renderização; o total matemático é sempre informado por inteiro.
     */
    public DivisaoIpv6 dividir(String cidr, int prefixoAlvo, int maxLinhas) {
        String bruto = cidr == null ? "" : cidr.strip().replace("\"", "").replace("'", "");
        if (bruto.isEmpty()) {
            throw new Ipv6Exception("Informe a rede base em CIDR (ex.: 2001:db8::/32).");
        }
        if (prefixoAlvo < 0 || prefixoAlvo > 128) {
            throw new Ipv6Exception("Prefixo alvo inválido: use um valor entre /0 e /128.");
        }
        IPAddressString parser = new IPAddressString(bruto);
        if (!parser.isValid() || !parser.isIPv6()) {
            throw new Ipv6Exception("Rede base IPv6 inválida (" + bruto + ").");
        }
        IPv6Address addr = parser.getAddress().toIPv6();
        Integer prefixoBaseInformado = addr.getNetworkPrefixLength();
        if (prefixoBaseInformado == null) {
            throw new Ipv6Exception("Informe o prefixo da rede base (ex.: 2001:db8::/32), não só o endereço.");
        }
        int prefixoBase = prefixoBaseInformado;
        if (prefixoAlvo < prefixoBase) {
            throw new Ipv6Exception("O prefixo alvo /" + prefixoAlvo + " é mais amplo que a base /" + prefixoBase
                    + ". Sub-rede é sempre um pedaço menor: use um prefixo de /" + prefixoBase + " a /128.");
        }

        IPv6Address baseBloco = addr.toPrefixBlock();
        BigInteger inicio = new BigInteger(1, baseBloco.getLower().getBytes());
        BigInteger passo = BigInteger.TWO.pow(128 - prefixoAlvo);
        BigInteger quantidade = BigInteger.TWO.pow(prefixoAlvo - prefixoBase);
        BigInteger enderecosPorSubrede = passo;

        int limite = quantidade.min(BigInteger.valueOf(Math.max(0, maxLinhas))).intValueExact();
        List<SubredeIpv6> subredes = new ArrayList<>(limite);
        for (int i = 0; i < limite; i++) {
            BigInteger valor = inicio.add(passo.multiply(BigInteger.valueOf(i)));
            IPv6Address rede = new IPv6Address(paraBytes16(valor));
            subredes.add(new SubredeIpv6(
                    i + 1,
                    rede.toCompressedString(),
                    "/" + prefixoAlvo,
                    rede.toCompressedString(),
                    ultimoDoBloco(valor, passo)));
        }
        boolean truncado = quantidade.compareTo(BigInteger.valueOf(limite)) > 0;

        return new DivisaoIpv6(
                baseBloco.getLower().withoutPrefixLength().toCompressedString() + "/" + prefixoBase,
                prefixoBase,
                prefixoAlvo,
                quantidade.toString(),
                enderecosPorSubrede.toString(),
                truncado,
                subredes.size(),
                subredes);
    }

    /**
     * PROPÓSITO DE NEGÓCIO: decomposição didática profunda de um endereço/prefixo IPv6 — o análogo
     * IPv6 do "array de 32 bits" e da tabela AND do IPv4, para a aba de Análise. Expõe os 128 bits
     * por hexteto marcando o corte entre bits de REDE (prefixo) e de INTERFACE, a tabela de
     * delegação (/48·/56·/64), o gateway sugerido e a dica Cisco/OSPFv3.
     *
     * INVARIANTES DO DOMÍNIO: reusa {@link #analisar} (fonte única); os bits de rede de cada hexteto
     * derivam só do prefixo; sem broadcast e sem "hosts úteis" — conceitos que não existem em IPv6.
     *
     * COMPORTAMENTO EM CASO DE FALHA: propaga a {@link Ipv6Exception} de {@code analisar}.
     */
    public DecomposicaoIpv6 decompor(String entrada) {
        AnaliseIpv6 base = analisar(entrada);
        int prefixo = base.prefixo();

        List<HextetInfo> hextetos = new ArrayList<>(8);
        List<String> bins = base.binarioHextetos();
        for (int i = 0; i < 8; i++) {
            int bitsRedeNoHextet = Math.max(0, Math.min(16, prefixo - i * 16));
            String bin = bins.get(i);
            hextetos.add(new HextetInfo(i + 1, hexDoBin(bin), bin, bitsRedeNoHextet,
                    bin.substring(0, bitsRedeNoHextet), bin.substring(bitsRedeNoHextet)));
        }

        List<DelegacaoInfo> delegacao = new ArrayList<>();
        delegacao.add(delegacaoLinha(prefixo, 48, "Site — alocação típica de um cliente por um RIR/ISP"));
        delegacao.add(delegacaoLinha(prefixo, 56, "Residência / filial — delegação comum do provedor"));
        delegacao.add(delegacaoLinha(prefixo, 64, "LAN — fronteira do SLAAC (uma sub-rede por enlace)"));

        String cisco = "ipv6 unicast-routing\ninterface GigabitEthernet0/0\n ipv6 address "
                + base.rede() + "/" + prefixo + "\n ipv6 ospf 1 area 0";

        return new DecomposicaoIpv6(base, hextetos, prefixo, 128 - prefixo,
                gatewaySugerido(base.rede(), prefixo), delegacao, cisco, enunciadoProva(prefixo));
    }

    // ------------------------------------------------------------------ helpers

    private static String hexDoBin(String bin16) {
        return String.format("%04x", Integer.parseInt(bin16, 2));
    }

    private String gatewaySugerido(String rede, int prefixo) {
        BigInteger inicio = new BigInteger(1, new IPAddressString(rede + "/" + prefixo)
                .getAddress().toIPv6().toPrefixBlock().getLower().getBytes());
        return new IPv6Address(paraBytes16(inicio.add(BigInteger.ONE))).toCompressedString();
    }

    private DelegacaoInfo delegacaoLinha(int prefixoBase, int alvo, String uso) {
        if (alvo < prefixoBase) {
            return new DelegacaoInfo("/" + alvo, "— (mais amplo que /" + prefixoBase + ")", uso);
        }
        return new DelegacaoInfo("/" + alvo, BigInteger.TWO.pow(alvo - prefixoBase).toString(), uso);
    }

    private String enunciadoProva(int prefixo) {
        if (prefixo <= 64) {
            return "Um /" + prefixo + " comporta " + BigInteger.TWO.pow(64 - prefixo)
                    + " sub-redes /64 — cada /64 é uma LAN com 2^64 endereços via SLAAC.";
        }
        return "Prefixo /" + prefixo + " é mais específico que /64: " + BigInteger.TWO.pow(128 - prefixo)
                + " endereços, abaixo da fronteira do SLAAC (enlaces ponto a ponto usam /127, loopbacks /128).";
    }

    private IPv6Address prefixoBloco(IPv6Address host, int prefixo) {
        return new IPAddressString(host.toCompressedString() + "/" + prefixo)
                .getAddress().toIPv6().toPrefixBlock();
    }

    private FaixaEspecial classificar(IPv6Address host) {
        IPv6Address alvo = host.withoutPrefixLength();
        for (FaixaEspecial f : FAIXAS) {
            IPAddress bloco = new IPAddressString(f.cidr()).getAddress();
            if (bloco != null && bloco.contains(alvo)) {
                return f;
            }
        }
        return new FaixaEspecial("", "Reservado/Outro", "Fora das faixas especiais mapeadas");
    }

    private String interfaceId(IPv6Address host) {
        byte[] b = host.getLower().getBytes();
        StringBuilder sb = new StringBuilder();
        for (int i = 8; i < 16; i += 2) {
            if (i > 8) {
                sb.append(':');
            }
            sb.append(String.format("%02x%02x", b[i] & 0xFF, b[i + 1] & 0xFF));
        }
        return sb.toString();
    }

    /** Multicast solicited-node de um unicast: ff02::1:ff + 24 bits baixos do endereço (RFC 4291). */
    private String solicitedNode(IPv6Address host) {
        byte[] b = host.getLower().getBytes();
        return String.format("ff02::1:ff%02x:%02x%02x", b[13] & 0xFF, b[14] & 0xFF, b[15] & 0xFF);
    }

    private List<String> binarioHextetos(IPv6Address host) {
        byte[] b = host.getLower().getBytes();
        List<String> out = new ArrayList<>(8);
        for (int i = 0; i < 16; i += 2) {
            int hextet = ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
            String bin = Integer.toBinaryString(hextet);
            out.add("0".repeat(16 - bin.length()) + bin);
        }
        return out;
    }

    private String ultimoDoBloco(BigInteger inicio, BigInteger passo) {
        BigInteger fim = inicio.add(passo).subtract(BigInteger.ONE);
        return new IPv6Address(paraBytes16(fim)).toCompressedString();
    }

    /** Converte um valor de até 128 bits para o array de 16 bytes (big-endian) que a lib aceita. */
    private static byte[] paraBytes16(BigInteger valor) {
        byte[] full = valor.toByteArray();
        byte[] out = new byte[16];
        int copiar = Math.min(full.length, 16);
        for (int i = 0; i < copiar; i++) {
            out[15 - i] = full[full.length - 1 - i];
        }
        return out;
    }

    private record FaixaEspecial(String cidr, String tipo, String descricao) { }

    public record AnaliseIpv6(
            String entrada, String comprimido, String expandido, int prefixo, boolean temPrefixo,
            String rede, String primeiro, String ultimo, String totalEnderecos, String totalPotencia,
            String interfaceId, String tipo, String descricaoTipo, String solicitedNode,
            String reversePtr, List<String> binarioHextetos) { }

    public record DivisaoIpv6(
            String baseCidr, int prefixoBase, int prefixoAlvo, String quantidadeSubredes,
            String enderecosPorSubrede, boolean truncado, int exibidas, List<SubredeIpv6> subredes) { }

    public record SubredeIpv6(int indice, String rede, String prefixoStr, String primeiro, String ultimo) { }

    /**
     * Um hexteto (16 bits): hex, binário completo, quantos bits são de rede (prefixo) e o binário
     * já partido em {@code binRede} (bits de prefixo) e {@code binInterface} (bits de interface).
     */
    public record HextetInfo(int indice, String hex, String bin, int bitsRede,
            String binRede, String binInterface) { }

    /** Uma linha da tabela de delegação de prefixo (/48, /56, /64). */
    public record DelegacaoInfo(String prefixo, String quantidade, String uso) { }

    /** Decomposição profunda para a Análise Didática IPv6. */
    public record DecomposicaoIpv6(AnaliseIpv6 base, List<HextetInfo> hextetos, int bitsRede,
            int bitsInterface, String gatewayLinkLocal, List<DelegacaoInfo> delegacao,
            String ciscoCli, String enunciado) { }
}
