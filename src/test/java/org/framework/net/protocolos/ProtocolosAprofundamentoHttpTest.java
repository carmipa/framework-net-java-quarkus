package org.framework.net.protocolos;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.telemetria.TelemetriaEvent;
import org.framework.net.telemetria.TelemetriaStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura HTTP das páginas de aprofundamento por protocolo.
 *
 * <p><b>Propósito de negócio:</b> {@code /protocolos/bgp} e
 * {@code /protocolos/ssh} são páginas de conteúdo — se abrirem em erro ou
 * perderem uma seção, ninguém percebe pelo build. Este teste prova que abrem,
 * que trazem o menu principal e o sub-menu do módulo, que a aba correta está
 * marcada e que o conteúdo essencial de cada uma chegou ao HTML.</p>
 *
 * <p><b>Invariantes do domínio:</b> a aba Geral continua respondendo em
 * {@code /protocolos} depois da chegada das sub-rotas — é a regressão que o
 * projeto já viu no módulo de Tráfego, quando dois {@code @Path} sob o mesmo
 * prefixo derrubaram uma das rotas para 404. Slug inexistente responde 404, e
 * nunca a página de outro protocolo.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> a asserção aponta a rota e o trecho
 * ausente no corpo da resposta.</p>
 */
@QuarkusTest
@DisplayName("Protocolos: páginas de aprofundamento (BGP e SSH)")
class ProtocolosAprofundamentoHttpTest {

    @Inject
    TelemetriaStore telemetriaStore;

    @ParameterizedTest(name = "{0} abre com o menu, o sub-menu e o título \"{1}\"")
    @CsvSource({
            "/protocolos,       Catálogo de Protocolos de Rede",
            "/protocolos/bgp,   Border Gateway Protocol",
            "/protocolos/ssh,   Secure Shell"
    })
    void paginasDoModuloAbrem(String rota, String titulo) {
        given()
                .when().get(rota)
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("aed-topnav"))
                .body(containsString("protocolo-subnav"))
                .body(containsString(titulo))
                .body(not(containsString("Internal Server Error")));
    }

    @ParameterizedTest(name = "{0} oferece a navegação completa do módulo")
    @CsvSource({"/protocolos", "/protocolos/bgp", "/protocolos/ssh"})
    void toda_pagina_do_modulo_leva_as_outras(String rota) {
        given()
                .when().get(rota)
                .then()
                .statusCode(200)
                .body(containsString("href=\"/protocolos\""))
                .body(containsString("href=\"/protocolos/bgp\""))
                .body(containsString("href=\"/protocolos/ssh\""));
    }

    @ParameterizedTest(name = "{0} marca a própria aba como atual no sub-menu")
    @CsvSource({"/protocolos/bgp", "/protocolos/ssh"})
    void abaAtivaEMarcada(String rota) {
        // Regex tolerante à quebra de linha do template: o que importa é que o
        // href da rota visitada carregue a classe is-active, e nenhum outro.
        given().when().get(rota).then().statusCode(200)
                .body(matchesPattern("(?s).*href=\"" + rota + "\"\\s+class=\"protocolo-subnav-item is-active\".*"));
    }

    @Test
    @DisplayName("regressão: a aba Geral não pode ser engolida pelas sub-rotas")
    void catalogoContinuaRespondendoEOferecendoAprofundar() {
        given()
                .when().get("/protocolos")
                .then()
                .statusCode(200)
                .body(containsString("data-grid-table=\"protocolos\""))
                .body(containsString("Aprofundar"));
    }

    @Test
    @DisplayName("slug inexistente responde 404, nunca a página de outro protocolo")
    void slugDesconhecidoNaoResolve() {
        given().when().get("/protocolos/telnet").then().statusCode(404);
        given().when().get("/protocolos/bgp2").then().statusCode(404);
    }

    @Test
    @DisplayName("BGP: o conteúdo operacional chegou ao HTML")
    void conteudoDoBgp() {
        given()
                .when().get("/protocolos/bgp")
                .then()
                .statusCode(200)
                .body(containsString("AS_PATH"))
                .body(containsString("LOCAL_PREF"))
                .body(containsString("Established"))
                .body(containsString("maximum-prefix"))
                .body(containsString("next-hop-self"))
                .body(containsString("Route Reflector"))
                .body(containsString("RPKI"));
    }

    @Test
    @DisplayName("SSH: o conteúdo operacional chegou ao HTML")
    void conteudoDoSsh() {
        given()
                .when().get("/protocolos/ssh")
                .then()
                .statusCode(200)
                .body(containsString("known_hosts"))
                .body(containsString("ed25519"))
                .body(containsString("ProxyJump"))
                .body(containsString("PermitRootLogin"))
                .body(containsString("AuthenticationMethods"))
                .body(containsString("sshd_config"));
    }

    @Test
    @DisplayName("telemetria: a visita ao aprofundamento vira evento no módulo Protocolos")
    void visitaGeraEventoDeTelemetria() {
        given().when().get("/protocolos/bgp").then().statusCode(200);

        Optional<TelemetriaEvent> evento = telemetriaStore.snapshotEventos().stream()
                .filter(e -> "aprofundamento_view".equals(e.evento()))
                .filter(e -> e.fields() != null && "bgp".equals(e.fields().get("protocolo")))
                .findFirst();

        assertTrue(evento.isPresent(),
                "Sem evento aprofundamento_view, o dashboard não distingue quem abriu o catálogo de quem "
                        + "abriu o aprofundamento.");
        assertEquals("protocolos", evento.get().modulo(),
                "Módulo divergente da atribuição de /protocolos/bgp contaria a mesma visita duas vezes.");
    }
}
