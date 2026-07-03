package org.framework.net.telemetria;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class TelemetriaDashboardService {

    private static final DateTimeFormatter MINUTO_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    @Inject
    TelemetriaStore store;

    @Inject
    TelemetriaConsoleBuffer console;

    public TelemetriaDashboard montar(int limiteEventos, int limiteConsole) {
        TelemetriaResumo resumo = store.gerarResumo(limiteEventos);
        Map<String, Long> porStatus = new LinkedHashMap<>();
        Map<String, Long> httpStatus = new LinkedHashMap<>();
        long httpOk = 0;
        long httpErro = 0;
        long somaDuracao = 0;
        long contagemDuracao = 0;
        Map<String, Long> buckets = new LinkedHashMap<>();

        for (TelemetriaEvent evento : resumo.ultimosEventos()) {
            String status = evento.status() == null ? "ok" : evento.status();
            porStatus.merge(status, 1L, Long::sum);

            if ("http_access".equals(evento.evento())) {
                if (evento.httpStatus() != null) {
                    String chave = String.valueOf(evento.httpStatus());
                    httpStatus.merge(chave, 1L, Long::sum);
                    if (evento.httpStatus() >= 400) {
                        httpErro++;
                    } else {
                        httpOk++;
                    }
                }
                if (evento.durationMs() != null) {
                    somaDuracao += evento.durationMs();
                    contagemDuracao++;
                }
            }

            if (evento.timestamp() != null) {
                Instant minuto = evento.timestamp().truncatedTo(ChronoUnit.MINUTES);
                String rotulo = MINUTO_FMT.format(minuto);
                buckets.merge(rotulo, 1L, Long::sum);
            }
        }

        List<TelemetriaDashboard.AtividadeMinuto> atividade = buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TelemetriaDashboard.AtividadeMinuto(e.getKey(), e.getValue()))
                .toList();

        long mediaDuracao = contagemDuracao == 0 ? 0 : somaDuracao / contagemDuracao;
        List<String> console = montarConsole(limiteConsole);

        return new TelemetriaDashboard(
                resumo,
                porStatus,
                httpStatus,
                httpOk,
                httpErro,
                mediaDuracao,
                atividade,
                console,
                store.pastaLogs().toAbsolutePath().toString()
        );
    }

    public List<String> montarConsole(int limite) {
        int max = Math.max(1, Math.min(limite, 500));
        List<String> buffer = console.snapshot(max);
        List<String> arquivo = store.lerUltimasLinhasArquivoLog(max);
        if (buffer.isEmpty()) {
            return arquivo;
        }
        if (arquivo.isEmpty()) {
            return buffer;
        }
        Set<String> vistos = new LinkedHashSet<>();
        List<String> mesclado = new ArrayList<>();
        for (String linha : arquivo) {
            if (vistos.add(linha)) {
                mesclado.add(linha);
            }
        }
        for (String linha : buffer) {
            if (vistos.add(linha)) {
                mesclado.add(linha);
            }
        }
        int inicio = Math.max(0, mesclado.size() - max);
        return List.copyOf(mesclado.subList(inicio, mesclado.size()));
    }

    public void limparConsole() {
        console.limpar();
    }
}
