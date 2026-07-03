package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
@TestProfile(AdminSecurityTestProfile.class)
class AdminApiKeyHttpTest {

    @Test
    void telemetriaPublicaSemChaveRetorna200() {
        given()
                .header("Accept", "application/json")
                .when().get("/telemetria/api/resumo")
                .then()
                .statusCode(200);
    }

    @Test
    void exportSemChaveRetorna401() {
        given()
                .header("Accept", "application/json")
                .when().get("/export/json")
                .then()
                .statusCode(401)
                .body(containsString("API key administrativa"));
    }

    @Test
    void exportComHeaderValidoRetorna200() {
        given()
                .header("X-Admin-Api-Key", "test-admin-secret")
                .when().get("/export/json")
                .then()
                .statusCode(200);
    }

    @Test
    void historyPublicoSemChaveRetorna200() {
        given()
                .header("Accept", "application/json")
                .when().get("/history")
                .then()
                .statusCode(200);
    }

    @Test
    void loginHtmlDisponivelSemChave() {
        given()
                .when().get("/admin/login")
                .then()
                .statusCode(200)
                .body(containsString("Acesso administrativo"));
    }
}
