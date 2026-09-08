package org.framework.net.segurancaRede;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.segurancaRede.application.AvaliadorTopologiaService;
import org.framework.net.segurancaRede.domain.DiagnosticoFluxo;
import org.framework.net.segurancaRede.exception.SegurancaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P05 fase 2 — motor de alcançabilidade sobre topologia montada.
 *
 * <p><b>Propósito de negócio:</b> travar o veredito sobre um grafo arbitrário —
 * chega/não chega e o ponto exato de bloqueio — e a recusa de entradas inválidas.</p>
 */
@QuarkusTest
@DisplayName("Segurança: motor de topologia montada (P05 fase 2)")
class AvaliadorTopologiaServiceTest {

    @Inject
    AvaliadorTopologiaService servico;

    private static final String TOPO = String.join("\n",
            "host H1 vlan=10 gw=R1",
            "switchl3 R1 vlans=10,20",
            "firewall FW deny=tcp/23",
            "server S1 vlan=20 porta=443",
            "link H1 R1",
            "link R1 FW",
            "link FW S1");

    @Test
    @DisplayName("tudo configurado: o fluxo alcança o servidor")
    void alcancaQuandoConfigurado() {
        DiagnosticoFluxo d = servico.diagnosticar(TOPO, "H1", "S1", "443");
        assertTrue(d.alcanca(), () -> "esperava alcançar; ponto: " + d.pontoBloqueio());
        assertTrue(d.pontoBloqueio().isEmpty());
    }

    @Test
    @DisplayName("ACL nega a porta: bloqueia no firewall")
    void aclNegaBloqueiaNoFirewall() {
        DiagnosticoFluxo d = servico.diagnosticar(TOPO, "H1", "S1", "23");
        assertFalse(d.alcanca());
        assertTrue(d.pontoBloqueio().contains("FW"), d.pontoBloqueio());
        verificarBloqueioUnicoEFinal(d);
    }

    @Test
    @DisplayName("switch L3 sem a VLAN de destino: bloqueia no roteamento inter-VLAN")
    void vlanNaoRoteadaBloqueia() {
        String topo = TOPO.replace("switchl3 R1 vlans=10,20", "switchl3 R1 vlans=10");
        DiagnosticoFluxo d = servico.diagnosticar(topo, "H1", "S1", "443");
        assertFalse(d.alcanca());
        assertTrue(d.pontoBloqueio().contains("R1"), d.pontoBloqueio());
        verificarBloqueioUnicoEFinal(d);
    }

    @Test
    @DisplayName("host sem gateway: bloqueia já no host")
    void semGatewayBloqueiaNoHost() {
        String topo = TOPO.replace("host H1 vlan=10 gw=R1", "host H1 vlan=10");
        DiagnosticoFluxo d = servico.diagnosticar(topo, "H1", "S1", "443");
        assertFalse(d.alcanca());
        assertTrue(d.pontoBloqueio().contains("H1"), d.pontoBloqueio());
    }

    @Test
    @DisplayName("sem enlace até o destino: sem caminho")
    void semCaminhoBloqueia() {
        String topo = TOPO.replace("link FW S1", "");
        DiagnosticoFluxo d = servico.diagnosticar(topo, "H1", "S1", "443");
        assertFalse(d.alcanca());
    }

    @Test
    @DisplayName("origem que não é host é recusada")
    void origemNaoHostRecusa() {
        assertThrows(SegurancaException.class, () -> servico.diagnosticar(TOPO, "R1", "S1", "443"));
    }

    @Test
    @DisplayName("id inexistente é recusado")
    void idInexistenteRecusa() {
        assertThrows(SegurancaException.class, () -> servico.diagnosticar(TOPO, "H1", "NAO_EXISTE", "443"));
    }

    @Test
    @DisplayName("tipo não suportado no texto é recusado")
    void tipoInvalidoRecusa() {
        assertThrows(SegurancaException.class, () -> servico.diagnosticar("gizmo X\nlink X X", "X", "X", "80"));
    }

    private static void verificarBloqueioUnicoEFinal(DiagnosticoFluxo d) {
        assertEquals(1, d.saltos().stream().filter(s -> !s.ok()).count(), "um único ponto de bloqueio");
        assertFalse(d.saltos().get(d.saltos().size() - 1).ok(), "o bloqueio é o último salto");
    }
}
