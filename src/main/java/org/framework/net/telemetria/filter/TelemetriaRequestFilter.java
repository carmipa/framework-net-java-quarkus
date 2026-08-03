package org.framework.net.telemetria.filter;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.framework.net.telemetria.TelemetriaContext;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.telemetria.TelemetriaRequestContext;

import java.io.IOException;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class TelemetriaRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String HEALTH_PATH = "/health";

    @Inject
    TelemetriaContext telemetriaContext;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /**
     * Propósito de negócio: inicia a correlação de uma requisição funcional atendida pela aplicação.
     * Invariantes do domínio: o healthcheck operacional nunca entra na telemetria funcional nem provoca I/O.
     * Comportamento em caso de falha: propaga a falha de filtro ao runtime JAX-RS e não cria contexto parcial
     * para o endpoint de saúde.
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String requestId = requestContext.getHeaderString("X-Request-Id");
        String method = requestContext.getMethod();
        String path = requestContext.getUriInfo().getRequestUri().getPath();
        if (HEALTH_PATH.equals(path)) {
            return;
        }
        TelemetriaRequestContext ctx = telemetriaContext.iniciarRequisicao(requestId, method, path);
        requestContext.setProperty(TelemetriaContext.REQUEST_CONTEXT_PROPERTY, ctx);
        requestContext.setProperty("requestId", ctx.requestId());
    }

    /**
     * Propósito de negócio: conclui a correlação e registra o resultado HTTP de uma requisição funcional.
     * Invariantes do domínio: somente requisições com contexto iniciado são registradas, excluindo o healthcheck.
     * Comportamento em caso de falha: sempre limpa o contexto da thread para impedir vazamento entre requisições.
     */
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        try {
            Object property = requestContext.getProperty(TelemetriaContext.REQUEST_CONTEXT_PROPERTY);
            if (property instanceof TelemetriaRequestContext ctx) {
                int status = responseContext.getStatus();
                telemetriaContext.registrarResposta(ctx, status);
                telemetriaLogger.logHttpAccess(ctx, status);
                Object requestId = requestContext.getProperty("requestId");
                if (requestId != null) {
                    responseContext.getHeaders().putSingle("X-Request-Id", requestId.toString());
                    responseContext.getHeaders().putSingle("X-Trace-Id", ctx.traceId());
                }
            }
        } finally {
            telemetriaContext.limpar();
        }
    }
}
