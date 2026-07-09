package org.framework.net.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Set;

@Provider
@Priority(Priorities.AUTHENTICATION + 20)
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Set<String> HEAVY_PATHS = Set.of(
            "/analise",
            "/resolucao-problemas",
            "/api/informacoes/geo",
            "/informacoes"
    );

    @Inject
    RequestRateLimiter rateLimiter;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = normalizePath(requestContext.getUriInfo().getPath());
        boolean heavy = HEAVY_PATHS.contains(path)
                || ("POST".equals(requestContext.getMethod()) && path.startsWith("/resolucao-problemas"));
        if (!rateLimiter.allow(requestContext, heavy)) {
            requestContext.abortWith(Response.status(429)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"erro\":\"Muitas requisições. Aguarde um minuto e tente novamente.\"}")
                    .build());
        }
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
    }
}
