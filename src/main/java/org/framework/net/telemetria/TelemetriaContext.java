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

    /**
     * Reconstrói a correlação da requisição em curso a partir do MDC.
     *
     * <p><b>Propósito de negócio:</b> serviços de aplicação emitem eventos sem
     * ter acesso ao {@code ContainerRequestContext} do JAX-RS. Sem este resgate,
     * todo evento de negócio sai sem {@code traceId} e o dataset publicado não
     * permite ligar um erro à requisição que o causou — era o caso de 29,1% dos
     * registros coletados até 2026-08-03.</p>
     *
     * <p><b>Invariantes do domínio:</b> devolve apenas os campos de correlação.
     * O {@code startedAtMillis} é o instante da reconstrução e <b>não</b> serve
     * para medir duração — quem precisa de duração real usa o contexto original,
     * criado em {@link #iniciarRequisicao}. O MDC é por thread, então só há
     * resgate quando o evento nasce na própria thread da requisição.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> devolve {@code null} quando não
     * há requisição em curso (inicialização da aplicação, tarefas de fundo,
     * outra thread). Nunca lança.</p>
     */
    public TelemetriaRequestContext contextoDoMdc() {
        String traceId = valorMdc(TelemetriaKeys.TRACE_ID);
        if (traceId == null) {
            return null;
        }
        return new TelemetriaRequestContext(
                valorMdc(TelemetriaKeys.REQUEST_ID),
                traceId,
                valorMdc(TelemetriaKeys.HTTP_METHOD),
                valorMdc(TelemetriaKeys.HTTP_PATH),
                System.currentTimeMillis());
    }

    private static String valorMdc(String chave) {
        Object valor = MDC.get(chave);
        if (valor == null) {
            return null;
        }
        String texto = String.valueOf(valor).strip();
        return texto.isEmpty() ? null : texto;
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
