package org.framework.net.analiseDidatica.domain;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class Ipv6Calculator {

    public Map<String, Object> processar(String ipv6S) {
        String raw = ipv6S == null ? "" : ipv6S.strip().replace("\"", "").replace("'", "");
        if (raw.isEmpty()) {
            throw new EntradaInvalidaException("IPv6 vazio.");
        }

        String zone = "";
        String base = raw;
        int zoneIdx = raw.indexOf('%');
        if (zoneIdx >= 0) {
            base = raw.substring(0, zoneIdx).strip();
            zone = raw.substring(zoneIdx + 1).strip();
            if (zone.isEmpty()) {
                throw new EntradaInvalidaException("IPv6 com zone index inválido (sufixo após % está vazio).");
            }
        }

        IPAddressString parser = new IPAddressString(base);
        if (!parser.isIPv6()) {
            throw new EntradaInvalidaException("IPv6 inválido: formato não reconhecido.");
        }
        IPAddress addr = parser.getAddress().toIPv6();
        if (addr == null) {
            throw new EntradaInvalidaException("IPv6 inválido.");
        }

        byte[] bytes = addr.getBytes();
        Tipo inet = Tipo.de(bytes);
        BigInteger value = new BigInteger(1, bytes);
        String bits = value.toString(2);
        bits = "0".repeat(Math.max(0, 128 - bits.length())) + bits;
        List<String> blocos16 = new ArrayList<>();
        for (int i = 0; i < 128; i += 16) {
            blocos16.add(bits.substring(i, i + 16));
        }

        String[] hextetos = addr.toFullString().split(":");
        String primeiros64 = String.join(":", List.of(hextetos).subList(0, 4));
        String ultimos64 = String.join(":", List.of(hextetos).subList(4, 8));
        String rede64 = new IPAddressString(addr.toCanonicalString() + "/64").getAddress().toZeroHost().toCanonicalString();

        String tipo = classificar(inet);
        String prefixoSugerido = prefixoSugeridoPorTipo(inet);
        String faixa = faixaReferencia(inet);
        String uso = uso(inet);
        String roteavel = isGlobalUnicast(inet) ? "Sim" : "Não";
        List<String> sinais = sinais(inet, addr);
        String comprimido = addr.toCanonicalString() + (zone.isEmpty() ? "" : "%" + zone);

        List<Map<String, Object>> itensExibicao = List.of(
                item("📥", "IPv6 informado", raw),
                item("🗜️", "Compactação IPv6", comprimido),
                item("🧱", "Expansão IPv6", addr.toFullString()),
                item("🏷️", "Tipo do endereço", tipo),
                item("📍", "Faixa", faixa),
                item("⚙️", "Uso", uso),
                item("🌍", "Roteável na internet", roteavel),
                item("📌", "Prefixo sugerido", prefixoSugerido),
                item("🌐", "Rede estimada (/64)", rede64 + "/64"),
                item("🆔", "Zone index", zone.isEmpty() ? "—" : zone),
                item("🧠", "Primeiros 64 bits", primeiros64),
                item("🔚", "Últimos 64 bits", ultimos64),
                item("🔢", "Total de bits", "128"),
                item("🧾", "Reverse DNS (PTR)", addr.toReverseDNSLookupString()),
                item("🛡️", "Sinais especiais", String.join(", ", sinais)),
                item("✅", "Resumo GRC", grcIpv6(inet))
        );

        String textoCopia = "Entrada: " + raw + "\n\nResultado:\nTipo: " + tipo + "\nFaixa: " + faixa
                + "\nUso: " + uso + "\nRoteável na internet: " + roteavel.toLowerCase();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entrada", raw);
        out.put("comprimido", comprimido);
        out.put("expandido", addr.toFullString());
        out.put("tipo", tipo);
        out.put("escopo", escopo(inet));
        out.put("faixa", faixa);
        out.put("uso", uso);
        out.put("roteavel", roteavel);
        out.put("prefixo_sugerido", prefixoSugerido);
        out.put("blocos_16", blocos16);
        out.put("hextetos", List.of(hextetos));
        out.put("primeiros_64", primeiros64);
        out.put("ultimos_64", ultimos64);
        out.put("rede_64", rede64);
        out.put("reverse_pointer", addr.toReverseDNSLookupString());
        out.put("sinais_especiais", sinais);
        out.put("grc_ipv6", grcIpv6(inet));
        out.put("itens_exibicao", itensExibicao);
        out.put("zone_index", zone.isEmpty() ? "—" : zone);
        out.put("texto_copia", textoCopia);
        return out;
    }

    private static Map<String, Object> item(String icone, String campo, String valor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("icone", icone);
        m.put("campo", campo);
        m.put("valor", valor);
        return m;
    }

    /**
     * PROPÓSITO DE NEGÓCIO: tipo do endereço IPv6 que o aluno vê (tipo, faixa, uso, "roteável na
     * Internet"), derivado só dos 128 bits — sem {@code InetAddress}, que devolve {@code Inet4Address}
     * para o IPv4-mapeado e derrubava a análise.
     *
     * INVARIANTES DO DOMÍNIO: "Global unicast" exige 2000::/3 (RFC 4291) e fica fora de 2001:db8::/32
     * (documentação, RFC 3849); tudo que não casa com uma faixa conhecida é "Outro/Reservado" e NÃO é
     * roteável — nunca "global por exclusão". Ordem de teste: da faixa mais específica para a geral.
     *
     * COMPORTAMENTO EM CASO DE FALHA: nunca lança; bytes com tamanho diferente de 16 caem em OUTRO.
     */
    enum Tipo {
        NAO_ESPECIFICADO("Não especificado", "::/128", "Não especificado"),
        LOOPBACK("Loopback", "::1/128", "Host local"),
        IPV4_MAPEADO("IPv4-mapeado", "::ffff:0:0/96", "IPv4 representado em IPv6 (pilha dupla)"),
        LINK_LOCAL("Link-local", "fe80::/10", "Enlace local (não roteável)"),
        ULA("ULA/Privado", "fc00::/7", "Site local/ULA (rede privada IPv6)"),
        MULTICAST("Multicast", "ff00::/8", "Multicast (grupo)"),
        DOCUMENTACAO("Documentação", "2001:db8::/32", "Documentação e exemplos (não roteável)"),
        GLOBAL("Global unicast", "2000::/3", "Global (roteável na Internet)"),
        OUTRO("Outro/Reservado", "Outro/Variável", "Reservado/Especial");

        final String rotulo;
        final String faixa;
        final String escopo;

        Tipo(String rotulo, String faixa, String escopo) {
            this.rotulo = rotulo;
            this.faixa = faixa;
            this.escopo = escopo;
        }

        static Tipo de(byte[] b) {
            if (b == null || b.length != 16) {
                return OUTRO;
            }
            boolean zeros96 = true;
            for (int i = 0; i < 12; i++) {
                if (b[i] != 0) {
                    zeros96 = false;
                    break;
                }
            }
            if (zeros96 && b[12] == 0 && b[13] == 0 && b[14] == 0) {
                if (b[15] == 0) return NAO_ESPECIFICADO;
                if (b[15] == 1) return LOOPBACK;
            }
            boolean zeros80 = true;
            for (int i = 0; i < 10; i++) {
                if (b[i] != 0) {
                    zeros80 = false;
                    break;
                }
            }
            if (zeros80 && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) return IPV4_MAPEADO;
            if ((b[0] & 0xFF) == 0xFE && (b[1] & 0xC0) == 0x80) return LINK_LOCAL;
            if ((b[0] & 0xFE) == 0xFC) return ULA;
            if ((b[0] & 0xFF) == 0xFF) return MULTICAST;
            if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x01 && (b[2] & 0xFF) == 0x0D && (b[3] & 0xFF) == 0xB8) {
                return DOCUMENTACAO;
            }
            if ((b[0] & 0xE0) == 0x20) return GLOBAL;
            return OUTRO;
        }
    }

    private static boolean isGlobalUnicast(Tipo t) {
        return t == Tipo.GLOBAL;
    }

    private static String classificar(Tipo t) {
        return t.rotulo;
    }

    private static String escopo(Tipo t) {
        return t.escopo;
    }

    private static List<String> sinais(Tipo t, IPAddress addr) {
        List<String> sinais = new ArrayList<>();
        if (t != Tipo.OUTRO && t != Tipo.NAO_ESPECIFICADO) {
            sinais.add(t.rotulo + " (" + t.faixa + ")");
        }
        if (t != Tipo.IPV4_MAPEADO && addr.isIPv4Convertible()) sinais.add("IPv4-convertível");
        return sinais.isEmpty() ? List.of("Sem sinais especiais") : sinais;
    }

    private static String faixaReferencia(Tipo t) {
        return t.faixa;
    }

    private static String uso(Tipo t) {
        return switch (t) {
            case LOOPBACK -> "Testes internos e comunicação dentro do próprio dispositivo";
            case LINK_LOCAL -> "Comunicação local no enlace, auto-configuração (NDP)";
            case ULA -> "Comunicação em rede privada, similar ao RFC1918 (ULA)";
            case MULTICAST -> "Transmissão para um grupo de dispositivos";
            case DOCUMENTACAO -> "Exemplos em livros e documentação (RFC 3849); nunca roteado na Internet";
            case IPV4_MAPEADO -> "Representa um IPv4 dentro de um socket IPv6 (pilha dupla); não trafega assim na rede";
            case GLOBAL -> "Comunicação pública na Internet";
            case NAO_ESPECIFICADO -> "Endereço de origem 'nenhum' (ex.: DAD antes de ter endereço)";
            case OUTRO -> "Especial ou reservado";
        };
    }

    /**
     * PROPÓSITO DE NEGÓCIO: sugere o prefixo típico do endereço conforme o seu tipo, em vez de
     * cravar "/64" para tudo — /64 é padrão de LAN/SLAAC, mas não é resposta universal.
     *
     * INVARIANTES DO DOMÍNIO: loopback e não-especificado são /128; multicast é endereço de grupo
     * (/128 para um grupo específico); unicast (link-local, ULA, global) usa /64 (fronteira do SLAAC).
     *
     * COMPORTAMENTO EM CASO DE FALHA: nunca lança; para tipo indeterminado devolve o padrão "/64".
     */
    private static String prefixoSugeridoPorTipo(Tipo t) {
        if (t == Tipo.LOOPBACK || t == Tipo.NAO_ESPECIFICADO) {
            return "/128";
        }
        if (t == Tipo.MULTICAST) {
            return "/128 (endereço de grupo)";
        }
        return "/64";
    }

    private static String grcIpv6(Tipo addr) {
        if (addr == Tipo.DOCUMENTACAO) {
            return "Documentação: prefixo de exemplo; se aparecer em tráfego real, é configuração copiada de livro.";
        }
        if (addr == Tipo.LINK_LOCAL) {
            return "Endereço de enlace local: válido para segmento local e troubleshooting, sem roteamento externo.";
        }
        if (addr == Tipo.ULA) {
            return "ULA: adequado para ambientes internos; manter ACL e segmentação de tráfego leste-oeste.";
        }
        if (isGlobalUnicast(addr)) {
            return "Global unicast: requer hardening de borda, filtros e monitoramento contínuo de exposição.";
        }
        if (addr == Tipo.MULTICAST) {
            return "Multicast: revisar escopo e assinaturas de grupo para evitar tráfego excessivo.";
        }
        return "Revisar contexto operacional para validar uso e escopo deste endereço IPv6.";
    }
}
