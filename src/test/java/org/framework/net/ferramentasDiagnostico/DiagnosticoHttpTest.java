package org.framework.net.ferramentasDiagnostico;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
class DiagnosticoHttpTest {

    @Test
    void pingSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "8.8.8.8")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("8.8.8.8"))
                .body(containsString("Simulado"))
                .body(containsString("<pre><code>"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void dnsSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("dominio", "exemplo.com")
                .when().post("/diagnostico/api/dns")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("exemplo.com"))
                .body(containsString("ANSWER SECTION"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void paginaTrazOsDoisFormulariosLigadosAoHtmx() {
        given()
                .when().get("/diagnostico")
                .then()
                .statusCode(200)
                .body(containsString("hx-post=\"/diagnostico/api/ping\""))
                .body(containsString("hx-post=\"/diagnostico/api/dns\""))
                .body(containsString("hx-target=\"#pingSaida\""))
                .body(containsString("hx-target=\"#dnsSaida\""))
                .body(not(containsString("diagnostico.js")));
    }

    @Test
    void erroDeRequisicaoHtmxVoltaComoFragmentoDeTerminal() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .header("HX-Request", "true")
                .formParam("host", "8.8.8.8; rm -rf /")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(400)
                .contentType(containsString("text/html"))
                .body(containsString("terminal"))
                .body(containsString("Erro:"));
    }

    @Test
    void rejeitaInjecaoDeComando() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "8.8.8.8; rm -rf /")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(400);
    }

    @Test
    void rejeitaCaracteresXss() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "<script>alert(1)</script>")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(400);
    }

    @Test
    void rejeitaHostVazio() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(400);
    }
}
