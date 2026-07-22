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
import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo;
import org.framework.net.analiseTrafego.aovivo.TrafegoAoVivoService;
import org.framework.net.analiseTrafego.application.TrafegoDecoderService;
import org.framework.net.analiseTrafego.domain.model.ResultadoDecodificacao;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.Map;

/**
 * Módulo Tráfego: dashboard de tráfego ao vivo (simulação didática) +
 * decodificador didático de pacotes (offline).
 */
@Path("/trafego")
public class TrafegoResource {

    @Inject
    TrafegoDecoderService decoderService;

    @Inject
    TrafegoAoVivoService aoVivoService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    @io.quarkus.qute.Location("trafego/index.html")
    Template index;

    @Inject
    @io.quarkus.qute.Location("trafego/partials/resultado_decodificacao.html")
    Template decodificacaoFragmento;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        return index.data("activeMainMenu", "trafego");
    }

    /**
     * Decodifica o hex dump e devolve as camadas já renderizadas como fragmento,
     * que o htmx troca no painel de resultado. Hex inválido também volta como
     * fragmento (com o aviso), e não como erro HTTP.
     */
    @POST
    @Path("/api/decodificar")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance decodificar(
            @FormParam("hex") String hex,
            @FormParam("camada") @DefaultValue("auto") String camada) {
        ResultadoDecodificacao resultado = decoderService.decodificar(hex, camada);
        telemetriaLogger.logEvent(resultado.ok() ? "info" : "warn", "analiseTrafego", "decode_packet",
                resultado.ok() ? "ok" : "error",
                Map.of("bytes", resultado.totalBytes(), "camadas", resultado.camadas().size()));
        return decodificacaoFragmento.data("resultado", resultado);
    }

    /** Snapshot do tráfego ao vivo (simulação didática, VPS-safe). */
    @GET
    @Path("/api/aovivo")
    @Produces(MediaType.APPLICATION_JSON)
    public SnapshotAoVivo aovivo() {
        return aoVivoService.snapshotDemo();
    }
}
