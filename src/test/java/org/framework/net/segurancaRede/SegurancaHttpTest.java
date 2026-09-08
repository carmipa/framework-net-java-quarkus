package org.framework.net.segurancaRede;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
class SegurancaHttpTest {

    @Test
    void aclPermitDaMatch() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("MATCH - PERMITIDO"))
                .body(containsString("check_circle"))
                .body(containsString("Origem: 192.168.1.5 -&gt; Destino: 10.0.0.1:80"));
    }

    @Test
    void aclDenyDaMatchBloqueado() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "deny tcp any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("MATCH - BLOQUEADO"))
                .body(containsString("cancel"));
    }

    @Test
    void pacoteForaDaRegraDaNoMatch() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp 10.0.0.5 eq 443")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("NO MATCH"))
                .body(containsString("help_center"));
    }

    @Test
    void fragmentoNaoTrazOLayoutCompleto() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(not(containsString("<!DOCTYPE html>")))
                .body(not(containsString("<body")));
    }

    @Test
    void paginaTrazOFormularioLigadoAoHtmx() {
        given()
                .when().get("/seguranca")
                .then()
                .statusCode(200)
                .body(containsString("hx-post=\"/seguranca/api/testar\""))
                .body(containsString("hx-target=\"#resultadoContainer\""))
                .body(containsString("/web/js/htmx.min.js"));
    }

    @Test
    void erroDeRequisicaoHtmxVoltaComoFragmento() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .header("HX-Request", "true")
                .formParam("regra", "permit tcp any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "abc")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400)
                .contentType(containsString("text/html"))
                .body(containsString("ERRO"))
                .body(containsString("Porta"));
    }

    @Test
    void portaNaoNumericaRetorna400Amigavel() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "abc")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400)
                .body(containsString("Porta"));
    }

    @Test
    void portaVaziaRetorna400() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400);
    }

    @Test
    void rejeitaCaracteresPerigosos() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit <script>")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400);
    }

    // ---- Contraexemplos que reprovariam a avaliação antiga (parecer C01) ----

    @Test
    void portaComparaExatoNaoSubstring() {
        // Antes: contains("eq 80") casava com "eq 8080". Porta 80 NÃO deve casar 8080.
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any eq 8080")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("NO MATCH"))
                .body(not(containsString("PERMITIDO")));
    }

    @Test
    void destinoForaDoAlcanceDaNoMatch() {
        // Antes: o IP de destino não participava da decisão. host 10.0.0.1 != 10.0.0.2 -> NO MATCH.
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit ip any host 10.0.0.1")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.2")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("NO MATCH"));
    }

    @Test
    void destinoNoAlcanceDaMatch() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit ip any host 10.0.0.1")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("PERMITIDO"));
    }

    @Test
    void curingaDeRedeCasaFaixa() {
        // 192.168.1.0 0.0.0.255 cobre 192.168.1.99, mas não 192.168.2.5.
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp 192.168.1.0 0.0.0.255 eq 80")
                .formParam("ipOrigem", "192.168.1.99")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("PERMITIDO"));
    }

    @Test
    void curingaDeRedeForaDaFaixaDaNoMatch() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp 192.168.1.0 0.0.0.255 eq 80")
                .formParam("ipOrigem", "192.168.2.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("NO MATCH"));
    }

    @Test
    void regraNaoSuportadaRetorna400() {
        // Sintaxe fora do suportado é recusada, não reinterpretada como "no match".
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "allow tcp any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400);
    }

    // ---- Inspeção TLS (aba nova) ----

    @Test
    void paginaTrazAbaDeInspecaoTls() {
        given()
                .when().get("/seguranca")
                .then()
                .statusCode(200)
                .body(containsString("data-tab=\"tls\""))
                .body(containsString("data-tab-panel=\"tls\""))
                .body(containsString("hx-post=\"/seguranca/api/tls\""))
                .body(containsString("hx-post=\"/seguranca/api/testar\""))
                .body(containsString("/web/js/aed-tabs.js"));
    }

    private static io.restassured.specification.RequestSpecification tls(
            String host, String cert, String dias, String self, String versao, String cipher) {
        return given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("hostAcessado", host)
                .formParam("nomeCertificado", cert)
                .formParam("diasParaExpirar", dias)
                .formParam("autoassinado", self)
                .formParam("versaoTls", versao)
                .formParam("cipher", cipher);
    }

    @Test
    void tlsConfiavelQuandoTudoOk() {
        tls("frameworknet.carminati.dev.br", "frameworknet.carminati.dev.br", "60", "nao",
                "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("CONFIÁVEL"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void tlsRecusaNomeDivergente() {
        tls("banco.com.br", "phishing.evil.com", "60", "nao", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("RECUSADO PELO NAVEGADOR"))
                .body(containsString("FALHA"))
                .body(containsString("Nome (SAN)"));
    }

    @Test
    void tlsRecusaCertificadoExpirado() {
        tls("exemplo.com", "exemplo.com", "-3", "nao", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("RECUSADO PELO NAVEGADOR"))
                .body(containsString("EXPIRADO"));
    }

    @Test
    void tlsAtencaoComTls12ECipherCbc() {
        tls("exemplo.com", "exemplo.com", "60", "nao", "TLS 1.2", "ECDHE-RSA-AES128-CBC-SHA")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("ACEITO COM RESSALVAS"))
                .body(containsString("ATENÇÃO"));
    }

    @Test
    void tlsCuringaCasaUmRotulo() {
        tls("www.exemplo.com", "*.exemplo.com", "60", "nao", "TLS 1.3", "TLS_CHACHA20_POLY1305_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("CONFIÁVEL"));
    }

    @Test
    void tlsDiasNaoNumericoRetorna400() {
        tls("exemplo.com", "exemplo.com", "abc", "nao", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(400);
    }

    @Test
    void tlsRejeitaCaracteresPerigosos() {
        tls("<script>", "exemplo.com", "60", "nao", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(400);
    }

    @Test
    void tlsRecusaCadeiaAutoassinada() {
        tls("exemplo.com", "exemplo.com", "60", "sim", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("RECUSADO PELO NAVEGADOR"))
                .body(containsString("Cadeia de confiança"))
                .body(containsString("autoassinado"));
    }

    @Test
    void tlsRecusaProtocoloObsoleto() {
        tls("exemplo.com", "exemplo.com", "60", "nao", "TLS 1.0", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("RECUSADO PELO NAVEGADOR"))
                .body(containsString("Protocolo"))
                .body(containsString("obsoleta"));
    }

    @Test
    void tlsRecusaCipherQuebrada() {
        tls("exemplo.com", "exemplo.com", "60", "nao", "TLS 1.2", "RC4-MD5")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("RECUSADO PELO NAVEGADOR"))
                .body(containsString("Cipher"))
                .body(containsString("quebrada"));
    }

    @Test
    void tlsCuringaNaoCasaMultiplosRotulos() {
        // O curinga cobre UM rótulo: *.exemplo.com casa www.exemplo.com, mas NÃO www.sub.exemplo.com.
        tls("www.sub.exemplo.com", "*.exemplo.com", "60", "nao", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("RECUSADO PELO NAVEGADOR"))
                .body(containsString("Nome (SAN)"));
    }

    @Test
    void tlsDiasForaDeFaixaRetorna400() {
        tls("exemplo.com", "exemplo.com", "999999", "nao", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(400);
    }
}
