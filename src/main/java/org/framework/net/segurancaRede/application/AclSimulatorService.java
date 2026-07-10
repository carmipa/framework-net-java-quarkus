package org.framework.net.segurancaRede.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.segurancaRede.exception.SegurancaException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.Map;

@ApplicationScoped
public class AclSimulatorService {

    @Inject
    TelemetriaLogger telemetriaLogger;

    public String testarPacote(String regra, String ipOrigem, String ipDestino, int portaDestino) {
        return telemetriaLogger.medir("seguranca", "teste_acl", () -> {
            validarEntradas(regra, ipOrigem, ipDestino, portaDestino);
            
            // Simulação simples de parser de ACL
            // Exemplo de regra esperada: permit tcp 192.168.1.0 0.0.0.255 eq 80
            String regraLower = regra.toLowerCase();
            boolean isPermit = regraLower.startsWith("permit");
            
            telemetriaLogger.logEvent("info", "seguranca", "acl_avaliada", Map.of(
                    "regra", regra,
                    "ipOrigem", ipOrigem,
                    "ipDestino", ipDestino,
                    "portaDestino", portaDestino
            ));
            
            boolean deuMatch = false;
            
            if (regraLower.contains(ipOrigem) || regraLower.contains("any")) {
                if (regraLower.contains("eq " + portaDestino) || !regraLower.contains("eq")) {
                    deuMatch = true;
                }
            }
            
            if (deuMatch) {
                return isPermit ? "MATCH (PERMITIDO) - O pacote atende à regra e foi liberado." 
                                : "MATCH (BLOQUEADO) - O pacote atende à regra de bloqueio (deny).";
            } else {
                return "NO MATCH - O pacote não foi capturado por essa regra (avaliaria a próxima ou default deny).";
            }
        });
    }

    private void validarEntradas(String regra, String ipOrigem, String ipDestino, int portaDestino) {
        if (regra == null || regra.trim().isEmpty()) throw new SegurancaException("Regra ACL não informada.");
        if (ipOrigem == null || ipOrigem.trim().isEmpty()) throw new SegurancaException("IP de Origem não informado.");
        if (ipDestino == null || ipDestino.trim().isEmpty()) throw new SegurancaException("IP de Destino não informado.");
        if (portaDestino <= 0 || portaDestino > 65535) throw new SegurancaException("Porta de destino inválida.");
    }
}
