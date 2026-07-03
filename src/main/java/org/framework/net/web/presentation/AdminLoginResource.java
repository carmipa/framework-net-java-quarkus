package org.framework.net.web.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.framework.net.security.AdminApiKeyService;

import java.net.URI;

@Path("/admin")
public class AdminLoginResource {

    private static final int COOKIE_MAX_AGE = 28_800; // 8 horas

    @Inject
    AdminApiKeyService adminApiKeyService;

    @Inject
    @io.quarkus.qute.Location("admin/login.html")
    Template loginTemplate;

    @GET
    @Path("/login")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance loginPage(
            @QueryParam("redirect") String redirect,
            @QueryParam("erro") String erro) {
        return loginTemplate
                .data("redirect", safeRedirect(redirect))
                .data("erro", erro == null ? "" : erro)
                .data("enforcementActive", adminApiKeyService.isEnforcementActive());
    }

    @POST
    @Path("/login")
    @Produces(MediaType.TEXT_HTML)
    public Response authenticate(
            @FormParam("api_key") String apiKey,
            @FormParam("redirect") String redirect) {
        if (!adminApiKeyService.isEnforcementActive()) {
            return Response.seeOther(URI.create(safeRedirect(redirect))).build();
        }
        if (!adminApiKeyService.isValid(apiKey)) {
            URI back = URI.create("/admin/login?erro=chave-invalida&redirect=" + urlEncode(safeRedirect(redirect)));
            return Response.seeOther(back).build();
        }
        NewCookie cookie = new NewCookie.Builder(AdminApiKeyService.COOKIE_NAME)
                .value(apiKey.strip())
                .path("/")
                .maxAge(COOKIE_MAX_AGE)
                .httpOnly(true)
                .secure(false)
                .sameSite(NewCookie.SameSite.STRICT)
                .build();
        return Response.seeOther(URI.create(safeRedirect(redirect)))
                .cookie(cookie)
                .build();
    }

    @GET
    @Path("/logout")
    public Response logout(@QueryParam("redirect") String redirect) {
        NewCookie cookie = new NewCookie.Builder(AdminApiKeyService.COOKIE_NAME)
                .value("")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(false)
                .sameSite(NewCookie.SameSite.STRICT)
                .build();
        return Response.seeOther(URI.create(safeRedirect(redirect)))
                .cookie(cookie)
                .build();
    }

    private static String safeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank() || !redirect.startsWith("/") || redirect.startsWith("//")) {
            return "/telemetria";
        }
        return redirect.strip();
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
