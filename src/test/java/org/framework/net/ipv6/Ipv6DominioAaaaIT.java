package org.framework.net.ipv6;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.ipv6.application.Ipv6CidrService;
import org.framework.net.ipv6.application.Ipv6CidrService.DominioAaaaResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova end-to-end da resolução AAAA (aba "Domínio → IPv6") contra um domínio dual-stack real.
 *
 * <p><b>Propósito:</b> os demais testes IPv6 são herméticos (sem rede). Este exercita o caminho
 * completo — {@link Ipv6CidrService#resolverDominio} → {@code DnsResolver.resolverAaaaComCache} →
 * {@code getAllByName} → filtro {@code Inet6Address} → guarda SSRF → {@code kernel.analisar} — que
 * nenhum teste hermético cobre (A1: o legítimo é aceito e classificado, não só o defeito rejeitado).</p>
 *
 * <p><b>Sem rede não é reprovação:</b> se o ambiente não tiver DNS/saída (CI offline), o teste é
 * <em>ignorado</em> via {@link Assumptions}, nunca falha por motivo errado — mesmo padrão do
 * {@code ArquiteturaCamadasTest}.</p>
 */
@QuarkusTest
class Ipv6DominioAaaaIT {

    @Inject
    Ipv6CidrService service;

    @Test
    void resolveEAnalisaAaaaDeDominioDualStackReal() {
        Assumptions.assumeTrue(temResolucaoDeRede(),
                "Sem DNS/saída de rede no ambiente — prova de resolução AAAA ignorada.");

        DominioAaaaResult r = service.resolverDominio("cloudflare.com");

        assertEquals("cloudflare.com", r.dominio());
        assertNotNull(r.enderecoAaaa());
        assertTrue(r.enderecoAaaa().contains(":"), "AAAA deve ser IPv6: " + r.enderecoAaaa());
        assertNotNull(r.analise());
        // cloudflare.com publica AAAA em 2606:4700::/32 → global unicast (2000::/3), endereço público
        // que passa pela guarda SSRF e é classificado pela faixa IANA.
        assertEquals("Global unicast", r.analise().base().tipo(),
                "AAAA público deve ser classificado como global unicast: " + r.enderecoAaaa());
    }

    private boolean temResolucaoDeRede() {
        try {
            InetAddress.getByName("cloudflare.com");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
