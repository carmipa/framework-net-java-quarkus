package org.framework.net.web.support;

import jakarta.enterprise.context.ApplicationScoped;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class MarkdownRenderer {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern UL = Pattern.compile("^- (.*)$");
    private static final Pattern OL = Pattern.compile("^(\\d+)\\.\\s+(.*)$");
    private static final Pattern CODE_INLINE = Pattern.compile("`([^`]+)`");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^\\s)]+)\\)");

    public RenderResult render(String markdown) {
        String texto = normalizarLinhasBadges(markdown);
        String[] linhas = texto.split("\n", -1);
        List<String> blocos = new ArrayList<>();
        List<Map<String, Object>> secoes = new ArrayList<>();
        Set<String> slugs = new HashSet<>();
        boolean emCode = false;
        String lang = "";
        List<String> codeBuffer = new ArrayList<>();
        boolean emUl = false;
        boolean emOl = false;

        for (String linha : linhas) {
            String stripped = linha.strip();
            if (stripped.startsWith("```")) {
                fecharListas(blocos, emUl, emOl);
                emUl = false;
                emOl = false;
                if (!emCode) {
                    emCode = true;
                    lang = stripped.substring(3).strip();
                    codeBuffer.clear();
                } else {
                    String code = String.join("\n", codeBuffer);
                    if ("mermaid".equalsIgnoreCase(lang)) {
                        blocos.add("<div class=\"mermaid\">" + escape(code) + "</div>");
                    } else {
                        blocos.add("<pre class='doc-pre'><code>" + escape(code) + "</code></pre>");
                    }
                    emCode = false;
                    lang = "";
                }
                continue;
            }
            if (emCode) {
                codeBuffer.add(linha);
                continue;
            }
            if (stripped.isEmpty()) {
                fecharListas(blocos, emUl, emOl);
                emUl = false;
                emOl = false;
                continue;
            }
            if (stripped.startsWith("<") && stripped.endsWith(">")) {
                fecharListas(blocos, emUl, emOl);
                emUl = false;
                emOl = false;
                blocos.add(linha);
                continue;
            }
            Matcher head = HEADING.matcher(stripped);
            if (head.matches()) {
                fecharListas(blocos, emUl, emOl);
                emUl = false;
                emOl = false;
                int nivel = head.group(1).length();
                String titulo = head.group(2).strip();
                String slug = slugify(titulo, slugs);
                Map<String, Object> secao = new LinkedHashMap<>();
                secao.put("id", slug);
                secao.put("titulo", titulo);
                secao.put("nivel", nivel);
                secoes.add(secao);
                blocos.add("<h" + nivel + " id=\"" + slug + "\">" + inline(titulo) + "</h" + nivel + ">");
                continue;
            }
            Matcher ol = OL.matcher(stripped);
            if (ol.matches()) {
                if (emUl) {
                    blocos.add("</ul>");
                    emUl = false;
                }
                if (!emOl) {
                    blocos.add("<ol>");
                    emOl = true;
                }
                blocos.add("<li>" + inline(ol.group(2)) + "</li>");
                continue;
            }
            Matcher ul = UL.matcher(stripped);
            if (ul.matches()) {
                if (emOl) {
                    blocos.add("</ol>");
                    emOl = false;
                }
                if (!emUl) {
                    blocos.add("<ul>");
                    emUl = true;
                }
                blocos.add("<li>" + inline(ul.group(1)) + "</li>");
                continue;
            }
            fecharListas(blocos, emUl, emOl);
            emUl = false;
            emOl = false;
            blocos.add("<p>" + inline(stripped) + "</p>");
        }
        if (emUl) blocos.add("</ul>");
        if (emOl) blocos.add("</ol>");
        return new RenderResult(String.join("\n", blocos), secoes);
    }

    private static void fecharListas(List<String> blocos, boolean emUl, boolean emOl) {
        if (emUl) blocos.add("</ul>");
        if (emOl) blocos.add("</ol>");
    }

    private String inline(String texto) {
        String escaped = escape(texto);
        escaped = LINK.matcher(escaped).replaceAll("<a href=\"$2\" target=\"_blank\" rel=\"noopener noreferrer\">$1</a>");
        escaped = CODE_INLINE.matcher(escaped).replaceAll("<code>$1</code>");
        return escaped;
    }

    private static String escape(String texto) {
        return texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String slugify(String titulo, Set<String> slugs) {
        String normalized = Normalizer.normalize(titulo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-zA-Z0-9\\s-]", "")
                .strip()
                .toLowerCase()
                .replaceAll("[\\s-]+", "-");
        if (normalized.isBlank()) {
            normalized = "secao";
        }
        String slug = normalized;
        int idx = 2;
        while (slugs.contains(slug)) {
            slug = normalized + "-" + idx++;
        }
        slugs.add(slug);
        return slug;
    }

    private static String normalizarLinhasBadges(String texto) {
        Pattern badge = Pattern.compile("^\\[!\\[[^\\]]*\\]\\(https?://img\\.shields\\.io/[^\\)]*\\)\\]\\([^)]+\\)\\s*$");
        String[] linhas = texto.split("\n", -1);
        List<String> saida = new ArrayList<>();
        List<String> buffer = new ArrayList<>();
        for (String linha : linhas) {
            String stripped = linha.strip();
            if (badge.matcher(stripped).matches()) {
                buffer.add(stripped);
                continue;
            }
            if (!buffer.isEmpty()) {
                saida.add(String.join(" ", buffer));
                buffer.clear();
            }
            saida.add(linha);
        }
        if (!buffer.isEmpty()) {
            saida.add(String.join(" ", buffer));
        }
        return String.join("\n", saida);
    }

    public record RenderResult(String html, List<Map<String, Object>> secoes) {
    }
}
