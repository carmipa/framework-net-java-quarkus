package org.framework.net.protocolos.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Registro das páginas de aprofundamento por protocolo.
 *
 * <p><b>Propósito de negócio:</b> o catálogo (aba Geral) responde "quais
 * protocolos existem e como se comparam"; ele não responde "me explica BGP".
 * Cada aprofundamento é uma página didática dedicada a um único protocolo, e
 * este registro é a fonte única de quais delas existem — alimenta ao mesmo
 * tempo o sub-menu, a rota, o CSS carregado e os testes. Protocolo novo entra
 * com uma linha aqui, um template e um CSS.</p>
 *
 * <p><b>Invariantes do domínio:</b> o {@code slug} é único, minúsculo e é o
 * último segmento da rota ({@code /protocolos/<slug>}); todo item apresentado
 * ao usuário tem template e CSS próprios existentes em disco — a guarda
 * {@code AprofundamentoProtocoloTest} reprova o build quando um dos dois falta,
 * porque item de menu apontando para página inexistente é erro 500 na cara de
 * quem clicou. Os nomes em {@code nomesNoCatalogo} precisam existir tal e qual
 * em {@code protocolos/catalogo.json}: é por eles que a linha do DataGrid ganha
 * o botão "Aprofundar", e um nome desatualizado faria o botão sumir em silêncio
 * — a mesma guarda cobre isso.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> {@link #porSlug(String)} e
 * {@link #porNomeDoCatalogo(String)} devolvem {@link Optional#empty()} para
 * entrada desconhecida, nula ou em branco — nunca um item arbitrário. Quem
 * chama traduz o vazio em 404 ou em "sem aprofundamento", jamais em página de
 * outro protocolo.</p>
 */
public record AprofundamentoProtocolo(
        String slug,
        String titulo,
        String rotuloMenu,
        String icone,
        String chamada,
        String template,
        String css,
        List<String> nomesNoCatalogo) {

    /** Versão dos estáticos; muda junto com o CSS para furar cache de browser. */
    private static final String VERSAO_CSS = "20260810";

    private static final List<AprofundamentoProtocolo> DISPONIVEIS = List.of(
            new AprofundamentoProtocolo(
                    "bgp",
                    "BGP-4 — Border Gateway Protocol",
                    "BGP",
                    "hub",
                    "O protocolo que mantém a Internet conectada: política entre Sistemas Autônomos, "
                            + "não menor custo interno.",
                    "protocolos/bgp/index.html",
                    "/protocolos/bgp/css/bgp.css",
                    List.of("BGP-4 / eBGP", "iBGP")),
            new AprofundamentoProtocolo(
                    "ssh",
                    "SSH — Secure Shell",
                    "SSH",
                    "terminal",
                    "Acesso remoto cifrado, autenticação por chave, túneis e o endurecimento do "
                            + "servidor que a maioria esquece.",
                    "protocolos/ssh/index.html",
                    "/protocolos/ssh/css/ssh.css",
                    List.of("SSH")));

    /** Itens do sub-menu, na ordem em que aparecem depois da aba Geral. */
    public static List<AprofundamentoProtocolo> disponiveis() {
        return DISPONIVEIS;
    }

    /**
     * Resolve o slug vindo da URL.
     *
     * <p><b>Comportamento em caso de falha:</b> entrada nula, em branco ou
     * desconhecida devolve {@link Optional#empty()}.</p>
     */
    public static Optional<AprofundamentoProtocolo> porSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String alvo = slug.trim().toLowerCase(Locale.ROOT);
        return DISPONIVEIS.stream().filter(item -> item.slug().equals(alvo)).findFirst();
    }

    /**
     * Resolve o aprofundamento a partir do nome exato usado no catálogo.
     *
     * <p><b>Propósito de negócio:</b> é o que permite à linha do DataGrid
     * oferecer o botão "Aprofundar" sem que o template saiba quais protocolos
     * têm página.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> nome nulo, em branco ou sem
     * página devolve {@link Optional#empty()} — a linha simplesmente não ganha
     * o botão.</p>
     */
    public static Optional<AprofundamentoProtocolo> porNomeDoCatalogo(String nome) {
        if (nome == null || nome.isBlank()) {
            return Optional.empty();
        }
        String alvo = nome.trim();
        return DISPONIVEIS.stream()
                .filter(item -> item.nomesNoCatalogo().stream().anyMatch(alvo::equalsIgnoreCase))
                .findFirst();
    }

    /** Rota HTTP da página, derivada do slug — evita rota escrita à mão divergindo do registro. */
    public String rota() {
        return "/protocolos/" + slug;
    }

    /** URL do CSS com a versão anexada. */
    public String cssVersionado() {
        return css + "?v=" + VERSAO_CSS;
    }
}
