package org.framework.net.ferramentasDiagnostico.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.ferramentasDiagnostico.exception.DiagnosticoException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.Map;

@ApplicationScoped
public class DiagnosticoService {

    @Inject
    TelemetriaLogger telemetriaLogger;

    public String executarPingSimulado(String host) {
        return telemetriaLogger.medir("diagnostico", "ping_simulado", () -> {
            validarHost(host);
            telemetriaLogger.logEvent("info", "diagnostico", "ping_executado", Map.of("host", host));
            
            StringBuilder sb = new StringBuilder();
            sb.append("Disparando PING contra ").append(host).append(" [Simulado] com 32 bytes de dados:\n");
            for (int i = 0; i < 4; i++) {
                sb.append("Resposta de ").append(host).append(": bytes=32 tempo=").append((int)(Math.random() * 40 + 10)).append("ms TTL=54\n");
            }
            sb.append("\nEstatísticas do Ping para ").append(host).append(":\n");
            sb.append("    Pacotes: Enviados = 4, Recebidos = 4, Perdidos = 0 (0% de perda)\n");
            return sb.toString();
        });
    }

    public String executarDnsSimulado(String dominio) {
        return telemetriaLogger.medir("diagnostico", "dns_simulado", () -> {
            validarHost(dominio);
            telemetriaLogger.logEvent("info", "diagnostico", "dns_executado", Map.of("dominio", dominio));
            
            return "; <<>> DiG 9.16.1-Ubuntu <<>> " + dominio + "\n" +
                   ";; global options: +cmd\n" +
                   ";; Got answer:\n" +
                   ";; ->>HEADER<<- opcode: QUERY, status: NOERROR, id: 12345\n" +
                   ";; flags: qr rd ra; QUERY: 1, ANSWER: 1, AUTHORITY: 0, ADDITIONAL: 1\n\n" +
                   ";; ANSWER SECTION:\n" +
                   dominio + ".\t\t300\tIN\tA\t" + gerarIpAleatorio() + "\n\n" +
                   ";; Query time: " + (int)(Math.random() * 20 + 2) + " msec\n" +
                   ";; SERVER: 8.8.8.8#53(8.8.8.8)\n";
        });
    }

    private void validarHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            throw new DiagnosticoException("Host ou domínio não pode ser vazio.");
        }
        if (host.length() > 255) {
            throw new DiagnosticoException("Host inválido ou muito longo.");
        }
        if (host.contains(" ") || host.contains(";") || host.contains("&") || host.contains("|")) {
            throw new DiagnosticoException("Caracteres inválidos detectados. Apenas hosts e IPs são permitidos.");
        }
    }
    
    private String gerarIpAleatorio() {
        return (int)(Math.random() * 254 + 1) + "." + 
               (int)(Math.random() * 255) + "." + 
               (int)(Math.random() * 255) + "." + 
               (int)(Math.random() * 254 + 1);
    }
}
