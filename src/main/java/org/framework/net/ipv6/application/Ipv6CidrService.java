package org.framework.net.ipv6.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.ipv6.config.Ipv6Config;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.AnaliseIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.DivisaoIpv6;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.Map;

/**
 * Orquestra a Calculadora IPv6: valida a intenção, chama o kernel e registra telemetria.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> ponto de aplicação entre o resource HTTP e o {@link
 * Ipv6SubnetKernel}, aplicando o teto de renderização da configuração.</p>
 * <p><b>INVARIANTES DO DOMÍNIO:</b> não reimplementa cálculo — delega ao kernel; o teto de linhas
 * vem sempre da config, nunca do cliente.</p>
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> deixa a {@code Ipv6Exception} do kernel subir para o
 * mapper (400); registra o evento de erro na telemetria via mapper.</p>
 */
@ApplicationScoped
public class Ipv6CidrService {

    @Inject
    Ipv6SubnetKernel kernel;

    @Inject
    Ipv6Config config;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public AnaliseIpv6 analisar(String entrada) {
        AnaliseIpv6 r = kernel.analisar(entrada);
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_analise",
                Map.of("status", "ok", "prefixo", r.prefixo(), "tipo", r.tipo()));
        return r;
    }

    public DivisaoIpv6 dividir(String cidr, int prefixoAlvo) {
        DivisaoIpv6 r = kernel.dividir(cidr, prefixoAlvo, config.maxLinhas());
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_divisao",
                Map.of("status", "ok", "prefixoBase", r.prefixoBase(),
                        "prefixoAlvo", r.prefixoAlvo(), "exibidas", r.exibidas()));
        return r;
    }
}
