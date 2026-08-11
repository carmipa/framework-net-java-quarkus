package org.framework.net.paginaErros.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.paginaErros.domain.CatalogoErros;
import org.framework.net.paginaErros.domain.CatalogoErros.ErroApresentado;
import org.framework.net.telemetria.TelemetriaContext;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.telemetria.TelemetriaRequestContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Monta o conteúdo da página de erro e registra o evento.
 *
 * <p><b>Propósito de negócio:</b> transformar um código HTTP seco no material que
 * a tela precisa — texto, ícone, rota tentada e, principalmente, o
 * {@code trace_id} <em>real</em> da requisição. É esse identificador que liga o
 * que o usuário viu ao evento gravado na telemetria; sem ele, "deu erro na tela"
 * é um relato que ninguém consegue investigar.</p>
 *
 * <p><b>Invariantes do domínio:</b> o {@code traceId} exibido é o mesmo que a
 * telemetria registrou para aquela requisição — inventar um identificador só para
 * preencher o campo seria pior que deixá-lo vazio, porque daria a impressão de
 * rastreabilidade que não existe. Quando não há contexto (erro fora do ciclo de
 * requisição), o campo sai como {@code indisponível}, honestamente.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> nunca lança. Falha ao obter contexto
 * de telemetria degrada para {@code indisponível}; falha ao registrar o evento é
 * absorvida pelo logger. Uma página de erro que quebra ao ser montada deixaria o
 * usuário com a tela branca que ela existe para evitar.</p>
 */
@ApplicationScoped
public class PaginaErroService {

    private static final String MODULO = "paginaErros";
    private static final String SEM_TRACE = "indisponível";

    @Inject
    TelemetriaContext telemetriaContext;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /**
     * Dados prontos para o template.
     *
     * @param codigo status HTTP da resposta
     * @param caminho rota que o usuário tentou alcançar
     * @param metodo verbo HTTP da tentativa
     */
    public DadosPaginaErro montar(int codigo, String caminho, String metodo) {
        ErroApresentado erro = CatalogoErros.porCodigo(codigo);
        String traceId = resolverTraceId();
        String rota = caminho == null || caminho.isBlank() ? "/" : caminho;

        registrar(codigo, rota, metodo, traceId);
        return new DadosPaginaErro(erro, codigo, rota, metodo == null ? "GET" : metodo, traceId);
    }

    /**
     * Recupera o identificador de rastreio da requisição corrente.
     *
     * <p><b>Comportamento em caso de falha:</b> sem contexto disponível devolve
     * {@code indisponível} em vez de gerar um identificador novo, que não
     * corresponderia a evento nenhum.</p>
     */
    private String resolverTraceId() {
        try {
            TelemetriaRequestContext ctx = telemetriaContext.contextoDoMdc();
            if (ctx == null) {
                return SEM_TRACE;
            }
            if (ctx.traceId() != null && !ctx.traceId().isBlank()) {
                return ctx.traceId();
            }
            return ctx.requestId() != null && !ctx.requestId().isBlank() ? ctx.requestId() : SEM_TRACE;
        } catch (RuntimeException ex) {
            return SEM_TRACE;
        }
    }

    private void registrar(int codigo, String rota, String metodo, String traceId) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("httpStatus", codigo);
        campos.put("rota", rota);
        campos.put("metodo", metodo == null ? "GET" : metodo);
        campos.put("traceId", traceId);
        String nivel = codigo >= 500 ? "error" : "warn";
        telemetriaLogger.logEvent(nivel, MODULO, "pagina_erro_exibida",
                codigo >= 500 ? "error" : "warn", campos);
    }

    /**
     * O que o template recebe.
     *
     * <p><b>Invariantes do domínio:</b> nenhum campo é nulo — o template renderiza
     * direto e {@code null} viraria a palavra "null" na tela do usuário.</p>
     */
    public record DadosPaginaErro(
            ErroApresentado erro,
            int codigo,
            String caminho,
            String metodo,
            String traceId) {

        /** Rota exibida no terminal de diagnóstico, com o verbo à frente. */
        public String rotaComVerbo() {
            return metodo + " " + caminho;
        }

        public String tituloDaAba() {
            return codigo + " — Framework de Redes";
        }
    }
}
