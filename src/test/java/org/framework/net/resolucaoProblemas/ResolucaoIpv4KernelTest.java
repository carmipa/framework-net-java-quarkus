package org.framework.net.resolucaoProblemas;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.domain.kernel.Ipv4Kernel;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cobre a blindagem de {@link Ipv4Kernel#parseIpv4Parts(String, String)} no kernel de VLSM.
 * O bug era gêmeo do da análise didática: octeto com mais de 3 dígitos estourava int e
 * propagava NumberFormatException crua (500) em vez de erro de domínio.
 */
@QuarkusTest
class ResolucaoIpv4KernelTest {

    @Inject
    Ipv4Kernel kernel;

    @Test
    void parseIpv4PartsValido() {
        int[] parts = kernel.parseIpv4Parts("10.20.30.40", "IP");
        assertEquals(10, parts[0]);
        assertEquals(40, parts[3]);
    }

    @Test
    void parseIpv4PartsOctetoEstouraIntEhRejeitadoComoDominio() {
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts("9999999999.1.1.1", "IP"));
        int[] noLimite = kernel.parseIpv4Parts("255.255.255.255", "IP");
        assertEquals(255, noLimite[0]);
    }

    @Test
    void parseIpv4PartsRejeitaPontoSobrando() {
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts("192.168.0.10.", "IP"));
        int[] ok = kernel.parseIpv4Parts("192.168.0.10", "IP");
        assertEquals(10, ok[3]);
    }

    @Test
    void parseIpv4PartsOctetoForaDaFaixa() {
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts("256.1.1.1", "IP"));
    }
}
