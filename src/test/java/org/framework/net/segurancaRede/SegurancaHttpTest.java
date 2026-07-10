package org.framework.net.segurancaRede;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;

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
                .body("data", anyOf(containsString("PERMITIDO"), containsString("MATCH")));
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
