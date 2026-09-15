package org.framework.net.ipv6;

import org.framework.net.ipv6.domain.Ipv6SubnetKernel;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.AnaliseIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.DivisaoIpv6;
import org.framework.net.ipv6.exception.Ipv6Exception;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre o motor IPv6 com valores concretos. Gabaritos independentes da implementação (A3): fatos
 * de IPv6 (RFC 5952 para a forma canônica, 2^(128−prefixo) para a contagem, faixas especiais IANA).
 */
class Ipv6SubnetKernelTest {

    private final Ipv6SubnetKernel kernel = new Ipv6SubnetKernel();

    @Test
    void comprimeExpandeEContaEnderecos() {
        AnaliseIpv6 r = kernel.analisar("2001:0db8:0000:0000:0000:0000:0000:0001");
        assertEquals("2001:db8::1", r.comprimido());
        assertEquals("2001:0db8:0000:0000:0000:0000:0000:0001", r.expandido());
        assertEquals(128, r.prefixo());
        assertFalse(r.temPrefixo());
        assertEquals("2^0", r.totalPotencia());
        assertEquals("1", r.totalEnderecos());
    }

    @Test
    void prefixoInformadoContaBlocoInteiro() {
        AnaliseIpv6 r = kernel.analisar("2001:db8::/48");
        assertTrue(r.temPrefixo());
        assertEquals(48, r.prefixo());
        assertEquals("2^80", r.totalPotencia());   // 128 − 48
        assertEquals("2001:db8::", r.rede());
    }

    @Test
    void classificaPorFaixaEspecialIana() {
        assertEquals("Loopback", kernel.analisar("::1").tipo());
        assertEquals("Link-local", kernel.analisar("fe80::1").tipo());
        assertEquals("ULA / Privado", kernel.analisar("fd00::1").tipo());
        assertEquals("Documentação", kernel.analisar("2001:db8::1").tipo());
        assertEquals("Global unicast", kernel.analisar("2606:4700:4700::1111").tipo());
    }

    @Test
    void solicitedNodeSoParaUnicast() {
        // Unicast global: ff02::1:ff + 24 bits baixos (RFC 4291).
        assertTrue(kernel.analisar("2606:4700:4700::1111").solicitedNode().startsWith("ff02::1:ff"));
        // Multicast não tem solicited-node.
        assertEquals("—", kernel.analisar("ff02::1").solicitedNode());
    }

    @Test
    void binarioTem8HextetosDe16Bits() {
        AnaliseIpv6 r = kernel.analisar("2001:db8::1");
        assertEquals(8, r.binarioHextetos().size());
        assertEquals(16, r.binarioHextetos().get(0).length());
        assertEquals("0010000000000001", r.binarioHextetos().get(0)); // 0x2001
    }

    @Test
    void dividirContaEEnumeraSubredes() {
        DivisaoIpv6 d = kernel.dividir("2001:db8::/32", 48, 256);
        assertEquals(32, d.prefixoBase());
        assertEquals(48, d.prefixoAlvo());
        assertEquals("65536", d.quantidadeSubredes());   // 2^(48−32)
        assertEquals(256, d.exibidas());                 // teto de renderização
        assertTrue(d.truncado());
        assertEquals("2001:db8::", d.subredes().get(0).rede());
        assertEquals("/48", d.subredes().get(0).prefixoStr());
    }

    @Test
    void dividirPequenoNaoTrunca() {
        DivisaoIpv6 d = kernel.dividir("2001:db8::/48", 50, 256);
        assertEquals("4", d.quantidadeSubredes());       // 2^(50−48)
        assertEquals(4, d.subredes().size());
        assertFalse(d.truncado());
        assertEquals("2001:db8::", d.subredes().get(0).rede());
    }

    @Test
    void alvoMaisAmploQueBaseEhRejeitado() {
        Ipv6Exception ex = assertThrows(Ipv6Exception.class,
                () -> kernel.dividir("2001:db8::/48", 32, 256));
        assertTrue(ex.getMessage().toLowerCase().contains("amplo"));
    }

    @Test
    void dividirSemPrefixoNaBaseEhRejeitado() {
        assertThrows(Ipv6Exception.class, () -> kernel.dividir("2001:db8::", 64, 256));
    }

    @Test
    void casoControleEntradaInvalidaXlegitima() {
        // A1: malformado e IPv4 puro rejeitados; o legítimo semelhante é aceito.
        assertThrows(Ipv6Exception.class, () -> kernel.analisar("nao-e-ipv6"));
        assertThrows(Ipv6Exception.class, () -> kernel.analisar(""));
        assertThrows(Ipv6Exception.class, () -> kernel.analisar("192.168.0.1"));
        assertEquals("2001:db8::", kernel.analisar("2001:db8::").comprimido());
    }
}
