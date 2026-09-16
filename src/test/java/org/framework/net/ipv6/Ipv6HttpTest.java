package org.framework.net.ipv6;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

/**
 * Guarda HTTP da Calculadora IPv6: a página abre, os assets são servidos, e os dois endpoints
 * (analisar e dividir) renderizam o fragmento com o conteúdo calculado — não só status 200.
 */
@QuarkusTest
class Ipv6HttpTest {

    private static final String FORM = "application/x-www-form-urlencoded";

    @Test
    void paginaCalculadoraCarregaComMenuEFormularios() {
        given()
                .when().get("/ipv6")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Calculadora IPv6 (CIDR)"))
                .body(containsString("hx-post=\"/ipv6/api/dividir\""))
                .body(containsString("data-tab=\"eui64\""))
                .body(containsString("data-tab=\"ula\""))
                .body(containsString("/ipv6/css/ipv6.css"));
    }

    @Test
    void paginaAnaliseCarregaComAnaliseComparadorEDominio() {
        given()
                .when().get("/ipv6/analise")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Análise Didática IPv6"))
                .body(containsString("data-active-tab=\"analise\""))
                .body(containsString("hx-post=\"/ipv6/api/calcular\""))
                .body(containsString("data-tab=\"comparador\""))
                .body(containsString("data-tab=\"dominio\""));
    }

    @Test
    void paginaResolucaoCarregaComFormulario() {
        given()
                .when().get("/ipv6/resolucao")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Resolução IPv6"))
                .body(containsString("hx-post=\"/ipv6/api/resolver\""));
    }

    @Test
    void menuTemDropdownIpv4EIpv6() {
        given()
                .when().get("/ipv6")
                .then()
                .statusCode(200)
                .body(containsString(">IPv4<"))
                .body(containsString(">IPv6<"))
                .body(containsString("href=\"/ipv6\""))
                .body(containsString("aed-nav-drop-toggle is-active"));
    }

    @Test
    void cssEJsServidosDaPastaPropria() {
        given().when().get("/ipv6/css/ipv6.css").then().statusCode(200).body(containsString(".ipv6-hextet"));
        given().when().get("/ipv6/js/ipv6.js").then().statusCode(200).body(containsString("data-limpar"));
    }

    @Test
    void analisarPrefixoDevolveTipoRedeEContagem() {
        given()
                .contentType(FORM)
                .formParam("endereco", "2001:db8::/48")
                .when().post("/ipv6/api/calcular")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Documentação"))
                .body(containsString("2^80"))
                .body(containsString("2001:db8::"))
                .body(containsString("Decomposição dos 128 bits"))
                .body(containsString("Delegação de prefixo"))
                .body(containsString("65536"));
    }

    @Test
    void dividirContaEListaSubredes() {
        given()
                .contentType(FORM)
                .formParam("bloco", "2001:db8::/32")
                .formParam("prefixoAlvo", "48")
                .when().post("/ipv6/api/dividir")
                .then()
                .statusCode(200)
                .body(containsString("65536"))
                .body(containsString("2001:db8::/48"))
                .body(containsString("Sub-redes que cabem"));
    }

    @Test
    void eui64RenderizaEnderecoSlaac() {
        given()
                .contentType(FORM)
                .formParam("prefixo", "2001:db8:0:1::/64")
                .formParam("mac", "00:1a:2b:3c:4d:5e")
                .when().post("/ipv6/api/eui64")
                .then()
                .statusCode(200)
                .body(containsString("2001:db8::1:21a:2bff:fe3c:4d5e"))
                .body(containsString("EUI-64"));
    }

    @Test
    void ulaGeraPrefixoFd() {
        given()
                .contentType(FORM)
                .formParam("subnetId", "1")
                .when().post("/ipv6/api/ula")
                .then()
                .statusCode(200)
                .body(containsString("RFC 4193"))
                .body(containsString("/48"));
    }

    @Test
    void resolverPlanejaDelegacaoDePrefixo() {
        given()
                .contentType(FORM)
                .formParam("base", "2001:db8::/48")
                .formParam("prefixoAlvo", "64")
                .formParam("nomes", "Matriz\nFilial\nServidores")
                .when().post("/ipv6/api/resolver")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Plano de endereçamento"))
                .body(containsString("Matriz"))
                .body(containsString("2001:db8::/64"))
                .body(containsString("2001:db8:0:1::/64"));
    }

    @Test
    void resolverAlvoNaoMaisEspecificoVolta400() {
        given()
                .contentType(FORM)
                .header("HX-Request", "true")
                .formParam("base", "2001:db8::/48")
                .formParam("prefixoAlvo", "48")
                .formParam("nomes", "A")
                .when().post("/ipv6/api/resolver")
                .then()
                .statusCode(400)
                .body(containsString("específico"));
    }

    @Test
    void compararDoisEnderecosMostraMesmaLanEContencao() {
        given()
                .contentType(FORM)
                .formParam("a", "2001:db8:0:1::/64")
                .formParam("b", "2001:db8:0:1:abcd::1")
                .when().post("/ipv6/api/comparar")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Comparação de endereços"))
                .body(containsString("Mesma /64"))
                .body(containsString("contém"));
    }

    @Test
    void dominioVazioVolta400ComFragmento() {
        given()
                .contentType(FORM)
                .header("HX-Request", "true")
                .formParam("dominio", "")
                .when().post("/ipv6/api/dominio")
                .then()
                .statusCode(400)
                .contentType(containsString("text/html"))
                .body(containsString("Informe um domínio"));
    }

    @Test
    void exportJsonDaAnaliseIpv6() {
        given()
                .when().get("/ipv6/export/json?endereco=2001:db8::/48")
                .then()
                .statusCode(200)
                .contentType(containsString("application/json"))
                .body(containsString("\"tipo\""))
                .body(containsString("Documentação"))
                .body(containsString("2^80"));
    }

    @Test
    void exportPdfDaAnaliseIpv6() {
        given()
                .when().get("/ipv6/export/pdf?endereco=2001:db8::/48")
                .then()
                .statusCode(200)
                .contentType(containsString("application/pdf"));
    }

    @Test
    void entradaInvalidaNoHtmxVolta400ComFragmento() {
        given()
                .contentType(FORM)
                .header("HX-Request", "true")
                .formParam("endereco", "nao-e-ipv6")
                .when().post("/ipv6/api/calcular")
                .then()
                .statusCode(400)
                .contentType(containsString("text/html"))
                .body(containsString("Não foi possível calcular"));
    }

    @Test
    void alvoMaisAmploQueBaseVolta400() {
        given()
                .contentType(FORM)
                .formParam("bloco", "2001:db8::/48")
                .formParam("prefixoAlvo", "32")
                .when().post("/ipv6/api/dividir")
                .then()
                .statusCode(400)
                .body(containsString("amplo"));
    }
}
