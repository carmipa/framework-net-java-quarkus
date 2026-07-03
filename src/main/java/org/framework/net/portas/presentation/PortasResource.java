package org.framework.net.portas.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.portas.application.PortasService;

@Path("/portas")
public class PortasResource {

    @Inject
    PortasService portasService;

    @Inject
    @io.quarkus.qute.Location("portas/index.html")
    Template index;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listar() {
        return index
            .data("activeMainMenu", "portas")
            .data("portasCatalogo", portasService.montarPortasCatalogoExibicao());
    }
}
