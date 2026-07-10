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

    public String testarPacote(String regra, String ipOrigem, String ipDestino, String portaDestinoRaw) {
        return telemetriaLogger.medir("seguranca", "teste_acl", () -> {
            int portaDestino = validarEntradas(regra, ipOrigem, ipDestino, portaDestinoRaw);

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

    private int validarEntradas(String regra, String ipOrigem, String ipDestino, String portaDestinoRaw) {
        exigir(regra, "Regra ACL não informada.");
        exigir(ipOrigem, "IP de Origem não informado.");
        exigir(ipDestino, "IP de Destino não informado.");
        if (regra.length() > 200 || ipOrigem.length() > 45 || ipDestino.length() > 45) {
            throw new SegurancaException("Entrada muito longa.");
        }
        // Defesa em profundidade: rejeita caracteres de HTML/scripts nos campos.
        if (contemPerigoso(regra) || contemPerigoso(ipOrigem) || contemPerigoso(ipDestino)) {
            throw new SegurancaException("Caracteres inválidos detectados nas entradas.");
        }
        int porta;
        try {
            porta = Integer.parseInt(portaDestinoRaw == null ? "" : portaDestinoRaw.trim());
        } catch (NumberFormatException ex) {
            throw new SegurancaException("Porta de destino inválida (informe um número).");
        }
        if (porta <= 0 || porta > 65535) {
            throw new SegurancaException("Porta de destino fora do intervalo (1–65535).");
        }
        return porta;
    }

    private static void exigir(String valor, String mensagem) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new SegurancaException(mensagem);
        }
    }

    private static boolean contemPerigoso(String valor) {
        return valor.indexOf('<') >= 0 || valor.indexOf('>') >= 0
                || valor.indexOf('"') >= 0 || valor.indexOf('\'') >= 0 || valor.indexOf('`') >= 0;
    }
}
