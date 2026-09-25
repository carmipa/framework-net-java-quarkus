package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Achado na verificação em navegador da auditoria (2026-09-24): o login redirecionava erro para
 * {@code /login?erro=...}, que o Quarkus manda com 301 para {@code /login/} DESCARTANDO a query — quem
 * errava o login nunca via o motivo. O redirect tem de apontar direto para a rota real {@code /login/}.
 */
@QuarkusTest
class LoginRedirectHttpTest {

    @Test
    void erroDeLoginChegaATela() {
        // Sem OAuth configurado no perfil de teste: /login/github devolve para a tela com o motivo.
        String destino = given().redirects().follow(false)
                .when().get("/login/github")
                .then().statusCode(303)
                .extract().header("Location");
        assertTrue(destino.contains("/login/?erro="), "redirect perde o erro no 301 de /login: " + destino);

        // A tela realmente mostra o motivo quando recebe a query (controle positivo).
        given().when().get("/login/?erro=Motivo-de-teste").then()
                .statusCode(200).body(containsString("Motivo-de-teste"));
    }
}
