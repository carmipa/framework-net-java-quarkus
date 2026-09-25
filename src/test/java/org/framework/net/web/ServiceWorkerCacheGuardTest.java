package org.framework.net.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F30: o service worker cacheia toda resposta GET 200 — e o Cache Storage sobrevive no disco do PC
 * de laboratório. A lista de exclusão só conhecia '/export' na raiz; exports e APIs com dado pessoal
 * que nasceram depois (/ipv6/export, /calculadora/export, /api/informacoes/geo, /localizacao/api/*)
 * entravam no cache. Esta guarda lê o sw.js de verdade e aplica a mesma regra que ele aplica.
 */
class ServiceWorkerCacheGuardTest {

    private static final Path SW = Path.of("src/main/resources/META-INF/resources/sw.js");

    private static List<String> array(String js, String nome) {
        Matcher m = Pattern.compile("const " + nome + "\\s*=\\s*\\[([^\\]]*)\\]").matcher(js);
        List<String> itens = new ArrayList<>();
        if (!m.find()) {
            return itens;
        }
        Matcher s = Pattern.compile("'([^']*)'").matcher(m.group(1));
        while (s.find()) {
            itens.add(s.group(1));
        }
        return itens;
    }

    private static boolean protegida(String caminho, List<String> prefixos, List<String> trechos) {
        return prefixos.stream().anyMatch(caminho::startsWith) || trechos.stream().anyMatch(caminho::contains);
    }

    @Test
    void rotasSensiveisNuncaEntramNoCache() throws Exception {
        String js = Files.readString(SW, StandardCharsets.UTF_8);
        List<String> prefixos = array(js, "NUNCA_CACHEAR");
        List<String> trechos = array(js, "NUNCA_CACHEAR_TRECHOS");
        assertFalse(prefixos.isEmpty(), "instrumento cego: NUNCA_CACHEAR não encontrado no sw.js");

        for (String sensivel : List.of("/telemetria", "/admin/x", "/history", "/export/pdf",
                "/ipv6/export/pdf", "/calculadora/export/divisao.csv", "/api/informacoes/geo",
                "/localizacao/api/inspecao", "/localizacao/api/ip")) {
            assertTrue(protegida(sensivel, prefixos, trechos), "entraria no cache do navegador: " + sensivel);
        }
        // Controle legítimo (A1): páginas e estáticos continuam cacheáveis (é o que faz o offline existir).
        for (String publico : List.of("/", "/protocolos", "/web/css/app.css", "/ipv6/analise")) {
            assertFalse(protegida(publico, prefixos, trechos), "não deveria ser excluído: " + publico);
        }
    }
}
