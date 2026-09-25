package org.framework.net.analiseDidatica.infrastructure.historico;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.config.AnaliseDidaticaConfig;
import org.framework.net.telemetria.TelemetriaLogger;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariantes do histórico (auditoria F06): o boot nunca aborta por arquivo ilegível (falha ABERTA
 * para dado didático, com quarentena); a gravação concorrente não corrompe o arquivo; leitura nunca
 * muta o que está guardado. Cada teste usa um diretório temporário próprio — nunca o
 * ~/.framework-net real da máquina.
 */
@QuarkusTest
class HistoricoStoreTest {

    @Inject
    AnaliseDidaticaConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TelemetriaLogger telemetriaLogger;

    private HistoricoStore novoStore(Path home) {
        HistoricoStore s = new HistoricoStore();
        s.config = config;
        s.objectMapper = objectMapper;
        s.telemetriaLogger = telemetriaLogger;
        s.userHome = home.toString();
        return s;
    }

    private static Path arquivo(Path home) {
        return home.resolve(".framework-net").resolve("consulta_history.json");
    }

    @Test
    void arquivoCorrompidoNaoDerrubaOBootEVaiParaQuarentena() throws Exception {
        Path home = Files.createTempDirectory("hist-corrompido");
        Files.createDirectories(arquivo(home).getParent());
        Files.writeString(arquivo(home), "[{\"id\":\"abc\",\"modo\":\"ip\"}, {\"id\":");  // truncado no meio

        HistoricoStore s = novoStore(home);
        assertDoesNotThrow(s::carregar);
        assertEquals(0, s.listar().size());
        assertFalse(Files.exists(arquivo(home)), "o arquivo ilegível deveria ter saído do caminho");
        try (var ls = Files.list(arquivo(home).getParent())) {
            assertTrue(ls.anyMatch(p -> p.getFileName().toString().startsWith("consulta_history.json.corrompido-")),
                    "o conteúdo ilegível precisa ficar preservado em quarentena");
        }

        // Controle positivo (A1): arquivo legítimo continua sendo carregado.
        Path home2 = Files.createTempDirectory("hist-ok");
        Files.createDirectories(arquivo(home2).getParent());
        Files.writeString(arquivo(home2), "[{\"id\":\"abc\",\"modo\":\"ip\"}]");
        HistoricoStore ok = novoStore(home2);
        ok.carregar();
        assertEquals(1, ok.listar().size());
    }

    @Test
    void paginarNaoMutaOsRegistrosGuardados() throws Exception {
        HistoricoStore s = novoStore(Files.createTempDirectory("hist-paginar"));
        s.registrarConsulta(Map.of("modo", "ip", "ip", "10.0.0.1"), Map.of("rede", "10.0.0.0"));
        s.paginar("10", "1");
        assertFalse(s.listar().get(0).containsKey("timestamp_utc"),
                "paginar escreveu no mapa guardado (compartilhado entre requisições)");
    }

    @Test
    void gravacaoConcorrenteNaoCorrompeOArquivo() throws Exception {
        Path home = Files.createTempDirectory("hist-concorrente");
        HistoricoStore s = novoStore(home);
        int threads = 8;
        int porThread = 40;
        ExecutorService pool = Executors.newFixedThreadPool(threads + 2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> tarefas = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            tarefas.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < porThread; i++) {
                    s.registrarConsulta(Map.of("modo", "ip", "ip", "10.0." + id + "." + i), Map.of("rede", "x"));
                }
                return null;
            }));
        }
        for (int t = 0; t < 2; t++) {
            tarefas.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < 200; i++) {
                    s.paginar("60", "1");
                    objectMapper.writeValueAsString(s.listar());
                }
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> f : tarefas) {
            f.get(60, TimeUnit.SECONDS);   // qualquer exceção (ConcurrentModification etc.) reprova aqui
        }
        pool.shutdown();

        List<Map<String, Object>> noDisco = objectMapper.readValue(arquivo(home).toFile(), new TypeReference<>() {});
        assertEquals(Math.min(config.maxHistory(), threads * porThread), noDisco.size());
        assertEquals(noDisco.size(), s.listar().size());
    }
}
