package org.framework.net.web.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Página de Documentação — construída no design system dark (diagramas em HTML/CSS,
 * sem Mermaid/caixas brancas). Conteúdo estático renderizado por Qute.
 */
@Path("/documentacao")
public class DocumentacaoResource {

    @Inject
    @io.quarkus.qute.Location("documentacao/index.html")
    Template documentacao;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        return documentacao.data("activeMainMenu", "documentacao");
    }
}
