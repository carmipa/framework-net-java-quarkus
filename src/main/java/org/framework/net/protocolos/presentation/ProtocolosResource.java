package org.framework.net.protocolos.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.protocolos.application.ProtocolosService;

@Path("/protocolos")
public class ProtocolosResource {

    @Inject
    ProtocolosService protocolosService;

    @Inject
    @io.quarkus.qute.Location("protocolos/index.html")
    Template index;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listar() {
        return index
                .data("activeMainMenu", "protocolos")
                .data("protocolosCatalogo", protocolosService.montarProtocolosCatalogoExibicao())
                .data("protocolosTroubleshooting", protocolosService.montarTroubleshootingRoteamento());
    }
}
