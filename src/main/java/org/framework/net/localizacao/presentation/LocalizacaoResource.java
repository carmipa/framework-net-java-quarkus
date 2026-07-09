package org.framework.net.localizacao.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.framework.net.localizacao.application.LocalizacaoService;

/**
 * Módulo Localização: localiza por IP (aproximado, cidade/ISP) ou por CEP (endereço + mapa).
 */
@Path("/localizacao")
public class LocalizacaoResource {

    @Inject
    LocalizacaoService localizacaoService;

    @Inject
    @io.quarkus.qute.Location("localizacao/index.html")
    Template index;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        return index.data("activeMainMenu", "localizacao");
    }

    @GET
    @Path("/api/ip")
    @Produces(MediaType.APPLICATION_JSON)
    public Response porIp(
            @QueryParam("ip") String ip,
            @Context HttpHeaders headers,
            @Context HttpServerRequest request) {
        String alvo = ip == null ? "" : ip.strip();
        if (alvo.isBlank()) {
            alvo = clienteIp(headers, request);
        }
        return Response.ok(localizacaoService.localizarPorIp(alvo)).build();
    }

    @GET
    @Path("/api/cep")
    @Produces(MediaType.APPLICATION_JSON)
    public Response porCep(@QueryParam("cep") String cep) {
        return Response.ok(localizacaoService.localizarPorCep(cep)).build();
    }

    /** Localização exata via GPS do navegador (coordenadas informadas com consentimento do usuário). */
    @GET
    @Path("/api/gps")
    @Produces(MediaType.APPLICATION_JSON)
    public Response porGps(@QueryParam("lat") Double lat, @QueryParam("lon") Double lon) {
        if (lat == null || lon == null || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return Response.ok(java.util.Map.of("ok", false,
                    "mensagem", "Coordenadas inválidas.")).build();
        }
        return Response.ok(localizacaoService.localizarPorCoordenadas(lat, lon)).build();
    }

    private static String clienteIp(HttpHeaders headers, HttpServerRequest request) {
        String xff = headers.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].strip();
        }
        String realIp = headers.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.strip();
        }
        if (request != null && request.remoteAddress() != null) {
            return request.remoteAddress().host();
        }
        return "";
    }
}
