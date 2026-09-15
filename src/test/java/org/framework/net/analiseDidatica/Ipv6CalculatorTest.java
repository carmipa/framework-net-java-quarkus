package org.framework.net.analiseDidatica;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.domain.Ipv6Calculator;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre o núcleo de cálculo IPv6 com valores concretos. Antes deste teste, o único toque no
 * Ipv6Calculator era um HttpTest tautológico que só verificava contains("2001") — substring da
 * própria entrada ecoada. Compressão, expansão e cálculo de rede /64 errados passavam verde.
 *
 * Gabarito independente da implementação (A3): os valores esperados são fatos conhecidos de IPv6
 * (RFC 5952 para forma canônica), não recalculados pela lógica sob teste.
 */
@QuarkusTest
class Ipv6CalculatorTest {

    @Inject
    Ipv6Calculator calc;

    @Test
    void comprimeExpandeValoresConcretos() {
        Map<String, Object> r = calc.processar("2001:0db8:0000:0000:0000:0000:0000:0001");
        assertEquals("2001:db8::1", r.get("comprimido"));
        assertEquals("2001:0db8:0000:0000:0000:0000:0000:0001", r.get("expandido"));
        assertEquals("/64", r.get("prefixo_sugerido"));
    }

    @Test
    void expandeLoopback() {
        Map<String, Object> r = calc.processar("::1");
        assertEquals("::1", r.get("comprimido"));
        assertEquals("0000:0000:0000:0000:0000:0000:0000:0001", r.get("expandido"));
        assertEquals("Loopback", r.get("tipo"));
    }

    @Test
    void rede64ZeraOsUltimos64Bits() {
        Map<String, Object> r = calc.processar("2001:db8:abcd:1234:5678:9abc:def0:1");
        String rede64 = String.valueOf(r.get("rede_64"));
        // A rede /64 preserva os 64 bits altos e zera os baixos: o "5678..." tem de sumir.
        assertTrue(rede64.startsWith("2001:db8:abcd:1234::"), "rede_64 inesperada: " + rede64);
        assertFalse(rede64.contains("5678"), "host nao foi zerado: " + rede64);
    }

    @Test
    void classificaLinkLocalUlaEGlobal() {
        assertEquals("Link-local", calc.processar("fe80::1").get("tipo"));
        assertEquals("ULA/Privado", calc.processar("fc00::1").get("tipo"));
        Map<String, Object> global = calc.processar("2001:db8::1");
        assertEquals("Global unicast", global.get("tipo"));
        assertEquals("Sim", global.get("roteavel"));
    }

    @Test
    void rejeitaEntradaInvalida() {
        // Caso-controle: entrada malformada e IPv4 puro rejeitados; a compressa legitima e aceita.
        assertThrows(EntradaInvalidaException.class, () -> calc.processar("nao-e-ipv6"));
        assertThrows(EntradaInvalidaException.class, () -> calc.processar(""));
        assertThrows(EntradaInvalidaException.class, () -> calc.processar("192.168.0.1"));
    }
}
