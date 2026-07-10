package org.framework.net.web.presentation;

import io.quarkus.qute.RawString;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.web.support.MarkdownRenderer;
import org.framework.net.web.support.ReadmeLoader;

import java.util.List;
import java.util.Map;

@Path("/documentacao")
public class DocumentacaoResource {

    @Inject
    ReadmeLoader readmeLoader;

    @Inject
    MarkdownRenderer markdownRenderer;

    @Inject
    @io.quarkus.qute.Location("documentacao/index.html")
    Template documentacao;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        ReadmeLoader.ReadmeContent readme = readmeLoader.carregar();
        String conteudoHtml = "";
        List<Map<String, Object>> secoes = List.of();
        String erro = readme.erro();
        if (readme.conteudo() != null) {
            MarkdownRenderer.RenderResult render = markdownRenderer.render(readme.conteudo());
            conteudoHtml = render.html();
            secoes = render.secoes();
        }
        return documentacao
                .data("activeMainMenu", "documentacao")
                .data("nomeDocumento", "README.md")
                .data("conteudoHtml", new RawString(conteudoHtml))
                .data("secoes", secoes)
                .data("erro", erro);
    }
}
