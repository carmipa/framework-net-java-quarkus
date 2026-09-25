package org.framework.net.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
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
            "/informacoes",
            "/calculadora",
            // Tentativa em serie contra a chave de contingencia da Telemetria.
            "/login/chave",
            // Regrava o arquivo de historico a cada chamada.
            "/history/catalog"
    );

    /**
     * Prefixos cujo custo e alto: Projetar/dominio IPv6 (CPU e DNS), Localizacao (DNS e APIs
     * externas com cota global: ip-api 45/min, Nominatim 1 req/s).
     */
    private static final java.util.List<String> HEAVY_PREFIXES = java.util.List.of(
            "/ipv6/api/",
            "/localizacao/api/"
    );

    @Context
    ResourceInfo resourceInfo;

    @Inject
    RequestRateLimiter rateLimiter;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = normalizePath(requestContext.getUriInfo().getPath());
        // Os cálculos da Calculadora e da Resolução podem gerar centenas de linhas por
        // requisição, então todo o subcaminho conta como pesado — não só a página.
        // Os POST /api dos simuladores (Diagnóstico, Segurança) também: cada requisição
        // grava telemetria (custo de I/O), e é entrada de usuário ecoada — limite estrito.
        boolean postApi = "POST".equals(requestContext.getMethod())
                && (path.startsWith("/diagnostico/api/") || path.startsWith("/seguranca/api/"));
        boolean heavy = HEAVY_PATHS.contains(path)
                || ("POST".equals(requestContext.getMethod()) && path.startsWith("/resolucao-problemas"))
                || path.startsWith("/calculadora/")
                || HEAVY_PREFIXES.stream().anyMatch(path::startsWith)
                || postApi;
        if (!rateLimiter.allow(requestContext, chaveDeRota(path), heavy)) {
            requestContext.abortWith(Response.status(429)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"erro\":\"Muitas requisições. Aguarde um minuto e tente novamente.\"}")
                    .build());
        }
    }

    /**
     * Identidade do endpoint resolvido (classe#método): /protocolos/a e /protocolos/b são a MESMA rota.
     * Sem recurso resolvido, cai no caminho normalizado (mantém o comportamento anterior).
     */
    private String chaveDeRota(String path) {
        try {
            if (resourceInfo != null && resourceInfo.getResourceClass() != null
                    && resourceInfo.getResourceMethod() != null) {
                return resourceInfo.getResourceClass().getSimpleName() + "#" + resourceInfo.getResourceMethod().getName();
            }
        } catch (RuntimeException semContexto) {
            // fallback abaixo
        }
        return path;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
    }
}
