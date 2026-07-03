package org.framework.net.resolucaoProblemas;

import inet.ipaddr.ipv4.IPv4Address;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.application.normalization.VlsmNormalizationService;
import org.framework.net.resolucaoProblemas.application.planning.VlsmPlanningService;
import org.framework.net.resolucaoProblemas.domain.kernel.Ipv4Kernel;
import org.framework.net.resolucaoProblemas.domain.model.LanBlock;
import org.framework.net.resolucaoProblemas.domain.model.WanLink;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class VlsmPlanningServiceTest {

    @Inject
    VlsmPlanningService planningService;

    @Inject
    VlsmNormalizationService normalizationService;

    @Inject
    Ipv4Kernel ipv4Kernel;

    private static final List<String> KEYS_4 = List.of("loc_1", "loc_2", "loc_3", "loc_4");

    @Test
    void normalizeTopologyPadraoStar() {
        assertEquals("star", planningService.normalizeTopologyType(""));
        assertEquals("star", planningService.normalizeTopologyType("star"));
    }

    @Test
    void normalizeTopologyAliases() {
        assertEquals("star", planningService.normalizeTopologyType("ring"));
        assertEquals("extended_star", planningService.normalizeTopologyType("ring_redundant"));
        assertEquals("extended_star", planningService.normalizeTopologyType("estrela_estendida"));
        assertEquals("mesh", planningService.normalizeTopologyType("mesh"));
    }

    @Test
    void requiredPrefix400Hosts() {
        assertEquals(23, planningService.requiredPrefixForHosts(400));
    }

    @Test
    void requiredPrefix300Hosts() {
        assertEquals(23, planningService.requiredPrefixForHosts(300));
    }

    @Test
    void requiredPrefix50Hosts() {
        assertEquals(26, planningService.requiredPrefixForHosts(50));
    }

    @Test
    void buildWanLinksStarQuatroPontos() {
        IPv4Address base = ipv4Kernel.parseNetwork("172.19.0.0/16", "base");
        List<IPv4Address> used = new ArrayList<>();
        List<WanLink> links = planningService.buildWanLinks(base, used, KEYS_4, "star", 30);
        assertEquals(3, links.size());
        assertTrue(links.stream().allMatch(l -> l.getEndpoints().get(0).equals("loc_1")));
        Set<String> filiais = links.stream().map(l -> l.getEndpoints().get(1)).collect(Collectors.toSet());
        assertEquals(Set.of("loc_2", "loc_3", "loc_4"), filiais);
    }

    @Test
    void buildWanLinksExtendedStarCincoLinks() {
        IPv4Address base = ipv4Kernel.parseNetwork("172.19.0.0/16", "base");
        List<IPv4Address> used = new ArrayList<>();
        List<WanLink> links = planningService.buildWanLinks(base, used, KEYS_4, "extended_star", 30);
        assertEquals(5, links.size());
    }

    @Test
    void buildWanLinksMeshSeisLinks() {
        IPv4Address base = ipv4Kernel.parseNetwork("172.19.0.0/16", "base");
        List<IPv4Address> used = new ArrayList<>();
        List<WanLink> links = planningService.buildWanLinks(base, used, KEYS_4, "mesh", 30);
        assertEquals(6, links.size());
    }

    @Test
    void buildWanLinksDoisPontosUmLink() {
        IPv4Address base = ipv4Kernel.parseNetwork("10.0.0.0/24", "base");
        List<IPv4Address> used = new ArrayList<>();
        List<WanLink> links = planningService.buildWanLinks(
                base, used, List.of("loc_1", "loc_2"), "star", 30);
        assertEquals(1, links.size());
    }

    @Test
    void buildWanLinksUmPontoSemLinks() {
        IPv4Address base = ipv4Kernel.parseNetwork("10.0.0.0/24", "base");
        List<IPv4Address> used = new ArrayList<>();
        List<WanLink> links = planningService.buildWanLinks(
                base, used, List.of("loc_1"), "star", 30);
        assertEquals(0, links.size());
    }

    @Test
    void buildWanLinksTopologiaInvalida() {
        IPv4Address base = ipv4Kernel.parseNetwork("172.19.0.0/16", "base");
        List<IPv4Address> used = new ArrayList<>();
        EntradaInvalidaException ex = assertThrows(EntradaInvalidaException.class, () ->
                planningService.buildWanLinks(base, used, KEYS_4, "anel", 30));
        assertTrue(ex.getMessage().toLowerCase().contains("inválida")
                || ex.getMessage().toLowerCase().contains("invalida"));
    }

    @Test
    void usableHostsExcluiRedeEBroadcast() {
        IPv4Address net30 = ipv4Kernel.parseNetwork("192.168.1.0/30", "base");
        List<IPv4Address> hosts = ipv4Kernel.usableHosts(net30);
        List<String> ips = hosts.stream().map(IPv4Address::toCanonicalString).toList();
        // /30 tem 4 endereços: .0 (rede), .1, .2, .3 (broadcast) → apenas .1 e .2 são utilizáveis.
        assertEquals(2, hosts.size());
        assertTrue(ips.contains("192.168.1.1"));
        assertTrue(ips.contains("192.168.1.2"));
        assertTrue(!ips.contains("192.168.1.0"));
        assertTrue(!ips.contains("192.168.1.3"));
    }

    @Test
    void usableHostsLanExcluiBroadcast() {
        IPv4Address net29 = ipv4Kernel.parseNetwork("10.0.0.0/29", "base");
        List<IPv4Address> hosts = ipv4Kernel.usableHosts(net29);
        List<String> ips = hosts.stream().map(IPv4Address::toCanonicalString).toList();
        // /29 = 8 endereços; 6 utilizáveis (.1..6), sem rede (.0) nem broadcast (.7).
        assertEquals(6, hosts.size());
        assertTrue(!ips.contains("10.0.0.0"));
        assertTrue(!ips.contains("10.0.0.7"));
    }

    @Test
    void iterateSubnetsGeraMultiplosBlocos() {
        IPv4Address base = ipv4Kernel.parseNetwork("172.19.0.0/16", "base");
        Iterator<? extends IPv4Address> it = ipv4Kernel.iterateSubnets(base, 21);
        int count = 0;
        while (it.hasNext()) {
            it.next();
            count++;
        }
        assertEquals(32, count);
    }

    @Test
    void buildLanBlocksOrdenaPorHosts() {
        IPv4Address base = ipv4Kernel.parseNetwork("172.19.0.0/16", "base");
        List<LanBlock> locs = normalizationService.normalizeLocationsInput(List.of(
                new org.framework.net.resolucaoProblemas.domain.model.LocationInput("Filial", "100"),
                new org.framework.net.resolucaoProblemas.domain.model.LocationInput("Matriz", "800"),
                new org.framework.net.resolucaoProblemas.domain.model.LocationInput("CPD", "550")
        ));
        VlsmPlanningService.PlanningResult result = planningService.buildLanBlocks(base, new ArrayList<>(locs));
        assertEquals(3, result.locations().size());
        List<Integer> hostsOrdenados = result.locations().stream()
                .map(LanBlock::getHostsRequired)
                .sorted(Comparator.reverseOrder())
                .toList();
        assertEquals(List.of(800, 550, 100), hostsOrdenados);
    }
}
