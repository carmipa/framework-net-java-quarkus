package org.framework.net.arquitetura;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda das regras de arquitetura do projeto.
 *
 * <p><b>Propósito de negócio:</b> o framework cresce por módulos (Análise,
 * Resolução, Calculadora, Tráfego…) e cada novo módulo tende a copiar o que
 * encontra pela frente. Este teste transforma as convenções acordadas em
 * verificação automática, para que uma violação apareça no build e não seis
 * meses depois, quando já custou refatoração.</p>
 *
 * <p><b>Invariantes do domínio:</b> quatro regras, todas verdadeiras no código
 * atual — (1) {@code domain} não conhece camadas de fora nem HTTP/template;
 * (2) {@code application} não conhece {@code presentation}; (3) módulos de
 * negócio não se importam entre si, salvo exceções explicitamente registradas
 * aqui; (4) classe anotada com {@code @Path} mora em {@code presentation}. A
 * leitura é feita sobre o código-fonte, sem dependência externa de análise.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> o teste falha listando arquivo,
 * import e regra violada. Se o diretório de fontes não for encontrado (execução
 * fora da raiz do projeto), os testes são ignorados via
 * {@code Assumptions} em vez de falharem por motivo errado.</p>
 */
@DisplayName("Arquitetura: camadas e acoplamento entre módulos")
class ArquiteturaCamadasTest {

    private static final Path RAIZ_FONTES = Path.of("src", "main", "java", "org", "framework", "net");

    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]+)\\s*;");

    /** Pacotes transversais que qualquer módulo pode usar. */
    private static final Set<String> TRANSVERSAIS = Set.of("shared", "telemetria", "security", "web");

    /**
     * Acoplamentos entre módulos aceitos conscientemente.
     *
     * <p>A Localização reaproveita o GeoIP que nasceu na Análise Didática quando
     * a aba "Região Geo" migrou de página. É reúso deliberado de infraestrutura,
     * não vazamento de camada — está aqui para que apareça em revisão, e para
     * que qualquer acoplamento NOVO quebre o build.</p>
     */
    private static final Map<String, Set<String>> ACOPLAMENTOS_ACEITOS = Map.of(
            "localizacao", Set.of("analiseDidatica"));

    /** Tipos de camada que o domínio jamais pode enxergar. */
    private static final Set<String> CAMADAS_PROIBIDAS_NO_DOMINIO =
            Set.of("application", "presentation", "infrastructure");

    /** Tecnologias de entrega que não podem entrar no domínio. */
    private static final List<String> TECNOLOGIAS_PROIBIDAS_NO_DOMINIO =
            List.of("jakarta.ws.rs", "io.quarkus.qute");

    @Test
    @DisplayName("domain não importa application, presentation, infrastructure, JAX-RS nem Qute")
    void dominioNaoConheceCamadasDeFora() {
        List<String> violacoes = new ArrayList<>();

        for (ArquivoJava arquivo : fontes()) {
            if (!"domain".equals(arquivo.camada())) {
                continue;
            }
            for (String imp : arquivo.imports()) {
                for (String tecnologia : TECNOLOGIAS_PROIBIDAS_NO_DOMINIO) {
                    if (imp.startsWith(tecnologia)) {
                        violacoes.add(arquivo.caminho() + " importa " + imp
                                + " — domínio não pode depender de tecnologia de entrega.");
                    }
                }
                if (!imp.startsWith("org.framework.net.")) {
                    continue;
                }
                String camadaImportada = camadaDoImport(imp);
                if (CAMADAS_PROIBIDAS_NO_DOMINIO.contains(camadaImportada)) {
                    violacoes.add(arquivo.caminho() + " importa " + imp
                            + " — domínio não pode depender da camada " + camadaImportada + ".");
                }
            }
        }

        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "O domínio precisa ser a camada mais interna: nada de HTTP, template ou serviço de aplicação.",
                violacoes));
    }

    @Test
    @DisplayName("application não importa presentation")
    void aplicacaoNaoConhecePresentation() {
        List<String> violacoes = new ArrayList<>();

        for (ArquivoJava arquivo : fontes()) {
            if (!"application".equals(arquivo.camada())) {
                continue;
            }
            for (String imp : arquivo.imports()) {
                if (imp.startsWith("org.framework.net.") && "presentation".equals(camadaDoImport(imp))) {
                    violacoes.add(arquivo.caminho() + " importa " + imp
                            + " — o serviço não pode depender do resource que o expõe.");
                }
            }
        }

        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "A aplicação é chamada pela apresentação, nunca o contrário.", violacoes));
    }

    @Test
    @DisplayName("módulos de negócio não se importam entre si, salvo exceções registradas")
    void modulosDeNegocioNaoSeAcoplam() {
        List<String> violacoes = new ArrayList<>();

        for (ArquivoJava arquivo : fontes()) {
            String modulo = arquivo.modulo();
            if (TRANSVERSAIS.contains(modulo)) {
                continue;
            }
            Set<String> aceitos = ACOPLAMENTOS_ACEITOS.getOrDefault(modulo, Set.of());
            for (String imp : arquivo.imports()) {
                if (!imp.startsWith("org.framework.net.")) {
                    continue;
                }
                String moduloImportado = moduloDoImport(imp);
                if (moduloImportado.isEmpty()
                        || moduloImportado.equals(modulo)
                        || TRANSVERSAIS.contains(moduloImportado)
                        || aceitos.contains(moduloImportado)) {
                    continue;
                }
                violacoes.add(arquivo.caminho() + " importa " + imp
                        + " — módulo \"" + modulo + "\" não deve depender de \"" + moduloImportado + "\". "
                        + "Extraia para shared ou registre o acoplamento em ACOPLAMENTOS_ACEITOS.");
            }
        }

        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "Cada módulo é um pacote autocontido; o que for comum vive em shared.", violacoes));
    }

    @Test
    @DisplayName("classe com @Path mora no pacote presentation")
    void resourcesFicamEmPresentation() {
        List<String> violacoes = new ArrayList<>();

        for (ArquivoJava arquivo : fontes()) {
            boolean ehResource = arquivo.conteudo().contains("@Path(")
                    && arquivo.conteudo().contains("import jakarta.ws.rs.Path;");
            if (ehResource && !"presentation".equals(arquivo.camada())) {
                violacoes.add(arquivo.caminho() + " expõe rota HTTP fora do pacote presentation.");
            }
        }

        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "Rota HTTP é apresentação: mantenha os resources em <modulo>/presentation.", violacoes));
    }

    /**
     * Lê todos os fontes do projeto uma única vez por teste.
     *
     * <p><b>Comportamento em caso de falha:</b> se o diretório não existir (teste
     * rodando fora da raiz do projeto), os testes são ignorados em vez de
     * falharem — falha por caminho errado não é violação de arquitetura.</p>
     */
    private List<ArquivoJava> fontes() {
        Assumptions.assumeTrue(Files.isDirectory(RAIZ_FONTES),
                "Diretório de fontes não encontrado a partir de " + Path.of("").toAbsolutePath());

        try (Stream<Path> caminhos = Files.walk(RAIZ_FONTES)) {
            return caminhos
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(p -> lerArquivo(p))
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao varrer os fontes do projeto", ex);
        }
    }

    private ArquivoJava lerArquivo(Path caminho) {
        try {
            String conteudo = Files.readString(caminho);
            List<String> imports = new ArrayList<>();
            for (String linha : conteudo.split("\\R")) {
                Matcher matcher = IMPORT.matcher(linha);
                if (matcher.find()) {
                    imports.add(matcher.group(2));
                }
            }
            Path relativo = RAIZ_FONTES.relativize(caminho);
            String modulo = relativo.getNameCount() > 1 ? relativo.getName(0).toString() : "";
            String camada = relativo.getNameCount() > 2 ? relativo.getName(1).toString() : "";
            return new ArquivoJava(
                    caminho.toString().replace('\\', '/'),
                    modulo,
                    camada.toLowerCase(Locale.ROOT),
                    imports,
                    conteudo);
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao ler " + caminho, ex);
        }
    }

    /** Segmento após {@code org.framework.net.} — o módulo de origem do import. */
    private String moduloDoImport(String imp) {
        String resto = imp.substring("org.framework.net.".length());
        int ponto = resto.indexOf('.');
        return ponto < 0 ? "" : resto.substring(0, ponto);
    }

    /** Segmento seguinte ao módulo — a camada do import ({@code domain}, {@code application}…). */
    private String camadaDoImport(String imp) {
        String resto = imp.substring("org.framework.net.".length());
        String[] partes = resto.split("\\.");
        return partes.length > 1 ? partes[1].toLowerCase(Locale.ROOT) : "";
    }

    private String mensagem(String regra, List<String> violacoes) {
        return regra + "\nViolações (" + violacoes.size() + "):\n  - " + String.join("\n  - ", violacoes);
    }

    private record ArquivoJava(
            String caminho, String modulo, String camada, List<String> imports, String conteudo) {
    }
}
