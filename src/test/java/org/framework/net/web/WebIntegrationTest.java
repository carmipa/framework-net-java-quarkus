package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
class WebIntegrationTest {

    @Test
    void homeDeveResponderComAnaliseDidatica() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(containsString("Análise Didática"))
                .body(containsString("CIDR"));
    }

    @Test
    void portasDeveResponderComCatalogo() {
        given()
                .when().get("/portas")
                .then()
                .statusCode(200)
                .body(containsString("Catálogo de Portas"))
                .body(containsString("SSH"));
    }

    @Test
    void protocolosDeveResponderComCatalogo() {
        given()
                .when().get("/protocolos")
                .then()
                .statusCode(200)
                .body(containsString("Catálogo de Protocolos"))
                .body(containsString("OSPF"));
    }

    @Test
    void resolucaoProblemasDeveResponder() {
        given()
                .when().get("/resolucao-problemas")
                .then()
                .statusCode(200)
                .body(containsString("Resolução de Problemas"));
    }

    @Test
    void resolucaoDemoFiapDeveResponder() {
        given()
                .when().get("/resolucao-problemas?demo=fiap")
                .then()
                .statusCode(200)
                .body(containsString("172.42.0.0"));
    }

    @Test
    void documentacaoDeveResponder() {
        given()
                .when().get("/documentacao")
                .then()
                .statusCode(200)
                .body(containsString("Framework"))
                .body(containsString("NAVEGAÇÃO"));
    }

    @Test
    void telemetriaDeveResponderComDashboard() {
        given()
                .when().get("/telemetria")
                .then()
                .statusCode(200)
                .body(containsString("Telemetria"))
                .body(containsString("Framework de Redes"));
    }

    @Test
    void informacoesDeveResponder() {
        given()
                .when().get("/informacoes")
                .then()
                .statusCode(200)
                .body(containsString("Região"));
    }

    @Test
    void iconeDeveResponderPngOu404() {
        given()
                .when().get("/icone.png")
                .then()
                .statusCode(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.equalTo(200),
                        org.hamcrest.Matchers.equalTo(404)));
    }

    @Test
    void apiGeoDeveResponderJson() {
        given()
                .queryParam("ip", "8.8.8.8")
                .when().get("/api/informacoes/geo")
                .then()
                .statusCode(200)
                .contentType(containsString("json"));
    }
}
