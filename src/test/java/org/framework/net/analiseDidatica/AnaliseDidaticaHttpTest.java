package org.framework.net.analiseDidatica;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
class AnaliseDidaticaHttpTest {

    @Test
    void postCidrExibeBlocoCompletoIpv4() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "192.168.1.10")
                .formParam("cidr", "24")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("Decomposição bitwise AND"))
                .body(containsString("Linha do tempo do bloco"))
                .body(containsString("Régua de sub-redes"));
    }

    @Test
    void postCidr31() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "10.0.0.0")
                .formParam("cidr", "31")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("/31"));
    }

    @Test
    void postCidrSemBarraInfereClassful() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "10.5.5.5")
                .formParam("cidr", "")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("/8"));
    }

    @Test
    void postCidrClasseCInfere24() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "200.1.1.1")
                .formParam("cidr", "")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("/24"));
    }

    @Test
    void postWildcardInvalida() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "wildcard")
                .formParam("ip", "172.16.8.8")
                .formParam("wildcard_mask", "0.0.255.0")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("Wildcard"));
    }

    @Test
    void postDominioInvalido() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "dominio")
                .formParam("ip", "dominio@@invalido")
                .when().post("/analise")
                .then()
                .statusCode(200)
                // F23: "Domínio" é texto fixo da página; só a mensagem de validação prova a recusa.
                .body(containsString("Domínio/hostname inválido"));
    }

    @Test
    void postMascaraSomente2552551920() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "mask")
                .formParam("mask_decimal", "255.255.192.0")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("/18"));
    }

    @Test
    void postIpv6() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "ipv6")
                .formParam("ipv6", "2001:db8::1")
                .when().post("/analise")
                .then()
                .statusCode(200)
                // F23: "IPv6" está no menu e "2001" é eco da entrada. A expansão e a classificação só
                // existem se o cálculo rodou (gabarito: RFC 4291 / RFC 3849).
                .body(containsString("2001:0db8:0000:0000:0000:0000:0000:0001"))
                .body(containsString("Documentação"));
    }

    @Test
    void postCidrComIpBarraNoCampoIp() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "172.16.0.10/24")
                .formParam("cidr", "")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("/24"))
                .body(not(containsString("Não foi possível resolver o domínio")));
    }

    @Test
    void postComparadorCidrInvalidoMarcaCampo() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "comparador")
                .formParam("ip", "10.0.0.1")
                .formParam("comparador_cidr_a", "99")
                .formParam("comparador_cidr_b", "24")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("is-invalid"))
                .body(containsString("entre 0 e 32"));
    }

    @Test
    void postComparador() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "comparador")
                .formParam("ip", "10.0.0.1")
                .formParam("comparador_cidr_a", "20")
                .formParam("comparador_cidr_b", "24")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(not(containsString("Erro:")));
    }
}
