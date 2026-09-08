package org.framework.net.segurancaRede.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.segurancaRede.exception.SegurancaException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.Locale;
import java.util.Map;

/**
 * Simulador didático de avaliação de UMA linha de ACL Cisco contra um pacote.
 *
 * <p><b>Propósito de negócio:</b> mostrar ao estudante como o roteador decide
 * permitir ou bloquear um pacote diante de uma regra de ACL, comparando IP de
 * origem, IP de destino e porta de destino com o que a regra especifica.</p>
 *
 * <p><b>Invariantes do domínio:</b> a comparação é EXATA e tipada — a porta é
 * inteiro (80 nunca casa 8080), os endereços são casados por máscara-curinga
 * (wildcard) de verdade e o IP de destino participa da decisão quando a regra o
 * especifica. Só a sintaxe suportada é aceita: comando fora dela é RECUSADO,
 * nunca reinterpretado como "não corresponde". "Não corresponde" (NO MATCH) e
 * "bloqueado" (deny que casa) são resultados distintos.</p>
 *
 * <p><b>Sintaxe suportada:</b>
 * {@code [access-list <n>] {permit|deny} {ip|tcp|udp|icmp} <origem> [<destino>] [eq <porta>]},
 * onde cada endereço é {@code any}, {@code host <ip>}, {@code <ip> <wildcard>} ou
 * {@code <ip>} (equivale a host). {@code eq <porta>} só vale para tcp/udp e é
 * comparado à porta de DESTINO do pacote.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> entrada inválida (IPv4 malformado,
 * porta fora de faixa, sintaxe não suportada, caractere perigoso) lança
 * {@link SegurancaException}, convertida em HTTP 400 com mensagem — o simulador
 * nunca "chuta" um veredito diante de entrada que não entende.</p>
 *
 * <p><b>Limitação declarada:</b> o formulário não informa o protocolo do pacote
 * nem a porta de origem; a regra pode declarar tcp/udp/ip/icmp e a porta de
 * destino, mas a incompatibilidade de protocolo do pacote em si não é modelada.</p>
 */
@ApplicationScoped
public class AclSimulatorService {

    @Inject
    TelemetriaLogger telemetriaLogger;

    public String testarPacote(String regra, String ipOrigem, String ipDestino, String portaDestinoRaw) {
        return telemetriaLogger.medir("seguranca", "teste_acl", () -> {
            int portaDestino = validarEntradas(regra, ipOrigem, ipDestino, portaDestinoRaw);
            long origem = ipParaLong(ipOrigem);
            long destino = ipParaLong(ipDestino);

            RegraAcl regraAcl = RegraAcl.parse(regra);

            telemetriaLogger.logEvent("info", "seguranca", "acl_avaliada", Map.of(
                    "regra", regra,
                    "ipOrigem", ipOrigem,
                    "ipDestino", ipDestino,
                    "portaDestino", portaDestino));

            boolean origemBate = regraAcl.origem().casa(origem);
            boolean destinoBate = regraAcl.destino().casa(destino);
            boolean portaBate = regraAcl.porta() < 0 || regraAcl.porta() == portaDestino;

            if (origemBate && destinoBate && portaBate) {
                return regraAcl.permite()
                        ? "MATCH (PERMITIDO) — origem, destino e porta atendem à regra; o pacote é liberado."
                        : "MATCH (BLOQUEADO) — o pacote atende à regra de deny e é descartado.";
            }
            return "NÃO CORRESPONDE (NO MATCH) — " + motivo(origemBate, destinoBate, portaBate)
                    + " O roteador avaliaria a próxima linha (ou o deny implícito no fim da lista).";
        });
    }

    /** Explica qual condição impediu o match — separa "não corresponde" de "bloqueado". */
    private static String motivo(boolean origemBate, boolean destinoBate, boolean portaBate) {
        StringBuilder sb = new StringBuilder("a regra não captura este pacote (");
        boolean primeiro = true;
        if (!origemBate) {
            sb.append("origem fora do alcance");
            primeiro = false;
        }
        if (!destinoBate) {
            if (!primeiro) {
                sb.append("; ");
            }
            sb.append("destino fora do alcance");
            primeiro = false;
        }
        if (!portaBate) {
            if (!primeiro) {
                sb.append("; ");
            }
            sb.append("porta de destino diferente");
        }
        sb.append(").");
        return sb.toString();
    }

    private int validarEntradas(String regra, String ipOrigem, String ipDestino, String portaDestinoRaw) {
        exigir(regra, "Regra ACL não informada.");
        exigir(ipOrigem, "IP de Origem não informado.");
        exigir(ipDestino, "IP de Destino não informado.");
        if (regra.length() > 200 || ipOrigem.length() > 45 || ipDestino.length() > 45) {
            throw new SegurancaException("Entrada muito longa.");
        }
        // Defesa em profundidade: rejeita caracteres de HTML/scripts nos campos.
        if (contemPerigoso(regra) || contemPerigoso(ipOrigem) || contemPerigoso(ipDestino)) {
            throw new SegurancaException("Caracteres inválidos detectados nas entradas.");
        }
        if (!ipValido(ipOrigem)) {
            throw new SegurancaException("IP de Origem inválido (use IPv4, ex.: 192.168.1.5).");
        }
        if (!ipValido(ipDestino)) {
            throw new SegurancaException("IP de Destino inválido (use IPv4, ex.: 10.0.0.1).");
        }
        int porta;
        try {
            porta = Integer.parseInt(portaDestinoRaw == null ? "" : portaDestinoRaw.trim());
        } catch (NumberFormatException ex) {
            throw new SegurancaException("Porta de destino inválida (informe um número).");
        }
        if (porta <= 0 || porta > 65535) {
            throw new SegurancaException("Porta de destino fora do intervalo (1–65535).");
        }
        return porta;
    }

    private static void exigir(String valor, String mensagem) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new SegurancaException(mensagem);
        }
    }

    private static boolean contemPerigoso(String valor) {
        return valor.indexOf('<') >= 0 || valor.indexOf('>') >= 0
                || valor.indexOf('"') >= 0 || valor.indexOf('\'') >= 0 || valor.indexOf('`') >= 0;
    }

    // ---- IPv4 tipado, contido nesta fatia (sem acoplar a outro módulo) ----

    private static boolean ipValido(String ip) {
        String[] octetos = ip.trim().split("\\.");
        if (octetos.length != 4) {
            return false;
        }
        for (String parte : octetos) {
            if (parte.isEmpty() || parte.length() > 3) {
                return false;
            }
            int valor;
            try {
                valor = Integer.parseInt(parte);
            } catch (NumberFormatException e) {
                return false;
            }
            if (valor < 0 || valor > 255) {
                return false;
            }
        }
        return true;
    }

    private static long ipParaLong(String ip) {
        String[] octetos = ip.trim().split("\\.");
        long valor = 0L;
        for (String parte : octetos) {
            valor = (valor << 8) | (Integer.parseInt(parte) & 0xFF);
        }
        return valor & 0xFFFFFFFFL;
    }

    /**
     * Endereço da regra: base + máscara-curinga. Bit 0 no wildcard exige igualdade;
     * bit 1 é "não importa". {@code host} → wildcard 0.0.0.0; {@code any} → 255.255.255.255.
     */
    private record EnderecoAcl(long base, long wildcard) {

        static final EnderecoAcl ANY = new EnderecoAcl(0L, 0xFFFFFFFFL);

        boolean casa(long ip) {
            return ((ip ^ base) & (~wildcard) & 0xFFFFFFFFL) == 0L;
        }
    }

    /** Uma linha de ACL já tipada. {@code porta = -1} quando a regra não restringe porta. */
    private record RegraAcl(boolean permite, EnderecoAcl origem, EnderecoAcl destino, int porta) {

        static RegraAcl parse(String texto) {
            String[] t = texto.trim().toLowerCase(Locale.ROOT).split("\\s+");
            int i = 0;
            if (i < t.length && t[i].equals("access-list")) {
                i += 2; // pula "access-list <n>"
            }
            if (i >= t.length || !(t[i].equals("permit") || t[i].equals("deny"))) {
                throw new SegurancaException("Regra ACL não suportada: comece com 'permit' ou 'deny'.");
            }
            boolean permite = t[i].equals("permit");
            i++;
            if (i >= t.length || !ehProtocolo(t[i])) {
                throw new SegurancaException("Protocolo não suportado: use ip, tcp, udp ou icmp.");
            }
            boolean portaPermitida = t[i].equals("tcp") || t[i].equals("udp");
            i++;

            int[] cursor = {i};
            EnderecoAcl origem = lerEndereco(t, cursor, "origem");
            EnderecoAcl destino = EnderecoAcl.ANY;
            if (cursor[0] < t.length && ehInicioEndereco(t[cursor[0]])) {
                destino = lerEndereco(t, cursor, "destino");
            }
            int porta = -1;
            if (cursor[0] < t.length && t[cursor[0]].equals("eq")) {
                if (!portaPermitida) {
                    throw new SegurancaException("Operador de porta 'eq' só vale para tcp/udp.");
                }
                if (cursor[0] + 1 >= t.length) {
                    throw new SegurancaException("Regra ACL não suportada: 'eq' sem número de porta.");
                }
                porta = lerPorta(t[cursor[0] + 1]);
                cursor[0] += 2;
            }
            if (cursor[0] < t.length) {
                throw new SegurancaException("Regra ACL não suportada: trecho não reconhecido '"
                        + t[cursor[0]] + "'.");
            }
            return new RegraAcl(permite, origem, destino, porta);
        }

        private static boolean ehProtocolo(String s) {
            return s.equals("ip") || s.equals("tcp") || s.equals("udp") || s.equals("icmp");
        }

        private static boolean ehInicioEndereco(String s) {
            return s.equals("any") || s.equals("host") || ipValido(s);
        }

        private static EnderecoAcl lerEndereco(String[] t, int[] cursor, String qual) {
            int i = cursor[0];
            if (i >= t.length) {
                throw new SegurancaException("Regra ACL não suportada: falta o endereço de " + qual + ".");
            }
            if (t[i].equals("any")) {
                cursor[0] = i + 1;
                return EnderecoAcl.ANY;
            }
            if (t[i].equals("host")) {
                if (i + 1 >= t.length || !ipValido(t[i + 1])) {
                    throw new SegurancaException("Regra ACL não suportada: 'host' sem IPv4 válido.");
                }
                cursor[0] = i + 2;
                return new EnderecoAcl(ipParaLong(t[i + 1]), 0L);
            }
            if (ipValido(t[i])) {
                // <ip> <wildcard>: dois IPv4 seguidos = endereço + máscara-curinga.
                if (i + 1 < t.length && ipValido(t[i + 1])) {
                    cursor[0] = i + 2;
                    return new EnderecoAcl(ipParaLong(t[i]), ipParaLong(t[i + 1]));
                }
                cursor[0] = i + 1;
                return new EnderecoAcl(ipParaLong(t[i]), 0L);
            }
            throw new SegurancaException("Regra ACL não suportada: endereço de " + qual
                    + " inválido '" + t[i] + "'.");
        }

        private static int lerPorta(String s) {
            int p;
            try {
                p = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new SegurancaException("Porta da regra inválida: '" + s + "'.");
            }
            if (p < 0 || p > 65535) {
                throw new SegurancaException("Porta da regra fora do intervalo (0–65535).");
            }
            return p;
        }
    }
}
