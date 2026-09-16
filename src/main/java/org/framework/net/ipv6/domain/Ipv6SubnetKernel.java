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

    /**
     * PROPÓSITO DE NEGÓCIO: deriva o Interface ID EUI-64 e o endereço SLAAC a partir de um prefixo
     * /64 e um MAC — mostra, passo a passo, como um host "ganha" o endereço IPv6 sozinho.
     *
     * INVARIANTES DO DOMÍNIO: EUI-64 = OUI(3 bytes) + FFFE + NIC(3 bytes), com o 7º bit do 1º byte
     * (U/L) invertido (XOR 0x02). O prefixo é reduzido ao seu /64 (a fronteira do SLAAC).
     *
     * COMPORTAMENTO EM CASO DE FALHA: MAC ou prefixo inválidos lançam {@link Ipv6Exception}.
     */
    public Eui64Result eui64(String prefixo, String mac) {
        String p = prefixo == null ? "" : prefixo.strip();
        if (p.isEmpty()) {
            throw new Ipv6Exception("Informe o prefixo /64 (ex.: 2001:db8:0:1::/64).");
        }
        IPAddressString ps = new IPAddressString(p);
        if (!ps.isValid() || !ps.isIPv6()) {
            throw new Ipv6Exception("Prefixo IPv6 inválido (" + p + ").");
        }
        byte[] rede = ps.getAddress().toIPv6().toPrefixBlock().getLower().getBytes();
        byte[] m = parseMac(mac);

        byte[] full = new byte[16];
        System.arraycopy(rede, 0, full, 0, 8);
        full[8] = (byte) (m[0] ^ 0x02);
        full[9] = m[1];
        full[10] = m[2];
        full[11] = (byte) 0xFF;
        full[12] = (byte) 0xFE;
        full[13] = m[3];
        full[14] = m[4];
        full[15] = m[5];

        IPv6Address addr = new IPv6Address(full);
        String macNorm = String.format("%02x:%02x:%02x:%02x:%02x:%02x",
                m[0] & 0xFF, m[1] & 0xFF, m[2] & 0xFF, m[3] & 0xFF, m[4] & 0xFF, m[5] & 0xFF);
        String iid = String.format("%02x%02x:%02x%02x:%02x%02x:%02x%02x",
                full[8] & 0xFF, full[9] & 0xFF, full[10] & 0xFF, full[11] & 0xFF,
                full[12] & 0xFF, full[13] & 0xFF, full[14] & 0xFF, full[15] & 0xFF);
        String passos = "1) MAC " + macNorm
                + " · 2) inverte o bit U/L do 1º octeto (" + String.format("%02x", m[0] & 0xFF)
                + " XOR 02 = " + String.format("%02x", full[8] & 0xFF)
                + ") · 3) insere FF:FE no meio · 4) Interface ID = " + iid;

        // Grade dos 64 bits do Interface ID, destacando o bit U/L invertido (pos 6) e o FF:FE (pos 24–39).
        List<Ipv6AnaliseRica.HextetoGrade> iidGrade = new ArrayList<>(4);
        for (int h = 0; h < 4; h++) {
            int hextet = ((full[8 + h * 2] & 0xFF) << 8) | (full[8 + h * 2 + 1] & 0xFF);
            List<Ipv6AnaliseRica.BitCelula> cel = new ArrayList<>(16);
            for (int j = 0; j < 16; j++) {
                int pos = h * 16 + j;
                char val = ((hextet >> (15 - j)) & 1) == 1 ? '1' : '0';
                String marca = pos == 6 ? "flip" : (pos >= 24 && pos < 40 ? "fffe" : "iid");
                cel.add(new Ipv6AnaliseRica.BitCelula(pos + 1, 63 - pos, val, 1L << (15 - j),
                        marca + (val == '1' ? " on" : " off")));
            }
            iidGrade.add(new Ipv6AnaliseRica.HextetoGrade(h + 1, String.format("%04x", hextet), cel));
        }

        return new Eui64Result(macNorm, iid, addr.toCompressedString(),
                new IPv6Address(rede).toCompressedString() + "/64", passos, iidGrade);
    }

    /**
     * PROPÓSITO DE NEGÓCIO: gera um prefixo ULA (fd00::/8) com Global ID pseudo-aleatório de 40 bits
     * (RFC 4193) — o "IP privado do IPv6", para uso interno sem coordenação com um RIR.
     *
     * INVARIANTES DO DOMÍNIO: prefixo sempre começa com fd (L=1, local); Global ID de 40 bits;
     * Subnet ID de 16 bits (0–65535). O resultado é um /48 (site) e um /64 (LAN).
     *
     * COMPORTAMENTO EM CASO DE FALHA: Subnet ID fora de 0–65535 lança {@link Ipv6Exception}.
     */
    public UlaResult gerarUla(String subnetId) {
        int sid = parseSubnetId(subnetId);
        byte[] gid = new byte[5];
        new java.security.SecureRandom().nextBytes(gid);

        byte[] p48 = new byte[16];
        p48[0] = (byte) 0xfd;
        System.arraycopy(gid, 0, p48, 1, 5);
        byte[] p64 = p48.clone();
        p64[6] = (byte) ((sid >> 8) & 0xFF);
        p64[7] = (byte) (sid & 0xFF);

        StringBuilder gidHex = new StringBuilder();
        for (byte b : gid) {
            gidHex.append(String.format("%02x", b & 0xFF));
        }
        String explic = "fd (prefixo ULA, L=1) + Global ID de 40 bits pseudo-aleatório (" + gidHex
                + ") + Subnet ID de 16 bits (" + sid + "). Gerado por SecureRandom, conforme RFC 4193.";
        return new UlaResult(new IPv6Address(p48).toCompressedString() + "/48",
                new IPv6Address(p64).toCompressedString() + "/64", gidHex.toString(), explic);
    }

    /**
     * PROPÓSITO DE NEGÓCIO: compara dois endereços/prefixos IPv6 lado a lado — quantos bits
     * compartilham, se caem na mesma /64 (mesma LAN), se um prefixo contém o outro endereço e a
     * distância numérica entre eles. É o análogo IPv6 do Comparador da Análise IPv4.
     *
     * INVARIANTES DO DOMÍNIO: a comparação é sobre os 128 bits reais (sem broadcast, sem "hosts
     * úteis"); "mesma LAN" é definida pela fronteira do SLAAC (/64), não por classe. A contenção usa
     * o prefixo declarado de cada lado; sem prefixo, o endereço vale como /128.
     *
     * COMPORTAMENTO EM CASO DE FALHA: qualquer lado malformado propaga {@link Ipv6Exception} (via
     * {@link #analisar}); nunca devolve comparação parcial.
     */
    public ComparacaoIpv6 comparar(String a, String b) {
        AnaliseIpv6 ra = analisar(a);
        AnaliseIpv6 rb = analisar(b);
        IPv6Address ha = hostDe(a);
        IPv6Address hb = hostDe(b);
        byte[] ba = ha.getBytes();
        byte[] bb = hb.getBytes();
        BigInteger va = new BigInteger(1, ba);
        BigInteger vb = new BigInteger(1, bb);

        boolean mesmoEndereco = va.equals(vb);
        int bitsComuns = bitsComuns(ba, bb);
        boolean mesmaLan = bitsComuns >= 64;
        BigInteger distancia = va.subtract(vb).abs();

        String explic = mesmoEndereco
                ? "São o mesmo endereço (distância zero). Prefixo comum: /128."
                : "Compartilham os primeiros " + bitsComuns + " bits (prefixo comum /" + bitsComuns
                        + "). " + (mesmaLan ? "Estão na mesma /64 — mesma LAN." : "Estão em /64 diferentes.")
                        + " Distância: " + distancia + " endereço(s) entre A e B.";

        // Grade dos 128 bits de A, marcando o prefixo comum (verde) x a partir da divergência (âmbar).
        List<Ipv6AnaliseRica.HextetoGrade> gradeComum = new ArrayList<>(8);
        for (int h = 0; h < 8; h++) {
            int hextet = ((ba[h * 2] & 0xFF) << 8) | (ba[h * 2 + 1] & 0xFF);
            List<Ipv6AnaliseRica.BitCelula> cel = new ArrayList<>(16);
            for (int j = 0; j < 16; j++) {
                int pos = h * 16 + j;
                char val = ((hextet >> (15 - j)) & 1) == 1 ? '1' : '0';
                String marca = pos < bitsComuns ? "comum" : "diverge";
                cel.add(new Ipv6AnaliseRica.BitCelula(pos + 1, 127 - pos, val, 1L << (15 - j),
                        marca + (val == '1' ? " on" : " off")));
            }
            gradeComum.add(new Ipv6AnaliseRica.HextetoGrade(h + 1, hexDoBin(
                    "0".repeat(16 - Integer.toBinaryString(hextet).length()) + Integer.toBinaryString(hextet)), cel));
        }

        return new ComparacaoIpv6(
                ra.entrada(), ra.comprimido(), ra.prefixo(), ra.temPrefixo(), ra.tipo(),
                rb.entrada(), rb.comprimido(), rb.prefixo(), rb.temPrefixo(), rb.tipo(),
                mesmoEndereco, mesmaLan, bitsComuns, "/" + bitsComuns,
                contencao(ra, rb, ha, hb), distancia.toString(), explic, gradeComum);
    }

    /** Host (sem prefixo, sem zone index) já validado por {@link #analisar}. */
    private IPv6Address hostDe(String entrada) {
        String bruto = entrada == null ? "" : entrada.strip().replace("\"", "").replace("'", "");
        int idx = bruto.indexOf('%');
        if (idx >= 0) {
            bruto = bruto.substring(0, idx).strip();
        }
        return new IPAddressString(bruto).getAddress().toIPv6().withoutPrefixLength();
    }

    /** Quantidade de bits iniciais idênticos entre dois endereços de 128 bits (0–128). */
    private static int bitsComuns(byte[] a, byte[] b) {
        int bits = 0;
        for (int i = 0; i < 16; i++) {
            int x = (a[i] ^ b[i]) & 0xFF;
            if (x == 0) {
                bits += 8;
                continue;
            }
            bits += Integer.numberOfLeadingZeros(x) - 24;
            break;
        }
        return bits;
    }

    private String contencao(AnaliseIpv6 ra, AnaliseIpv6 rb, IPv6Address ha, IPv6Address hb) {
        boolean aContemB = ra.temPrefixo() && prefixoBloco(ha, ra.prefixo()).contains(hb);
        boolean bContemA = rb.temPrefixo() && prefixoBloco(hb, rb.prefixo()).contains(ha);
        if (aContemB && bContemA) {
            return "Os dois prefixos coincidem: descrevem o mesmo bloco.";
        }
        if (aContemB) {
            return "A rede A (/" + ra.prefixo() + ") contém o endereço B.";
        }
        if (bContemA) {
            return "A rede B (/" + rb.prefixo() + ") contém o endereço A.";
        }
        if (!ra.temPrefixo() && !rb.temPrefixo()) {
            return "Nenhum lado tem prefixo declarado: comparados como endereços /128.";
        }
        return "Nenhum dos prefixos declarados contém o outro endereço.";
    }

    /**
     * PROPÓSITO DE NEGÓCIO: monta o modelo de EXIBIÇÃO rica da Análise IPv6 — o gêmeo IPv6 do
     * resultado da Análise Didática IPv4 (grade de 128 bits, aplicação do prefixo, capacidade,
     * passo-a-passo, linha do tempo, delegação, régua de /64, referência, conversão, GRC, CLI,
     * termos, banner e resumo tipo prova). Reusa {@link #analisar} e {@link #decompor} como fonte.
     *
     * INVARIANTES DO DOMÍNIO: sem broadcast e sem "hosts úteis"; toda contagem é 2^(128−prefixo).
     *
     * COMPORTAMENTO EM CASO DE FALHA: propaga {@link Ipv6Exception} de {@code analisar}.
     */
    public Ipv6AnaliseRica.Resultado analisarRica(String entrada, int reguaCount) {
        DecomposicaoIpv6 dec = decompor(entrada);
        AnaliseIpv6 base = dec.base();
        int prefixo = base.prefixo();

        List<Ipv6AnaliseRica.HextetoGrade> grade = new ArrayList<>(8);
        List<String> bins = base.binarioHextetos();
        for (int h = 0; h < 8; h++) {
            String bin = bins.get(h);
            List<Ipv6AnaliseRica.BitCelula> celulas = new ArrayList<>(16);
            for (int j = 0; j < 16; j++) {
                int global = h * 16 + j;
                char val = bin.charAt(j);
                boolean rede = global < prefixo;
                String css = (rede ? "rede" : "iface") + (val == '1' ? " on" : " off");
                celulas.add(new Ipv6AnaliseRica.BitCelula(
                        global + 1, 127 - global, val, 1L << (15 - j), css));
            }
            grade.add(new Ipv6AnaliseRica.HextetoGrade(h + 1, hexDoBin(bin), celulas));
        }

        List<Ipv6AnaliseRica.LinhaPrefixo> aplicacao = List.of(
                new Ipv6AnaliseRica.LinhaPrefixo("Máscara de prefixo",
                        "/" + prefixo + " — " + prefixo + " bits 1 seguidos de " + (128 - prefixo) + " bits 0",
                        "and-row-mask"),
                new Ipv6AnaliseRica.LinhaPrefixo("Rede (endereço & prefixo)", base.rede(), "and-row-result"),
                new Ipv6AnaliseRica.LinhaPrefixo("Bits de interface (& complemento)",
                        base.interfaceId(), "and-row-ip"),
                new Ipv6AnaliseRica.LinhaPrefixo("Fronteira rede/interface",
                        "após o bit " + prefixo + " (hexteto " + (Math.min(7, prefixo / 16) + 1)
                                + ", nibble " + (((prefixo % 16) / 4) + 1) + ")", "and-row-wild"));

        List<Ipv6AnaliseRica.PassoWizard> wizard = List.of(
                new Ipv6AnaliseRica.PassoWizard("🧭", "Tipo e escopo", "Classificar pela faixa IANA",
                        base.tipo() + " — " + base.descricaoTipo()),
                new Ipv6AnaliseRica.PassoWizard("📏", "Prefixo", "Ler o /" + prefixo + " (fronteira rede/interface)",
                        "/" + prefixo + " · " + prefixo + " bits de rede, " + (128 - prefixo) + " de interface"),
                new Ipv6AnaliseRica.PassoWizard("🧠", "Rede (bloco)", "Zerar os bits de interface", base.rede()),
                new Ipv6AnaliseRica.PassoWizard("📣", "Interface / SLAAC",
                        "Os bits baixos identificam a interface", base.interfaceId()));

        Ipv6AnaliseRica.LinhaTempo linhaTempo = new Ipv6AnaliseRica.LinhaTempo(
                "faixa", base.rede(), base.primeiro(), base.ultimo(), base.entrada());

        List<SubredeIpv6> regua = reguaSubredes(entrada, prefixo, reguaCount);

        List<Ipv6AnaliseRica.ReferenciaPrefixo> referencia = referenciaPrefixos(prefixo);
        List<Ipv6AnaliseRica.ConversaoLinha> conversao = List.of(
                new Ipv6AnaliseRica.ConversaoLinha("1 bit", "1", "—", "—", "—"),
                new Ipv6AnaliseRica.ConversaoLinha("1 nibble", "4", "1", "—", "1 dígito hex"),
                new Ipv6AnaliseRica.ConversaoLinha("1 hexteto", "16", "4", "1", "4 hex (0000–ffff)"),
                new Ipv6AnaliseRica.ConversaoLinha("1 endereço IPv6", "128", "32", "8", "8 hextetos"));

        List<String> grc = List.of(
                "Escopo: " + base.tipo() + " — " + base.descricaoTipo() + ".",
                "Superfície: 2^" + (128 - prefixo) + " endereços no bloco; varredura completa é inviável"
                        + " (vantagem de segurança do IPv6 sobre o IPv4).",
                grcRecomendacao(base.tipo()));

        List<Ipv6AnaliseRica.DicaSeguranca> dicas = segurancaDicas(base.tipo());

        List<Ipv6AnaliseRica.TermoRede> termos = List.of(
                new Ipv6AnaliseRica.TermoRede("SLAAC", "Autoconfiguração do host a partir do prefixo /64 + Interface ID (RA)."),
                new Ipv6AnaliseRica.TermoRede("DHCPv6", "Atribuição stateful/stateless coordenada por servidor (opcional)."),
                new Ipv6AnaliseRica.TermoRede("NDP / RA", "Neighbor Discovery substitui o ARP; Router Advertisement anuncia o prefixo."),
                new Ipv6AnaliseRica.TermoRede("Broadcast", "Não existe em IPv6 — substituído por multicast."),
                new Ipv6AnaliseRica.TermoRede("Solicited-node", base.solicitedNode() + " (multicast usado pelo NDP)."));

        String ospfv3 = dec.ciscoCli();
        String eigrp = "ipv6 unicast-routing\nipv6 router eigrp 100\n no shutdown\ninterface GigabitEthernet0/0\n ipv6 eigrp 100";
        String ciscoNota = "Em IPv6 não há wildcard: o roteamento é habilitado por interface, não por 'network' com máscara curinga.";

        List<Ipv6AnaliseRica.BannerItem> bannerItens = List.of(
                new Ipv6AnaliseRica.BannerItem("Tipo", base.tipo() + " — " + base.descricaoTipo()),
                new Ipv6AnaliseRica.BannerItem("Prefixo", "/" + prefixo + " (LAN padrão é /64 via SLAAC)"),
                new Ipv6AnaliseRica.BannerItem("Endereços", "2^" + (128 - prefixo) + " = " + base.totalEnderecos()),
                new Ipv6AnaliseRica.BannerItem("Sem broadcast", "todos os endereços do bloco são atribuíveis"));

        List<Ipv6AnaliseRica.ItemProva> provaItens = List.of(
                new Ipv6AnaliseRica.ItemProva("🏷️ Tipo", base.tipo()),
                new Ipv6AnaliseRica.ItemProva("📏 Prefixo", "/" + prefixo),
                new Ipv6AnaliseRica.ItemProva("🔢 Endereços", "2^" + (128 - prefixo) + " = " + base.totalEnderecos()),
                new Ipv6AnaliseRica.ItemProva("🌐 Rede", base.rede()),
                new Ipv6AnaliseRica.ItemProva("🔌 Interface ID", base.interfaceId()));
        String provaFrase = base.comprimido() + "/" + prefixo + " é " + base.tipo()
                + ": " + prefixo + " bits de rede, " + (128 - prefixo) + " de interface, 2^" + (128 - prefixo)
                + " endereços, sem broadcast.";

        String[] tema = temaPorPrefixo(prefixo);

        String textoCopia = montarTextoCopia(base, prefixo);

        return new Ipv6AnaliseRica.Resultado(
                base, dec.bitsRede(), dec.bitsInterface(), grade, aplicacao,
                base.totalEnderecos(), base.totalPotencia(), dec.gatewayLinkLocal(),
                dec.delegacao(), regua, reguaCount, wizard, linhaTempo, referencia, conversao,
                grc, dicas, termos, ospfv3, eigrp, ciscoNota,
                "Contexto do bloco IPv6", "Como este prefixo se encaixa no plano de endereçamento",
                bannerItens, provaFrase, provaItens,
                tema[0], tema[1], tema[2], tema[3], dec.enunciado(), textoCopia);
    }

    /** Próximas {@code n} sub-redes contíguas do mesmo prefixo (régua), a partir do bloco base. */
    private List<SubredeIpv6> reguaSubredes(String entrada, int prefixo, int n) {
        IPv6Address addr = new IPAddressString(hostDe(entrada).toCompressedString() + "/" + prefixo)
                .getAddress().toIPv6().toPrefixBlock();
        BigInteger inicio = new BigInteger(1, addr.getLower().getBytes());
        BigInteger passo = BigInteger.TWO.pow(128 - prefixo);
        BigInteger teto = BigInteger.TWO.pow(128);
        List<SubredeIpv6> out = new ArrayList<>();
        for (int i = 0; i < Math.max(1, n); i++) {
            BigInteger valor = inicio.add(passo.multiply(BigInteger.valueOf(i)));
            if (valor.compareTo(teto) >= 0) {
                break;
            }
            IPv6Address rede = new IPv6Address(paraBytes16(valor));
            out.add(new SubredeIpv6(i + 1, rede.toCompressedString(), "/" + prefixo,
                    rede.toCompressedString(), ultimoDoBloco(valor, passo)));
        }
        return out;
    }

    private List<Ipv6AnaliseRica.ReferenciaPrefixo> referenciaPrefixos(int prefixoAtual) {
        int[] prefixos = {32, 40, 48, 52, 56, 60, 64};
        List<Ipv6AnaliseRica.ReferenciaPrefixo> out = new ArrayList<>(prefixos.length);
        for (int p : prefixos) {
            String lans = p <= 64 ? BigInteger.TWO.pow(64 - p).toString() : "—";
            String ends = "2^" + (128 - p);
            String fronteira = (p % 4 == 0) ? "alinhado a nibble" : "no meio de um nibble";
            out.add(new Ipv6AnaliseRica.ReferenciaPrefixo("/" + p, lans, ends, fronteira, p == prefixoAtual));
        }
        return out;
    }

    private String grcRecomendacao(String tipo) {
        return switch (tipo) {
            case "Global unicast" -> "Recomendação: firewall stateful (sem NAT para proteger), RA Guard e DHCPv6 snooping no enlace.";
            case "ULA / Privado" -> "Recomendação: uso interno; não roteie o fc00::/7 para a Internet.";
            case "Link-local" -> "Recomendação: válido apenas no enlace; nunca roteável — use para NDP e vizinhança.";
            case "Documentação" -> "Recomendação: 2001:db8::/32 é só para exemplos/aula — nunca em produção.";
            case "Multicast" -> "Recomendação: controle de grupos (MLD) e filtragem de escopo.";
            default -> "Recomendação: valide o escopo antes de rotear e aplique filtragem por prefixo.";
        };
    }

    private List<Ipv6AnaliseRica.DicaSeguranca> segurancaDicas(String tipo) {
        List<Ipv6AnaliseRica.DicaSeguranca> out = new ArrayList<>();
        out.add(new Ipv6AnaliseRica.DicaSeguranca("info", "🕵️",
                "Privacidade SLAAC (RFC 4941): use endereços temporários para não expor o MAC no Interface ID."));
        out.add(new Ipv6AnaliseRica.DicaSeguranca("warning", "🛡️",
                "RA Guard e ND inspection no switch evitam Router Advertisement forjado (ataque clássico de IPv6)."));
        out.add(new Ipv6AnaliseRica.DicaSeguranca("danger", "🔥",
                "IPv6 não tem NAT como fronteira — o firewall stateful é obrigatório, não opcional."));
        if ("ULA / Privado".equals(tipo)) {
            out.add(new Ipv6AnaliseRica.DicaSeguranca("success", "🔒",
                    "ULA (fd00::/8) fica contido na organização — bom para serviços internos."));
        }
        return out;
    }

    private String[] temaPorPrefixo(int prefixo) {
        if (prefixo >= 64) {
            return new String[] {"Verde", "prefixo /64+ (uma LAN ou mais específico)", "#3fb950", "#238636"};
        }
        if (prefixo >= 48) {
            return new String[] {"Azul", "prefixo /48–/63 (site / delegação)", "#58a6ff", "#1f6feb"};
        }
        if (prefixo >= 32) {
            return new String[] {"Âmbar", "prefixo /32–/47 (alocação de provedor)", "#d29922", "#9e6a03"};
        }
        return new String[] {"Vermelho", "prefixo /0–/31 (bloco enorme, raro)", "#f85149", "#da3633"};
    }

    private String montarTextoCopia(AnaliseIpv6 base, int prefixo) {
        return "Análise IPv6\n"
                + "Entrada: " + base.entrada() + "\n"
                + "Comprimido: " + base.comprimido() + "\n"
                + "Expandido: " + base.expandido() + "\n"
                + "Tipo: " + base.tipo() + " (" + base.descricaoTipo() + ")\n"
                + "Prefixo: /" + prefixo + "\n"
                + "Rede: " + base.rede() + "\n"
                + "Faixa: " + base.primeiro() + " — " + base.ultimo() + "\n"
                + "Endereços: 2^" + (128 - prefixo) + " = " + base.totalEnderecos() + "\n"
                + "Interface ID: " + base.interfaceId() + "\n"
                + "Solicited-node: " + base.solicitedNode() + "\n"
                + "Reverso: " + base.reversePtr();
    }

    private static byte[] parseMac(String mac) {
        String limpo = (mac == null ? "" : mac).replaceAll("[.:\\-\\s]", "");
        if (!limpo.matches("[0-9a-fA-F]{12}")) {
            throw new Ipv6Exception("MAC inválido. Use 12 dígitos hex (ex.: 00:1a:2b:3c:4d:5e).");
        }
        byte[] out = new byte[6];
        for (int i = 0; i < 6; i++) {
            out[i] = (byte) Integer.parseInt(limpo.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static int parseSubnetId(String subnetId) {
        String txt = subnetId == null ? "" : subnetId.strip();
        if (txt.isEmpty()) {
            return 0;
        }
        int base = txt.toLowerCase().startsWith("0x") ? 16 : 10;
        try {
            int v = Integer.parseInt(base == 16 ? txt.substring(2) : txt, base);
            if (v < 0 || v > 65535) {
                throw new Ipv6Exception("Subnet ID fora da faixa: use 0 a 65535.");
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new Ipv6Exception("Subnet ID inválido: use um número de 0 a 65535 (ou 0x____).");
        }
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

    /** Resultado do EUI-64: MAC normalizado, Interface ID, endereço SLAAC, passos e grade de bits do IID. */
    public record Eui64Result(String mac, String interfaceId, String enderecoSlaac,
            String prefixoRede, String passos, List<Ipv6AnaliseRica.HextetoGrade> iidGrade) { }

    /** Resultado da geração de ULA (RFC 4193): prefixo /48, /64, o Global ID e a explicação. */
    public record UlaResult(String ula48, String ula64, String globalId, String explicacao) { }

    /**
     * Comparação de dois endereços/prefixos IPv6: forma canônica e tipo de cada lado, se são o mesmo
     * endereço, se caem na mesma /64 (LAN), quantos bits compartilham, a relação de contenção e a
     * distância numérica entre eles.
     */
    public record ComparacaoIpv6(
            String aEntrada, String aComprimido, int aPrefixo, boolean aTemPrefixo, String aTipo,
            String bEntrada, String bComprimido, int bPrefixo, boolean bTemPrefixo, String bTipo,
            boolean mesmoEndereco, boolean mesmaLan, int bitsComuns, String prefixoComum,
            String contencao, String distancia, String explicacao,
            List<Ipv6AnaliseRica.HextetoGrade> gradeComum) { }
}
