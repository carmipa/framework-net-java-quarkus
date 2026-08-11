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

    /**
     * Caminhos do fluxo de login: precisam ficar abertos, senão o usuário não
     * consegue nem chegar à tela que o autenticaria.
     */
    private static final Set<String> CAMINHOS_DE_LOGIN = Set.of(
            "/login",
            "/login/github",
            "/login/github/callback",
            "/login/chave",
            "/login/sair");

    /**
     * Acoes que alteram estado ou extraem dado: so o dono.
     *
     * <p><b>Invariantes do dominio:</b> lista de PERMISSAO por acao, nao por
     * prefixo. Rota nova de escrita entra aqui explicitamente; esquecer de
     * inclui-la deixa a acao aberta ao leitor, e por isso existe teste cobrando
     * cada uma.</p>
     */
    private static final Set<String> ACOES_DE_DONO = Set.of(
            "/telemetria/api/exportar",
            "/telemetria/api/console/limpar",
            "/telemetria/api/pasta",
            "/telemetria/api/dataset/sincronizar");

    @Inject
    AdminApiKeyService adminApiKeyService;

    @Inject
    SensitiveApisService sensitiveApisService;

    @Inject
    SessaoTelemetriaService sessaoTelemetriaService;

    @ConfigProperty(name = "framework.telemetry.dashboard-enabled", defaultValue = "true")
    boolean telemetryDashboardEnabled;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = normalizePath(requestContext.getUriInfo().getPath());
        if (PUBLIC_ADMIN_PATHS.contains(path) || CAMINHOS_DE_LOGIN.contains(path)) {
            return;
        }
        if (isTelemetryPathDisabled(path)) {
            abortUnavailable(requestContext);
            return;
        }
        if (path.startsWith("/telemetria")) {
            exigirSessaoDeTelemetria(requestContext, path);
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

    /**
     * Exige sessão para abrir a Telemetria.
     *
     * <p><b>Propósito de negócio:</b> o painel expõe eventos, rotas e endereços de
     * quem usou o site. Até esta mudança ele ficava <b>aberto</b> em produção: a
     * regra anterior só pedia chave quando o dashboard estava <em>desligado</em>,
     * de modo que ligar o recurso desligava a proteção.</p>
     *
     * <p><b>Invariantes do domínio:</b> a sessão nasce do login pelo GitHub ou da
     * contingência; o header {@code X-Admin-Api-Key} continua valendo para
     * automação, que não tem navegador para completar OAuth. Navegador sem sessão
     * vai para a tela de login; cliente de máquina recebe 401 em JSON.</p>
     */
    private void exigirSessaoDeTelemetria(ContainerRequestContext requestContext, String path) {
        String cookies = requestContext.getHeaderString("Cookie");
        if (sessaoTelemetriaService.temSessaoValida(cookies)) {
            if (!ACOES_DE_DONO.contains(path) || sessaoTelemetriaService.ehDono(cookies)) {
                return;
            }
            // Leitor autenticado ve o painel, mas nao mexe: 403 vira a pagina
            // "ACESSO BLOQUEADO" quando o cliente e navegador.
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"erro\":\"Esta acao e restrita ao dono da Telemetria.\"}")
                    .build());
            return;
        }
        // Exigir o header PRESENTE: em ambiente com enforcement desligado, isValid()
        // aprova qualquer coisa, e uma checagem ingenua abriria a Telemetria inteira.
        String chave = requestContext.getHeaderString(AdminApiKeyService.HEADER_NAME);
        if (chave != null && !chave.isBlank() && adminApiKeyService.isValid(chave)) {
            return;
        }
        if (prefersHtml(requestContext)) {
            requestContext.abortWith(Response.seeOther(URI.create("/login")).build());
            return;
        }
        requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"erro\":\"Telemetria exige autenticação. Abra /login ou envie o header "
                        + AdminApiKeyService.HEADER_NAME + ".\"}")
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
