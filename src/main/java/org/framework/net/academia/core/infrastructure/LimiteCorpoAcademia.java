package org.framework.net.academia.core.infrastructure;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Recusa corpo grande nas APIs da Academia antes de qualquer outra camada lê-lo.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> as APIs da Academia são abertas (a lição funciona sem login).
 * O filtro de CSRF do site lê o corpo inteiro para procurar o token de formulário; sem um teto
 * antes dele, um corpo de megabytes ocuparia memória da JVM que é a mesma do site inteiro (D12).
 * Os eventos da Academia têm poucas centenas de bytes.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> vale só para {@code /academia/api/*}; roda no roteador do
 * Vert.x, antes do RESTEasy e dos filtros JAX-RS; teto de {@link #TETO_BYTES} pelo
 * {@code Content-Length} declarado.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> {@code Content-Length} acima do teto ⇒ 413 e a conexão
 * não chega à aplicação; {@code Content-Length} ilegível ⇒ 413 também (falha fechada). Corpo
 * sem {@code Content-Length} (envio em pedaços) segue para o limite global do Quarkus — risco
 * residual declarado.</p>
 */
@ApplicationScoped
public class LimiteCorpoAcademia {

    /** Teto do corpo nas APIs da Academia. */
    public static final long TETO_BYTES = 8 * 1024;

    void registrar(@Observes Router router) {
        router.route("/academia/api/*").order(-1000).handler(contexto -> {
            String declarado = contexto.request().getHeader("Content-Length");
            if (declarado != null && excede(declarado)) {
                contexto.response().setStatusCode(413)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"resultado\":\"RECUSADO\",\"motivo\":\"corpo acima de 8 KB\"}");
                return;
            }
            contexto.next();
        });
    }

    /** {@code true} se o {@code Content-Length} declarado passa do teto ou não é um número. */
    public static boolean excede(String contentLength) {
        try {
            return Long.parseLong(contentLength.strip()) > TETO_BYTES;
        } catch (NumberFormatException ilegivel) {
            return true;
        }
    }
}
