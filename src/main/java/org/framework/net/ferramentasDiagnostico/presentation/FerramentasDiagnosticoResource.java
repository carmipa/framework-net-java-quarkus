package org.framework.net.ferramentasDiagnostico.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.framework.net.ferramentasDiagnostico.application.DiagnosticoService;
import java.util.Map;

@Path("/diagnostico")
public class FerramentasDiagnosticoResource {

    @Inject
    DiagnosticoService diagnosticoService;

    @Inject
    @io.quarkus.qute.Location("ferramentasDiagnostico/index.html")
    Template index;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance paginaInicial() {
        return index.data("activeMainMenu", "diagnostico");
    }

    @POST
    @Path("/api/ping")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response executarPing(@FormParam("host") String host) {
        String resultado = diagnosticoService.executarPingSimulado(host);
        return Response.ok(Map.of("data", resultado)).build();
    }

    @POST
    @Path("/api/dns")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response executarDns(@FormParam("dominio") String dominio) {
        String resultado = diagnosticoService.executarDnsSimulado(dominio);
        return Response.ok(Map.of("data", resultado)).build();
    }
}
