package org.framework.net.telemetria.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.framework.net.telemetria.TelemetriaDashboard;
import org.framework.net.telemetria.TelemetriaDashboardService;
import org.framework.net.telemetria.TelemetriaResumo;
import org.framework.net.telemetria.TelemetriaStore;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

@Path("/telemetria")
public class TelemetriaResource {

    @Inject
    TelemetriaStore store;

    @Inject
    TelemetriaDashboardService dashboardService;

    @Inject
    @io.quarkus.qute.Location("telemetria/dashboard.html")
    Template dashboardTemplate;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        return dashboardTemplate.data("activeMainMenu", "telemetria");
    }

    @GET
    @Path("/api/resumo")
    @Produces(MediaType.APPLICATION_JSON)
    public TelemetriaResumo resumo(@QueryParam("limit") @DefaultValue("100") int limit) {
        return store.gerarResumo(limit);
    }

    @GET
    @Path("/api/dashboard")
    @Produces(MediaType.APPLICATION_JSON)
    public TelemetriaDashboard dadosDashboard(
            @QueryParam("limit") @DefaultValue("200") int limit,
            @QueryParam("console") @DefaultValue("200") int console) {
        return dashboardService.montar(limit, console);
    }

    @GET
    @Path("/api/console")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> console(@QueryParam("limit") @DefaultValue("200") int limit) {
        return Map.of("linhas", dashboardService.montarConsole(limit));
    }

    @POST
    @Path("/api/console/limpar")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> limparConsole() {
        dashboardService.limparConsole();
        return Map.of("status", "ok");
    }

    @GET
    @Path("/api/exportar")
    public Response exportar() throws IOException {
        var arquivo = store.arquivoCompartilhado();
        if (!Files.exists(arquivo)) {
            store.flush();
        }
        byte[] conteudo = Files.readAllBytes(arquivo);
        return Response.ok(conteudo)
                .type(MediaType.APPLICATION_JSON)
                .header("Content-Disposition", "attachment; filename=\"telemetria_compartilhada.json\"")
                .build();
    }

    @GET
    @Path("/api/pasta")
    @Produces(MediaType.APPLICATION_JSON)
    public Response pasta() {
        return Response.ok(Map.of(
                "pasta", store.pastaLogs().toAbsolutePath().toString(),
                "arquivoCompartilhado", store.arquivoCompartilhado().toAbsolutePath().toString()
        )).build();
    }
}
