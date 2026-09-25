package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04: o limite era por (IP, caminho bruto). Rotas {@code /{slug}} davam um balde novo por slug
 * (limite inexistente e mapa crescendo com a escolha do atacante), o total por IP era 120 × número de
 * rotas, e rotas caras tinham ficado fora do "pesado". Os testes ligam o limitador com tetos pequenos
 * e mandam o DOBRO do teto + 2 — mesmo que a janela de um minuto vire no meio, sai ao menos um 429.
 */
@QuarkusTest
@TestProfile(RateLimitHttpTest.Perfil.class)
class RateLimitHttpTest {

    static final int POR_ROTA = 5;
    static final int PESADO = 2;
    static final int GLOBAL = 8;

    public static class Perfil implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "framework.security.rate-limit-enabled", "true",
                    "framework.security.rate-limit-per-minute", String.valueOf(POR_ROTA),
                    "framework.security.rate-limit-heavy-per-minute", String.valueOf(PESADO),
                    "framework.security.rate-limit-global-per-minute", String.valueOf(GLOBAL));
        }
    }

    @Test
    void slugAleatorioNaoGanhaBaldeNovo() {
        int limitados = 0;
        for (int i = 0; i < POR_ROTA * 2 + 2; i++) {
            int st = given().when().get("/protocolos/" + UUID.randomUUID()).then().extract().statusCode();
            if (st == 429) {
                limitados++;
            }
        }
        assertTrue(limitados > 0, "slugs distintos não podem burlar o limite por rota");
    }

    @Test
    void tetoGlobalPorClienteSomaTodasAsRotas() {
        String[] rotas = {"/", "/portas", "/protocolos", "/camadas", "/wifi", "/criptografia",
                "/ferramentas", "/certificados", "/sobre", "/documentacao"};
        int limitados = 0;
        for (int rep = 0; rep < 2; rep++) {
            for (String r : rotas) {
                if (given().when().get(r).then().extract().statusCode() == 429) {
                    limitados++;
                }
            }
        }
        assertTrue(limitados > 0, "20 requisições em 10 rotas com teto global " + GLOBAL + " precisam gerar 429");
    }

    @Test
    void historicoDeCatalogoEhRotaPesada() {
        // Exatamente POR_ROTA requisições (A1): como rota comum, nenhuma daria 429; como pesada
        // (teto 2), dá ao menos um 429 mesmo com uma virada de janela (2 + 2 < 5).
        int limitados = 0;
        for (int i = 0; i < POR_ROTA; i++) {
            int st = given().contentType("application/json")
                    .body("{\"modo\":\"portas\",\"entrada\":\"443\"}")
                    .when().post("/history/catalog").then().extract().statusCode();
            if (st == 429) {
                limitados++;
            }
        }
        assertTrue(limitados > 0, "POST /history/catalog regrava o arquivo: tem de contar como pesado");
    }
}
