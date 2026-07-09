package org.framework.net.analiseTrafego.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo;
import org.framework.net.analiseTrafego.aovivo.TrafegoAoVivoService;
import org.framework.net.analiseTrafego.application.TrafegoDecoderService;
import org.framework.net.analiseTrafego.domain.model.ResultadoDecodificacao;
import org.framework.net.security.AdminApiKeyService;
import org.framework.net.telemetria.TelemetriaLogger;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Módulo Tráfego: decodificador didático de pacotes (offline) + dashboard de tráfego ao vivo.
 */
@Path("/trafego")
public class TrafegoResource {

    @Inject
    TrafegoDecoderService decoderService;

    @Inject
    TrafegoAoVivoService aoVivoService;

    @Inject
    AdminApiKeyService adminApiKeyService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @ConfigProperty(name = "framework.trafego.ingest-token")
    Optional<String> ingestToken;

    @Inject
    @io.quarkus.qute.Location("trafego/index.html")
    Template index;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        return index.data("activeMainMenu", "trafego");
    }

    @POST
    @Path("/api/decodificar")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public ResultadoDecodificacao decodificar(
            @FormParam("hex") String hex,
            @FormParam("camada") @DefaultValue("auto") String camada) {
        ResultadoDecodificacao resultado = decoderService.decodificar(hex, camada);
        telemetriaLogger.logEvent(resultado.ok() ? "info" : "warn", "analiseTrafego", "decode_packet",
                resultado.ok() ? "ok" : "error",
                Map.of("bytes", resultado.totalBytes(), "camadas", resultado.camadas().size()));
        return resultado;
    }

    /** Snapshot do tráfego ao vivo. {@code modo=demo} (simulação) ou {@code modo=agente} (dados reais). */
    @GET
    @Path("/api/aovivo")
    @Produces(MediaType.APPLICATION_JSON)
    public SnapshotAoVivo aovivo(@QueryParam("modo") @DefaultValue("demo") String modo,
                                 @Context HttpHeaders headers) {
        if ("agente".equalsIgnoreCase(modo)) {
            // Dados reais da máquina do usuário: exigem login admin quando a chave está ativa (VPS).
            if (adminApiKeyService.isEnforcementActive()) {
                String submitted = firstNonBlank(
                        headers.getHeaderString(AdminApiKeyService.HEADER_NAME),
                        adminApiKeyService.extractFromCookie(headers.getHeaderString("Cookie")));
                if (!adminApiKeyService.isValid(submitted)) {
                    throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                            .entity(Map.of("erro", "Dados do agente exigem autenticação admin. Faça login em /admin/login."))
                            .build());
                }
            }
            return aoVivoService.snapshotAgente();
        }
        return aoVivoService.snapshotDemo();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.strip();
        }
        return b == null ? "" : b.strip();
    }

    /** Ingestão de dados reais do agente local. Requer o header {@code X-Trafego-Token}. */
    @POST
    @Path("/api/ingest")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response ingest(@HeaderParam("X-Trafego-Token") String token, Map<String, Object> payload) {
        String esperado = ingestToken.map(String::strip).orElse("");
        if (esperado.isBlank()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("ok", false,
                            "erro", "Ingestão desabilitada: defina framework.trafego.ingest-token."))
                    .build();
        }
        if (!constantTimeEquals(esperado, token)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("ok", false, "erro", "Token de ingestão inválido.")).build();
        }
        aoVivoService.ingerir(payload);
        return Response.ok(Map.of("ok", true)).build();
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
