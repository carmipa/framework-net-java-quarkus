package org.framework.net.telemetria;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TelemetriaLoggerTest {

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    TelemetriaStore store;

    @Test
    void registraEventoEPersisteArquivoCompartilhado() throws Exception {
        int antes = store.gerarResumo(500).totalEventos();
        telemetriaLogger.logEvent("info", "teste", "evento_unitario",
                Map.of("campo", "valor", "status", "ok"));
        store.flush();

        TelemetriaResumo resumo = store.gerarResumo(500);
        assertTrue(resumo.totalEventos() >= antes + 1);
        assertTrue(Files.exists(store.arquivoCompartilhado()));
        assertTrue(resumo.contagemPorModulo().containsKey("teste"));
    }
}
