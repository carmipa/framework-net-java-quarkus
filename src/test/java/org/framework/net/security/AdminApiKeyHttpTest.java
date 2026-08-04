package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
@TestProfile(AdminSecurityTestProfile.class)
class AdminApiKeyHttpTest {

    /**
     * Regressão: a telemetria era pública e vazava o IP real do visitante.
     *
     * <p>Verificado em produção em 2026-08-03: {@code GET /telemetria/api/exportar}
     * devolvia 347 KB sem autenticação, contendo o IPv4 e os dois IPv6 do usuário.
     * {@code /api/resumo}, {@code /api/dashboard} e {@code /api/console} carregavam
     * o mesmo dado nas mensagens de evento.</p>
     */
    @Test
    void telemetriaSemChaveRetorna401() {
        for (String rota : new String[]{
                "/telemetria/api/resumo",
                "/telemetria/api/dashboard",
                "/telemetria/api/console",
                "/telemetria/api/exportar",
                "/telemetria/api/pasta"}) {
            given()
                    .header("Accept", "application/json")
                    .when().get(rota)
                    .then()
                    .statusCode(401);
        }
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
