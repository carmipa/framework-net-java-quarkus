package org.framework.net.analiseDidatica.infrastructure.dns;

import org.framework.net.analiseDidatica.exception.DnsResolucaoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F03: com pool de 2 threads e fila sem limite, nomes que resolvem devagar enfileiravam sem fim e
 * toda consulta legítima esperava o timeout e falhava — por minutos depois do ataque. Invariantes:
 * saturado, recusa NA HORA (não espera na fila); tarefa que estourou o tempo é cancelada e não roda
 * depois ocupando o pool.
 */
class DnsResolverPoolTest {

    private final DnsResolver resolver = new DnsResolver();
    private final CountDownLatch solta = new CountDownLatch(1);
    private final List<Thread> ocupantes = new ArrayList<>();

    @AfterEach
    void liberar() throws InterruptedException {
        solta.countDown();
        for (Thread t : ocupantes) {
            t.join(5_000);
        }
        resolver.shutdown();
    }

    private void ocupar(int quantidade) throws InterruptedException {
        CountDownLatch comecaram = new CountDownLatch(quantidade);
        for (int i = 0; i < quantidade; i++) {
            Thread t = new Thread(() -> {
                try {
                    resolver.executar(() -> {
                        comecaram.countDown();
                        solta.await();
                        return "lento";
                    }, 30);
                } catch (Exception ignore) {
                    comecaram.countDown();
                }
            });
            t.start();
            ocupantes.add(t);
        }
        comecaram.await(5, TimeUnit.SECONDS);
    }

    @Test
    void saturadoRecusaNaHoraSemEnfileirar() throws Exception {
        ocupar(DnsResolver.THREADS + DnsResolver.FILA);
        long inicio = System.nanoTime();
        assertThrows(DnsResolucaoException.class, () -> resolver.executar(() -> "legitimo", 3));
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        assertTrue(ms < 1_000, "deveria recusar de imediato, esperou " + ms + " ms");
    }

    @Test
    void tarefaQueEstourouOTempoNaoRodaDepois() throws Exception {
        ocupar(DnsResolver.THREADS);
        AtomicBoolean rodou = new AtomicBoolean(false);
        assertThrows(TimeoutException.class, () -> resolver.executar(() -> {
            rodou.set(true);
            return "tarde";
        }, 1));
        solta.countDown();
        Thread.sleep(500);
        assertFalse(rodou.get(), "a tarefa cancelada ainda rodou depois, ocupando o pool");
    }

    @Test
    void controlePositivoPoolLivreResolve() throws Exception {
        assertEquals("ok", resolver.executar(() -> "ok", 3));
    }
}
