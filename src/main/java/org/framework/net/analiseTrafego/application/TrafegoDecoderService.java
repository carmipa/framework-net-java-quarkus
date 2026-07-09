package org.framework.net.analiseTrafego.application;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.analiseTrafego.domain.model.ResultadoDecodificacao;
import org.framework.net.analiseTrafego.domain.model.ResultadoDecodificacao.Camada;
import org.framework.net.analiseTrafego.domain.model.ResultadoDecodificacao.Campo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decodificador didático de pacotes: recebe um <em>hex dump</em> de um quadro/pacote e
 * decompõe os cabeçalhos Ethernet / ARP / IPv4 / IPv6 / TCP / UDP / ICMP, explicando cada campo.
 *
 * <p>É pura análise de bytes (sem privilégios, sem captura ao vivo) — a captura de tráfego real
 * exigiria libpcap + acesso de root e um agente nativo separado.
 */
@ApplicationScoped
public class TrafegoDecoderService {

    private static final int MAX_BYTES = 65535;
    private static final Set<String> INICIOS_VALIDOS =
            Set.of("ethernet", "ipv4", "ipv6", "arp", "tcp", "udp", "icmp");

    private static final Map<Integer, String> PORTAS = Map.ofEntries(
            Map.entry(20, "FTP-DATA"), Map.entry(21, "FTP"), Map.entry(22, "SSH"), Map.entry(23, "Telnet"),
            Map.entry(25, "SMTP"), Map.entry(53, "DNS"), Map.entry(67, "DHCP"), Map.entry(68, "DHCP"),
            Map.entry(69, "TFTP"), Map.entry(80, "HTTP"), Map.entry(110, "POP3"), Map.entry(123, "NTP"),
            Map.entry(143, "IMAP"), Map.entry(161, "SNMP"), Map.entry(443, "HTTPS"), Map.entry(3306, "MySQL"),
            Map.entry(3389, "RDP"), Map.entry(8080, "HTTP-Alt"));

    /** Passo interno da decodificação sequencial de camadas. */
    private record Passo(Camada camada, int proximoOffset, String proximaCamada) {
    }

    public ResultadoDecodificacao decodificar(String hexInput, String camadaInicial) {
        String cleaned = normalizar(hexInput);
        if (cleaned == null || cleaned.isEmpty()) {
            return erro("Cole um hex dump do pacote (ex.: bytes copiados do Wireshark/tcpdump).");
        }
        if (!cleaned.matches("[0-9a-fA-F]+")) {
            return erro("A entrada contém caracteres não hexadecimais. Cole apenas bytes em hex (0-9, a-f).");
        }
        if (cleaned.length() % 2 != 0) {
            return erro("Número ímpar de dígitos hexadecimais — cada byte precisa de 2 dígitos.");
        }
        byte[] bytes = hexToBytes(cleaned);
        if (bytes.length > MAX_BYTES) {
            return erro("Pacote muito grande (máximo " + MAX_BYTES + " bytes).");
        }

        String inicio = resolverInicio(camadaInicial, bytes);
        List<Camada> camadas = new ArrayList<>();
        String proxima = inicio;
        int offset = 0;
        int guarda = 0;
        while (proxima != null && offset < bytes.length && guarda++ < 10) {
            Passo passo = switch (proxima) {
                case "ethernet" -> decodeEthernet(bytes, offset);
                case "arp" -> decodeArp(bytes, offset);
                case "ipv4" -> decodeIpv4(bytes, offset);
                case "ipv6" -> decodeIpv6(bytes, offset);
                case "tcp" -> decodeTcp(bytes, offset);
                case "udp" -> decodeUdp(bytes, offset);
                case "icmp" -> decodeIcmp(bytes, offset);
                default -> null;
            };
            if (passo == null) {
                break;
            }
            camadas.add(passo.camada());
            if (passo.proximoOffset() <= offset) {
                break;
            }
            offset = passo.proximoOffset();
            proxima = passo.proximaCamada();
        }
        if (offset < bytes.length) {
            camadas.add(payload(bytes, offset));
        }
        return new ResultadoDecodificacao(true, "", bytes.length, inicio, camadas);
    }

    // ------------------------------------------------------------------ camadas

    private Passo decodeEthernet(byte[] b, int off) {
        if (falta(b, off, 14)) {
            return truncado("Ethernet II", off);
        }
        String dst = mac(b, off);
        String src = mac(b, off + 6);
        int ethertype = u16(b, off + 12);
        String tipoNome = ethertypeNome(ethertype);
        List<Campo> campos = List.of(
                campo("MAC destino", dst, "Endereço físico de destino (48 bits)."),
                campo("MAC origem", src, "Endereço físico de origem (48 bits)."),
                campo("EtherType", String.format("0x%04X (%s)", ethertype, tipoNome),
                        "Identifica o protocolo da camada superior."));
        String prox = switch (ethertype) {
            case 0x0800 -> "ipv4";
            case 0x86DD -> "ipv6";
            case 0x0806 -> "arp";
            default -> null;
        };
        return new Passo(new Camada("Ethernet II", src + " → " + dst + " · " + tipoNome, campos), off + 14, prox);
    }

    private Passo decodeArp(byte[] b, int off) {
        if (falta(b, off, 28)) {
            return truncado("ARP", off);
        }
        int oper = u16(b, off + 6);
        String operNome = oper == 1 ? "Request" : oper == 2 ? "Reply" : "?";
        List<Campo> campos = List.of(
                campo("Hardware type", String.valueOf(u16(b, off)), "1 = Ethernet."),
                campo("Protocol type", String.format("0x%04X", u16(b, off + 2)), "0x0800 = IPv4."),
                campo("Hardware size", String.valueOf(u8(b, off + 4)), "Tamanho do endereço MAC (6)."),
                campo("Protocol size", String.valueOf(u8(b, off + 5)), "Tamanho do endereço IP (4)."),
                campo("Operation", oper + " (" + operNome + ")", "1 = quem tem esse IP? 2 = eu tenho."),
                campo("Sender MAC", mac(b, off + 8), "MAC de quem envia."),
                campo("Sender IP", ipv4(b, off + 14), "IP de quem envia."),
                campo("Target MAC", mac(b, off + 18), "MAC alvo (00:.. em request)."),
                campo("Target IP", ipv4(b, off + 24), "IP procurado."));
        return new Passo(new Camada("ARP", operNome + " · alvo " + ipv4(b, off + 24), campos), off + 28, null);
    }

    private Passo decodeIpv4(byte[] b, int off) {
        if (falta(b, off, 20)) {
            return truncado("IPv4", off);
        }
        int b0 = u8(b, off);
        int ihl = b0 & 0x0F;
        int headerLen = ihl * 4;
        int b1 = u8(b, off + 1);
        int total = u16(b, off + 2);
        int flagsFrag = u16(b, off + 6);
        int flags = flagsFrag >> 13;
        int proto = u8(b, off + 9);
        String protoNome = protoNome(proto);
        List<Campo> campos = List.of(
                campo("Versão", String.valueOf(b0 >> 4), "4 = IPv4."),
                campo("IHL", ihl + " (" + headerLen + " bytes)", "Tamanho do cabeçalho em palavras de 32 bits."),
                campo("DSCP/ECN", (b1 >> 2) + " / " + (b1 & 0x03), "Classe de serviço e controle de congestionamento."),
                campo("Total length", total + " bytes", "Tamanho total (cabeçalho + dados)."),
                campo("Identification", String.format("0x%04X", u16(b, off + 4)), "ID para remontar fragmentos."),
                campo("Flags", "0b" + Integer.toBinaryString(flags)
                        + descFlags((flags & 0b010) != 0 ? "DF" : "", (flags & 0b001) != 0 ? "MF" : ""),
                        "DF = não fragmentar; MF = mais fragmentos."),
                campo("Fragment offset", String.valueOf(flagsFrag & 0x1FFF), "Posição do fragmento."),
                campo("TTL", String.valueOf(u8(b, off + 8)), "Tempo de vida (saltos restantes)."),
                campo("Protocolo", proto + " (" + protoNome + ")", "Protocolo encapsulado."),
                campo("Header checksum", String.format("0x%04X", u16(b, off + 10)), "Verificação do cabeçalho."),
                campo("IP origem", ipv4(b, off + 12), "Endereço de origem."),
                campo("IP destino", ipv4(b, off + 16), "Endereço de destino."));
        String prox = switch (proto) {
            case 6 -> "tcp";
            case 17 -> "udp";
            case 1 -> "icmp";
            default -> null;
        };
        int nextOff = Math.max(off + 20, off + headerLen);
        return new Passo(new Camada("IPv4",
                ipv4(b, off + 12) + " → " + ipv4(b, off + 16) + " · " + protoNome, campos), nextOff, prox);
    }

    private Passo decodeIpv6(byte[] b, int off) {
        if (falta(b, off, 40)) {
            return truncado("IPv6", off);
        }
        long word = u32(b, off);
        int nextHeader = u8(b, off + 6);
        String nhNome = protoNome(nextHeader);
        List<Campo> campos = List.of(
                campo("Versão", String.valueOf((word >> 28) & 0xF), "6 = IPv6."),
                campo("Traffic class", String.valueOf((word >> 20) & 0xFF), "Prioridade/QoS."),
                campo("Flow label", String.valueOf(word & 0xFFFFF), "Identifica fluxos para QoS."),
                campo("Payload length", u16(b, off + 4) + " bytes", "Tamanho dos dados após o cabeçalho."),
                campo("Next header", nextHeader + " (" + nhNome + ")", "Protocolo encapsulado."),
                campo("Hop limit", String.valueOf(u8(b, off + 7)), "Equivalente ao TTL do IPv4."),
                campo("IP origem", ipv6(b, off + 8), "Endereço IPv6 de origem."),
                campo("IP destino", ipv6(b, off + 24), "Endereço IPv6 de destino."));
        String prox = switch (nextHeader) {
            case 6 -> "tcp";
            case 17 -> "udp";
            case 58 -> "icmp";
            default -> null;
        };
        return new Passo(new Camada("IPv6",
                ipv6(b, off + 8) + " → " + ipv6(b, off + 24) + " · " + nhNome, campos), off + 40, prox);
    }

    private Passo decodeTcp(byte[] b, int off) {
        if (falta(b, off, 20)) {
            return truncado("TCP", off);
        }
        int src = u16(b, off);
        int dst = u16(b, off + 2);
        int b12 = u8(b, off + 12);
        int dataOffset = b12 >> 4;
        int headerLen = dataOffset * 4;
        int flags = u8(b, off + 13);
        List<Campo> campos = List.of(
                campo("Porta origem", src + porta(src), "Porta TCP de origem."),
                campo("Porta destino", dst + porta(dst), "Porta TCP de destino."),
                campo("Sequence number", Long.toString(u32(b, off + 4)), "Número de sequência."),
                campo("Ack number", Long.toString(u32(b, off + 8)), "Próximo byte esperado."),
                campo("Data offset", dataOffset + " (" + headerLen + " bytes)", "Tamanho do cabeçalho TCP."),
                campo("Flags", flagsTcp(flags), "Bits de controle (SYN/ACK/FIN/RST/PSH/URG)."),
                campo("Window", String.valueOf(u16(b, off + 14)), "Tamanho da janela de recepção."),
                campo("Checksum", String.format("0x%04X", u16(b, off + 16)), "Verificação (cabeçalho + dados)."),
                campo("Urgent pointer", String.valueOf(u16(b, off + 18)), "Usado com a flag URG."));
        int nextOff = Math.max(off + 20, off + headerLen);
        return new Passo(new Camada("TCP",
                src + " → " + dst + " · " + flagsTcpCurto(flags), campos), nextOff, null);
    }

    private Passo decodeUdp(byte[] b, int off) {
        if (falta(b, off, 8)) {
            return truncado("UDP", off);
        }
        int src = u16(b, off);
        int dst = u16(b, off + 2);
        List<Campo> campos = List.of(
                campo("Porta origem", src + porta(src), "Porta UDP de origem."),
                campo("Porta destino", dst + porta(dst), "Porta UDP de destino."),
                campo("Length", u16(b, off + 4) + " bytes", "Tamanho (cabeçalho + dados)."),
                campo("Checksum", String.format("0x%04X", u16(b, off + 6)), "Verificação (opcional no IPv4)."));
        return new Passo(new Camada("UDP", src + " → " + dst, campos), off + 8, null);
    }

    private Passo decodeIcmp(byte[] b, int off) {
        if (falta(b, off, 4)) {
            return truncado("ICMP", off);
        }
        int tipo = u8(b, off);
        int code = u8(b, off + 1);
        List<Campo> campos = List.of(
                campo("Type", tipo + " (" + icmpTipo(tipo) + ")", "Tipo de mensagem ICMP."),
                campo("Code", String.valueOf(code), "Subtipo/código."),
                campo("Checksum", String.format("0x%04X", u16(b, off + 2)), "Verificação da mensagem."));
        return new Passo(new Camada("ICMP", icmpTipo(tipo), campos), off + 4, null);
    }

    private Camada payload(byte[] b, int off) {
        int len = b.length - off;
        String hex = hex(b, off, Math.min(len, 64));
        return new Camada("Payload / dados",
                len + " bytes restantes",
                List.of(campo("Bytes", hex + (len > 64 ? " …" : ""),
                        "Dados da aplicação após os cabeçalhos (primeiros " + Math.min(len, 64) + " bytes).")));
    }

    // ------------------------------------------------------------------ helpers

    private String resolverInicio(String camadaInicial, byte[] b) {
        String c = camadaInicial == null ? "" : camadaInicial.strip().toLowerCase();
        if (INICIOS_VALIDOS.contains(c)) {
            return c;
        }
        // auto
        if (b.length >= 14) {
            int ethertype = u16(b, 12);
            if (ethertype == 0x0800 || ethertype == 0x86DD || ethertype == 0x0806 || ethertype == 0x8100) {
                return "ethernet";
            }
        }
        int v = b.length > 0 ? (u8(b, 0) >> 4) : 0;
        if (v == 6) {
            return "ipv6";
        }
        if (v == 4) {
            return "ipv4";
        }
        return "ethernet";
    }

    private static Passo truncado(String nome, int off) {
        return new Passo(new Camada(nome, "bytes insuficientes",
                List.of(new Campo("Aviso", "Pacote truncado", "Não há bytes suficientes para decodificar " + nome + "."))),
                off, null);
    }

    private static ResultadoDecodificacao erro(String mensagem) {
        return new ResultadoDecodificacao(false, mensagem, 0, "", List.of());
    }

    private static Campo campo(String nome, String valor, String descricao) {
        return new Campo(nome, valor, descricao);
    }

    public static String normalizar(String input) {
        if (input == null) {
            return null;
        }
        String semPrefixo = input.replace("0x", "").replace("0X", "");
        return semPrefixo.replaceAll("[\\s:.,\\-|]", "").trim();
    }

    static byte[] hexToBytes(String hex) {
        int n = hex.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static boolean falta(byte[] b, int off, int precisa) {
        return off + precisa > b.length;
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xFF;
    }

    private static int u16(byte[] b, int i) {
        return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }

    private static long u32(byte[] b, int i) {
        return ((long) (b[i] & 0xFF) << 24) | ((b[i + 1] & 0xFF) << 16)
                | ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
    }

    private static String mac(byte[] b, int i) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 6; k++) {
            if (k > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02x", b[i + k] & 0xFF));
        }
        return sb.toString();
    }

    private static String ipv4(byte[] b, int i) {
        return (b[i] & 0xFF) + "." + (b[i + 1] & 0xFF) + "." + (b[i + 2] & 0xFF) + "." + (b[i + 3] & 0xFF);
    }

    private static String ipv6(byte[] b, int i) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 8; k++) {
            if (k > 0) {
                sb.append(':');
            }
            sb.append(String.format("%x", u16(b, i + k * 2)));
        }
        return sb.toString();
    }

    private static String hex(byte[] b, int off, int len) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < len; k++) {
            if (k > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02x", b[off + k] & 0xFF));
        }
        return sb.toString();
    }

    private static String ethertypeNome(int t) {
        return switch (t) {
            case 0x0800 -> "IPv4";
            case 0x86DD -> "IPv6";
            case 0x0806 -> "ARP";
            case 0x8100 -> "VLAN 802.1Q";
            default -> t < 0x0600 ? "IEEE 802.3 length" : "desconhecido";
        };
    }

    private static String protoNome(int p) {
        return switch (p) {
            case 1 -> "ICMP";
            case 2 -> "IGMP";
            case 6 -> "TCP";
            case 17 -> "UDP";
            case 58 -> "ICMPv6";
            case 89 -> "OSPF";
            default -> "proto " + p;
        };
    }

    private static String icmpTipo(int t) {
        return switch (t) {
            case 0 -> "Echo Reply";
            case 3 -> "Destination Unreachable";
            case 8 -> "Echo Request";
            case 11 -> "Time Exceeded";
            case 128 -> "Echo Request (v6)";
            case 129 -> "Echo Reply (v6)";
            default -> "tipo " + t;
        };
    }

    private static String porta(int p) {
        String nome = PORTAS.get(p);
        return nome == null ? "" : " (" + nome + ")";
    }

    private static String descFlags(String df, String mf) {
        String s = (df + " " + mf).strip();
        return s.isEmpty() ? "" : " [" + s + "]";
    }

    private static String flagsTcp(int f) {
        List<String> on = new ArrayList<>();
        if ((f & 0x01) != 0) on.add("FIN");
        if ((f & 0x02) != 0) on.add("SYN");
        if ((f & 0x04) != 0) on.add("RST");
        if ((f & 0x08) != 0) on.add("PSH");
        if ((f & 0x10) != 0) on.add("ACK");
        if ((f & 0x20) != 0) on.add("URG");
        if ((f & 0x40) != 0) on.add("ECE");
        if ((f & 0x80) != 0) on.add("CWR");
        return String.format("0x%02X", f) + (on.isEmpty() ? "" : " [" + String.join(", ", on) + "]");
    }

    private static String flagsTcpCurto(int f) {
        List<String> on = new ArrayList<>();
        if ((f & 0x02) != 0) on.add("SYN");
        if ((f & 0x10) != 0) on.add("ACK");
        if ((f & 0x01) != 0) on.add("FIN");
        if ((f & 0x04) != 0) on.add("RST");
        if ((f & 0x08) != 0) on.add("PSH");
        return on.isEmpty() ? "sem flags" : String.join("/", on);
    }
}
