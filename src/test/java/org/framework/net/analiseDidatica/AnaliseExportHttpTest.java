package org.framework.net.analiseDidatica;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class AnaliseExportHttpTest {

    @Test
    void exportJson() {
        given()
                .when().get("/export/json")
                .then()
                .statusCode(200)
                .body("generated_at", notNullValue())
                .body("history", notNullValue());
    }

    @Test
    void exportPdfSemHistoricoRedirecionaHome() {
        // F23: aceitar "303 OU 200 de qualquer coisa" não provava nada. O histórico do export é global
        // e outros testes podem tê-lo preenchido, então os DOIS desfechos legítimos são exigidos por
        // inteiro: sem histórico, 303 para "/"; com histórico, um PDF de verdade.
        var r = given().redirects().follow(false).when().get("/export/pdf").then().extract();
        if (r.statusCode() == 303) {
            org.junit.jupiter.api.Assertions.assertTrue(r.header("Location").endsWith("/"), r.header("Location"));
        } else {
            org.junit.jupiter.api.Assertions.assertEquals(200, r.statusCode());
            org.junit.jupiter.api.Assertions.assertTrue(r.contentType().contains("application/pdf"), r.contentType());
            org.junit.jupiter.api.Assertions.assertTrue(new String(r.asByteArray(), 0, 4,
                    java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF"), "corpo não é PDF");
        }
    }
}
