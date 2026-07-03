package org.framework.net.analiseDidatica.geo;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.infrastructure.geo.GeoLookupService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class GeoLookupServiceTest {

    @Inject
    GeoLookupService geoLookupService;

    @Test
    void ipPrivadoMarcaReservado() {
        Map<String, Object> out = geoLookupService.lookupRegiaoGeografica("192.168.1.10");
        assertEquals("private_or_local", out.get("motivo"));
        assertTrue(Boolean.TRUE.equals(out.get("reservado")));
        assertEquals("🏠 Rede local", out.get("risco_badge"));
        assertNotNull(out.get("reservado_motivo"));
    }

    @Test
    void ipInvalidoRetornaErro() {
        Map<String, Object> out = geoLookupService.lookupRegiaoGeografica("nao-e-ip");
        assertEquals("invalid", out.get("motivo"));
        assertFalse(Boolean.TRUE.equals(out.get("ok")));
        assertNotNull(out.get("erro"));
    }

    @Test
    void ipPublicoRetornaCamposEnriquecidos() {
        Map<String, Object> out = geoLookupService.lookupRegiaoGeografica("8.8.8.8");
        assertTrue(Boolean.TRUE.equals(out.get("ok")));
        assertNotNull(out.get("pais"));
        assertNotNull(out.get("pais_codigo"));
        assertNotNull(out.get("risco_badge"));
        assertNotNull(out.get("proxy_flag"));
    }

    @Test
    void normalizarIpRejeitaHostname() {
        Optional<String> norm = geoLookupService.normalizarIpDigitado("example.com");
        assertTrue(norm.isEmpty());
    }

    @Test
    void normalizarIpAceitaIpv4() {
        Optional<String> norm = geoLookupService.normalizarIpDigitado("8.8.8.8");
        assertTrue(norm.isPresent());
        assertEquals("8.8.8.8", norm.get());
    }
}
