package org.framework.net.analiseDidatica.domain;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;

import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;
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

        Inet6Address inet;
        try {
            inet = (Inet6Address) InetAddress.getByName(base);
        } catch (Exception ex) {
            throw new EntradaInvalidaException("IPv6 inválido: " + ex.getMessage());
        }

        byte[] bytes = addr.getBytes();
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
        String faixa = faixaReferencia(inet);
        String uso = uso(inet);
        String roteavel = isGlobalUnicast(inet) ? "Sim" : "Não";
        List<String> sinais = sinais(inet, addr);
        String comprimido = addr.toCompressedString() + (zone.isEmpty() ? "" : "%" + zone);

        List<Map<String, Object>> itensExibicao = List.of(
                item("📥", "IPv6 informado", raw),
                item("🗜️", "Compactação IPv6", comprimido),
                item("🧱", "Expansão IPv6", addr.toFullString()),
                item("🏷️", "Tipo do endereço", tipo),
                item("📍", "Faixa", faixa),
                item("⚙️", "Uso", uso),
                item("🌍", "Roteável na internet", roteavel),
                item("📌", "Prefixo sugerido", "/64"),
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
        out.put("prefixo_sugerido", "/64");
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

    private static boolean isUla(Inet6Address addr) {
        byte[] b = addr.getAddress();
        return (b[0] & 0xFE) == 0xFC;
    }

    private static boolean isGlobalUnicast(Inet6Address addr) {
        return !addr.isLoopbackAddress() && !addr.isLinkLocalAddress() && !isUla(addr)
                && !addr.isMulticastAddress() && !addr.isAnyLocalAddress();
    }

    private static String classificar(Inet6Address addr) {
        if (addr.isLoopbackAddress()) return "Loopback";
        if (addr.isLinkLocalAddress()) return "Link-local";
        if (isUla(addr)) return "ULA/Privado";
        if (addr.isMulticastAddress()) return "Multicast";
        if (isGlobalUnicast(addr)) return "Global unicast";
        return "Outro/Reservado";
    }

    private static String escopo(Inet6Address addr) {
        if (addr.isLoopbackAddress()) return "Host local";
        if (addr.isLinkLocalAddress()) return "Enlace local (não roteável)";
        if (isUla(addr)) return "Site local/ULA (rede privada IPv6)";
        if (isGlobalUnicast(addr)) return "Global (roteável na Internet)";
        if (addr.isMulticastAddress()) return "Multicast (grupo)";
        if (addr.isAnyLocalAddress()) return "Não especificado";
        return "Reservado/Especial";
    }

    private static List<String> sinais(Inet6Address inet, IPAddress addr) {
        List<String> sinais = new ArrayList<>();
        if (inet.isLoopbackAddress()) sinais.add("Loopback (::1)");
        if (inet.isLinkLocalAddress()) sinais.add("Link-local (fe80::/10)");
        if (isUla(inet)) sinais.add("ULA (fc00::/7)");
        if (isGlobalUnicast(inet)) sinais.add("Global (2000::/3)");
        if (inet.isMulticastAddress()) sinais.add("Multicast (ff00::/8)");
        if (addr.isIPv4Convertible()) sinais.add("IPv4-convertível");
        return sinais.isEmpty() ? List.of("Sem sinais especiais") : sinais;
    }

    private static String faixaReferencia(Inet6Address addr) {
        if (addr.isLoopbackAddress()) return "::1/128";
        if (addr.isLinkLocalAddress()) return "fe80::/10";
        if (isUla(addr)) return "fc00::/7";
        if (addr.isMulticastAddress()) return "ff00::/8";
        if (isGlobalUnicast(addr)) return "2000::/3";
        return "Outro/Variável";
    }

    private static String uso(Inet6Address addr) {
        if (addr.isLoopbackAddress()) return "Testes internos e comunicação dentro do próprio dispositivo";
        if (addr.isLinkLocalAddress()) return "Comunicação local no enlace, auto-configuração (NDP)";
        if (isUla(addr)) return "Comunicação em rede privada, similar ao RFC1918 (ULA)";
        if (addr.isMulticastAddress()) return "Transmissão para um grupo de dispositivos";
        if (isGlobalUnicast(addr)) return "Comunicação pública na Internet";
        return "Especial ou reservado";
    }

    private static String grcIpv6(Inet6Address addr) {
        if (addr.isLinkLocalAddress()) {
            return "Endereço de enlace local: válido para segmento local e troubleshooting, sem roteamento externo.";
        }
        if (isUla(addr)) {
            return "ULA: adequado para ambientes internos; manter ACL e segmentação de tráfego leste-oeste.";
        }
        if (isGlobalUnicast(addr)) {
            return "Global unicast: requer hardening de borda, filtros e monitoramento contínuo de exposição.";
        }
        if (addr.isMulticastAddress()) {
            return "Multicast: revisar escopo e assinaturas de grupo para evitar tráfego excessivo.";
        }
        return "Revisar contexto operacional para validar uso e escopo deste endereço IPv6.";
    }
}
