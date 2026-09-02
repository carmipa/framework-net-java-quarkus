package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Política pública para robôs de busca — o {@code /robots.txt} do site.
 *
 * <p><b>Propósito de negócio:</b> o site precisa ser encontrado pelas páginas
 * didáticas e, ao mesmo tempo, manter robô fora das rotas que custam caro ou
 * não têm valor de busca: {@code /informacoes} dispara consulta geográfica
 * externa a cada GET (ip-api, 45 req/min), {@code /export} gera PDF sob
 * demanda, {@code /history} devolve o histórico com o IP de quem consultou e as
 * rotas de API devolvem JSON. Sem o arquivo, tudo isso é rastreável.</p>
 *
 * <p><b>Invariantes do domínio:</b> (1) o arquivo responde 200 como
 * {@code text/plain}; (2) o robots.txt <b>não soma grupos</b> — um robô obedece
 * só ao bloco mais específico que casa com ele, então cada grupo permissivo
 * repete a lista inteira de {@code Disallow}; (3) toda rota de API declarada por
 * {@code @Path} no código está fechada em todos esses grupos; (4) as páginas
 * didáticas continuam abertas — bloqueio a mais tira o site do índice, que é o
 * oposto do objetivo.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> o teste falha nomeando o grupo e a
 * rota que ficou de fora. A varredura dos fontes falha fechada: se não achar
 * nenhuma rota de API, reprova em vez de aprovar por cegueira — "não achei" e
 * "não tinha como achar" não podem parecer a mesma coisa. Fora da raiz do
 * projeto a varredura é ignorada via {@code Assumptions}, nunca aprovada.</p>
 */
@QuarkusTest
@DisplayName("robots.txt: política pública para robôs de busca")
class RobotsTxtHttpTest {

    private static final Path RAIZ_FONTES = Path.of("src", "main", "java", "org", "framework", "net");

    private static final Pattern ANOTACAO_PATH = Pattern.compile("@Path\\(\"([^\"]*)\"\\)");

    /** Grupos que liberam a navegação: cada um precisa repetir a lista inteira. */
    private static final List<String> GRUPOS_PERMISSIVOS =
            List.of("googlebot", "bingbot", "duckduckbot", "*");

    /** Rotas que nenhum robô deve rastrear, com exemplos de subcaminho real. */
    private static final List<String> ROTAS_FECHADAS = List.of(
            "/admin", "/admin/login", "/login", "/login/chave",
            "/telemetria", "/telemetria/api/exportar",
            "/history", "/history/catalog",
            "/export/json", "/export/pdf",
            "/informacoes", "/mascara-referencia", "/health",
            "/api/informacoes/geo");

    /** Páginas didáticas que existem para ser encontradas. */
    private static final List<String> PAGINAS_ABERTAS = List.of(
            "/", "/analise", "/calculadora", "/portas", "/protocolos",
            "/protocolos/bgp", "/protocolos/ssh", "/resolucao-problemas",
            "/localizacao", "/trafego", "/seguranca", "/diagnostico",
            "/documentacao", "/sobre");

    @Test
    @DisplayName("é servido na raiz, com 200 e text/plain")
    void servidoNaRaizComoTextoSimples() {
        // Precisa estar na raiz: robots.txt em subpasta não vale para o site.
        // O tipo importa — o manifest já foi descartado em silêncio neste projeto
        // por ser entregue sem Content-Type.
        given()
                .when().get("/robots.txt")
                .then()
                .statusCode(200)
                .contentType(containsString("text/plain"))
                .body(containsString("User-agent:"));
    }

    @Test
    @DisplayName("todo grupo permissivo repete a lista inteira — robots.txt não soma grupos")
    void cadaGrupoPermissivoFechaTodasAsRotas() {
        Map<String, List<String>> grupos = grupos();
        List<String> faltando = new ArrayList<>();

        for (String agente : GRUPOS_PERMISSIVOS) {
            List<String> regras = grupos.get(agente);
            assertTrue(regras != null, "O robots.txt não declara grupo para \"" + agente + "\".");
            for (String rota : ROTAS_FECHADAS) {
                if (!bloqueado(regras, rota)) {
                    faltando.add(agente + " não fecha " + rota);
                }
            }
        }

        assertTrue(faltando.isEmpty(), () -> "Um robô obedece só ao bloco mais específico que casa "
                + "com o nome dele: o que faltar no grupo dele fica liberado.\n  - "
                + String.join("\n  - ", faltando));
    }

    @Test
    @DisplayName("guarda: rota de API nova no código precisa nascer fechada aqui")
    void todaRotaDeApiDeclaradaNoCodigoEstaFechada() {
        List<String> rotasApi = rotasApiDeclaradasNoCodigo();

        // Falha fechada: varredura que não acha nada não é aprovação. Guarda verde
        // e cega, varrendo diretório vazio, já custou meses neste método.
        assertFalse(rotasApi.isEmpty(),
                "A varredura dos fontes não encontrou nenhuma rota de API — instrumento cego, "
                        + "não sistema limpo. Confira o padrão de leitura de @Path.");

        Map<String, List<String>> grupos = grupos();
        List<String> faltando = new ArrayList<>();
        for (String agente : GRUPOS_PERMISSIVOS) {
            List<String> regras = grupos.getOrDefault(agente, List.of());
            for (String rota : rotasApi) {
                if (!bloqueado(regras, rota)) {
                    faltando.add(agente + " não fecha " + rota);
                }
            }
        }

        assertTrue(faltando.isEmpty(), () -> "Rota de API declarada no código está aberta para robô. "
                + "Acrescente o prefixo em TODOS os grupos permissivos do robots.txt.\n  - "
                + String.join("\n  - ", faltando));
    }

    @Test
    @DisplayName("controle: as páginas didáticas continuam abertas")
    void paginasDidaticasContinuamAbertas() {
        // Este teste é o caso-controle dos outros: prova que a leitura do arquivo
        // sabe dizer NÃO. Sem ele, um "Disallow: /" tiraria o site do índice e
        // todos os demais testes continuariam verdes.
        Map<String, List<String>> grupos = grupos();
        List<String> excessos = new ArrayList<>();

        for (String agente : GRUPOS_PERMISSIVOS) {
            List<String> regras = grupos.getOrDefault(agente, List.of());
            for (String pagina : PAGINAS_ABERTAS) {
                if (bloqueado(regras, pagina)) {
                    excessos.add(agente + " bloqueia " + pagina);
                }
            }
        }

        assertTrue(excessos.isEmpty(), () -> "Bloqueio a mais tira o site do índice, "
                + "que é o oposto do objetivo do arquivo.\n  - " + String.join("\n  - ", excessos));
    }

    @Test
    @DisplayName("coletor de treinamento de modelo e robô de SEO ficam fora do site inteiro")
    void coletoresEmMassaBloqueados() {
        Map<String, List<String>> grupos = grupos();
        List<String> abertos = new ArrayList<>();

        for (String agente : List.of("gptbot", "ccbot", "claudebot", "anthropic-ai",
                "google-extended", "perplexitybot", "bytespider", "ahrefsbot", "semrushbot")) {
            List<String> regras = grupos.get(agente);
            if (regras == null) {
                abertos.add(agente + " não está no arquivo");
            } else if (!bloqueado(regras, "/analise")) {
                abertos.add(agente + " não está fechado no site inteiro");
            }
        }

        assertTrue(abertos.isEmpty(), () -> String.join("\n  - ", abertos));
    }

    // --- leitura do arquivo -------------------------------------------------

    private Map<String, List<String>> grupos() {
        return gruposDe(given().when().get("/robots.txt").then().statusCode(200).extract().asString());
    }

    /**
     * Lê o arquivo como um robô lê: acumula os {@code User-agent:} consecutivos e
     * anexa a eles as regras que vierem em seguida; um {@code User-agent:} depois
     * de uma regra abre grupo novo.
     */
    private static Map<String, List<String>> gruposDe(String corpo) {
        Map<String, List<String>> grupos = new LinkedHashMap<>();
        List<String> agentes = new ArrayList<>();
        boolean lendoRegras = false;

        for (String bruta : corpo.split("\\R")) {
            String linha = bruta;
            int comentario = linha.indexOf(35); // '#'
            if (comentario >= 0) {
                linha = linha.substring(0, comentario);
            }
            linha = linha.trim();
            int separador = linha.indexOf(58); // ':'
            if (linha.isEmpty() || separador < 0) {
                continue;
            }
            String campo = linha.substring(0, separador).trim().toLowerCase(Locale.ROOT);
            String valor = linha.substring(separador + 1).trim();

            if ("user-agent".equals(campo)) {
                if (lendoRegras) {
                    agentes = new ArrayList<>();
                    lendoRegras = false;
                }
                String agente = valor.toLowerCase(Locale.ROOT);
                agentes.add(agente);
                grupos.computeIfAbsent(agente, chave -> new ArrayList<>());
                continue;
            }

            lendoRegras = true;
            if ("disallow".equals(campo) && !valor.isEmpty()) {
                for (String agente : agentes) {
                    grupos.get(agente).add(valor);
                }
            }
        }
        return grupos;
    }

    /** Casamento por prefixo, que é como o robots.txt decide. */
    private static boolean bloqueado(List<String> disallows, String caminho) {
        return disallows.stream().anyMatch(caminho::startsWith);
    }

    // --- varredura dos fontes ----------------------------------------------

    /**
     * Reconstrói as rotas de API a partir dos {@code @Path} do código: o primeiro
     * do arquivo é o da classe, os seguintes são dos métodos.
     */
    private static List<String> rotasApiDeclaradasNoCodigo() {
        Assumptions.assumeTrue(Files.isDirectory(RAIZ_FONTES),
                "Diretório de fontes não encontrado a partir de " + Path.of("").toAbsolutePath());

        List<String> rotas = new ArrayList<>();
        try (Stream<Path> caminhos = Files.walk(RAIZ_FONTES)) {
            for (Path arquivo : caminhos.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java")).toList()) {
                String conteudo = Files.readString(arquivo);
                Matcher matcher = ANOTACAO_PATH.matcher(conteudo);
                String base = null;
                while (matcher.find()) {
                    String valor = matcher.group(1);
                    if (base == null) {
                        base = valor;
                        rotas.add(normalizar(valor));
                    } else {
                        rotas.add(normalizar(base + valor));
                    }
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao varrer os fontes do projeto", ex);
        }

        return rotas.stream()
                .filter(rota -> rota.contains("/api/") || rota.endsWith("/api"))
                .distinct()
                .sorted()
                .toList();
    }

    private static String normalizar(String caminho) {
        String valor = caminho.startsWith("/") ? caminho : "/" + caminho;
        return valor.replace("//", "/");
    }
}
