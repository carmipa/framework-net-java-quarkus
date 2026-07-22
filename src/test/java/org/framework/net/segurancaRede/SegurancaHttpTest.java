package org.framework.net.segurancaRede;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
class SegurancaHttpTest {

    @Test
    void aclPermitDaMatch() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("MATCH - PERMITIDO"))
                .body(containsString("check_circle"))
                .body(containsString("Origem: 192.168.1.5 -&gt; Destino: 10.0.0.1:80"));
    }

    @Test
    void aclDenyDaMatchBloqueado() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "deny tcp any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("MATCH - BLOQUEADO"))
                .body(containsString("cancel"));
    }

    @Test
    void pacoteForaDaRegraDaNoMatch() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp 10.0.0.5 eq 443")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("NO MATCH"))
                .body(containsString("help_center"));
    }

    @Test
    void fragmentoNaoTrazOLayoutCompleto() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(not(containsString("<!DOCTYPE html>")))
                .body(not(containsString("<body")));
    }

    @Test
    void paginaTrazOFormularioLigadoAoHtmx() {
        given()
                .when().get("/seguranca")
                .then()
                .statusCode(200)
                .body(containsString("hx-post=\"/seguranca/api/testar\""))
                .body(containsString("hx-target=\"#resultadoContainer\""))
                .body(containsString("/web/js/htmx.min.js"));
    }

    @Test
    void erroDeRequisicaoHtmxVoltaComoFragmento() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .header("HX-Request", "true")
                .formParam("regra", "permit tcp any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "abc")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400)
                .contentType(containsString("text/html"))
                .body(containsString("ERRO"))
                .body(containsString("Porta"));
    }

    @Test
    void portaNaoNumericaRetorna400Amigavel() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "abc")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400)
                .body(containsString("Porta"));
    }

    @Test
    void portaVaziaRetorna400() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400);
    }

    @Test
    void rejeitaCaracteresPerigosos() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit <script>")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400);
    }
}
