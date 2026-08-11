package org.framework.net.telemetria.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.telemetria.TelemetriaEvent;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.telemetria.TelemetriaStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gera, sob demanda, o pacote publicável do dataset de telemetria.
 *
 * <p><b>Propósito de negócio:</b> transformar a telemetria corrente num
 * {@code .zip} pronto para virar um snapshot no repositório público, sem que o
 * servidor precise de credencial de escrita no GitHub. Quem publica é o dono, da
 * máquina dele, com as credenciais dele — o servidor entrega o arquivo e não
 * ganha poder nenhum sobre o repositório.</p>
 *
 * <p><b>Invariantes do domínio:</b> a pseudonimização é <b>por pacote</b> e sem
 * segredo persistente. O identificador do visitante vira {@code visitante-001},
 * {@code visitante-002}… e o mapa morre junto com a geração. Isso mantém a única
 * informação útil (quantos visitantes distintos) e elimina a obrigação de guardar
 * um sal para sempre — com sal fixo, quem o obtivesse reverteria todos os hashes
 * de IPv4 por força bruta, porque o espaço tem só ~4 bilhões de valores. O campo
 * {@code body} é <b>reconstruído</b> a partir dos atributos já sanitizados, nunca
 * filtrado por expressão regular: ele repetia os valores em texto livre, e limpar
 * só os atributos deixaria o dado sensível no texto.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> registro que não puder ser
 * interpretado é <b>descartado e contabilizado</b>, nunca publicado por via das
 * dúvidas; a contagem aparece em {@code estatisticas.json}. Se a auditoria final
 * encontrar identificador residual, a geração falha com
 * {@link IllegalStateException} em vez de devolver um arquivo suspeito — publicar
 * não tem desfazer.</p>
 */
@ApplicationScoped
public class DatasetPublicavelService {

    /** Rotas que são ruído de infraestrutura e não descrevem uso do sistema. */
    private static final List<String> PREFIXOS_RUIDO =
            List.of("/q/", "/web/", "/telemetria/api", "/health", "/favicon", "/pwa/", "/sw.js");

    /** Atributos que não têm tratamento seguro e saem inteiros. */
    private static final List<String> CAMPOS_REMOVIDOS =
            List.of("framework.field.lat", "framework.field.lon", "framework.field.gps");

    /** Atributos cujo valor identifica pessoa e vira pseudônimo do pacote. */
    private static final List<String> CAMPOS_IDENTIFICADORES =
            List.of("framework.field.ip", "framework.field.ip_hash", "framework.field.host",
                    "client.address", "framework.field.endereco", "framework.field.cep");

    @Inject
    TelemetriaStore telemetriaStore;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /**
     * Monta o pacote completo.
     *
     * <p><b>Comportamento em caso de falha:</b> {@link IllegalStateException}
     * quando a auditoria final reprova; {@link UncheckedIOException} se o ZIP não
     * puder ser escrito em memória.</p>
     */
    public Pacote gerar() {
        List<TelemetriaEvent> eventos = telemetriaStore.snapshotEventos();
        Map<String, String> pseudonimos = new LinkedHashMap<>();
        Map<String, Integer> descartes = new LinkedHashMap<>();
        Map<String, Integer> porEvento = new TreeMap<>();
        List<String> linhas = new ArrayList<>();
        int semCorrelacao = 0;

        for (TelemetriaEvent evento : eventos) {
            if (evento == null || evento.evento() == null) {
                descartes.merge("sem_evento", 1, Integer::sum);
                continue;
            }
            if (ehRuido(evento.httpPath())) {
                descartes.merge("ruido_tecnico", 1, Integer::sum);
                continue;
            }
            try {
                linhas.add(objectMapper.writeValueAsString(registroSanitizado(evento, pseudonimos)));
                porEvento.merge(evento.evento(), 1, Integer::sum);
                if (evento.traceId() == null || evento.traceId().isBlank()) {
                    semCorrelacao++;
                }
            } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
                descartes.merge("nao_interpretado", 1, Integer::sum);
            }
        }

        String ndjson = String.join("\n", linhas) + (linhas.isEmpty() ? "" : "\n");
        auditar(ndjson);

        String data = LocalDate.now(ZoneOffset.UTC).toString();
        Map<String, Object> estatisticas = montarEstatisticas(
                data, linhas.size(), pseudonimos.size(), descartes, porEvento, semCorrelacao);

        Map<String, String> arquivos = new LinkedHashMap<>();
        String base = "dataset/" + data + "/";
        try {
            arquivos.put(base + "eventos.jsonl", ndjson);
            arquivos.put(base + "estatisticas.json",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(estatisticas));
            arquivos.put(base + "schema.json",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema()));
            arquivos.put(base + "README.md", leiaMe(data, estatisticas));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar o dataset", ex);
        }

        telemetriaLogger.logEvent("info", "telemetria", "dataset_gerado", Map.of(
                "registros", linhas.size(),
                "visitantes", pseudonimos.size(),
                "descartados", descartes.values().stream().mapToInt(Integer::intValue).sum()));

        return new Pacote(data, arquivos, linhas.size(), pseudonimos.size());
    }

    // --------------------------------------------------------------- sanitização

    private Map<String, Object> registroSanitizado(TelemetriaEvent evento,
                                                   Map<String, String> pseudonimos) {
        List<Map<String, Object>> atributos = new ArrayList<>();
        Map<String, String> camposDoBody = new LinkedHashMap<>();

        atributo(atributos, "framework.event_id", evento.id());
        atributo(atributos, "framework.module", evento.modulo());
        atributo(atributos, "event.name", evento.evento());
        atributo(atributos, "framework.status", evento.status());
        atributo(atributos, "http.request.method", evento.httpMethod());
        atributo(atributos, "http.route", evento.httpPath());
        if (evento.httpStatus() != null) {
            atributo(atributos, "http.response.status_code", String.valueOf(evento.httpStatus()));
        }
        if (evento.durationMs() != null) {
            atributo(atributos, "framework.duration_ms", String.valueOf(evento.durationMs()));
        }

        Map<String, Object> campos = evento.fields() == null ? Map.of() : evento.fields();
        for (Map.Entry<String, Object> entrada : campos.entrySet()) {
            String chave = "framework.field." + entrada.getKey();
            String valor = entrada.getValue() == null ? "" : String.valueOf(entrada.getValue());

            if (CAMPOS_REMOVIDOS.contains(chave)) {
                continue;
            }
            if (CAMPOS_IDENTIFICADORES.contains(chave)) {
                valor = pseudonimo(valor, pseudonimos);
            }
            if (valor.isBlank()) {
                continue;
            }
            atributo(atributos, chave, valor);
            camposDoBody.put(entrada.getKey(), valor);
        }

        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("timeUnixNano", String.valueOf(
                evento.timestamp() == null ? 0L : evento.timestamp().getEpochSecond() * 1_000_000_000L));
        registro.put("severityText", evento.level() == null ? "INFO" : evento.level());
        // body RECONSTRUÍDO: nunca filtrado. Ver invariante na documentação da classe.
        registro.put("body", Map.of("stringValue",
                montarBody(evento.evento(), evento.status(), camposDoBody)));
        registro.put("attributes", atributos);
        if (evento.traceId() != null && !evento.traceId().isBlank()) {
            registro.put("traceId", evento.traceId());
        }
        return registro;
    }

    private String montarBody(String evento, String status, Map<String, String> campos) {
        StringBuilder sb = new StringBuilder("evento=").append(evento)
                .append(" status=").append(status == null ? "ok" : status);
        campos.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(' ').append(e.getKey()).append('=').append(e.getValue()));
        return sb.toString();
    }

    /**
     * Pseudônimo estável dentro deste pacote e sem sentido fora dele.
     *
     * <p><b>Invariantes do domínio:</b> o mapa vive só durante a geração. Não há
     * sal, não há chave, não há nada para vazar depois — a irreversibilidade vem
     * da construção, não do sigilo de um segredo que alguém teria de guardar para
     * sempre.</p>
     */
    private String pseudonimo(String valor, Map<String, String> pseudonimos) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        return pseudonimos.computeIfAbsent(valor,
                v -> String.format(Locale.ROOT, "visitante-%03d", pseudonimos.size() + 1));
    }

    private void atributo(List<Map<String, Object>> destino, String chave, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        destino.add(Map.of("key", chave, "value", Map.of("stringValue", valor)));
    }

    private boolean ehRuido(String rota) {
        if (rota == null || rota.isBlank()) {
            return false;
        }
        return PREFIXOS_RUIDO.stream().anyMatch(rota::startsWith);
    }

    /**
     * Auditoria final do texto gerado.
     *
     * <p><b>Invariantes do domínio:</b> varre o arquivo pronto, não os objetos —
     * é assim que se pega vazamento que escapou pelo {@code body} em texto livre.
     * Endereço de faixa privada, loopback e documentação permanecem de propósito:
     * são valores de laboratório digitados pelos usuários, não identificadores.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> lança em vez de devolver o pacote.
     * Publicar não tem desfazer.</p>
     */
    private void auditar(String ndjson) {
        List<String> problemas = new ArrayList<>();
        var ipv4 = java.util.regex.Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b").matcher(ndjson);
        while (ipv4.find()) {
            if (ehPublico(ipv4.group())) {
                problemas.add("IP público residual: " + ipv4.group());
            }
        }
        if (java.util.regex.Pattern.compile("\\b-?\\d{1,3}\\.\\d{4,}\\b").matcher(ndjson).find()) {
            problemas.add("coordenada com precisão de GPS residual");
        }
        if (java.util.regex.Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+").matcher(ndjson).find()) {
            problemas.add("endereço de e-mail residual");
        }
        if (!problemas.isEmpty()) {
            throw new IllegalStateException(
                    "A auditoria reprovou o dataset e ele NÃO foi gerado: " + String.join("; ", problemas));
        }
    }

    private boolean ehPublico(String ip) {
        try {
            var endereco = java.net.InetAddress.getByName(ip);
            return !(endereco.isSiteLocalAddress() || endereco.isLoopbackAddress()
                    || endereco.isLinkLocalAddress() || endereco.isAnyLocalAddress()
                    || ip.startsWith("192.0.2.") || ip.startsWith("198.51.100.")
                    || ip.startsWith("203.0.113.") || ip.startsWith("100.64."));
        } catch (Exception ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ pacote

    private Map<String, Object> montarEstatisticas(
            String data, int publicados, int visitantes, Map<String, Integer> descartes,
            Map<String, Integer> porEvento, int semCorrelacao) {

        Map<String, Object> estatisticas = new LinkedHashMap<>();
        estatisticas.put("gerado_em_utc", java.time.Instant.now().toString());
        estatisticas.put("snapshot", data);
        estatisticas.put("registros_publicados", publicados);
        estatisticas.put("visitantes_distintos", visitantes);
        estatisticas.put("descartados", descartes);
        estatisticas.put("qualidade", Map.of("registros_sem_correlacao", semCorrelacao));
        estatisticas.put("pseudonimizacao", Map.of(
                "metodo", "pseudonimo sequencial por pacote",
                "segredo_persistente", false,
                "correlacao_entre_snapshots", false));
        estatisticas.put("eventos", porEvento);
        return estatisticas;
    }

    private Map<String, Object> schema() {
        return Map.of(
                "formato", "NDJSON — um OpenTelemetry LogRecord por linha",
                "campos", List.of("timeUnixNano", "severityText", "body", "attributes", "traceId"),
                "atributos", List.of("framework.event_id", "framework.module", "event.name",
                        "framework.status", "http.request.method", "http.route",
                        "http.response.status_code", "framework.duration_ms", "framework.field.*"));
    }

    private String leiaMe(String data, Map<String, Object> estatisticas) {
        return """
                # Snapshot %s

                Gerado a partir da telemetria de produção de
                [frameworknet.carminati.dev.br](https://frameworknet.carminati.dev.br).

                - Registros publicados: **%s**
                - Visitantes distintos: **%s**
                - Formato: NDJSON, um OpenTelemetry `LogRecord` por linha.

                ## Sanitização

                | Dado original | Tratamento |
                |---|---|
                | Identificador de visitante (IP, host, endereço) | `visitante-NNN`, sequencial **dentro deste pacote** |
                | Coordenadas GPS | removidas |
                | Campo `body` | reconstruído a partir dos atributos já sanitizados |
                | Estáticos, `/q/*`, `/web/*`, `/telemetria/api*`, health | descartados |

                **Não existe sal nem segredo por trás do pseudônimo.** O mapa de
                identidades vive só durante a geração e é descartado. A consequência
                aceita conscientemente: não dá para correlacionar o mesmo visitante
                entre dois snapshots — em troca, não há segredo algum a guardar, e
                nenhum vazamento futuro torna estes dados reversíveis.

                Endereços IPv4 privados, de loopback e de documentação permanecem: são
                valores de laboratório digitados pelos usuários para estudar, não
                identificadores de pessoas.

                A geração falha e nada é produzido se a auditoria final encontrar IP
                público, coordenada ou e-mail residual no arquivo pronto.
                """.formatted(data,
                estatisticas.get("registros_publicados"),
                estatisticas.get("visitantes_distintos"));
    }

    /**
     * O snapshot pronto para ir ao repositorio.
     *
     * <p><b>Invariantes do dominio:</b> os arquivos existem so em memoria. O
     * servidor nao grava dataset em disco nem guarda copia — ele gera, envia e
     * descarta. Menos um lugar onde dado de visitante pode ficar esquecido.</p>
     */
    public record Pacote(String data, Map<String, String> arquivos,
                         int registros, int visitantes) {
    }
}
