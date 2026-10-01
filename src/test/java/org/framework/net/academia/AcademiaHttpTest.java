package org.framework.net.academia;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.framework.net.academia.core.application.PortaoAcademia;
import org.framework.net.academia.core.domain.ports.VerificacaoArranque;
import org.framework.net.telemetria.TelemetriaStore;
import org.framework.net.telemetria.infrastructure.TelemetriaAcademiaAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Academia de pé: páginas, API de eventos, teto do corpo e o isolamento de falha (D12).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> provar pela fronteira HTTP o que o aluno e o resto do site
 * recebem — as páginas abrem com menu e lição certos; o evento de lição desconhecida não entra; o
 * erro de JavaScript não leva dado pessoal nem entra na telemetria comum; e a Academia fechada não
 * derruba nenhuma outra rota.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> contagens distintas e não nulas onde há contagem (a visita
 * aparece na telemetria; o erro aparece no buffer próprio e NÃO na telemetria comum); o estado do
 * portão é restaurado ao fim do teste que o fecha.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção nomeia rota, status e trecho.</p>
 */
@QuarkusTest
@DisplayName("Academia: páginas, eventos e isolamento de falha")
class AcademiaHttpTest {

    @Inject
    PortaoAcademia portao;

    @Inject
    Instance<VerificacaoArranque> verificacoes;

    @Inject
    TelemetriaStore telemetriaStore;

    @Inject
    TelemetriaAcademiaAdapter adaptador;

    @ParameterizedTest(name = "{0} abre com o menu na Academia")
    @ValueSource(strings = {"/academia", "/academia/fundamentos", "/academia/fundamentos/binario",
            "/academia/fundamentos/hexadecimal", "/academia/fundamentos/camadas",
            "/academia/ipv4", "/academia/ipv4/mascara", "/academia/ipv4/subredes"})
    void paginasAbrem(String rota) {
        String html = given().header("Accept", "text/html").when().get(rota)
                .then().statusCode(200).contentType(containsString("text/html"))
                .body(not(containsString("Internal Server Error")))
                .extract().asString();
        assertTrue(html.contains("href=\"/academia\""), "link do menu ausente em " + rota);
        assertTrue(html.matches("(?s).*<a\\s+href=\"/academia\"[^>]*?class=\"aed-nav-link is-active\".*"),
                "Academia não aparece ativa no menu em " + rota);
        assertTrue(html.contains("/academia/core/css/academia.css"), "CSS da Academia ausente em " + rota);
    }

    @ParameterizedTest(name = "lição {0} traz o próprio id, o selo e os scripts da lição")
    @ValueSource(strings = {"fundamentos.binario", "fundamentos.hexadecimal", "fundamentos.camadas",
            "ipv4.mascara", "ipv4.subredes"})
    void licaoTrazIdESelo(String id) {
        String nivel = id.substring(0, id.indexOf('.'));
        String slug = id.substring(id.indexOf('.') + 1);
        given().when().get("/academia/" + nivel + "/" + slug).then().statusCode(200)
                .body(containsString("data-acad-licao=\"" + id + "\""))
                .body(containsString("data-acad-selo=\"" + id + "\""))
                .body(containsString("/academia/" + nivel + "/js/" + slug + ".js"))
                .body(containsString("só neste navegador"));
    }

    @Test
    @DisplayName("lição inexistente é 404 pela página padrão, nunca página pela metade")
    void licaoInexistente() {
        given().header("Accept", "text/html").when().get("/academia/fundamentos/inventada")
                .then().statusCode(404).body(containsString("err-404"));
    }

    @Test
    @DisplayName("visita de lição do catálogo é aceita e vira evento com faixas, nunca o número exato")
    void visitaAceita() {
        // A telemetria do teste é persistida entre execuções: só conta evento nascido AGORA.
        java.time.Instant antes = java.time.Instant.now();
        given().contentType("application/json")
                .body("{\"tipo\":\"visita\",\"licaoId\":\"fundamentos.hexadecimal\",\"segundos\":417,"
                        + "\"interacoes\":12,\"concluiu\":true,\"extra\":\"ignorado\"}")
                .when().post("/academia/api/eventos")
                .then().statusCode(202).body("resultado", equalTo("ACEITO"));

        var evento = telemetriaStore.snapshotEventos().stream()
                .filter(e -> "academia_visita".equals(e.evento()))
                .filter(e -> e.timestamp() != null && !e.timestamp().isBefore(antes))
                .filter(e -> e.fields() != null && "fundamentos.hexadecimal".equals(e.fields().get("licao")))
                .findFirst().orElseThrow(() -> new AssertionError("visita não chegou à telemetria"));
        assertEquals("ATE_10MIN", evento.fields().get("tempo"));
        assertEquals("VARIAS", evento.fields().get("interacoes"));
        assertFalse(evento.fields().containsValue(417L) || evento.fields().containsValue(417),
                "o tempo exato não pode sair");
        assertFalse(evento.fields().containsKey("extra"), "campo fora do esquema não pode entrar");
    }

    @Test
    @DisplayName("lição desconhecida e tipo desconhecido são recusados com 422")
    void recusas() {
        given().contentType("application/json")
                .body("{\"tipo\":\"visita\",\"licaoId\":\"../../etc/passwd\",\"segundos\":1}")
                .when().post("/academia/api/eventos")
                .then().statusCode(422).body("motivo", equalTo("lição desconhecida"));
        given().contentType("application/json")
                .body("{\"tipo\":\"apagar\",\"licaoId\":\"fundamentos.binario\"}")
                .when().post("/academia/api/eventos")
                .then().statusCode(422).body("motivo", equalTo("tipo desconhecido"));
        given().contentType("application/json").body("{}")
                .when().post("/academia/api/eventos")
                .then().statusCode(422).body("motivo", equalTo("tipo ausente"));
    }

    @Test
    @DisplayName("erro de JavaScript chega saneado ao buffer próprio e NÃO entra na telemetria comum")
    void erroJsForaDoDataset() {
        // Marca só de letras (sobrevive ao saneamento) e única por execução: a telemetria do teste é
        // persistida em disco entre execuções, então a busca tem de achar ESTE evento, não um antigo.
        String marca = "sentinela" + java.util.UUID.randomUUID().toString().replaceAll("[^a-f]", "")
                .replace('a', 'q').replace('b', 'w');
        given().contentType("application/json")
                .body("{\"tipo\":\"erro\",\"licaoId\":\"fundamentos.camadas\",\"tipoErro\":\"erro\","
                        + "\"mensagem\":\"" + marca + " 8.8.8.8 a@b.com 123.456.789-09\"}")
                .when().post("/academia/api/eventos")
                .then().statusCode(202).body("resultado", equalTo("ACEITO"));

        var guardado = adaptador.errosGuardados().stream()
                .filter(e -> e.mensagem().startsWith(marca))
                .findFirst().orElseThrow(() -> new AssertionError("erro não chegou ao buffer próprio"));
        assertFalse(guardado.mensagem().contains("8.8.8.8"));
        assertFalse(guardado.mensagem().contains("a@b.com"));
        assertFalse(guardado.mensagem().contains("123.456.789-09"));

        boolean naTelemetriaComum = telemetriaStore.snapshotEventos().stream()
                .anyMatch(e -> String.valueOf(e.fields()).contains(marca));
        assertFalse(naTelemetriaComum, "erro de JS não pode entrar na telemetria que alimenta o dataset público");
    }

    @Test
    @DisplayName("corpo acima de 8 KB é recusado com 413 antes de chegar à aplicação; 8 KB passa do teto")
    void tetoDoCorpo() {
        String preenchimento = "x".repeat(8200);
        given().contentType("application/json")
                .body("{\"tipo\":\"erro\",\"licaoId\":\"fundamentos.binario\",\"mensagem\":\"" + preenchimento + "\"}")
                .when().post("/academia/api/eventos")
                .then().statusCode(413).body(containsString("8 KB"));

        String cabe = "x".repeat(7900);
        given().contentType("application/json")
                .body("{\"tipo\":\"erro\",\"licaoId\":\"fundamentos.binario\",\"mensagem\":\"" + cabe + "\"}")
                .when().post("/academia/api/eventos")
                .then().statusCode(202);
    }

    /**
     * D12: com a Academia fechada, as rotas dela respondem 503 e as demais seguem iguais. O estado
     * é restaurado no fim, pela mesma decisão do arranque.
     */
    @Test
    @DisplayName("Academia degradada responde 503 nas rotas dela e o resto do site continua 200")
    void falhaIsolada() {
        try {
            portao.decidir(true, List.of(new VerificacaoArranque() {
                @Override
                public String nome() {
                    return "teste";
                }

                @Override
                public void verificar() {
                    throw new IllegalStateException("falha simulada");
                }
            }));
            given().header("Accept", "text/html").when().get("/academia/fundamentos/binario")
                    .then().statusCode(503).body(containsString("err-503"));
            given().contentType("application/json")
                    .body("{\"tipo\":\"visita\",\"licaoId\":\"fundamentos.binario\"}")
                    .when().post("/academia/api/eventos").then().statusCode(503);
            for (String rota : List.of("/", "/calculadora", "/laboratorios", "/protocolos")) {
                given().header("Accept", "text/html").when().get(rota).then().statusCode(200);
            }
        } finally {
            portao.decidir(true, verificacoes);
        }
        given().when().get("/academia").then().statusCode(200);
    }
}
