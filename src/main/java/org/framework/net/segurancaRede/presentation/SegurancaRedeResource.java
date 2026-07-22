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
import org.framework.net.segurancaRede.application.AclSimulatorService;

@Path("/seguranca")
public class SegurancaRedeResource {

    @Inject
    AclSimulatorService aclSimulatorService;

    @Inject
    @io.quarkus.qute.Location("segurancaRede/index.html")
    Template index;

    @Inject
    @io.quarkus.qute.Location("segurancaRede/partials/resultado.html")
    Template resultadoFragmento;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance paginaInicial() {
        return index.data("activeMainMenu", "seguranca");
    }

    /**
     * Avalia o pacote forjado contra a regra ACL e devolve o fragmento de
     * resultado já renderizado, que o htmx troca no painel da direita.
     */
    @POST
    @Path("/api/testar")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance testarAcl(
            @FormParam("regra") String regra,
            @FormParam("ipOrigem") String ipOrigem,
            @FormParam("ipDestino") String ipDestino,
            @FormParam("portaDestino") String portaDestino) {

        String resultado = aclSimulatorService.testarPacote(regra, ipOrigem, ipDestino, portaDestino);
        return resultadoFragmento
                .data("veredito", vereditoDe(resultado))
                .data("explicacao", resultado)
                .data("origem", ipOrigem)
                .data("destino", ipDestino)
                .data("porta", portaDestino);
    }

    private static String vereditoDe(String resultado) {
        if (resultado.contains("PERMITIDO")) {
            return "PERMITIDO";
        }
        if (resultado.contains("BLOQUEADO")) {
            return "BLOQUEADO";
        }
        return "NOMATCH";
    }
}
