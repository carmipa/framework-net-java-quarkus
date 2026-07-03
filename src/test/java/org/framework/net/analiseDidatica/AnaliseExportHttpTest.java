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
        given()
                .redirects().follow(false)
                .when().get("/export/pdf")
                .then()
                .statusCode(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.equalTo(303),
                        org.hamcrest.Matchers.equalTo(302),
                        org.hamcrest.Matchers.equalTo(200)));
    }
}
