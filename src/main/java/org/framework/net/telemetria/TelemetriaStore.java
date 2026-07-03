package org.framework.net.telemetria;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
public class TelemetriaStore {

    private static final Logger LOG = Logger.getLogger(TelemetriaStore.class);
    private static final String ARQUIVO_COMPARTILHADO = "telemetria_compartilhada.json";
    private static final String ARQUIVO_EVENTOS = "framework-net-eventos.jsonl";

    private final ReentrantLock lock = new ReentrantLock();
    private final Deque<TelemetriaEvent> eventos = new ArrayDeque<>();
    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "framework.logs.base-dir", defaultValue = "logs")
    String baseDir;

    @ConfigProperty(name = "framework.telemetry.max-events", defaultValue = "5000")
    int maxEvents;

    @ConfigProperty(name = "framework.telemetry.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "framework-net-java-quarkus")
    String appName;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0-SNAPSHOT")
    String appVersion;

    @Inject
    public TelemetriaStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    void onStart(@Observes @Priority(1) StartupEvent event) {
        if (!enabled) {
            return;
        }
        try {
            garantirPasta();
            carregarPersistido();
            LOG.infof("Telemetria carregada de %s (%d eventos)", pastaLogs(), eventos.size());
        } catch (IOException ex) {
            LOG.warnf(ex, "Falha ao carregar telemetria persistida em %s", pastaLogs());
        }
    }

    @PreDestroy
    void onStop() {
        if (!enabled) {
            return;
        }
        try {
            persistirCanonico();
        } catch (IOException ex) {
            LOG.warnf(ex, "Falha ao persistir telemetria ao encerrar");
        }
    }

    public void registrar(TelemetriaEvent evento) {
        if (!enabled || evento == null) {
            return;
        }
        lock.lock();
        try {
            eventos.addFirst(evento);
            while (eventos.size() > maxEvents) {
                eventos.removeLast();
            }
            appendJsonl(evento);
            persistirCanonico();
        } catch (IOException ex) {
            LOG.warnf(ex, "Falha ao persistir evento de telemetria %s", evento.evento());
        } finally {
            lock.unlock();
        }
    }

    public void flush() {
        if (!enabled) {
            return;
        }
        lock.lock();
        try {
            persistirCanonico();
        } catch (IOException ex) {
            LOG.warnf(ex, "Falha ao flush da telemetria");
        } finally {
            lock.unlock();
        }
    }

    public TelemetriaResumo gerarResumo(int limiteUltimos) {
        lock.lock();
        try {
            int limite = Math.max(1, Math.min(limiteUltimos, maxEvents));
            List<TelemetriaEvent> copia = new ArrayList<>(eventos);
            Map<String, Long> porModulo = new LinkedHashMap<>();
            Map<String, Long> porNivel = new LinkedHashMap<>();
            for (TelemetriaEvent evento : copia) {
                porModulo.merge(evento.modulo(), 1L, Long::sum);
                porNivel.merge(evento.level(), 1L, Long::sum);
            }
            List<TelemetriaEvent> ultimos = copia.stream().limit(limite).toList();
            return new TelemetriaResumo(
                    appName,
                    appVersion,
                    Instant.now(),
                    copia.size(),
                    porModulo,
                    porNivel,
                    ultimos
            );
        } finally {
            lock.unlock();
        }
    }

    public Path arquivoCompartilhado() {
        return pastaLogs().resolve(ARQUIVO_COMPARTILHADO);
    }

    public Path pastaLogs() {
        return Path.of(baseDir);
    }

    public List<String> lerUltimasLinhasArquivoLog(int limite) {
        int max = Math.max(1, Math.min(limite, 500));
        Path arquivo = pastaLogs().resolve("framework-net.log");
        if (!Files.exists(arquivo)) {
            return List.of();
        }
        try {
            List<String> todas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            int inicio = Math.max(0, todas.size() - max);
            return List.copyOf(todas.subList(inicio, todas.size()));
        } catch (IOException ex) {
            LOG.debugf(ex, "Falha ao ler tail do log %s", arquivo);
            return List.of();
        }
    }

    private void carregarPersistido() throws IOException {
        Path arquivo = arquivoCompartilhado();
        if (!Files.exists(arquivo)) {
            return;
        }
        Map<String, Object> root = objectMapper.readValue(arquivo.toFile(), new TypeReference<>() {});
        Object rawEventos = root.get("eventos");
        if (!(rawEventos instanceof List<?> lista)) {
            return;
        }
        eventos.clear();
        for (Object item : lista) {
            if (item instanceof Map<?, ?> mapa) {
                TelemetriaEvent evento = mapToEvent(mapa);
                if (evento != null) {
                    eventos.addLast(evento);
                }
            }
        }
        while (eventos.size() > maxEvents) {
            eventos.removeFirst();
        }
    }

    private TelemetriaEvent mapToEvent(Map<?, ?> mapa) {
        try {
            return new TelemetriaEvent(
                    stringValue(mapa.get("id")),
                    Instant.parse(stringValue(mapa.get("timestamp"))),
                    stringValue(mapa.get("level")),
                    stringValue(mapa.get("modulo")),
                    stringValue(mapa.get("evento")),
                    stringValue(mapa.get("status")),
                    stringValue(mapa.get("requestId")),
                    stringValue(mapa.get("traceId")),
                    stringValue(mapa.get("httpMethod")),
                    stringValue(mapa.get("httpPath")),
                    integerValue(mapa.get("httpStatus")),
                    longValue(mapa.get("durationMs")),
                    stringValue(mapa.get("message")),
                    fieldsValue(mapa.get("fields"))
            );
        } catch (Exception ex) {
            LOG.debugf(ex, "Evento de telemetria ignorado por formato inválido");
            return null;
        }
    }

    private void persistirCanonico() throws IOException {
        garantirPasta();
        TelemetriaResumo resumo = gerarResumo(maxEvents);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("projeto", resumo.projeto());
        root.put("versao", resumo.versao());
        root.put("atualizadoEm", resumo.atualizadoEm().toString());
        root.put("totalEventos", resumo.totalEventos());
        root.put("contagemPorModulo", resumo.contagemPorModulo());
        root.put("contagemPorNivel", resumo.contagemPorNivel());
        root.put("eventos", resumo.ultimosEventos().stream().map(TelemetriaEvent::toMap).toList());

        Path destino = arquivoCompartilhado();
        Path temporario = destino.resolveSibling(destino.getFileName() + ".tmp");
        objectMapper.writeValue(temporario.toFile(), root);
        Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void appendJsonl(TelemetriaEvent evento) throws IOException {
        garantirPasta();
        Path arquivo = pastaLogs().resolve(ARQUIVO_EVENTOS);
        String linha = objectMapper.copy().disable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(evento.toMap());
        Files.writeString(arquivo, linha + System.lineSeparator(),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    private void garantirPasta() throws IOException {
        Files.createDirectories(pastaLogs());
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fieldsValue(Object value) {
        if (value instanceof Map<?, ?> mapa) {
            Map<String, Object> fields = new LinkedHashMap<>();
            mapa.forEach((k, v) -> fields.put(String.valueOf(k), v));
            return fields;
        }
        return Map.of();
    }
}
