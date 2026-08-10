package org.framework.net.resolucaoProblemas.application.parsing;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.resolucaoProblemas.domain.kernel.MascaraIpv4;
import org.framework.net.resolucaoProblemas.domain.model.AchadoConfiguracao;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.BlocoRoteamento;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.InterfaceLida;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.LinhaNaoInterpretada;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.RedeAnunciada;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.RotaEstatica;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.RoteadorLido;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.VizinhoBgp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Leitor de configuração Cisco colada pelo usuário.
 *
 * <p><b>Propósito de negócio:</b> transformar o texto de CLI que o aluno recebeu
 * do professor — com abreviações, erros de digitação e vários roteadores no mesmo
 * bloco — em uma estrutura consultável. É a primeira metade da engenharia
 * reversa: aqui só se lê, não se julga. A conferência entre roteadores é da
 * auditoria.</p>
 *
 * <p><b>Invariantes do domínio:</b> nenhuma linha desaparece. Toda linha ou vira
 * dado, ou entra em {@code linhasNaoInterpretadas} com número e motivo. Um parser
 * que ignora linha em silêncio produz topologia errada com aparência de correta,
 * que numa ferramenta de estudo é pior do que não desenhar nada. Erros de
 * <em>sintaxe</em> cuja correção é evidente no próprio token (o clássico
 * {@code ip adress}) já saem daqui como achado corrigido; erros de
 * <em>coerência</em> entre roteadores não são competência deste componente.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> nunca lança por causa do conteúdo.
 * Texto nulo, vazio ou sem nenhum {@code hostname} devolve
 * {@link ConfiguracaoLida#vazia()} ou uma configuração com roteador sintético e o
 * achado correspondente — cabe à apresentação dizer ao usuário que não havia o
 * que reconhecer.</p>
 */
@ApplicationScoped
public class CiscoConfigParser {

    /** Motivo para linha que o parser entende, mas que não participa da topologia. */
    static final String MOTIVO_FORA_DO_ESCOPO =
            "Comando reconhecido, porém sem efeito na reconstrução da topologia.";

    /** Motivo para linha que o parser não soube classificar. */
    static final String MOTIVO_DESCONHECIDO = "Comando não reconhecido pelo interpretador.";

    /** Comandos de sessão e de gravação: existem no script, não descrevem a rede. */
    private static final Set<String> CONTROLE = Set.of(
            "ena", "en", "enable", "exit", "end", "quit", "logout", "wr", "write",
            "write memory", "write mem", "conf t", "config t", "conf terminal",
            "configure terminal", "copy running-config startup-config",
            "copy run start", "do wr", "do write", "no ip domain-lookup",
            "no ip domain lookup", "do show run", "show run");

    /** Prefixos de comandos legítimos que este módulo não modela (acesso, banner, senha…). */
    private static final List<String> FORA_DO_ESCOPO = List.of(
            "line ", "banner ", "logging ", "service ", "username ", "enable secret",
            "enable password", "password ", "login", "transport input", "ip domain-name",
            "ip domain name", "crypto key", "spanning-tree", "vtp ", "switchport",
            "vlan ", "name ", "duplex", "speed", "bandwidth", "ip nat", "access-list",
            "ip access-list", "snmp-server", "ntp ", "clock timezone", "hostname?");

    /**
     * Lê o texto colado.
     *
     * <p><b>Comportamento em caso de falha:</b> entrada nula ou em branco devolve
     * {@link ConfiguracaoLida#vazia()}.</p>
     */
    public ConfiguracaoLida analisar(String texto) {
        if (texto == null || texto.isBlank()) {
            return ConfiguracaoLida.vazia();
        }

        String[] linhas = texto.split("\\R", -1);
        List<RoteadorLido> roteadores = new ArrayList<>();
        List<LinhaNaoInterpretada> naoInterpretadas = new ArrayList<>();
        List<AchadoConfiguracao> achados = new ArrayList<>();

        RoteadorEmMontagem atual = null;
        InterfaceEmMontagem interfaceAtual = null;
        BlocoEmMontagem blocoAtual = null;

        for (int i = 0; i < linhas.length; i++) {
            int numero = i + 1;
            String bruta = linhas[i];
            String linha = bruta.strip();

            if (ignoravel(linha)) {
                continue;
            }

            String minuscula = linha.toLowerCase(Locale.ROOT);
            String[] tokens = linha.split("\\s+");
            String comando = tokens[0].toLowerCase(Locale.ROOT);

            if (CONTROLE.contains(minuscula) || CONTROLE.contains(comando)) {
                continue;
            }

            // --- hostname: abre um roteador novo -----------------------------
            if ("hostname".equals(comando) && tokens.length >= 2) {
                atual = fecharEAbrir(roteadores, atual, tokens[1], numero);
                interfaceAtual = null;
                blocoAtual = null;
                continue;
            }

            // A partir daqui tudo pertence a um roteador; sem hostname, criamos um sintético
            // para não jogar fora a configuração — e registramos o fato como achado.
            if (atual == null && ehComandoDeRoteador(comando)) {
                String sintetico = "ROTEADOR-" + (roteadores.size() + 1);
                atual = new RoteadorEmMontagem(sintetico, numero);
                achados.add(AchadoConfiguracao.semCorrecao(
                        "Identificação", sintetico, numero, linha,
                        "Bloco de configuração sem comando hostname — o roteador recebeu o nome "
                                + "provisório " + sintetico + ".",
                        "Sem hostname não há como cruzar este bloco com os outros scripts colados."));
            }
            if (atual == null) {
                naoInterpretadas.add(new LinhaNaoInterpretada(numero, linha, motivoDe(minuscula)));
                continue;
            }

            // --- interface ---------------------------------------------------
            if (ehPrefixoDe(comando, "interface", 3) && tokens.length >= 2) {
                interfaceAtual = atual.abrirInterface(normalizarNomeInterface(tokens[1]), numero);
                blocoAtual = null;
                continue;
            }

            // --- router <protocolo> ------------------------------------------
            if ("router".equals(comando) && tokens.length >= 2) {
                String protocolo = tokens[1].toUpperCase(Locale.ROOT);
                int id = tokens.length >= 3 ? inteiroOu(tokens[2], 0) : 0;
                blocoAtual = atual.abrirBloco(protocolo, id, numero);
                interfaceAtual = null;
                continue;
            }

            // --- ip route (global) --------------------------------------------
            if ("ip".equals(comando) && tokens.length >= 5 && "route".equalsIgnoreCase(tokens[1])) {
                atual.rotasEstaticas.add(new RotaEstatica(tokens[2], tokens[3], tokens[4], numero));
                continue;
            }

            // --- dentro de uma interface ---------------------------------------
            if (interfaceAtual != null
                    && lerComandoDeInterface(interfaceAtual, atual.hostname, tokens, linha, numero, achados)) {
                continue;
            }

            // --- dentro de um bloco de roteamento -------------------------------
            if (blocoAtual != null
                    && lerComandoDeRoteamento(blocoAtual, atual.hostname, tokens, linha, numero, achados)) {
                continue;
            }

            naoInterpretadas.add(new LinhaNaoInterpretada(numero, linha, motivoDe(minuscula)));
        }

        fechar(roteadores, atual);
        return new ConfiguracaoLida(roteadores, naoInterpretadas, achados, linhas.length);
    }

    // ---------------------------------------------------------------- interface

    /**
     * Lê um comando dentro de {@code interface}.
     *
     * <p><b>Invariantes do domínio:</b> {@code ip address} escrito errado é
     * corrigido aqui somente quando a distância para a palavra correta é pequena
     * o bastante para não haver outra leitura plausível — {@code adress} sim,
     * uma palavra qualquer não.</p>
     *
     * @return {@code true} quando a linha foi consumida.
     */
    private boolean lerComandoDeInterface(
            InterfaceEmMontagem alvo, String roteador, String[] tokens, String linha,
            int numero, List<AchadoConfiguracao> achados) {

        String comando = tokens[0].toLowerCase(Locale.ROOT);

        if ("ip".equals(comando) && tokens.length >= 2 && pareceAddress(tokens[1])) {
            if (!"address".equalsIgnoreCase(tokens[1])) {
                achados.add(AchadoConfiguracao.corrigido(
                        "Sintaxe", roteador, numero, linha,
                        linha.replaceFirst("(?i)\\b" + java.util.regex.Pattern.quote(tokens[1]) + "\\b", "address"),
                        "Comando escrito como \"ip " + tokens[1] + "\".",
                        "A forma aceita pelo IOS é \"ip address\"; a diferença é de digitação."));
            }
            if (tokens.length >= 3 && "dhcp".equalsIgnoreCase(tokens[2])) {
                achados.add(AchadoConfiguracao.aviso("Endereçamento", roteador, numero,
                        "Interface " + alvo.nome + " usa DHCP — sem endereço fixo não há como "
                                + "posicioná-la na topologia.",
                        "Endereço obtido em tempo de execução não existe no texto."));
                return true;
            }
            if (tokens.length >= 4) {
                alvo.ip = tokens[2];
                alvo.mascaraTexto = tokens[3];
                alvo.prefixo = MascaraIpv4.prefixoDe(tokens[3]);
                if (alvo.prefixo < 0) {
                    registrarMascaraInvalida(achados, roteador, numero, linha, tokens[3], alvo);
                }
                if (!MascaraIpv4.ipValido(tokens[2])) {
                    achados.add(AchadoConfiguracao.semCorrecao("Endereçamento", roteador, numero, linha,
                            "Endereço IPv4 inválido em " + alvo.nome + ": " + tokens[2] + ".",
                            "Octeto fora da faixa 0–255 ou quantidade de octetos diferente de quatro."));
                }
                return true;
            }
            return false;
        }

        if ("no".equals(comando) && tokens.length >= 2 && ehPrefixoDe(tokens[1], "shutdown", 4)) {
            alvo.noShutdown = true;
            return true;
        }
        if (ehPrefixoDe(comando, "shutdown", 4)) {
            alvo.noShutdown = false;
            return true;
        }
        if ("clock".equals(comando) && tokens.length >= 2 && tokens[1].toLowerCase(Locale.ROOT).startsWith("rate")) {
            alvo.clockRate = true;
            return true;
        }
        if (ehPrefixoDe(comando, "description", 4)) {
            alvo.descricao = linha.substring(tokens[0].length()).strip();
            return true;
        }
        if (ehPrefixoDe(comando, "encapsulation", 5) && tokens.length >= 3) {
            alvo.vlan = inteiroOu(tokens[2], 0);
            return true;
        }
        return false;
    }

    private void registrarMascaraInvalida(
            List<AchadoConfiguracao> achados, String roteador, int numero,
            String linha, String mascara, InterfaceEmMontagem alvo) {

        int octetos = MascaraIpv4.contarOctetos(mascara);
        String detalhe = octetos != 4
                ? "A máscara tem " + octetos + " octetos; IPv4 usa exatamente quatro."
                : "A máscara não é contígua — os bits 1 precisam ficar todos à esquerda.";
        achados.add(AchadoConfiguracao.semCorrecao("Máscara", roteador, numero, linha,
                "Máscara inválida em " + alvo.nome + ": " + mascara + ".", detalhe));
    }

    // -------------------------------------------------------------- roteamento

    /**
     * Lê um comando dentro de {@code router <protocolo>}.
     *
     * <p><b>Invariantes do domínio:</b> a palavra {@code network} tem gramática
     * diferente conforme o bloco — {@code mask} no BGP, wildcard e {@code area} no
     * OSPF, nua no EIGRP e no RIP. A leitura é pelo contexto do bloco, nunca pela
     * palavra isolada.</p>
     *
     * @return {@code true} quando a linha foi consumida.
     */
    private boolean lerComandoDeRoteamento(
            BlocoEmMontagem bloco, String roteador, String[] tokens, String linha,
            int numero, List<AchadoConfiguracao> achados) {

        String comando = tokens[0].toLowerCase(Locale.ROOT);

        if (ehPrefixoDe(comando, "neighbor", 4) && tokens.length >= 4
                && "remote-as".equalsIgnoreCase(tokens[2])) {
            bloco.vizinhos.add(new VizinhoBgp(tokens[1], inteiroOu(tokens[3], 0), numero));
            return true;
        }

        if ("no".equals(comando) && tokens.length >= 2
                && tokens[1].toLowerCase(Locale.ROOT).startsWith("auto-summary")) {
            bloco.autoSummaryDesligado = true;
            return true;
        }

        if (ehPrefixoDe(comando, "network", 3) && tokens.length >= 2) {
            String rede = tokens[1];
            String mascara = "";
            String wildcard = "";
            int area = -1;

            if ("BGP".equals(bloco.protocolo)) {
                if (tokens.length >= 4 && "mask".equalsIgnoreCase(tokens[2])) {
                    // Máscara inválida NÃO vira achado aqui: a correção sai de cruzar o
                    // anúncio com a interface conectada, o que é competência da auditoria.
                    mascara = tokens[3];
                }
            } else if ("OSPF".equals(bloco.protocolo)) {
                if (tokens.length >= 3) {
                    wildcard = tokens[2];
                }
                if (tokens.length >= 5 && "area".equalsIgnoreCase(tokens[3])) {
                    area = inteiroOu(tokens[4], 0);
                }
            } else if (tokens.length >= 3 && MascaraIpv4.ipValido(tokens[2])) {
                wildcard = tokens[2];
            }

            bloco.redes.add(new RedeAnunciada(rede, mascara, wildcard, area, linha, numero));
            return true;
        }

        // router-id, version, passive-interface e afins: legítimos, sem efeito no desenho.
        return ehPrefixoDe(comando, "version", 4)
                || comando.endsWith("router-id")
                || ("bgp".equals(comando) && tokens.length >= 2)
                || ehPrefixoDe(comando, "passive-interface", 7);
    }

    // ------------------------------------------------------------------ apoio

    private boolean ignoravel(String linha) {
        if (linha.isEmpty() || linha.startsWith("!") || linha.startsWith("#")) {
            return true;
        }
        // Separadores que o usuário usa para dividir um script do outro.
        return linha.chars().allMatch(c -> c == '-' || c == '=' || c == '*' || c == '_' || c == ' ');
    }

    private String motivoDe(String minuscula) {
        for (String prefixo : FORA_DO_ESCOPO) {
            if (minuscula.startsWith(prefixo)) {
                return MOTIVO_FORA_DO_ESCOPO;
            }
        }
        return MOTIVO_DESCONHECIDO;
    }

    private boolean ehComandoDeRoteador(String comando) {
        return ehPrefixoDe(comando, "interface", 3) || "router".equals(comando) || "ip".equals(comando);
    }

    /** {@code true} quando {@code token} é abreviação legítima de {@code completo}. */
    static boolean ehPrefixoDe(String token, String completo, int minimo) {
        if (token == null || token.length() < minimo || token.length() > completo.length()) {
            return false;
        }
        return completo.startsWith(token.toLowerCase(Locale.ROOT));
    }

    /**
     * O token pretendia ser {@code address}?
     *
     * <p><b>Invariantes do domínio:</b> aceita abreviação legítima
     * ({@code add}, {@code addr}) e erro de digitação a distância 1 ou 2
     * ({@code adress}, {@code addres}). Distância maior é recusada: corrigir
     * palavra distante seria adivinhar a intenção do usuário.</p>
     */
    static boolean pareceAddress(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        String t = token.toLowerCase(Locale.ROOT);
        if ("address".startsWith(t) && t.length() >= 3) {
            return true;
        }
        return t.charAt(0) == 'a' && distancia(t, "address") <= 2;
    }

    /** Distância de edição de Levenshtein, limitada ao tamanho das palavras de comando. */
    static int distancia(String a, String b) {
        int[] anterior = new int[b.length() + 1];
        int[] atual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            anterior[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            atual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int custo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                atual[j] = Math.min(Math.min(atual[j - 1] + 1, anterior[j] + 1), anterior[j - 1] + custo);
            }
            int[] troca = anterior;
            anterior = atual;
            atual = troca;
        }
        return anterior[b.length()];
    }

    /** {@code g0/0} vira {@code GigabitEthernet0/0} — o script corrigido sai na forma canônica. */
    static String normalizarNomeInterface(String nome) {
        if (nome == null || nome.isBlank()) {
            return "";
        }
        String texto = nome.strip();
        int corte = 0;
        while (corte < texto.length() && Character.isLetter(texto.charAt(corte))) {
            corte++;
        }
        String tipo = texto.substring(0, corte).toLowerCase(Locale.ROOT);
        String resto = texto.substring(corte);
        String canonico = switch (tipo) {
            case "g", "gi", "gig", "gigabit", "gigabitethernet" -> "GigabitEthernet";
            case "f", "fa", "fast", "fastethernet" -> "FastEthernet";
            case "s", "se", "ser", "serial" -> "Serial";
            case "e", "et", "eth", "ethernet" -> "Ethernet";
            case "lo", "loop", "loopback" -> "Loopback";
            case "vl", "vlan" -> "Vlan";
            case "te", "tengigabitethernet" -> "TenGigabitEthernet";
            default -> texto.substring(0, corte);
        };
        return canonico + resto;
    }

    private static int inteiroOu(String texto, int alternativa) {
        try {
            return Integer.parseInt(texto.strip());
        } catch (RuntimeException ex) {
            return alternativa;
        }
    }

    private RoteadorEmMontagem fecharEAbrir(
            List<RoteadorLido> destino, RoteadorEmMontagem atual, String hostname, int numero) {
        fechar(destino, atual);
        return new RoteadorEmMontagem(hostname, numero);
    }

    private void fechar(List<RoteadorLido> destino, RoteadorEmMontagem atual) {
        if (atual != null) {
            destino.add(atual.selar());
        }
    }

    // ------------------------------------------------- estruturas de montagem

    /** Roteador em construção: mutável só durante a varredura, selado ao final. */
    private static final class RoteadorEmMontagem {
        private final String hostname;
        private final int linhaInicial;
        private final List<InterfaceEmMontagem> interfaces = new ArrayList<>();
        private final List<BlocoEmMontagem> blocos = new ArrayList<>();
        private final List<RotaEstatica> rotasEstaticas = new ArrayList<>();

        RoteadorEmMontagem(String hostname, int linhaInicial) {
            this.hostname = hostname;
            this.linhaInicial = linhaInicial;
        }

        InterfaceEmMontagem abrirInterface(String nome, int linha) {
            InterfaceEmMontagem nova = new InterfaceEmMontagem(nome, linha);
            interfaces.add(nova);
            return nova;
        }

        BlocoEmMontagem abrirBloco(String protocolo, int identificador, int linha) {
            BlocoEmMontagem novo = new BlocoEmMontagem(protocolo, identificador, linha);
            blocos.add(novo);
            return novo;
        }

        RoteadorLido selar() {
            List<InterfaceLida> lidas = new ArrayList<>();
            for (InterfaceEmMontagem i : interfaces) {
                lidas.add(i.selar());
            }
            List<BlocoRoteamento> selados = new ArrayList<>();
            for (BlocoEmMontagem b : blocos) {
                selados.add(b.selar());
            }
            return new RoteadorLido(hostname, linhaInicial, lidas, selados, rotasEstaticas);
        }
    }

    private static final class InterfaceEmMontagem {
        private final String nome;
        private final int linha;
        private String ip = "";
        private String mascaraTexto = "";
        private int prefixo = -1;
        private boolean clockRate;
        private boolean noShutdown;
        private String descricao = "";
        private int vlan;

        InterfaceEmMontagem(String nome, int linha) {
            this.nome = nome;
            this.linha = linha;
        }

        InterfaceLida selar() {
            return new InterfaceLida(nome, ip, mascaraTexto, prefixo, clockRate, noShutdown,
                    descricao, vlan, linha);
        }
    }

    private static final class BlocoEmMontagem {
        private final String protocolo;
        private final int identificador;
        private final int linha;
        private final List<VizinhoBgp> vizinhos = new ArrayList<>();
        private final List<RedeAnunciada> redes = new ArrayList<>();
        private boolean autoSummaryDesligado;

        BlocoEmMontagem(String protocolo, int identificador, int linha) {
            this.protocolo = protocolo;
            this.identificador = identificador;
            this.linha = linha;
        }

        BlocoRoteamento selar() {
            return new BlocoRoteamento(protocolo, identificador, vizinhos, redes,
                    autoSummaryDesligado, linha);
        }
    }
}
