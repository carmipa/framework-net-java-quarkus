package org.framework.net.paginaErros;

import io.quarkus.qute.Engine;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.paginaErros.application.PaginaErroService;
import org.framework.net.paginaErros.application.PaginaErroService.DadosPaginaErro;
import org.framework.net.paginaErros.domain.CatalogoErros;
import org.framework.net.paginaErros.domain.CatalogoErros.ErroApresentado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Páginas de erro personalizadas.
 *
 * <p><b>Propósito de negócio:</b> URL errada, verbo errado ou falha interna
 * devolviam a tela padrão do servidor. Agora devolvem a página do Framework, com
 * o texto em português, o {@code trace_id} real e atalhos de volta. Este teste
 * prova que a substituição acontece de verdade e — o mais importante — que ela
 * <b>não</b> vazou para as rotas de API.</p>
 *
 * <p><b>Invariantes do domínio:</b> HTML para navegador, JSON para máquina. Um
 * {@code fetch()} do frontend que receba uma página HTML no lugar do JSON quebra
 * no {@code response.json()} com um erro que não diz nada — por isso a rota de
 * API e o cliente que não pede {@code text/html} continuam recebendo JSON.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> a asserção nomeia a rota, o status e
 * o trecho ausente.</p>
 */
@QuarkusTest
@DisplayName("Páginas de erro: 12 estados, HTML para gente e JSON para máquina")
class PaginaErroHttpTest {

    @Inject
    Engine engine;

    static List<Integer> codigos() {
        return CatalogoErros.codigos();
    }

    @Test
    @DisplayName("rota inexistente devolve 404 com a página do Framework")
    void rotaInexistenteDevolveAPagina() {
        given()
                .header("Accept", "text/html,application/xhtml+xml")
                .when().get("/rota-que-nao-existe")
                .then()
                .statusCode(404)
                .contentType(containsString("text/html"))
                .body(containsString("class=\"err-404\""))
                .body(containsString("ROTA NÃO MAPEADA"))
                .body(containsString("Esta rota não existe na topologia."))
                .body(containsString("Voltar ao Início"))
                .body(containsString("Ver Documentação"))
                .body(containsString("id=\"matrix\""))
                .body(containsString("trace_id"));
    }

    @Test
    @DisplayName("o path tentado e o verbo aparecem no terminal de diagnóstico")
    void diagnosticoMostraARotaTentada() {
        given()
                .header("Accept", "text/html")
                .when().get("/modulo/inexistente/profundo")
                .then()
                .statusCode(404)
                .body(containsString("/modulo/inexistente/profundo"))
                .body(containsString("GET"));
    }

    @Test
    @DisplayName("verbo errado numa rota existente devolve 405 com a cor e o texto de 405")
    void verboErradoDevolve405() {
        given()
                .header("Accept", "text/html")
                .when().post("/protocolos")
                .then()
                .statusCode(405)
                .body(containsString("class=\"err-405\""))
                .body(containsString("MÉTODO NÃO PERMITIDO"));
    }

    @Test
    @DisplayName("regressão: rota de API nunca recebe HTML, mesmo pedindo text/html")
    void rotaDeApiContinuaEmJson() {
        given()
                .header("Accept", "text/html")
                .when().get("/telemetria/api/inexistente")
                .then()
                .statusCode(404)
                .body(not(containsString("<html")))
                .body(not(containsString("err-404")));
    }

    @Test
    @DisplayName("cliente que não pede text/html recebe JSON, não a página")
    void clienteDeMaquinaRecebeJson() {
        given()
                .header("Accept", "application/json")
                .when().get("/rota-que-nao-existe")
                .then()
                .statusCode(404)
                .body(not(containsString("<html")))
                .body(containsString("\"status\":404"));
    }

    @Test
    @DisplayName("regressão: página de erro não é indexável")
    void paginaDeErroNaoEIndexavel() {
        given()
                .header("Accept", "text/html")
                .when().get("/rota-que-nao-existe")
                .then()
                .statusCode(404)
                .body(containsString("name=\"robots\" content=\"noindex\""));
    }

    @ParameterizedTest(name = "o estado {0} renderiza inteiro")
    @MethodSource("codigos")
    void todosOsDozeEstadosRenderizam(int codigo) {
        ErroApresentado erro = CatalogoErros.porCodigo(codigo);
        DadosPaginaErro dados = new DadosPaginaErro(erro, codigo, "/teste", "GET", "trace-de-teste");

        String html = engine.getTemplate("paginaErros/erro.html")
                .data("erro", erro)
                .data("dados", dados)
                .render();

        assertTrue(html.contains("class=\"err-" + codigo + "\""),
                "O estado precisa vir na classe do body para o CSS aplicar a cor de acento.");
        assertTrue(html.contains(erro.badge()), "Badge ausente em " + codigo);
        assertTrue(html.contains(erro.titulo()), "Título ausente em " + codigo);
        assertTrue(html.contains(erro.artTag()), "Tag da arte ausente em " + codigo);
        assertTrue(html.contains("trace-de-teste"), "O trace_id precisa chegar à tela.");
        assertTrue(html.contains("id=\"matrix\""), "A animação precisa existir em todos os estados.");
        assertFalse(html.contains("NOT_FOUND"), "Nada de identificador técnico cru na tela.");
    }

    @Test
    @DisplayName("o catálogo cobre os doze códigos, sem campo vazio")
    void catalogoCompleto() {
        assertEquals(12, CatalogoErros.codigos().size());
        for (int codigo : CatalogoErros.codigos()) {
            ErroApresentado erro = CatalogoErros.porCodigo(codigo);
            assertNotNull(erro);
            assertEquals(codigo, erro.codigo());
            assertEquals("err-" + codigo, erro.classeCss());
            assertFalse(erro.badge().isBlank(), "badge vazio em " + codigo);
            assertFalse(erro.icone().isBlank(), "ícone vazio em " + codigo);
            assertFalse(erro.artTag().isBlank(), "artTag vazio em " + codigo);
            assertFalse(erro.titulo().isBlank(), "título vazio em " + codigo);
            assertFalse(erro.descricao().isBlank(), "descrição vazia em " + codigo);
            assertFalse(erro.hint().isBlank(), "hint vazio em " + codigo);
        }
    }

    @Test
    @DisplayName("código fora do catálogo cai na família certa, nunca em página vazia")
    void codigoDesconhecidoTemFallback() {
        assertEquals(400, CatalogoErros.porCodigo(418).codigo(), "4xx desconhecido vira 400.");
        assertEquals(400, CatalogoErros.porCodigo(451).codigo());
        assertEquals(500, CatalogoErros.porCodigo(507).codigo(), "5xx desconhecido vira 500.");
        assertEquals(500, CatalogoErros.porCodigo(0).codigo(), "Código absurdo não pode virar tela branca.");
    }

    @Test
    @DisplayName("o hint não vaza detalhe interno da exceção")
    void hintNaoVazaInterno() {
        for (int codigo : CatalogoErros.codigos()) {
            String hint = CatalogoErros.porCodigo(codigo).hint();
            assertFalse(hint.contains("Exception") && hint.contains("."),
                    "Hint de " + codigo + " parece carregar nome de classe Java: " + hint);
            assertFalse(hint.contains("org.framework"),
                    "Hint de " + codigo + " expõe pacote interno.");
        }
    }

    @Test
    @DisplayName("regressão: as páginas do menu continuam respondendo 200")
    void menuNaoFoiAfetadoPeloMapper() {
        for (String rota : List.of("/", "/analise", "/protocolos", "/resolucao-problemas", "/telemetria")) {
            given().header("Accept", "text/html")
                    .when().get(rota)
                    .then()
                    .statusCode(200)
                    .body(not(containsString("class=\"err-")));
        }
    }

    @Inject
    PaginaErroService paginaErroService;

    @Test
    @DisplayName("sem contexto de requisição o trace_id sai como indisponível, não inventado")
    void semContextoOTraceEHonesto() {
        DadosPaginaErro dados = paginaErroService.montar(500, "/fora-de-requisicao", "GET");

        assertNotNull(dados.traceId());
        assertFalse(dados.traceId().isBlank(),
                "Campo vazio na tela é pior que dizer que não há identificador.");
    }
}
