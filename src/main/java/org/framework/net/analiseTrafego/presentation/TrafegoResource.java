package org.framework.net.analiseTrafego.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.analiseTrafego.application.TrafegoDecoderService;
import org.framework.net.analiseTrafego.domain.model.ResultadoDecodificacao;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.Map;

/**
 * Módulo Tráfego: decodificador didático de pacotes (hex dump → camadas explicadas).
 */
@Path("/trafego")
public class TrafegoResource {

    @Inject
    TrafegoDecoderService decoderService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    @io.quarkus.qute.Location("trafego/index.html")
    Template index;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        return index.data("activeMainMenu", "trafego");
    }

    @POST
    @Path("/api/decodificar")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public ResultadoDecodificacao decodificar(
            @FormParam("hex") String hex,
            @FormParam("camada") @DefaultValue("auto") String camada) {
        ResultadoDecodificacao resultado = decoderService.decodificar(hex, camada);
        telemetriaLogger.logEvent(resultado.ok() ? "info" : "warn", "analiseTrafego", "decode_packet",
                resultado.ok() ? "ok" : "error",
                Map.of("bytes", resultado.totalBytes(), "camadas", resultado.camadas().size()));
        return resultado;
    }
}
