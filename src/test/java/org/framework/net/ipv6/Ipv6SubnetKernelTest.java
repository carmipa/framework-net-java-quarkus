package org.framework.net.ipv6;

import org.framework.net.ipv6.domain.Ipv6SubnetKernel;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.AnaliseIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.ComparacaoIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.DecomposicaoIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.DelegacaoPlano;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.DivisaoIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.Eui64Result;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.UlaResult;
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
    void decomporTem128BitsCorteRedeInterfaceEDelegacao() {
        DecomposicaoIpv6 d = kernel.decompor("2001:db8::/48");
        assertEquals(8, d.hextetos().size());
        assertEquals(48, d.bitsRede());
        assertEquals(80, d.bitsInterface());
        // Hexteto 3 (bits 32-47) está inteiro no prefixo /48; o hexteto 4 (bits 48-63), fora.
        assertEquals(16, d.hextetos().get(2).bitsRede());
        assertEquals(0, d.hextetos().get(3).bitsRede());
        // Delegação: um /48 comporta 65536 sub-redes /64 (gabarito 2^(64-48)).
        assertTrue(d.delegacao().stream()
                .anyMatch(l -> l.prefixo().equals("/64") && l.quantidade().equals("65536")));
    }

    @Test
    void eui64DerivaInterfaceIdComFlipUL() {
        // Gabarito independente (RFC 4291): flip do bit U/L (00 XOR 02 = 02) + inserção de FFFE.
        Eui64Result e = kernel.eui64("2001:db8:0:1::/64", "00:1a:2b:3c:4d:5e");
        assertEquals("021a:2bff:fe3c:4d5e", e.interfaceId());
        // Forma canônica da lib (seancfoley) — comprime o grupo-zero único como "::".
        assertEquals("2001:db8::1:21a:2bff:fe3c:4d5e", e.enderecoSlaac());
        assertEquals("2001:db8:0:1::/64", e.prefixoRede());
    }

    @Test
    void eui64RejeitaMacInvalido() {
        assertThrows(Ipv6Exception.class, () -> kernel.eui64("2001:db8::/64", "xyz"));
    }

    @Test
    void ulaComecaComFdEGeraGlobalIdDe40Bits() {
        UlaResult u = kernel.gerarUla("1");
        assertTrue(u.ula48().startsWith("fd"), "ULA deve começar com fd: " + u.ula48());
        assertTrue(u.ula48().endsWith("/48"));
        assertTrue(u.ula64().endsWith("/64"));
        assertEquals(10, u.globalId().length()); // 40 bits = 10 dígitos hex
    }

    @Test
    void ulaRejeitaSubnetIdForaDaFaixa() {
        assertThrows(Ipv6Exception.class, () -> kernel.gerarUla("70000"));
    }

    @Test
    void compararMesmoEnderecoTemDistanciaZero() {
        ComparacaoIpv6 c = kernel.comparar("2001:db8::1", "2001:db8::1");
        assertTrue(c.mesmoEndereco());
        assertEquals("0", c.distancia());
        assertEquals(128, c.bitsComuns());
        assertTrue(c.mesmaLan());
    }

    @Test
    void compararVizinhosContam126BitsComuns() {
        // Gabarito independente: ...0001 vs ...0010 diferem nos 2 últimos bits → 126 bits em comum.
        ComparacaoIpv6 c = kernel.comparar("2001:db8::1", "2001:db8::2");
        assertFalse(c.mesmoEndereco());
        assertEquals(126, c.bitsComuns());
        assertEquals("1", c.distancia());
        assertTrue(c.mesmaLan());
    }

    @Test
    void compararPrefixoContemEnderecoNaMesmaLan() {
        ComparacaoIpv6 c = kernel.comparar("2001:db8:0:1::/64", "2001:db8:0:1:abcd::1");
        assertTrue(c.mesmaLan());
        assertEquals(64, c.bitsComuns());
        assertTrue(c.contencao().contains("contém"));
    }

    @Test
    void compararLansDiferentesNaoSaoMesmaLan() {
        // 2001:db8:0:1 vs 2001:db8:0:2: divergem no hexteto 4 (bit 63) → 62 bits comuns, < 64.
        ComparacaoIpv6 c = kernel.comparar("2001:db8:0:1::1", "2001:db8:0:2::1");
        assertFalse(c.mesmaLan());
        assertEquals(62, c.bitsComuns());
    }

    @Test
    void planejarDelegacaoAlocaUmBlocoPorNome() {
        DelegacaoPlano p = kernel.planejarDelegacao("2001:db8::/48", 64,
                java.util.List.of("Matriz", "Filial", "DMZ"), 256);
        assertEquals(48, p.prefixoBase());
        assertEquals(64, p.prefixoAlvo());
        assertEquals("65536", p.capacidade());   // 2^(64-48)
        assertEquals(3, p.usados());
        assertEquals(3, p.alocacoes().size());
        assertEquals("Matriz", p.alocacoes().get(0).nome());
        assertEquals("2001:db8::", p.alocacoes().get(0).rede());
        assertEquals("2001:db8:0:1::", p.alocacoes().get(1).rede());
        assertEquals("2001:db8::1", p.alocacoes().get(0).gateway()); // ::1 do bloco
    }

    @Test
    void planejarDelegacaoRejeitaAlvoNaoMaisEspecifico() {
        assertThrows(Ipv6Exception.class,
                () -> kernel.planejarDelegacao("2001:db8::/48", 48, java.util.List.of("A"), 256));
    }

    @Test
    void planejarDelegacaoRejeitaMaisNomesQueCapacidade() {
        // Um /48 em /50 comporta 4 blocos; pedir 5 estoura (gabarito 2^(50-48)=4).
        assertThrows(Ipv6Exception.class, () -> kernel.planejarDelegacao("2001:db8::/48", 50,
                java.util.List.of("a", "b", "c", "d", "e"), 256));
    }

    @Test
    void planejarDelegacaoRejeitaSemNomes() {
        assertThrows(Ipv6Exception.class,
                () -> kernel.planejarDelegacao("2001:db8::/48", 64, java.util.List.of(), 256));
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
