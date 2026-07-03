package org.framework.net.telemetria;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.MDC;

import java.util.UUID;

@ApplicationScoped
public class TelemetriaContext {

    public static final String REQUEST_CONTEXT_PROPERTY = "framework.telemetria.requestContext";

    public TelemetriaRequestContext iniciarRequisicao(String requestId, String httpMethod, String httpPath) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String resolvedRequestId = requestId == null || requestId.isBlank()
                ? traceId.substring(0, 8)
                : requestId.strip();
        TelemetriaRequestContext ctx = new TelemetriaRequestContext(
                resolvedRequestId,
                traceId,
                httpMethod,
                httpPath,
                System.currentTimeMillis()
        );
        aplicarMdc(ctx, null, null);
        return ctx;
    }

    public void registrarResposta(TelemetriaRequestContext ctx, int httpStatus) {
        if (ctx == null) {
            return;
        }
        aplicarMdc(ctx, httpStatus, ctx.elapsedMillis());
    }

    public void limpar() {
        MDC.remove(TelemetriaKeys.REQUEST_ID);
        MDC.remove(TelemetriaKeys.TRACE_ID);
        MDC.remove(TelemetriaKeys.HTTP_METHOD);
        MDC.remove(TelemetriaKeys.HTTP_PATH);
        MDC.remove(TelemetriaKeys.HTTP_STATUS);
        MDC.remove(TelemetriaKeys.DURATION_MS);
        MDC.remove(TelemetriaKeys.MODULO);
        MDC.remove(TelemetriaKeys.EVENTO);
        MDC.remove(TelemetriaKeys.STATUS);
    }

    public void aplicarEvento(String modulo, String evento, String status) {
        MDC.put(TelemetriaKeys.MODULO, modulo);
        MDC.put(TelemetriaKeys.EVENTO, evento);
        MDC.put(TelemetriaKeys.STATUS, status);
    }

    private void aplicarMdc(TelemetriaRequestContext ctx, Integer httpStatus, Long durationMs) {
        MDC.put(TelemetriaKeys.REQUEST_ID, ctx.requestId());
        MDC.put(TelemetriaKeys.TRACE_ID, ctx.traceId());
        MDC.put(TelemetriaKeys.HTTP_METHOD, ctx.httpMethod());
        MDC.put(TelemetriaKeys.HTTP_PATH, ctx.httpPath());
        if (httpStatus != null) {
            MDC.put(TelemetriaKeys.HTTP_STATUS, String.valueOf(httpStatus));
        }
        if (durationMs != null) {
            MDC.put(TelemetriaKeys.DURATION_MS, String.valueOf(durationMs));
        }
    }
}
