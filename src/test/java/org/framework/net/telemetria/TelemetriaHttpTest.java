package org.framework.net.telemetria;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class TelemetriaHttpTest {

    @Test
    void resumoJson() {
        given()
                .when().get("/telemetria/api/resumo")
                .then()
                .statusCode(200)
                .body("projeto", notNullValue())
                .body("totalEventos", notNullValue());
    }

    @Test
    void dashboardJson() {
        given()
                .when().get("/telemetria/api/dashboard")
                .then()
                .statusCode(200)
                .body("resumo.projeto", notNullValue())
                .body("consoleLinhas", notNullValue());
    }

    @Test
    void paginaHtml() {
        given()
                .when().get("/telemetria")
                .then()
                .statusCode(200)
                .body(containsString("Telemetria do Framework"))
                .body(containsString("console-telemetria"))
                .body(containsString("chart-modulos"));
    }

    @Test
    void exportarArquivoCompartilhado() {
        given()
                .when().get("/telemetria/api/exportar")
                .then()
                .statusCode(200)
                .header("Content-Disposition", containsString("telemetria_compartilhada"));
    }

    @Test
    void requestIncluiTraceId() {
        given()
                .when().get("/documentacao")
                .then()
                .statusCode(200)
                .header("X-Request-Id", notNullValue())
                .header("X-Trace-Id", notNullValue());
    }
}
