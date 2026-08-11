package org.framework.net.paginaErros.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo dos estados de erro apresentáveis ao usuário.
 *
 * <p><b>Propósito de negócio:</b> quando alguém erra a URL, perde a sessão ou
 * esbarra num limite, a resposta padrão do servidor é uma tela branca com uma
 * frase em inglês — o pior momento possível para o sistema parecer quebrado. Este
 * catálogo dá a cada código HTTP um texto em português que explica <em>o que
 * aconteceu</em> e <em>o que fazer</em>, no vocabulário de redes do próprio
 * framework.</p>
 *
 * <p><b>Invariantes do domínio:</b> o catálogo é a fonte única dos textos — a
 * página não os escreve à mão, e o CSS deriva a cor de acento da classe
 * {@code err-<código>}. Código desconhecido nunca produz página em branco: cai no
 * fallback da própria família (4xx → 400, 5xx → 500), porque erro sem texto é
 * exatamente o problema que esta tela existe para resolver.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> {@link #porCodigo(int)} sempre devolve
 * um {@link ErroApresentado}; não há caminho que retorne nulo.</p>
 */
public final class CatalogoErros {

    private CatalogoErros() {
    }

    /**
     * Um estado de erro pronto para a tela.
     *
     * <p><b>Invariantes do domínio:</b> {@code hint} descreve a <em>classe</em> do
     * problema, nunca o detalhe interno da exceção. Nome de classe Java, trecho de
     * stack e mensagem de driver não vão para a tela do usuário: quem precisa
     * diagnosticar usa o {@code traceId}, que liga a página ao evento na
     * telemetria.</p>
     */
    public record ErroApresentado(
            int codigo,
            String badge,
            String icone,
            String artTag,
            String titulo,
            String descricao,
            String statusTexto,
            String hint) {

        /** Classe CSS que define a cor de acento, a aura e a cor da chuva Matrix. */
        public String classeCss() {
            return "err-" + codigo;
        }

        public boolean cliente() {
            return codigo >= 400 && codigo < 500;
        }
    }

    private static final Map<Integer, ErroApresentado> CATALOGO = criar();

    private static Map<Integer, ErroApresentado> criar() {
        Map<Integer, ErroApresentado> mapa = new LinkedHashMap<>();

        // ---------- 4xx · cliente ----------
        mapa.put(400, new ErroApresentado(400, "REQUISIÇÃO MALFORMADA", "data_object",
                "PAYLOAD CORROMPIDO",
                "A requisição não pôde ser interpretada.",
                "A sintaxe ou o corpo enviado está inválido. Revise os campos, os parâmetros e os "
                        + "tipos de dados antes de reenviar.",
                "400 Bad Request",
                "Corpo da requisição fora do formato esperado pelo endpoint."));

        mapa.put(401, new ErroApresentado(401, "AUTENTICAÇÃO EXIGIDA", "lock",
                "SESSÃO EXPIRADA",
                "Faça login para continuar.",
                "A Telemetria e as rotas de exportação exigem sessão administrativa ativa. "
                        + "Autentique-se para prosseguir.",
                "401 Unauthorized",
                "Sessão inexistente ou expirada."));

        mapa.put(403, new ErroApresentado(403, "ACESSO BLOQUEADO", "gpp_maybe",
                "FIREWALL ATIVO",
                "Você não tem permissão para este recurso.",
                "Esta rota é protegida por chave administrativa. Autentique-se em Administração "
                        + "para liberar as exportações e a Telemetria.",
                "403 Forbidden",
                "Credencial administrativa ausente ou inválida."));

        mapa.put(404, new ErroApresentado(404, "ROTA NÃO MAPEADA", "travel_explore",
                "PACOTE SEM DESTINO",
                "Esta rota não existe na topologia.",
                "O endereço solicitado não corresponde a nenhum módulo do Framework. Confira a URL "
                        + "ou use um dos atalhos abaixo para retomar a navegação.",
                "404 Not Found",
                "Rota não registrada em nenhum Resource JAX-RS."));

        mapa.put(405, new ErroApresentado(405, "MÉTODO NÃO PERMITIDO", "block",
                "VERBO INCOMPATÍVEL",
                "Este verbo HTTP não é aceito nesta rota.",
                "O endereço existe, mas não responde ao método utilizado. Confira na Documentação "
                        + "qual verbo a rota espera.",
                "405 Method Not Allowed",
                "A rota existe, mas foi registrada para outro método HTTP."));

        mapa.put(409, new ErroApresentado(409, "CONFLITO DE ESTADO", "merge_type",
                "COLISÃO DE ENDEREÇO",
                "Este recurso conflita com um registro existente.",
                "A operação não pôde ser concluída porque geraria duplicidade ou sobreposição. "
                        + "Ajuste os dados e tente novamente.",
                "409 Conflict",
                "O recurso enviado colide com um estado já registrado."));

        mapa.put(422, new ErroApresentado(422, "VALIDAÇÃO FALHOU", "rule",
                "DADOS INCONSISTENTES",
                "Os dados enviados não passaram na validação.",
                "A sintaxe está correta, mas o conteúdo é semanticamente inválido para o cálculo "
                        + "solicitado. Revise os campos destacados.",
                "422 Unprocessable Entity",
                "Valor fora do domínio aceito pela regra de negócio."));

        mapa.put(429, new ErroApresentado(429, "LIMITE EXCEDIDO", "speed",
                "RATE LIMIT ATINGIDO",
                "Muitas requisições em pouco tempo.",
                "O limite de chamadas foi atingido para proteger a plataforma. Aguarde um minuto "
                        + "antes de tentar novamente.",
                "429 Too Many Requests",
                "Limite por minuto excedido para esta origem."));

        // ---------- 5xx · servidor ----------
        mapa.put(500, new ErroApresentado(500, "FALHA NO SERVIDOR", "error",
                "PIPELINE INTERROMPIDO",
                "Algo quebrou no processamento.",
                "Um erro inesperado ocorreu ao processar sua requisição. O evento foi registrado "
                        + "na telemetria com o trace_id abaixo, que é o que permite rastreá-lo.",
                "500 Internal Server Error",
                "Exceção não tratada — use o trace_id para localizar o evento na telemetria."));

        mapa.put(502, new ErroApresentado(502, "GATEWAY INVÁLIDO", "router",
                "ROTA SEM PRÓXIMO SALTO",
                "O gateway não conseguiu falar com a aplicação.",
                "A resposta recebida do serviço de origem é inválida ou o backend está "
                        + "inacessível.",
                "502 Bad Gateway",
                "Serviço de origem recusou a conexão."));

        mapa.put(503, new ErroApresentado(503, "SERVIÇO INDISPONÍVEL", "cloud_off",
                "LINK WAN CAÍDO",
                "O serviço está temporariamente fora do ar.",
                "Manutenção ou sobrecarga momentânea. Aguarde alguns instantes e tente novamente.",
                "503 Service Unavailable",
                "Dependência externa sem resposta."));

        mapa.put(504, new ErroApresentado(504, "TEMPO ESGOTADO", "hourglass_disabled",
                "TIMEOUT NA RESPOSTA",
                "A aplicação demorou demais para responder.",
                "O tempo limite foi atingido antes da conclusão do processamento. Tente novamente "
                        + "ou reduza o escopo da operação.",
                "504 Gateway Timeout",
                "Tempo de espera do upstream excedido."));

        return Map.copyOf(mapa);
    }

    /** Os códigos cobertos, em ordem — usados pela guarda de cobertura nos testes. */
    public static List<Integer> codigos() {
        return List.of(400, 401, 403, 404, 405, 409, 422, 429, 500, 502, 503, 504);
    }

    /**
     * Estado de erro correspondente ao código HTTP.
     *
     * <p><b>Comportamento em caso de falha:</b> código fora do catálogo cai no
     * representante da família — 4xx vira 400, qualquer outra coisa vira 500.
     * Nunca devolve nulo: página de erro em branco por causa de um código não
     * previsto seria o próprio defeito que esta tela combate.</p>
     */
    public static ErroApresentado porCodigo(int codigo) {
        ErroApresentado exato = CATALOGO.get(codigo);
        if (exato != null) {
            return exato;
        }
        return codigo >= 400 && codigo < 500 ? CATALOGO.get(400) : CATALOGO.get(500);
    }
}
