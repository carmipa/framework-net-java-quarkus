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
    private final ObjectMapper compactMapper;

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
        this.compactMapper = objectMapper.copy().disable(SerializationFeature.INDENT_OUTPUT);
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

    public int maxEventos() {
        return maxEvents;
    }

    /** Cópia dos eventos em memória (mais recentes primeiro), até {@code maxEvents}. */
    public List<TelemetriaEvent> snapshotEventos() {
        lock.lock();
        try {
            return new ArrayList<>(eventos);
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
        List<TelemetriaEvent> carregados = TelemetriaOtlpMapper.fromLogsData(root);
        eventos.clear();
        for (TelemetriaEvent evento : carregados) {
            eventos.addLast(evento);
        }
        while (eventos.size() > maxEvents) {
            eventos.removeLast();
        }
    }

    /**
     * Persiste o arquivo canônico compartilhado em OpenTelemetry OTLP/JSON (Logs data model).
     */
    private void persistirCanonico() throws IOException {
        garantirPasta();
        TelemetriaResumo resumo = gerarResumo(maxEvents);
        Map<String, Object> root = TelemetriaOtlpMapper.toLogsData(
                resumo.projeto(), resumo.versao(), resumo.ultimosEventos());

        Path destino = arquivoCompartilhado();
        Path temporario = destino.resolveSibling(destino.getFileName() + ".tmp");
        objectMapper.writeValue(temporario.toFile(), root);
        Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Stream NDJSON: um {@code LogRecord} OTLP por linha. */
    private void appendJsonl(TelemetriaEvent evento) throws IOException {
        garantirPasta();
        Path arquivo = pastaLogs().resolve(ARQUIVO_EVENTOS);
        String linha = compactMapper.writeValueAsString(TelemetriaOtlpMapper.toLogRecord(evento));
        Files.writeString(arquivo, linha + System.lineSeparator(),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    private void garantirPasta() throws IOException {
        Files.createDirectories(pastaLogs());
    }
}
