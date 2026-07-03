package org.framework.net.analiseDidatica.infrastructure.historico;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.framework.net.analiseDidatica.config.AnaliseDidaticaConfig;
import org.framework.net.analiseDidatica.exception.HistoricoPersistenciaException;
import org.framework.net.telemetria.TelemetriaLogger;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class HistoricoStore {

    private static final Logger LOG = Logger.getLogger(HistoricoStore.class);
    private static final DateTimeFormatter UTC_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    @Inject
    AnaliseDidaticaConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @ConfigProperty(name = "user.home")
    String userHome;

    private final Deque<Map<String, Object>> historyStore = new ArrayDeque<>();

    void onStart(@Observes @Priority(1) StartupEvent event) {
        carregar();
    }

    public void carregar() {
        Path file = historyFile();
        if (!Files.exists(file)) {
            return;
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(file.toFile(), new TypeReference<>() {});
            if (raw != null) {
                int start = Math.max(0, raw.size() - config.maxHistory());
                for (Map<String, Object> item : raw.subList(start, raw.size())) {
                    historyStore.addLast(item);
                }
            }
            LOG.infof("Histórico carregado: %d registros", historyStore.size());
            telemetriaLogger.logEvent("info", "analiseDidatica", "history_load",
                    Map.of("status", "ok", "total", historyStore.size()));
        } catch (IOException ex) {
            throw new HistoricoPersistenciaException("Falha ao carregar histórico local.", ex);
        }
    }

    public void persistir() {
        try {
            Files.createDirectories(historyFile().getParent());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(historyFile().toFile(), new ArrayList<>(historyStore));
            LOG.infof("Histórico persistido: %d registros", historyStore.size());
            telemetriaLogger.logEvent("info", "analiseDidatica", "history_persist",
                    Map.of("status", "ok", "total", historyStore.size()));
        } catch (IOException ex) {
            throw new HistoricoPersistenciaException("Falha ao persistir histórico local.", ex);
        }
    }

    public void registrarConsulta(Map<String, String> entrada, Map<String, Object> res) {
        if (res == null || res.isEmpty()) {
            return;
        }
        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("id", UUID.randomUUID().toString().substring(0, 8));
        registro.put("timestamp", Instant.now().toString());
        registro.put("modo", entrada.getOrDefault("modo", ""));
        registro.put("ip_entrada", entrada.getOrDefault("ip", ""));
        registro.put("ipv6_entrada", entrada.getOrDefault("ipv6", ""));
        registro.put("cidr_entrada", entrada.getOrDefault("cidr", ""));
        registro.put("mask_entrada", entrada.getOrDefault("mask_decimal", ""));
        registro.put("wildcard_entrada", entrada.getOrDefault("wildcard_mask", ""));
        registro.put("rede", res.getOrDefault("rede", ""));
        registro.put("broadcast", res.getOrDefault("broad", ""));
        registro.put("mask", res.getOrDefault("mask", ""));
        registro.put("cidr", res.getOrDefault("cidr", ""));
        registro.put("tema", res.getOrDefault("nivel_tema", ""));
        Object gc = res.get("geo_consulta");
        if (gc instanceof Map<?, ?> geoMap) {
            registro.put("geo_consulta", geoMap);
        }
        historyStore.addFirst(registro);
        while (historyStore.size() > config.maxHistory()) {
            historyStore.removeLast();
        }
        LOG.infof("Histórico append modo=%s id=%s", registro.get("modo"), registro.get("id"));
        persistir();
    }

    public List<Map<String, Object>> listar() {
        return new ArrayList<>(historyStore);
    }

    public Map<String, Object> paginar(String historyLimitPre, String historyPagePre) {
        int historyLimitInt = parsePositive(historyLimitPre, 1);
        if (historyLimitInt > config.maxHistory()) {
            historyLimitInt = config.maxHistory();
        }
        int historyPageInt = Math.max(1, parsePositive(historyPagePre, 1));

        List<Map<String, Object>> historyList = listar();
        int totalHistory = historyList.size();
        int totalHistoryPages;
        List<Map<String, Object>> historyPageItems;

        if (historyLimitInt > 0) {
            totalHistoryPages = Math.max(1, (totalHistory + historyLimitInt - 1) / historyLimitInt);
            if (historyPageInt > totalHistoryPages) {
                historyPageInt = totalHistoryPages;
            }
            int start = (historyPageInt - 1) * historyLimitInt;
            int end = Math.min(start + historyLimitInt, totalHistory);
            historyPageItems = new ArrayList<>(historyList.subList(start, end));
            for (Map<String, Object> item : historyPageItems) {
                item.put("timestamp_utc", formatarTimestampUtc(String.valueOf(item.getOrDefault("timestamp", ""))));
            }
        } else {
            totalHistoryPages = 1;
            historyPageItems = List.of();
            historyPageInt = 1;
        }

        Map<String, Object> pag = new LinkedHashMap<>();
        pag.put("history", historyList);
        pag.put("history_limit", historyLimitInt);
        pag.put("history_limit_pre", String.valueOf(historyLimitInt));
        pag.put("history_limit_max", config.maxHistory());
        pag.put("history_page", historyPageInt);
        pag.put("total_history_pages", totalHistoryPages);
        pag.put("has_prev_history", historyPageInt > 1);
        pag.put("has_next_history", historyPageInt < totalHistoryPages);
        pag.put("history_page_items", historyPageItems);
        return pag;
    }

    public Map<String, Object> buscarReplay(String replayId) {
        if (replayId == null || replayId.isBlank()) {
            return null;
        }
        return listar().stream()
                .filter(item -> replayId.equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElse(null);
    }

    public void registrarCatalogo(String modo, String entrada) {
        Map<String, String> fields = Map.of(
                "modo", modo,
                "ip", entrada == null ? "" : entrada,
                "ipv6", "",
                "cidr", "",
                "mask_decimal", "",
                "wildcard_mask", ""
        );
        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("rede", "N/A");
        resumo.put("broad", "N/A");
        resumo.put("mask", "N/A");
        resumo.put("cidr", "");
        resumo.put("nivel_tema", "Consulta de catálogo: " + modo);
        registrarConsulta(fields, resumo);
    }

    public List<Map<String, Object>> listarPorModo(String modo) {
        return listar().stream()
                .filter(item -> modo.equalsIgnoreCase(String.valueOf(item.getOrDefault("modo", ""))))
                .toList();
    }

    private Path historyFile() {
        return Paths.get(userHome, ".framework-net", "consulta_history.json");
    }

    private static int parsePositive(String value, int defaultValue) {
        if (value == null || !value.chars().allMatch(Character::isDigit)) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private static String formatarTimestampUtc(String ts) {
        if (ts == null || ts.isBlank()) {
            return "—";
        }
        try {
            return UTC_FMT.format(Instant.parse(ts.replace("Z", "+00:00"))) + " UTC";
        } catch (Exception ex) {
            return ts + " UTC";
        }
    }
}
