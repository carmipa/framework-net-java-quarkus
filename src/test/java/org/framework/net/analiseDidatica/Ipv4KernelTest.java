package org.framework.net.analiseDidatica;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class Ipv4KernelTest {

    @Inject
    Ipv4Kernel kernel;

    @Test
    void parseIpv4PartsValido() {
        int[] parts = kernel.parseIpv4Parts("192.168.1.10");
        assertEquals(192, parts[0]);
        assertEquals(168, parts[1]);
        assertEquals(1, parts[2]);
        assertEquals(10, parts[3]);
    }

    @Test
    void parseIpv4PartsInvalido() {
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts("999.1.1.1"));
    }

    @Test
    void inferirCidrClassfulClasseA() {
        Ipv4Kernel.InferenciaCidr inf = kernel.inferirCidrPorIp("10.5.5.5");
        assertEquals(8, inf.cidr());
    }

    @Test
    void inferirCidrClassfulClasseC() {
        Ipv4Kernel.InferenciaCidr inf = kernel.inferirCidrPorIp("200.1.1.1");
        assertEquals(24, inf.cidr());
    }

    @Test
    void mascaraDottedParaCidr() {
        assertEquals(24, kernel.mascaraDottedParaCidr("255.255.255.0"));
        assertEquals(18, kernel.mascaraDottedParaCidr("255.255.192.0"));
        assertNull(kernel.mascaraDottedParaCidr("255.255.255.1"));
    }

    @Test
    void wildcardDottedParaCidr() {
        assertEquals(24, kernel.wildcardDottedParaCidr("0.0.0.255"));
    }

    @Test
    void coreMascaraSlash24() {
        Map<String, Object> core = kernel.coreMascara(24);
        assertNotNull(core);
        assertEquals("255.255.255.0", core.get("mask"));
        assertEquals(256L, core.get("total"));
        assertEquals(254L, core.get("uteis"));
    }

    @Test
    void processarCidr31Rfc3021() {
        Map<String, Object> res = kernel.processar("10.0.0.0", 31);
        assertNotNull(res);
        assertEquals(31, res.get("cidr"));
        assertTrue(res.get("ip_papel_alerta").toString().contains("RFC 3021")
                || res.toString().contains("RFC 3021"));
    }

    @Test
    void processarCidr32HostUnico() {
        Map<String, Object> res = kernel.processar("10.0.0.1", 32);
        assertEquals(32, res.get("cidr"));
        assertEquals("10.0.0.1", res.get("primeiro_host"));
        assertEquals("10.0.0.1", res.get("ultimo_host"));
    }

    @Test
    void broadcastSmurfAlerta() {
        Map<String, Object> res = kernel.processar("192.168.1.255", 24);
        String json = res.toString();
        assertTrue(json.contains("Smurf") || json.contains("broadcast"));
    }

    @Test
    void enunciadoProvaMascara2552552240() {
        Map<String, Object> core = kernel.coreMascara(19);
        long pulo = ((Number) core.get("pulo")).longValue();
        Ipv4Kernel.TabelaReferenciaSubredes tab = kernel.tabelaReferenciaSubredes(19);
        Map<String, Object> e = kernel.enunciadoProvaIntervalos(
                19, pulo, ((Number) core.get("total")).longValue(),
                ((Number) core.get("uteis")).longValue(), tab.octeto());
        assertEquals(8L, ((Number) e.get("qtde_intervalos")).longValue());
        assertEquals(32L, ((Number) e.get("variacao")).longValue());
        assertEquals(3, ((Number) e.get("octeto_referencia")).intValue());
        assertTrue(e.get("frase_estilo_quadro").toString().contains("8 intervalos"));
    }

    @Test
    void processarSomenteMascara() {
        Map<String, Object> res = kernel.processarSomenteMascara(18);
        assertTrue((Boolean) res.get("somente_mascara"));
        assertEquals("—", res.get("rede"));
        assertEquals(18, res.get("cidr"));
    }

    @Test
    void classeBDidaticaMascaraCorreta() {
        Ipv4Kernel.ClasseDidatica info = kernel.classeIpv4Didatica(172);
        assertEquals("B", info.classe());
        assertTrue(info.classeFaixa().contains("255.255.0.0"));
    }

    @Test
    void processarRedeSlash24ValoresConcretos() {
        Map<String, Object> res = kernel.processar("192.168.1.10", 24);
        assertEquals("192.168.1.0", res.get("rede"));
        assertEquals("192.168.1.255", res.get("broad"));
        assertEquals("192.168.1.1", res.get("primeiro_host"));
        assertEquals("192.168.1.254", res.get("ultimo_host"));
        assertEquals("255.255.255.0", res.get("mask"));
        assertEquals("0.0.0.255", res.get("wildcard"));
        assertEquals(256L, ((Number) res.get("total")).longValue());
        assertEquals(254L, ((Number) res.get("uteis")).longValue());
        assertEquals(1L, ((Number) res.get("pulo")).longValue());
    }

    @Test
    void processarRedeSlash19ValoresConcretos() {
        Map<String, Object> res = kernel.processar("172.19.0.10", 19);
        assertEquals("172.19.0.0", res.get("rede"));
        assertEquals("172.19.31.255", res.get("broad"));
        assertEquals("172.19.0.1", res.get("primeiro_host"));
        assertEquals("172.19.31.254", res.get("ultimo_host"));
        assertEquals(8192L, ((Number) res.get("total")).longValue());
        assertEquals(8190L, ((Number) res.get("uteis")).longValue());
        assertEquals(32L, ((Number) res.get("pulo")).longValue());
    }

    @Test
    void processarRedeSlash30ValoresConcretos() {
        Map<String, Object> res = kernel.processar("10.0.0.0", 30);
        assertEquals("10.0.0.0", res.get("rede"));
        assertEquals("10.0.0.3", res.get("broad"));
        assertEquals("10.0.0.1", res.get("primeiro_host"));
        assertEquals("10.0.0.2", res.get("ultimo_host"));
        assertEquals(4L, ((Number) res.get("total")).longValue());
        assertEquals(2L, ((Number) res.get("uteis")).longValue());
    }

    @Test
    void wildcardValidaSlash20() {
        assertEquals(20, kernel.wildcardDottedParaCidr("0.0.15.255"));
        Map<String, Object> res = kernel.processar("172.16.8.8", 20);
        assertEquals("172.16.0.0", res.get("rede"));
        assertEquals("172.16.15.255", res.get("broad"));
    }

    @Test
    void enunciadoProvaIncluiPotenciasDeDois() {
        Map<String, Object> res = kernel.processarSomenteMascara(19);
        @SuppressWarnings("unchecked")
        Map<String, Object> enunciado = (Map<String, Object>) res.get("enunciado_prova");
        assertNotNull(enunciado);
        assertNotNull(enunciado.get("linha_potencias_quadro"));
        assertTrue(enunciado.get("linha_potencias_quadro").toString().contains("2^"));
    }

    @Test
    void privacidadeRfc1918() {
        Ipv4Kernel.PrivacidadeResult p = kernel.privacidadeRfc1918(new int[]{10, 0, 0, 1});
        assertTrue(p.tipo().contains("Privado"));
    }
}
