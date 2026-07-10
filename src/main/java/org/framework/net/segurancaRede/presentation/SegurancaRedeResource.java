package org.framework.net.segurancaRede.presentation;

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
import org.framework.net.segurancaRede.application.AclSimulatorService;
import java.util.Map;

@Path("/seguranca")
public class SegurancaRedeResource {

    @Inject
    AclSimulatorService aclSimulatorService;

    @Inject
    @io.quarkus.qute.Location("segurancaRede/index.html")
    Template index;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance paginaInicial() {
        return index.data("activeMainMenu", "seguranca");
    }

    @POST
    @Path("/api/testar")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response testarAcl(
            @FormParam("regra") String regra,
            @FormParam("ipOrigem") String ipOrigem,
            @FormParam("ipDestino") String ipDestino,
            @FormParam("portaDestino") String portaDestino) {

        String resultado = aclSimulatorService.testarPacote(regra, ipOrigem, ipDestino, portaDestino);
        return Response.ok(Map.of("data", resultado)).build();
    }
}
