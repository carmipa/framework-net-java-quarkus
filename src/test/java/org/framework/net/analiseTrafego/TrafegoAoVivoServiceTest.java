package org.framework.net.analiseTrafego;

import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo;
import org.framework.net.analiseTrafego.aovivo.TrafegoAoVivoService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafegoAoVivoServiceTest {

    private final TrafegoAoVivoService service = new TrafegoAoVivoService();

    @Test
    void demoGeraPacotesProtocolosESerie() {
        SnapshotAoVivo s1 = service.snapshotDemo();
        SnapshotAoVivo s2 = service.snapshotDemo();
        assertEquals("demo", s1.modo());
        assertTrue(s2.totalPacotes() > s1.totalPacotes(), "total deve crescer a cada tick");
        assertFalse(s2.porProtocolo().isEmpty(), "deve haver contagem por protocolo");
        assertFalse(s2.ultimosPacotes().isEmpty(), "deve haver pacotes");
        assertEquals(2, s2.serie().size(), "série cresce um ponto por tick");
    }

    @Test
    void demoDetectaRedesAbertasInseguras() {
        SnapshotAoVivo s = service.snapshotDemo();
        assertTrue(s.redesAbertas() >= 1, "demo tem redes abertas para o alerta");
        boolean temAberta = s.wifi().stream().anyMatch(SnapshotAoVivo.RedeWifi::aberta);
        assertTrue(temAberta);
    }

    @Test
    void ingestaoDoAgenteAtualizaSnapshotReal() {
        service.ingerir(Map.of(
                "pacotes", List.of(Map.of(
                        "protocolo", "TCP", "origem", "192.168.0.5", "destino", "8.8.8.8",
                        "portaOrigem", 51000, "portaDestino", 443, "tamanho", 120, "info", "ACK")),
                "wifi", List.of(Map.of("ssid", "TEST_OPEN", "seguranca", "Aberta", "sinal", -50))));
        SnapshotAoVivo s = service.snapshotAgente();
        assertEquals("agente", s.modo());
        assertTrue(s.agenteConectado());
        assertEquals(1L, s.totalPacotes());
        assertTrue(s.porProtocolo().getOrDefault("TCP", 0L) >= 1);
        assertEquals(1, s.redesAbertas());
    }
}
