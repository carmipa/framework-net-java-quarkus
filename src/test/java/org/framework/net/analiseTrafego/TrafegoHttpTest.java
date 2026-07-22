package org.framework.net.analiseTrafego;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

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
                .body(containsString("trafego-hex"))
                .body(containsString("hx-post=\"/trafego/api/decodificar\""))
                .body(containsString("hx-target=\"#trafego-resultado\""));
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
                .contentType(containsString("text/html"))
                .body(containsString("54 bytes decodificados"))
                .body(containsString("IPv4"))
                .body(containsString("trafego-camada"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void hexInvalidoVoltaComoFragmentoDeAviso() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("hex", "isto não é hex")
                .when().post("/trafego/api/decodificar")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("trafego-erro"))
                .body(containsString("Não foi possível decodificar"));
    }
}
