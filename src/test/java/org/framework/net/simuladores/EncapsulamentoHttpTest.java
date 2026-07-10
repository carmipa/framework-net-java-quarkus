package org.framework.net.simuladores;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

@QuarkusTest
class EncapsulamentoHttpTest {

    @Test
    void encapsulaViaHttp() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("mensagem", "GET / HTTP/1.1")
                .formParam("transporte", "TCP")
                .formParam("ipOrigem", "192.168.0.10")
                .formParam("ipDestino", "142.250.79.14")
                .formParam("portaOrigem", "51000")
                .formParam("portaDestino", "80")
                .when().post("/simuladores/api/encapsular")
                .then()
                .statusCode(200)
                .body("ok", equalTo(true))
                .body("transporte", equalTo("TCP"))
                .body("camadas.size()", equalTo(4))
                .body("totalBytes", greaterThan(0));
    }

    @Test
    void mensagemVaziaRetornaOkFalse() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("mensagem", "")
                .formParam("transporte", "TCP")
                .when().post("/simuladores/api/encapsular")
                .then()
                .statusCode(200)
                .body("ok", equalTo(false));
    }
}
