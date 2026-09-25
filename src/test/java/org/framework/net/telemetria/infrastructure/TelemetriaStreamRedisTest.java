package org.framework.net.telemetria.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F28: uma única falha (Redis reiniciado, recriado por deploy, OOM no limite de 64 MB) desligava o
 * Stream de telemetria até a APLICAÇÃO reiniciar — o painel ficava preso na janela em memória sem
 * ninguém saber. Invariante: indisponível, volta a ser testado a cada intervalo, sem martelar o Redis
 * a cada evento (entre um teste e outro, nenhuma sonda).
 */
class TelemetriaStreamRedisTest {

    @Test
    void voltaATestarDepoisDoIntervaloSemMartelarAntes() {
        TelemetriaStreamRedis s = new TelemetriaStreamRedis();
        s.habilitado = true;
        s.chave = "teste";
        AtomicBoolean redisNoAr = new AtomicBoolean(false);
        AtomicInteger sondagens = new AtomicInteger();
        AtomicLong agora = new AtomicLong(1_000_000L);
        s.sonda = () -> {
            sondagens.incrementAndGet();
            return redisNoAr.get();
        };
        s.relogio = agora::get;

        assertFalse(s.ativo(), "Redis fora: indisponível");
        redisNoAr.set(true);                       // o Redis voltou

        agora.addAndGet(30_000);                   // antes do intervalo: sem nova sonda
        assertFalse(s.ativo());
        assertEquals(1, sondagens.get(), "não pode sondar o Redis a cada evento");

        agora.addAndGet(31_000);                   // passou o intervalo
        assertTrue(s.ativo(), "depois do intervalo, volta a testar e religa");
        assertEquals(2, sondagens.get());

        agora.addAndGet(600_000);                  // disponível: não re-sonda à toa (A1)
        assertTrue(s.ativo());
        assertEquals(2, sondagens.get());
    }
}
