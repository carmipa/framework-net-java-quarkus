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
import org.framework.net.ferramentasDiagnostico.application.DiagnosticoService;

@Path("/diagnostico")
public class FerramentasDiagnosticoResource {

    @Inject
    DiagnosticoService diagnosticoService;

    @Inject
    @io.quarkus.qute.Location("ferramentasDiagnostico/index.html")
    Template index;

    @Inject
    @io.quarkus.qute.Location("ferramentasDiagnostico/partials/terminal.html")
    Template terminalFragmento;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance paginaInicial() {
        return index.data("activeMainMenu", "diagnostico");
    }

    /** Devolve a saída do ping simulado como fragmento de terminal, trocado pelo htmx. */
    @POST
    @Path("/api/ping")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance executarPing(@FormParam("host") String host) {
        return terminalFragmento.data("saida", diagnosticoService.executarPingSimulado(host));
    }

    /** Devolve a saída do dig simulado como fragmento de terminal, trocado pelo htmx. */
    @POST
    @Path("/api/dns")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance executarDns(@FormParam("dominio") String dominio) {
        return terminalFragmento.data("saida", diagnosticoService.executarDnsSimulado(dominio));
    }
}
