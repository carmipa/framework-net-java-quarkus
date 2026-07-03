package org.framework.net.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

@Provider
@Priority(Priorities.AUTHENTICATION + 5)
public class AdminApiKeyFilter implements ContainerRequestFilter {

    private static final Set<String> PUBLIC_ADMIN_PATHS = Set.of("/admin/login", "/admin/logout");

    @Inject
    AdminApiKeyService adminApiKeyService;

    @Inject
    SensitiveApisService sensitiveApisService;

    @ConfigProperty(name = "framework.telemetry.dashboard-enabled", defaultValue = "true")
    boolean telemetryDashboardEnabled;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = normalizePath(requestContext.getUriInfo().getPath());
        if (PUBLIC_ADMIN_PATHS.contains(path)) {
            return;
        }
        if (isTelemetryPathDisabled(path)) {
            abortUnavailable(requestContext);
            return;
        }
        if (!adminApiKeyService.isProtectedPath(path)) {
            return;
        }
        if (!sensitiveApisService.isEnabled()) {
            abortUnavailable(requestContext);
            return;
        }
        if (!adminApiKeyService.isEnforcementActive()) {
            return;
        }

        String submitted = firstNonBlank(
                requestContext.getHeaderString(AdminApiKeyService.HEADER_NAME),
                adminApiKeyService.extractFromCookie(requestContext.getHeaderString("Cookie"))
        );
        if (adminApiKeyService.isValid(submitted)) {
            return;
        }

        if (prefersHtml(requestContext)) {
            URI login = UriBuilder.fromPath("/admin/login")
                    .queryParam("redirect", safeRedirect(path))
                    .build();
            requestContext.abortWith(Response.seeOther(login).build());
            return;
        }

        requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"erro\":\"API key administrativa ausente ou inválida. Use o header "
                        + AdminApiKeyService.HEADER_NAME + " ou faça login em /admin/login.\"}")
                .build());
    }

    private static void abortUnavailable(ContainerRequestContext requestContext) {
        requestContext.abortWith(Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"erro\":\"Recurso indisponível neste ambiente.\"}")
                .build());
    }

    private boolean isTelemetryPathDisabled(String path) {
        return path.startsWith("/telemetria") && !telemetryDashboardEnabled;
    }

    private static boolean prefersHtml(ContainerRequestContext ctx) {
        if ("GET".equalsIgnoreCase(ctx.getMethod()) || "HEAD".equalsIgnoreCase(ctx.getMethod())) {
            String accept = ctx.getHeaderString("Accept");
            return accept == null || accept.contains("text/html") || accept.contains("*/*");
        }
        return false;
    }

    private static String safeRedirect(String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//")) {
            return "/export/json";
        }
        return path;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.strip();
        }
        if (b != null && !b.isBlank()) {
            return b.strip();
        }
        return "";
    }
}
