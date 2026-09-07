package org.framework.net.ferramentasDiagnostico;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
class DiagnosticoHttpTest {

    @Test
    void pingSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "8.8.8.8")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("8.8.8.8"))
                .body(containsString("Simulado"))
                .body(containsString("<pre><code>"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void dnsSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("dominio", "exemplo.com")
                .when().post("/diagnostico/api/dns")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("exemplo.com"))
                .body(containsString("ANSWER SECTION"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void paginaTrazOsDoisFormulariosLigadosAoHtmx() {
        given()
                .when().get("/diagnostico")
                .then()
                .statusCode(200)
                .body(containsString("hx-post=\"/diagnostico/api/ping\""))
                .body(containsString("hx-post=\"/diagnostico/api/dns\""))
                .body(containsString("hx-target=\"#pingSaida\""))
                .body(containsString("hx-target=\"#dnsSaida\""))
                .body(not(containsString("diagnostico.js")));
    }

    @Test
    void pingTrazDissecacaoDidatica() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "8.8.8.8")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(200)
                .body(containsString("diag-kpis"))          // indicadores
                .body(containsString("comando real"))        // o comando que representa
                .body(containsString("Como funciona"))       // explicação passo a passo
                .body(containsString("O que observar"))       // leitura
                .body(containsString("Legenda dos termos"))   // glossário
                .body(containsString("RTT"))                  // termo dissecado
                .body(containsString("Saída bruta"));          // terminal preservado
    }

    @Test
    void scanTrazTabelaDissecadaComEstados() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "scanme.exemplo.com")
                .when().post("/diagnostico/api/scan")
                .then()
                .statusCode(200)
                .body(containsString("diag-tabela"))
                .body(containsString("diag-linha-success"))   // porta aberta destacada
                .body(containsString("diag-linha-warning"))   // porta filtrada destacada
                .body(containsString("Legenda dos termos"))
                .body(containsString("half-open"));
    }

    @Test
    void paginaTrazAsNovasAbasDeAnalista() {
        given()
                .when().get("/diagnostico")
                .then()
                .statusCode(200)
                .body(containsString("data-tab=\"traceroute\""))
                .body(containsString("data-tab=\"sweep\""))
                .body(containsString("data-tab=\"scan\""))
                .body(containsString("data-tab=\"spoofing\""))
                .body(containsString("hx-post=\"/diagnostico/api/traceroute\""))
                .body(containsString("hx-post=\"/diagnostico/api/ping-sweep\""))
                .body(containsString("hx-post=\"/diagnostico/api/scan\""))
                .body(containsString("hx-post=\"/diagnostico/api/dns-spoofing\""))
                .body(containsString("/web/js/aed-tabs.js"));
    }

    @Test
    void tracerouteSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "1.1.1.1")
                .when().post("/diagnostico/api/traceroute")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("1.1.1.1"))
                .body(containsString("TTL"))
                .body(containsString("Simulado"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void pingSweepSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("rede", "192.168.1.0/24")
                .when().post("/diagnostico/api/ping-sweep")
                .then()
                .statusCode(200)
                .body(containsString("ping sweep"))
                .body(containsString("ATIVO"))
                .body(containsString("192.168.1"));
    }

    @Test
    void scanSynSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "scanme.exemplo.com")
                .when().post("/diagnostico/api/scan")
                .then()
                .statusCode(200)
                .body(containsString("SYN stealth"))
                .body(containsString("filtered"))
                .body(containsString("22/tcp"));
    }

    @Test
    void dnsSpoofingSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("dominio", "banco.exemplo.com")
                .when().post("/diagnostico/api/dns-spoofing")
                .then()
                .statusCode(200)
                .body(containsString("spoofing"))
                .body(containsString("Transaction ID"))
                .body(containsString("DNSSEC"));
    }

    @ParameterizedTest(name = "{0} rejeita injeção de comando com 400")
    @CsvSource({
            "/diagnostico/api/ping,         host",
            "/diagnostico/api/dns,          dominio",
            "/diagnostico/api/traceroute,   host",
            "/diagnostico/api/ping-sweep,   rede",
            "/diagnostico/api/scan,         host",
            "/diagnostico/api/dns-spoofing, dominio"
    })
    void todosOsEndpointsRejeitamInjecao(String rota, String campo) {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam(campo.trim(), "1.1.1.1; rm -rf /")
                .when().post(rota.trim())
                .then()
                .statusCode(400);
    }

    @ParameterizedTest(name = "{0} rejeita <script> com 400")
    @CsvSource({
            "/diagnostico/api/ping,         host",
            "/diagnostico/api/dns,          dominio",
            "/diagnostico/api/traceroute,   host",
            "/diagnostico/api/ping-sweep,   rede",
            "/diagnostico/api/scan,         host",
            "/diagnostico/api/dns-spoofing, dominio"
    })
    void todosOsEndpointsRejeitamXss(String rota, String campo) {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam(campo.trim(), "<script>alert(1)</script>")
                .when().post(rota.trim())
                .then()
                .statusCode(400);
    }

    @Test
    void erroDeRequisicaoHtmxVoltaComoFragmentoDeTerminal() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .header("HX-Request", "true")
                .formParam("host", "8.8.8.8; rm -rf /")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(400)
                .contentType(containsString("text/html"))
                .body(containsString("terminal"))
                .body(containsString("Erro:"));
    }

    @Test
    void rejeitaInjecaoDeComando() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "8.8.8.8; rm -rf /")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(400);
    }

    @Test
    void rejeitaCaracteresXss() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "<script>alert(1)</script>")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(400);
    }

    @Test
    void rejeitaHostVazio() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(400);
    }
}
