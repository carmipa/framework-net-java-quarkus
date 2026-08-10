package org.framework.net.resolucaoProblemas.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Retrato do que o parser conseguiu ler de um texto de configuração Cisco.
 *
 * <p><b>Propósito de negócio:</b> a aba "Engenharia reversa" é a inversão da aba
 * "Projetar": em vez de partir de requisitos e produzir configuração, parte da
 * configuração e reconstrói o projeto. Este record é a fronteira entre as duas
 * metades — tudo o que o texto disse, ainda sem julgamento sobre estar certo ou
 * errado. A auditoria vem depois, em cima disto.</p>
 *
 * <p><b>Invariantes do domínio:</b> {@code linhasNaoInterpretadas} carrega TODA
 * linha que o parser não reconheceu, com número e motivo. Essa lista existe
 * porque parser que ignora linha em silêncio desenha uma topologia errada com
 * cara de certa — o pior resultado possível numa ferramenta didática. Nenhuma
 * lista é nula; ausência é lista vazia.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> texto vazio ou sem nenhum
 * {@code hostname} produz uma configuração com zero roteadores — não é exceção,
 * é resultado legítimo que a apresentação traduz em "nada reconhecido".</p>
 */
public record ConfiguracaoLida(
        List<RoteadorLido> roteadores,
        List<LinhaNaoInterpretada> linhasNaoInterpretadas,
        List<AchadoConfiguracao> achadosSintaxe,
        int totalLinhas) {

    public ConfiguracaoLida {
        roteadores = roteadores == null ? List.of() : List.copyOf(roteadores);
        linhasNaoInterpretadas = linhasNaoInterpretadas == null
                ? List.of() : List.copyOf(linhasNaoInterpretadas);
        achadosSintaxe = achadosSintaxe == null ? List.of() : List.copyOf(achadosSintaxe);
    }

    public static ConfiguracaoLida vazia() {
        return new ConfiguracaoLida(List.of(), List.of(), List.of(), 0);
    }

    public boolean isVazia() {
        return roteadores.isEmpty();
    }

    public int totalRoteadores() {
        return roteadores.size();
    }

    /**
     * Um roteador reconhecido no texto.
     *
     * <p><b>Invariantes do domínio:</b> {@code hostname} nunca é vazio — um bloco
     * sem {@code hostname} recebe um nome sintético e o fato vira achado, porque
     * roteador sem nome impede o cruzamento entre scripts.</p>
     */
    public record RoteadorLido(
            String hostname,
            int linhaInicial,
            List<InterfaceLida> interfaces,
            List<BlocoRoteamento> blocos,
            List<RotaEstatica> rotasEstaticas) {

        public RoteadorLido {
            interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
            blocos = blocos == null ? List.of() : List.copyOf(blocos);
            rotasEstaticas = rotasEstaticas == null ? List.of() : List.copyOf(rotasEstaticas);
        }

        /** Bloco BGP do roteador, se houver — é dele que sai o cruzamento entre scripts. */
        public BlocoRoteamento bgp() {
            return blocos.stream().filter(b -> "BGP".equals(b.protocolo())).findFirst().orElse(null);
        }

        /** Número do AS BGP, ou 0 quando o roteador não roda BGP. */
        public int asBgp() {
            BlocoRoteamento bgp = bgp();
            return bgp == null ? 0 : bgp.identificador();
        }

        /** Interfaces que receberam endereço IP — as únicas que participam da topologia. */
        public List<InterfaceLida> interfacesComIp() {
            List<InterfaceLida> comIp = new ArrayList<>();
            for (InterfaceLida i : interfaces) {
                if (i.temIp()) {
                    comIp.add(i);
                }
            }
            return comIp;
        }
    }

    /**
     * Uma interface com o que foi declarado nela.
     *
     * <p><b>Invariantes do domínio:</b> {@code ip} e {@code mascara} podem vir
     * vazios (interface declarada sem endereço) — quem consome checa
     * {@link #temIp()} antes de calcular sub-rede. {@code prefixo} vale -1
     * enquanto não houver máscara válida, nunca 0, que é um prefixo legítimo.</p>
     */
    public record InterfaceLida(
            String nome,
            String ip,
            String mascara,
            int prefixo,
            boolean clockRate,
            boolean noShutdown,
            String descricao,
            int vlan,
            int linha) {

        public boolean temIp() {
            return ip != null && !ip.isBlank() && prefixo >= 0;
        }

        /** Interface serial participa de enlace WAN; a distinção guia a correção de endereço. */
        public boolean serial() {
            String n = nome == null ? "" : nome.toLowerCase(java.util.Locale.ROOT);
            return n.startsWith("s") && !n.startsWith("sw");
        }

        public boolean loopback() {
            return nome != null && nome.toLowerCase(java.util.Locale.ROOT).startsWith("lo");
        }

        /** Cópia com outro endereço — usada pela auditoria ao aplicar uma correção derivada. */
        public InterfaceLida comIp(String novoIp) {
            return new InterfaceLida(nome, novoIp, mascara, prefixo, clockRate, noShutdown,
                    descricao, vlan, linha);
        }
    }

    /** Bloco {@code router <protocolo> <id>} com o que foi declarado dentro dele. */
    public record BlocoRoteamento(
            String protocolo,
            int identificador,
            List<VizinhoBgp> vizinhos,
            List<RedeAnunciada> redes,
            boolean autoSummaryDesligado,
            int linha) {

        public BlocoRoteamento {
            vizinhos = vizinhos == null ? List.of() : List.copyOf(vizinhos);
            redes = redes == null ? List.of() : List.copyOf(redes);
        }
    }

    /** {@code neighbor <ip> remote-as <as>} — a declaração que sustenta todo o cruzamento. */
    public record VizinhoBgp(String ip, int remoteAs, int linha) {
    }

    /**
     * {@code network} dentro de um bloco de roteamento.
     *
     * <p><b>Invariantes do domínio:</b> a mesma palavra {@code network} tem três
     * gramáticas conforme o bloco que a envolve — {@code mask} no BGP, wildcard e
     * {@code area} no OSPF, nua no EIGRP e no RIP. O campo {@code textoOriginal}
     * guarda o que foi escrito para que a correção mostre o antes e o depois.</p>
     */
    public record RedeAnunciada(
            String rede,
            String mascara,
            String wildcard,
            int area,
            String textoOriginal,
            int linha) {
    }

    /** {@code ip route <rede> <máscara> <próximo salto>}. */
    public record RotaEstatica(String rede, String mascara, String proximoSalto, int linha) {
    }

    /**
     * Linha que o parser não reconheceu.
     *
     * <p><b>Propósito de negócio:</b> tornar visível o que a ferramenta não
     * entendeu, em vez de deixar o usuário supor que foi tudo considerado.</p>
     */
    public record LinhaNaoInterpretada(int numero, String conteudo, String motivo) {
    }
}
