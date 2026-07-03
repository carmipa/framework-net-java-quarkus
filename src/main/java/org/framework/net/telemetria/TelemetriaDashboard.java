package org.framework.net.telemetria;

import java.util.List;
import java.util.Map;

public record TelemetriaDashboard(
        TelemetriaResumo resumo,
        Map<String, Long> contagemPorStatus,
        Map<String, Long> httpStatusDistribuicao,
        long totalHttpOk,
        long totalHttpErro,
        long mediaDuracaoHttpMs,
        List<AtividadeMinuto> atividadePorMinuto,
        List<String> consoleLinhas,
        String pastaLogs
) {

    public record AtividadeMinuto(String minuto, long total) {
    }
}
