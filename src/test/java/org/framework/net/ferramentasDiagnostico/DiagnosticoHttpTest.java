package org.framework.net.ferramentasDiagnostico;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

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
                .body("data", containsString("8.8.8.8"))
                .body("data", containsString("Simulado"));
    }

    @Test
    void dnsSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("dominio", "exemplo.com")
                .when().post("/diagnostico/api/dns")
                .then()
                .statusCode(200)
                .body("data", containsString("exemplo.com"))
                .body("data", containsString("ANSWER SECTION"));
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
