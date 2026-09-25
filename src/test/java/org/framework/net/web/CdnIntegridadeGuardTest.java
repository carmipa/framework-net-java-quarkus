package org.framework.net.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F20: script/CSS de CDN roda com acesso ao cookie do token CSRF da página. Sem {@code integrity}, um
 * CDN comprometido injeta código; com versão flutuante ({@code mermaid@11}), uma release nova muda o
 * comportamento sem ninguém saber. A guarda antiga só olhava a rota "/". Esta varre TODOS os templates.
 *
 * <p>Isentos, com motivo: {@code translate.google.com/translate_a/element.js} (servido dinamicamente,
 * SRI impossível) e o CSS de fontes de TEXTO do Google Fonts (varia por navegador; sem ele a página cai
 * na fonte do sistema — a fonte de ÍCONE é local, ver I18nIconesGuardTest).</p>
 */
class CdnIntegridadeGuardTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    private static final Pattern EXTERNO = Pattern.compile(
            "<(script|link)\\b[^>]*?(?:src|href)=\"(https?://[^\"]+)\"[^>]*>");
    private static final Pattern VERSAO_FLUTUANTE = Pattern.compile("@\\d+(?:\\.\\d+)?/");

    static List<String> violacoes(String texto) {
        List<String> out = new ArrayList<>();
        Matcher m = EXTERNO.matcher(texto);
        while (m.find()) {
            String tag = m.group();
            String url = m.group(2);
            boolean stylesheetOuScript = m.group(1).equals("script") || tag.contains("stylesheet");
            if (!stylesheetOuScript || url.contains("translate.google.com/translate_a/element.js")
                    || url.startsWith("https://fonts.googleapis.com/css2?family=Space")) {
                continue;
            }
            if (!tag.contains("integrity=\"sha384-")) {
                out.add("sem integrity: " + url);
            }
            if (VERSAO_FLUTUANTE.matcher(url).find()) {
                out.add("versão flutuante: " + url);
            }
        }
        return out;
    }

    @Test
    void instrumentoDiscriminaDoenteDeLegitimo() {
        assertEquals(2, violacoes("<script src=\"https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js\"></script>").size());
        assertEquals(0, violacoes("<script src=\"https://cdn.jsdelivr.net/npm/mermaid@11.17.2/dist/mermaid.min.js\" "
                + "integrity=\"sha384-abc\" crossorigin=\"anonymous\"></script>").size());
        assertEquals(0, violacoes("<script src=\"https://translate.google.com/translate_a/element.js?cb=x\"></script>").size());
    }

    @Test
    void todoAssetDeCdnTemVersaoExataEIntegrity() throws IOException {
        List<String> todas = new ArrayList<>();
        int externos = 0;
        try (Stream<Path> t = Files.walk(TEMPLATES)) {
            for (Path p : t.filter(x -> x.toString().endsWith(".html")).toList()) {
                String s = Files.readString(p, StandardCharsets.UTF_8);
                Matcher m = EXTERNO.matcher(s);
                while (m.find()) {
                    externos++;
                }
                for (String v : violacoes(s)) {
                    todas.add(p + ": " + v);
                }
            }
        }
        assertTrue(externos > 20, "instrumento cego: só " + externos + " assets externos encontrados");
        assertEquals(List.of(), todas);
    }
}
