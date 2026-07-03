package org.framework.net.analiseDidatica;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class HistoricoApiHttpTest {

    @Test
    void historyGet() {
        given()
                .when().get("/history")
                .then()
                .statusCode(200)
                .body("items", notNullValue());
    }

    @Test
    void mascaraReferencia() {
        given()
                .when().get("/mascara-referencia")
                .then()
                .statusCode(200)
                .body("table", notNullValue());
    }

    @Test
    void historyCatalogModoInvalido() {
        given()
                .contentType("application/json")
                .body("{\"modo\":\"invalido\",\"entrada\":\"teste\"}")
                .when().post("/history/catalog")
                .then()
                .statusCode(400);
    }

    @Test
    void historyCatalogPortasValido() {
        given()
                .contentType("application/json")
                .body("{\"modo\":\"portas\",\"entrada\":\"443 https\"}")
                .when().post("/history/catalog")
                .then()
                .statusCode(200);
    }
}
