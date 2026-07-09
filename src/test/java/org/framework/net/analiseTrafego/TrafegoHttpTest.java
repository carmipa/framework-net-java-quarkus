package org.framework.net.analiseTrafego;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
class TrafegoHttpTest {

    private static final String FRAME_SYN =
            "aabbccddeeff1122334455660800"
                    + "450000281c4640004006b1e6c0a80001c0a80002"
                    + "d4310050000000000000000050027210e5770000";

    @Test
    void paginaTrafegoResponde() {
        given()
                .when().get("/trafego")
                .then()
                .statusCode(200)
                .body(containsString("Decodificador"))
                .body(containsString("trafego-hex"));
    }

    @Test
    void decodificaFrameViaApi() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("hex", FRAME_SYN)
                .formParam("camada", "auto")
                .when().post("/trafego/api/decodificar")
                .then()
                .statusCode(200)
                .body("ok", equalTo(true))
                .body("totalBytes", equalTo(54))
                .body("camadas.nome", hasItem("IPv4"));
    }

    @Test
    void hexInvalidoRetornaOkFalse() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("hex", "isto não é hex")
                .when().post("/trafego/api/decodificar")
                .then()
                .statusCode(200)
                .body("ok", equalTo(false));
    }
}
