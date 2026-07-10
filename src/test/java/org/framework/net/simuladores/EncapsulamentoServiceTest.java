package org.framework.net.simuladores;

import org.framework.net.simuladores.application.EncapsulamentoService;
import org.framework.net.simuladores.domain.ResultadoEncapsulamento;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncapsulamentoServiceTest {

    private final EncapsulamentoService service = new EncapsulamentoService();

    @Test
    void encapsulaTcpHttpEmQuatroCamadas() {
        ResultadoEncapsulamento r = service.encapsular(
                "GET / HTTP/1.1", "TCP", "192.168.0.10", "142.250.79.14", "51000", "80");
        assertTrue(r.ok());
        assertEquals("TCP", r.transporte());
        assertEquals(4, r.camadas().size());
        // Ordem de encapsulamento: 7 → 4 → 3 → 2
        assertEquals(7, r.camadas().get(0).nivel());
        assertEquals(2, r.camadas().get(3).nivel());
        // App detectada como HTTP pela porta 80
        assertEquals("HTTP", r.camadas().get(0).protocolo());
        // O quadro é maior que a mensagem (cabeçalhos somados)
        int appBytes = "GET / HTTP/1.1".getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        assertTrue(r.totalBytes() > appBytes);
    }

    @Test
    void udpUsaProtocolo17EDetectaDns() {
        ResultadoEncapsulamento r = service.encapsular(
                "example.com A?", "UDP", "192.168.0.10", "8.8.8.8", "51000", "53");
        assertTrue(r.ok());
        assertEquals("UDP", r.transporte());
        assertEquals("DNS", r.camadas().get(0).protocolo());
        boolean temProto17 = r.camadas().stream()
                .filter(c -> c.nivel() == 3)
                .flatMap(c -> c.cabecalho().stream())
                .anyMatch(campo -> campo.valor().contains("17"));
        assertTrue(temProto17, "IPv4 deve indicar protocolo 17 (UDP)");
    }

    @Test
    void quadroRespeitaPayloadMinimoEthernet() {
        // Mensagem curta → payload IP < 46 bytes → quadro mínimo (14 + 46 + 4 = 64)
        ResultadoEncapsulamento r = service.encapsular(
                "oi", "UDP", "192.168.0.10", "8.8.8.8", "51000", "53");
        assertTrue(r.ok());
        assertEquals(64, r.totalBytes(), "quadro Ethernet mínimo de 64 bytes com padding");
    }

    @Test
    void rejeitaMensagemVazia() {
        ResultadoEncapsulamento r = service.encapsular(
                "  ", "TCP", "192.168.0.10", "8.8.8.8", "51000", "80");
        assertFalse(r.ok());
    }

    @Test
    void rejeitaIpInvalido() {
        ResultadoEncapsulamento r = service.encapsular(
                "GET /", "TCP", "999.999.0.1", "8.8.8.8", "51000", "80");
        assertFalse(r.ok());
    }

    @Test
    void rejeitaPortaInvalida() {
        ResultadoEncapsulamento r = service.encapsular(
                "GET /", "TCP", "192.168.0.10", "8.8.8.8", "51000", "70000");
        assertFalse(r.ok());
    }
}
