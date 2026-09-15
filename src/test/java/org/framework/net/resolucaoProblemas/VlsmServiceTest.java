package org.framework.net.resolucaoProblemas;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.application.VlsmService;
import org.framework.net.resolucaoProblemas.application.export.ExportTxtService;
import org.framework.net.resolucaoProblemas.application.export.ExportZipService;
import org.framework.net.resolucaoProblemas.application.normalization.VlsmNormalizationService;
import org.framework.net.resolucaoProblemas.application.routing.VlsmRoutingService;
import org.framework.net.resolucaoProblemas.domain.model.LanBlock;
import org.framework.net.resolucaoProblemas.domain.model.LocationInput;
import org.framework.net.resolucaoProblemas.domain.model.NetworkScenarioResult;
import org.framework.net.resolucaoProblemas.domain.model.WanLink;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class VlsmServiceTest {

    @Inject
    VlsmService vlsmService;

    @Inject
    VlsmRoutingService routingService;

    @Inject
    ExportTxtService exportTxtService;

    @Inject
    ExportZipService exportZipService;

    private NetworkScenarioResult solveDemo(VlsmNormalizationService.DemoScenario demo) {
        return vlsmService.solveNetworkProblem(
                demo.baseNetwork(),
                demo.locations(),
                demo.topologyType(),
                Integer.parseInt(demo.wanPrefix()),
                demo.eigrpAs().isBlank() ? 71 : Integer.parseInt(demo.eigrpAs()),
                demo.remoteAccess(),
                demo.routingMode(),
                demo.ospfProcess().isBlank() ? 1 : Integer.parseInt(demo.ospfProcess()));
    }

    @Test
    void cenarioFiapCheckpoint() {
        NetworkScenarioResult s = solveDemo(VlsmNormalizationService.FIAP_CHECKPOINT_DEMO);
        assertEquals("172.42.0.0/16", s.getBaseNetwork());
        assertEquals(4, s.getTotalLocations());
        assertEquals(4, s.getLanBlocks().size());
        assertTrue(s.getWanLinks().size() >= 3);
        assertNotNull(s.getRouterCommands().get("Matriz"));
        assertTrue(s.getRouterCommands().values().iterator().next().contains("router eigrp"));
    }

    @Test
    void cenarioMazolaGlobalSolution() {
        NetworkScenarioResult s = solveDemo(VlsmNormalizationService.MAZOLAS_GLOBAL_SOLUTION_DEMO);
        assertEquals("172.63.0.0/16", s.getBaseNetwork());
        assertEquals(3, s.getTotalLocations());
        assertEquals("star", s.getTopologyType());
        assertEquals(2, s.getWanLinks().size());
    }

    @Test
    void oitoRoteadoresSsh() {
        NetworkScenarioResult s = solveDemo(VlsmNormalizationService.EIGHT_ROUTERS_DEMO);
        assertEquals(8, s.getTotalLocations());
        assertEquals("ssh", s.getRemoteAccess());
        String cli = s.getRouterCommands().get("Matriz");
        assertTrue(cli.contains("transport input ssh"));
        assertTrue(cli.contains("ip ssh version 2"));
    }

    @Test
    void suggestedBasePrefixDentroDoIntervaloValido() {
        List<LocationInput> locs = List.of(
                new LocationInput("A", "50"),
                new LocationInput("B", "50")
        );
        NetworkScenarioResult s = vlsmService.solveNetworkProblem(
                "10.0.0.0/16", locs, "star", 30, 71, "telnet", "eigrp_only", 1);
        int suggested = s.getSuggestedBasePrefix();
        // Prefixo sugerido deve ser um CIDR válido (0..32) e nunca mais amplo que a base.
        assertTrue(suggested >= 0 && suggested <= 32,
                "prefixo sugerido fora de 0..32: " + suggested);
        assertTrue(suggested >= s.getBaseNetworkPrefix(),
                "prefixo sugerido não pode ser mais amplo que a rede base");
    }

    @Test
    void cenarioPequenoEigrpAs100() {
        List<LocationInput> locs = List.of(
                new LocationInput("A", "50"),
                new LocationInput("B", "50")
        );
        NetworkScenarioResult s = vlsmService.solveNetworkProblem(
                "10.0.0.0/24", locs, "star", 30, 100, "telnet", "eigrp_only", 1);
        assertEquals(100, s.getEigrpAs());
        assertTrue(s.getRouterCommands().get("A").contains("router eigrp 100"));
    }

    @Test
    void eigrpAsInvalidoZero() {
        List<LocationInput> locs = List.of(new LocationInput("X", "10"));
        EntradaInvalidaException ex = assertThrows(EntradaInvalidaException.class, () ->
                vlsmService.solveNetworkProblem("192.168.0.0/24", locs, "star", 30, 0, "telnet", "auto", 1));
        assertTrue(ex.getMessage().toLowerCase().contains("65535"));
    }

    @Test
    void routingModeAutoQuatroSitesDualSplit() {
        assertEquals("dual_split", routingService.normalizeRoutingMode("auto", 4));
        assertEquals("eigrp_only", routingService.normalizeRoutingMode("auto", 2));
    }

    @Test
    void exportTxtConsolidado() {
        NetworkScenarioResult s = solveDemo(VlsmNormalizationService.FIAP_CHECKPOINT_DEMO);
        String txt = exportTxtService.generatePacketTracerScript(s);
        assertNotNull(txt);
        assertTrue(txt.length() > 200);
        assertTrue(txt.contains("hostname") || txt.contains("configure terminal"));
    }

    @Test
    void exportEntregaRelatorio() {
        NetworkScenarioResult s = solveDemo(VlsmNormalizationService.FIAP_CHECKPOINT_DEMO);
        String relatorio = exportTxtService.generateEntregaRelatorioTxt(s);
        assertTrue(relatorio.length() > 500);
    }

    @Test
    void exportZipLaboratorio() throws Exception {
        NetworkScenarioResult s = solveDemo(VlsmNormalizationService.FIAP_CHECKPOINT_DEMO);
        byte[] zip = exportZipService.generatePacketTracerZipBuffer(s);
        assertNotNull(zip);
        assertTrue(zip.length > 100);
        int entries = 0;
        boolean algumComScriptCisco = false;
        // A6 — relê o CONTEÚDO de cada entrada, não só conta entradas. Uma entrada vazia ou
        // corrompida passaria no antigo "entries >= 3".
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries++;
                byte[] dados = zis.readAllBytes();
                assertTrue(dados.length > 0, "entrada de ZIP vazia: " + entry.getName());
                String texto = new String(dados, java.nio.charset.StandardCharsets.UTF_8);
                if (texto.contains("hostname") || texto.contains("configure terminal")
                        || texto.contains("interface")) {
                    algumComScriptCisco = true;
                }
            }
        }
        assertTrue(entries >= 3);
        assertTrue(algumComScriptCisco, "nenhuma entrada do ZIP continha script Cisco legivel");
    }

    /**
     * Invariante central do VLSM: nenhum bloco alocado (LAN ou WAN) pode sobrepor outro — dois
     * sites recebendo a mesma sub-rede é o pior erro possível de um planejador. Antes desta
     * asserção, os testes só conferiam CONTAGENS; a sobreposição passaria verde.
     *
     * Gabarito independente da implementação (A3): as faixas são recalculadas via inet.ipaddr a
     * partir de network/prefix (low/high do prefix block), não pela lógica do alocador sob teste.
     */
    @Test
    void blocosVlsmNaoSobrepoemEComportamOsHosts() {
        for (VlsmNormalizationService.DemoScenario demo : List.of(
                VlsmNormalizationService.FIAP_CHECKPOINT_DEMO,
                VlsmNormalizationService.EIGHT_ROUTERS_DEMO,
                VlsmNormalizationService.MAZOLAS_GLOBAL_SOLUTION_DEMO)) {
            NetworkScenarioResult s = solveDemo(demo);

            List<String> rotulos = new java.util.ArrayList<>();
            List<long[]> faixas = new java.util.ArrayList<>();

            for (LanBlock lan : s.getLanBlocks()) {
                assertTrue(lan.getHostsSupported() >= lan.getHostsRequired(),
                        "LAN " + lan.getLocationName() + " comporta " + lan.getHostsSupported()
                                + " < requeridos " + lan.getHostsRequired() + " em " + demo.baseNetwork());
                assertNotNull(lan.getNetwork(), "LAN sem network em " + demo.baseNetwork());
                rotulos.add("LAN:" + lan.getLocationName());
                faixas.add(faixaCidr(lan.getNetwork(), lan.getPrefix()));
            }
            for (WanLink wan : s.getWanLinks()) {
                assertNotNull(wan.getNetwork(), "WAN sem network em " + demo.baseNetwork());
                rotulos.add("WAN:" + wan.getName());
                faixas.add(faixaCidr(wan.getNetwork(), wan.getPrefix()));
            }

            for (int i = 0; i < faixas.size(); i++) {
                for (int j = i + 1; j < faixas.size(); j++) {
                    long[] a = faixas.get(i);
                    long[] b = faixas.get(j);
                    boolean disjuntos = a[1] < b[0] || b[1] < a[0];
                    assertTrue(disjuntos, "Sobreposicao entre " + rotulos.get(i) + " e "
                            + rotulos.get(j) + " no cenario " + demo.baseNetwork());
                }
            }
        }
    }

    private static long[] faixaCidr(String network, int prefix) {
        inet.ipaddr.ipv4.IPv4Address a = new inet.ipaddr.IPAddressString(network + "/" + prefix)
                .getAddress().toIPv4().toPrefixBlock();
        return new long[]{a.getLower().longValue(), a.getUpper().longValue()};
    }
}
