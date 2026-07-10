package org.framework.net.simuladores.presentation;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.simuladores.application.EncapsulamentoService;
import org.framework.net.simuladores.domain.ResultadoEncapsulamento;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.Map;

/**
 * API do simulador de encapsulamento (consumido pela sub-aba "Encapsulamento" da página Tráfego).
 * Lógica própria no pacote {@code simuladores}; computação pura, sem acesso privilegiado.
 */
@Path("/simuladores/api")
public class EncapsulamentoResource {

    @Inject
    EncapsulamentoService encapsulamentoService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @POST
    @Path("/encapsular")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public ResultadoEncapsulamento encapsular(
            @FormParam("mensagem") String mensagem,
            @FormParam("transporte") @DefaultValue("TCP") String transporte,
            @FormParam("ipOrigem") String ipOrigem,
            @FormParam("ipDestino") String ipDestino,
            @FormParam("portaOrigem") String portaOrigem,
            @FormParam("portaDestino") String portaDestino) {
        ResultadoEncapsulamento r = encapsulamentoService.encapsular(
                mensagem, transporte, ipOrigem, ipDestino, portaOrigem, portaDestino);
        telemetriaLogger.logEvent(r.ok() ? "info" : "warn", "simuladores", "encapsular",
                r.ok() ? "ok" : "error",
                Map.of("transporte", r.transporte(), "totalBytes", r.totalBytes(),
                        "camadas", r.camadas().size()));
        return r;
    }
}
