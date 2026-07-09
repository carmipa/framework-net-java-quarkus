package org.framework.net.localizacao;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
class LocalizacaoHttpTest {

    @Test
    void paginaLocalizacaoResponde() {
        given()
                .when().get("/localizacao")
                .then()
                .statusCode(200)
                .body(containsString("Localização"))
                .body(containsString("loc-map"));
    }

    @Test
    void cepInvalidoRetornaOkFalseSemRede() {
        given()
                .queryParam("cep", "abc")
                .when().get("/localizacao/api/cep")
                .then()
                .statusCode(200)
                .contentType(containsString("json"))
                .body("ok", equalTo(false))
                .body("motivo", equalTo("invalid"));
    }

    @Test
    void ipReservadoRetornaJsonSemRede() {
        given()
                .queryParam("ip", "127.0.0.1")
                .when().get("/localizacao/api/ip")
                .then()
                .statusCode(200)
                .contentType(containsString("json"))
                .body("origem", equalTo("ip"));
    }
}
