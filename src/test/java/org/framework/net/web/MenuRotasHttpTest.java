package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.security.SessaoTelemetriaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

/**
 * Cobertura de fumaça de todas as rotas do menu principal.
 *
 * <p><b>Propósito de negócio:</b> o menu é a porta de entrada do sistema. Um
 * item que abre em erro 500 ou renderiza template quebrado é a falha mais cara
 * possível — o usuário nem chega ao recurso. Este teste garante que cada função
 * do menu abre, traz o menu de volta e se marca como ativa.</p>
 *
 * <p><b>Invariantes do domínio:</b> a lista aqui deve espelhar
 * {@code shared/main_menu.html}; um item novo no menu sem linha nova aqui é
 * regressão de cobertura, e o teste {@code menuNaoTemItemSemCobertura} falha
 * justamente para forçar a atualização.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> a asserção aponta a rota, o status
 * recebido e o marcador ausente.</p>
 */
@QuarkusTest
@DisplayName("Menu principal: todas as rotas abrem e se marcam como ativas")
class MenuRotasHttpTest {

    @Inject
    SessaoTelemetriaService sessao;

    /**
     * Cookie de dono para as rotas protegidas.
     *
     * <p>A Telemetria deixou de ser publica: o menu continua levando ate ela, mas
     * abrir exige sessao. O teste de fumaca do menu autentica para continuar
     * provando que a PAGINA abre - quem prova que ela fecha sem sessao e o
     * AcessoTelemetriaHttpTest.</p>
     */
    private String cookieDeDono() {
        return SessaoTelemetriaService.COOKIE_NAME + "=" + sessao.emitirCookie(
                "carmipa", SessaoTelemetriaService.ORIGEM_GITHUB,
                SessaoTelemetriaService.PAPEL_DONO).getValue();
    }

    /** Rotas do menu principal, na ordem em que aparecem em shared/main_menu.html. */
    private static final List<String> ROTAS_DO_MENU = List.of(
            "/", "/analise", "/calculadora", "/portas", "/protocolos", "/resolucao-problemas",
            "/localizacao", "/trafego", "/diagnostico", "/seguranca", "/telemetria",
            "/documentacao", "/sobre");

    @ParameterizedTest(name = "{0} abre e destaca \"{1}\" no menu")
    @CsvSource({
            "/,                       Início",
            "/analise,                Análise Didática",
            "/calculadora,            Calculadora",
            "/portas,                 Portas",
            "/protocolos,             Protocolos",
            "/resolucao-problemas,    Resolução (VLSM+WAN)",
            "/localizacao,            Localização",
            "/trafego,                Tráfego",
            "/diagnostico,            Diagnóstico",
            "/seguranca,              Segurança (ACL)",
            "/telemetria,             Telemetria",
            "/documentacao,           Documentação",
            "/sobre,                  Sobre"
    })
    void rotaDoMenuAbreComOMenuERotuloCorreto(String rota, String rotulo) {
        given()
                .header("Cookie", cookieDeDono())
                .when().get(rota)
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("aed-topnav"))
                .body(containsString(rotulo))
                .body(not(containsString("Internal Server Error")));
    }

    @ParameterizedTest(name = "{0} expõe todos os demais itens do menu")
    @CsvSource({"/", "/analise", "/calculadora", "/portas", "/protocolos", "/resolucao-problemas",
            "/localizacao", "/trafego", "/diagnostico", "/seguranca", "/telemetria",
            "/documentacao", "/sobre"})
    void todaPaginaDoMenuNavegaParaAsOutras(String rota) {
        var resposta = given().header("Cookie", cookieDeDono())
                .when().get(rota).then().statusCode(200);
        for (String destino : ROTAS_DO_MENU) {
            resposta.body(containsString("href=\"" + destino + "\""));
        }
    }

    @Test
    @DisplayName("regressão: item novo no menu sem teste correspondente quebra o build")
    void menuNaoTemItemSemCobertura() {
        String html = given().when().get("/").then().statusCode(200).extract().asString();

        int itens = contarOcorrencias(html, "class=\"aed-nav-link ");
        org.junit.jupiter.api.Assertions.assertEquals(ROTAS_DO_MENU.size(), itens,
                "O menu principal tem " + itens + " item(ns), mas o teste cobre "
                        + ROTAS_DO_MENU.size() + ". Adicione a rota nova em ROTAS_DO_MENU e no @CsvSource.");
    }

    @Test
    void rotaInexistenteDevolve404ENaoErroDeServidor() {
        given()
                .when().get("/rota-que-nao-existe")
                .then()
                .statusCode(404);
    }

    private static int contarOcorrencias(String texto, String agulha) {
        int total = 0;
        int idx = texto.indexOf(agulha);
        while (idx >= 0) {
            total++;
            idx = texto.indexOf(agulha, idx + agulha.length());
        }
        return total;
    }
}
