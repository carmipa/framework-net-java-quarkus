package org.framework.net.ferramentasDiagnostico.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.ferramentasDiagnostico.domain.ResultadoDiagnostico;
import org.framework.net.ferramentasDiagnostico.domain.ResultadoDiagnostico.Kpi;
import org.framework.net.ferramentasDiagnostico.domain.ResultadoDiagnostico.Tabela;
import org.framework.net.ferramentasDiagnostico.domain.ResultadoDiagnostico.Tabela.Linha;
import org.framework.net.ferramentasDiagnostico.domain.ResultadoDiagnostico.Termo;
import org.framework.net.ferramentasDiagnostico.exception.DiagnosticoException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simuladores didáticos de ferramentas de diagnóstico de rede.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> cada ferramenta devolve um resultado DISSECADO
 * ({@link ResultadoDiagnostico}) — indicadores, o comando real que aquilo
 * representa, tabela campo a campo, legenda dos termos, "como funciona" e "o que
 * observar" — no mesmo nível didático da Calculadora e da Análise, além da saída
 * bruta preservada para comparação.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> computação pura e determinística no formato;
 * nenhum pacote real é enviado (o app roda em VPS; sondagem real exigiria agente
 * nativo com privilégio). Entrada sempre sanitizada antes de ser ecoada.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> entrada inválida dispara
 * {@link DiagnosticoException} (HTTP 400 pelo mapper), nunca uma saída vazia.</p>
 */
@ApplicationScoped
public class DiagnosticoService {

    @Inject
    TelemetriaLogger telemetriaLogger;

    // ------------------------------------------------------------------ PING

    public ResultadoDiagnostico executarPingSimulado(String host) {
        return telemetriaLogger.medir("diagnostico", "ping_simulado", () -> {
            validarHost(host);
            telemetriaLogger.logEvent("info", "diagnostico", "ping_executado", Map.of("host", host));

            int[] tempos = {18, 17, 35, 13};
            StringBuilder sb = new StringBuilder();
            sb.append("Disparando PING contra ").append(host).append(" [Simulado] com 32 bytes de dados:\n");
            List<Linha> linhas = new ArrayList<>();
            int i = 1;
            for (int t : tempos) {
                sb.append("Resposta de ").append(host).append(": bytes=32 tempo=").append(t).append("ms TTL=54\n");
                linhas.add(new Linha(List.of("#" + i, host, "32", t + " ms", "54"), ""));
                i++;
            }
            sb.append("\nEstatísticas do Ping para ").append(host).append(":\n");
            sb.append("    Pacotes: Enviados = 4, Recebidos = 4, Perdidos = 0 (0% de perda)\n");

            return new ResultadoDiagnostico(
                    "ping",
                    "Ping — teste de conectividade (ICMP Echo)",
                    "radar",
                    "ping " + host,
                    "Envia ICMP Echo Request e cronometra o Echo Reply de volta: prova que o host responde e mede a latência.",
                    List.of(
                            new Kpi("Enviados", "4", "info", "ICMP Echo Request"),
                            new Kpi("Recebidos", "4", "success", "ICMP Echo Reply"),
                            new Kpi("Perda", "0%", "success", "0 de 4 pacotes"),
                            new Kpi("RTT médio", media(tempos) + " ms", "info", "ida + volta")),
                    new Tabela("Respostas recebidas",
                            List.of("#", "Resposta de", "Bytes", "Tempo (RTT)", "TTL"),
                            linhas),
                    List.of(
                            new Termo("ICMP Echo Request/Reply", "As mensagens tipo 8 (pergunta) e tipo 0 (resposta) do protocolo ICMP."),
                            new Termo("bytes=32", "Tamanho do payload enviado no pacote; não muda a latência, só o volume."),
                            new Termo("tempo (RTT)", "Round-Trip Time: quanto o pacote levou para ir e voltar, em milissegundos."),
                            new Termo("TTL=54", "Time To Live restante. Origem comum é 64 (Linux); 64 − 54 ≈ 10 roteadores no caminho."),
                            new Termo("perda", "Percentual de Echo Requests sem resposta — indica congestionamento ou enlace ruim.")),
                    List.of(
                            "O cliente envia um ICMP Echo Request (tipo 8) ao host.",
                            "Cada roteador no caminho decrementa o TTL do pacote IP em 1.",
                            "O host responde com um ICMP Echo Reply (tipo 0); o RTT é o tempo entre enviar e receber.",
                            "Quatro pacotes por padrão dão uma amostra de latência e de perda."),
                    List.of(
                            "Perda > 0 sem padrão sugere congestionamento ou enlace instável.",
                            "O TTL denuncia o SO de origem: 64 (Linux/Unix), 128 (Windows), 255 (equipamento de rede).",
                            "\"Request timed out\" pode ser firewall bloqueando ICMP — não é prova de host desligado."),
                    "Simulado — nenhum pacote ICMP real foi enviado; os tempos são ilustrativos.",
                    sb.toString());
        });
    }

    // ------------------------------------------------------------------- DNS

    public ResultadoDiagnostico executarDnsSimulado(String dominio) {
        return telemetriaLogger.medir("diagnostico", "dns_simulado", () -> {
            validarHost(dominio);
            telemetriaLogger.logEvent("info", "diagnostico", "dns_executado", Map.of("dominio", dominio));

            String ip = gerarIpAleatorio();
            int queryTime = (int) (Math.random() * 20 + 2);
            String bruta = "; <<>> DiG 9.16.1-Ubuntu <<>> " + dominio + "\n" +
                    ";; global options: +cmd\n" +
                    ";; Got answer:\n" +
                    ";; ->>HEADER<<- opcode: QUERY, status: NOERROR, id: 12345\n" +
                    ";; flags: qr rd ra; QUERY: 1, ANSWER: 1, AUTHORITY: 0, ADDITIONAL: 1\n\n" +
                    ";; ANSWER SECTION:\n" +
                    dominio + ".\t\t300\tIN\tA\t" + ip + "\n\n" +
                    ";; Query time: " + queryTime + " msec\n" +
                    ";; SERVER: 8.8.8.8#53(8.8.8.8)\n";

            return new ResultadoDiagnostico(
                    "dns",
                    "DNS — resolução de nome (dig)",
                    "dns",
                    "dig " + dominio + " A",
                    "Pergunta a um resolver recursivo o endereço de um nome e mostra a resposta no formato do dig.",
                    List.of(
                            new Kpi("Status", "NOERROR", "success", "o nome existe e resolveu"),
                            new Kpi("Respostas", "1", "info", "registros na ANSWER SECTION"),
                            new Kpi("Query time", queryTime + " ms", "info", "tempo da consulta"),
                            new Kpi("Servidor", "8.8.8.8#53", "info", "resolver consultado (UDP 53)")),
                    new Tabela("ANSWER SECTION",
                            List.of("Nome", "TTL", "Classe", "Tipo", "Valor"),
                            List.of(new Linha(List.of(dominio + ".", "300", "IN", "A", ip), "success"))),
                    List.of(
                            new Termo("status: NOERROR", "A consulta teve sucesso. NXDOMAIN significaria que o nome não existe."),
                            new Termo("flags: qr rd ra", "qr = resposta; rd = recursão pedida; ra = recursão disponível no servidor."),
                            new Termo("registro A", "Mapeia o nome para um endereço IPv4 (AAAA seria IPv6)."),
                            new Termo("TTL 300", "Segundos que a resposta pode ficar em cache antes de nova consulta."),
                            new Termo("#53", "Porta padrão do DNS (UDP para consultas, TCP para respostas grandes/zona).")),
                    List.of(
                            "O stub do sistema pergunta ao resolver recursivo configurado.",
                            "O resolver percorre raiz → TLD → autoritativo (ou responde do cache) e devolve o valor.",
                            "A ANSWER SECTION traz o(s) registro(s); o TTL diz por quanto tempo cachear."),
                    List.of(
                            "NXDOMAIN no lugar de NOERROR = nome inexistente ou zona não delegada.",
                            "Query time muito baixo com TTL decrescente indica resposta vinda do cache.",
                            "Sem a flag 'ra', o servidor não faz recursão para você — use um resolver recursivo."),
                    "Simulado — a resposta é ilustrativa. Veja Protocolos > DNS para a resolução completa e os ataques.",
                    bruta);
        });
    }

    // ----------------------------------------------------------- TRACEROUTE

    public ResultadoDiagnostico executarTracerouteSimulado(String host) {
        return telemetriaLogger.medir("diagnostico", "traceroute_simulado", () -> {
            validarHost(host);
            telemetriaLogger.logEvent("info", "diagnostico", "traceroute_executado", Map.of("host", host));

            String[][] saltos = {
                    {"roteador-local", "192.168.0.1"},
                    {"gateway-isp", "100.64.0.1"},
                    {"core-isp", "187.100.24.1"},
                    {"backbone", "200.152.0.33"},
                    {"borda-destino", "203.0.113.1"},
                    {"destino", host}
            };
            StringBuilder sb = new StringBuilder();
            sb.append("Rastreando rota para ").append(host).append(" [Simulado], no máximo 30 saltos:\n\n");
            List<Linha> linhas = new ArrayList<>();
            for (int i = 0; i < saltos.length; i++) {
                int ttl = i + 1;
                int a = (int) (Math.random() * 8 + ttl * 3);
                int b = (int) (Math.random() * 8 + ttl * 3);
                int c = (int) (Math.random() * 8 + ttl * 3);
                sb.append(String.format("%2d   TTL=%-2d  %3d ms  %3d ms  %3d ms   %s   %s%n",
                        ttl, ttl, a, b, c, saltos[i][0], saltos[i][1]));
                boolean ultimo = i == saltos.length - 1;
                linhas.add(new Linha(
                        List.of(String.valueOf(ttl), "TTL=" + ttl, a + " / " + b + " / " + c + " ms",
                                saltos[i][0] + " (" + saltos[i][1] + ")"),
                        ultimo ? "success" : ""));
            }

            return new ResultadoDiagnostico(
                    "traceroute",
                    "Traceroute — a rota salto a salto (manipulação de TTL)",
                    "route",
                    "traceroute " + host + "   (tracert no Windows)",
                    "Descobre cada roteador entre você e o destino enviando pacotes com TTL crescente.",
                    List.of(
                            new Kpi("Saltos", String.valueOf(saltos.length), "info", "roteadores até o destino"),
                            new Kpi("Destino alcançado", "sim", "success", "o último nó respondeu"),
                            new Kpi("TTL inicial", "1", "info", "e cresce a cada sonda")),
                    new Tabela("Saltos da rota",
                            List.of("Salto", "TTL enviado", "RTT (3 sondas)", "Nó que respondeu"),
                            linhas),
                    List.of(
                            new Termo("TTL", "Time To Live: número máximo de roteadores que o pacote pode atravessar."),
                            new Termo("ICMP Time Exceeded", "A mensagem que o roteador manda ao ZERAR o TTL — é ela que revela o salto."),
                            new Termo("3 sondas", "Cada salto é medido 3 vezes para dar uma noção da variação de latência."),
                            new Termo("* * *", "Salto sem resposta: roteador que não envia Time Exceeded (comum por filtro).")),
                    List.of(
                            "Envia um pacote com TTL=1; o primeiro roteador zera o TTL e responde ICMP Time Exceeded.",
                            "Repete com TTL=2, 3, 4… cada valor expira num roteador mais distante, revelando o caminho.",
                            "Quando o pacote chega ao destino, ele responde (Echo Reply ou porta), encerrando a rota."),
                    List.of(
                            "Saltos como \"* * *\" costumam ser filtro de ICMP, não rota quebrada.",
                            "Um salto de latência grande entre dois nós indica o enlace mais longo (às vezes intercontinental).",
                            "A rota de ida pode diferir da de volta — traceroute mostra só a ida."),
                    "Simulado — sem raw socket; a rota e os tempos são ilustrativos.",
                    sb.toString());
        });
    }

    // ----------------------------------------------------------- PING SWEEP

    public ResultadoDiagnostico executarPingSweepSimulado(String rede) {
        return telemetriaLogger.medir("diagnostico", "ping_sweep_simulado", () -> {
            validarHost(rede);
            telemetriaLogger.logEvent("info", "diagnostico", "ping_sweep_executado", Map.of("rede", rede));

            String base = prefixoDidatico(rede);
            // Endereços "vivos" — todos dentro do conjunto REALMENTE amostrado pelo laço
            // abaixo (1..24 de 1 em 1, depois 25,65,105,145,185,225), senão virariam dado morto.
            int[] ativos = {1, 10, 15, 20, 65, 105, 225};
            StringBuilder sb = new StringBuilder();
            sb.append("Varredura de descoberta (ping sweep) em ").append(rede).append(" [Simulado]:\n\n");
            List<Linha> linhas = new ArrayList<>();
            int testados = 0;
            int vivos = 0;
            for (int host = 1; host <= 254; host += (host < 25 ? 1 : 40)) {
                testados++;
                boolean ativo = contemValor(ativos, host);
                String ip = base + "." + host;
                if (ativo) {
                    vivos++;
                    int rtt = (int) (Math.random() * 12 + 1);
                    sb.append(String.format("%-16s ATIVO   ICMP Echo Reply, %d ms%n", ip, rtt));
                    linhas.add(new Linha(List.of(ip, "ATIVO", "ICMP Echo Reply, " + rtt + " ms"), "success"));
                } else {
                    sb.append(String.format("%-16s ---     sem resposta%n", ip));
                    linhas.add(new Linha(List.of(ip, "sem resposta", "—"), ""));
                }
            }
            sb.append("\nHosts ativos encontrados: ").append(vivos).append(" (amostra didática da faixa).\n");

            return new ResultadoDiagnostico(
                    "ping-sweep",
                    "Ping Sweep — descoberta de hosts vivos",
                    "travel_explore",
                    "nmap -sn " + rede,
                    "Pinga cada endereço de uma faixa para mapear quais hosts estão ativos na rede.",
                    List.of(
                            new Kpi("Endereços testados", String.valueOf(testados), "info", "amostra da faixa"),
                            new Kpi("Hosts ativos", String.valueOf(vivos), "success", "responderam ao ICMP"),
                            new Kpi("Faixa", rede, "info", "rede alvo")),
                    new Tabela("Resultado por endereço",
                            List.of("Endereço", "Estado", "Observado"),
                            linhas),
                    List.of(
                            new Termo("ping sweep", "Enviar um ICMP Echo Request a cada endereço de uma faixa para descobrir quem está vivo."),
                            new Termo("ATIVO", "O host respondeu com Echo Reply — está ligado e alcançável."),
                            new Termo("sem resposta", "Nenhum Echo Reply — pode estar desligado OU com ICMP bloqueado por firewall."),
                            new Termo("nmap -sn", "O modo 'host discovery' do nmap: descobre hosts sem varrer portas.")),
                    List.of(
                            "Para cada endereço da faixa, envia um ICMP Echo Request.",
                            "Quem responde Echo Reply é marcado como ativo; o resto fica como sem resposta.",
                            "É o primeiro passo de um mapeamento: descobrir alvos antes de varrer portas."),
                    List.of(
                            "Ausência de resposta NÃO prova host inexistente — firewall que descarta ICMP o esconde.",
                            "Redes grandes (/16) levam muito tempo por ICMP; ferramentas reais paralelizam.",
                            "Ping sweep barulhento acende alertas em IDS — muitos ICMP de uma origem só."),
                    "Simulado — a amostra é ilustrativa; nenhuma varredura real foi feita.",
                    sb.toString());
        });
    }

    // ------------------------------------------------------- VARREDURA SYN

    public ResultadoDiagnostico executarScanSimulado(String host) {
        return telemetriaLogger.medir("diagnostico", "scan_simulado", () -> {
            validarHost(host);
            telemetriaLogger.logEvent("info", "diagnostico", "scan_executado", Map.of("host", host));

            String[][] portas = {
                    {"22/tcp", "open", "ssh", "SYN-ACK recebido", "success"},
                    {"80/tcp", "open", "http", "SYN-ACK recebido", "success"},
                    {"443/tcp", "open", "https", "SYN-ACK recebido", "success"},
                    {"25/tcp", "closed", "smtp", "RST recebido", ""},
                    {"3306/tcp", "filtered", "mysql", "sem resposta — firewall descartou o SYN", "warning"},
                    {"8080/tcp", "closed", "http-proxy", "RST recebido", ""}
            };
            StringBuilder sb = new StringBuilder();
            sb.append("Varredura de portas SYN stealth (-sS) em ").append(host).append(" [Simulado]:\n\n");
            sb.append(String.format("%-10s %-10s %-12s %s%n", "PORTA", "ESTADO", "SERVIÇO", "OBSERVADO"));
            List<Linha> linhas = new ArrayList<>();
            int abertas = 0;
            int filtradas = 0;
            int fechadas = 0;
            for (String[] p : portas) {
                sb.append(String.format("%-10s %-10s %-12s %s%n", p[0], p[1], p[2], p[3]));
                linhas.add(new Linha(List.of(p[0], p[1], p[2], p[3]), p[4]));
                switch (p[1]) {
                    case "open" -> abertas++;
                    case "filtered" -> filtradas++;
                    default -> fechadas++;
                }
            }

            return new ResultadoDiagnostico(
                    "scan",
                    "Varredura de portas — SYN stealth (-sS)",
                    "radar",
                    "nmap -sS " + host,
                    "Descobre portas abertas enviando SYN e nunca completando o handshake — menos rastro que uma conexão completa.",
                    List.of(
                            new Kpi("Abertas", String.valueOf(abertas), "success", "SYN-ACK recebido"),
                            new Kpi("Filtradas", String.valueOf(filtradas), "warning", "firewall engoliu o SYN"),
                            new Kpi("Fechadas", String.valueOf(fechadas), "info", "RST recebido")),
                    new Tabela("Portas",
                            List.of("Porta", "Estado", "Serviço", "O que aconteceu"),
                            linhas),
                    List.of(
                            new Termo("open", "A porta respondeu SYN-ACK: há um serviço escutando."),
                            new Termo("closed", "A porta respondeu RST: alcançável, mas sem serviço escutando."),
                            new Termo("filtered", "Nenhuma resposta: um firewall descartou o SYN — não dá para saber se há serviço."),
                            new Termo("SYN stealth (-sS)", "Envia SYN e responde o SYN-ACK com RST, sem completar o handshake."),
                            new Termo("half-open", "A conexão fica meio-aberta e é abortada — por isso muitos logs de app não a registram.")),
                    List.of(
                            "Envia um SYN para cada porta alvo, como se fosse abrir conexão.",
                            "SYN-ACK de volta = aberta; RST = fechada; silêncio = filtrada por firewall.",
                            "Ao ver o SYN-ACK, responde com RST em vez de ACK — o handshake nunca se completa."),
                    List.of(
                            "\"filtered\" é o estado que interessa ao defensor: há um firewall entre você e a porta.",
                            "Um IDS detecta a varredura pelo padrão: muitos SYN sem o ACK final, da mesma origem.",
                            "Varredura em host que você não tem autorização para testar pode ser crime — só em laboratório/autorizado."),
                    "Simulado — nenhuma porta real foi sondada; o conjunto é ilustrativo.",
                    sb.toString());
        });
    }

    // ---------------------------------------------------------- DNS SPOOFING

    public ResultadoDiagnostico executarDnsSpoofingSimulado(String dominio) {
        return telemetriaLogger.medir("diagnostico", "dns_spoofing_simulado", () -> {
            validarHost(dominio);
            telemetriaLogger.logEvent("info", "diagnostico", "dns_spoofing_executado", Map.of("dominio", dominio));

            StringBuilder sb = new StringBuilder();
            sb.append("Simulação didática de DNS spoofing para ").append(dominio).append(" (53/UDP):\n\n");
            sb.append("[t=0.000s] Cliente  -> Resolver : consulta  A? ").append(dominio)
                    .append("   (Transaction ID=0x7f3a, porta origem=54721)\n");
            sb.append("[t=0.008s] Atacante -> Cliente  : RESPOSTA FORJADA  ").append(dominio)
                    .append(" A 6.6.6.6   (chuta ID + porta)\n");
            sb.append("[t=0.021s] Resolver -> Cliente  : resposta legítima ").append(dominio)
                    .append(" A 203.0.113.10\n");

            List<Linha> linhas = List.of(
                    new Linha(List.of("t=0.000s", "Cliente → Resolver", "consulta A? " + dominio + " (ID=0x7f3a, porta=54721)"), ""),
                    new Linha(List.of("t=0.008s", "Atacante → Cliente", "RESPOSTA FORJADA → 6.6.6.6 (chuta ID + porta)"), "danger"),
                    new Linha(List.of("t=0.021s", "Resolver → Cliente", "resposta legítima → 203.0.113.10 (chegou tarde)"), "success"));

            return new ResultadoDiagnostico(
                    "dns-spoofing",
                    "DNS Spoofing — a corrida das respostas",
                    "gpp_bad",
                    "(didático — sem comando único; ilustra a corrida na porta 53/UDP)",
                    "Mostra por que o DNS sem DNSSEC é falsificável: vence a primeira resposta UDP que casar Transaction ID e porta.",
                    List.of(
                            new Kpi("Transaction ID", "16 bits", "warning", "o atacante precisa acertar"),
                            new Kpi("Porta de origem", "aleatória", "info", "mais bits a adivinhar"),
                            new Kpi("Vencedor", "quem chega 1º", "danger", "e casa ID + porta"),
                            new Kpi("Efeito", "dura o TTL", "danger", "cache envenenado")),
                    new Tabela("Linha do tempo da corrida",
                            List.of("Tempo", "Sentido", "O que trafega"),
                            linhas),
                    List.of(
                            new Termo("Transaction ID", "Número de 16 bits que casa a resposta com a pergunta — o atacante tem de adivinhá-lo."),
                            new Termo("porta de origem", "Porta UDP aleatória da consulta; a resposta tem de bater com ela também."),
                            new Termo("cache poisoning", "Envenenar a entrada no cache do resolver, redirecionando todos os clientes dele."),
                            new Termo("off-path × on-path", "Off-path chuta os valores; on-path (mesma rede) os enxerga e não precisa chutar.")),
                    List.of(
                            "O cliente pergunta A? do domínio ao resolver, com um Transaction ID e uma porta de origem.",
                            "O atacante dispara uma resposta forjada tentando casar esses dois valores antes da real.",
                            "Se a forjada chega primeiro E acerta ID + porta, o resolver a aceita e cacheia o IP falso."),
                    List.of(
                            "Sem DNSSEC, a defesa é probabilística: mais entropia (porta aleatória + 0x20) = mais difícil acertar.",
                            "DNSSEC recusa a resposta forjada porque ela não tem assinatura válida.",
                            "DoT/DoH cifram o canal cliente↔resolver, tirando o on-path da jogada."),
                    "Simulado — nenhum pacote real; valores didáticos. Veja Protocolos > DNS para DNSSEC e mitigações.",
                    sb.toString());
        });
    }

    // ------------------------------------------------------------- helpers

    private static int media(int[] v) {
        int s = 0;
        for (int x : v) {
            s += x;
        }
        return v.length == 0 ? 0 : Math.round((float) s / v.length);
    }

    /** Deriva um prefixo /24 didático a partir da faixa informada (só para exibição). */
    private static String prefixoDidatico(String rede) {
        String semMascara = rede.contains("/") ? rede.substring(0, rede.indexOf('/')) : rede;
        int ultimoPonto = semMascara.lastIndexOf('.');
        if (ultimoPonto > 0 && semMascara.chars().filter(c -> c == '.').count() == 3) {
            return semMascara.substring(0, ultimoPonto);
        }
        return "192.168.1";
    }

    private static boolean contemValor(int[] valores, int alvo) {
        for (int v : valores) {
            if (v == alvo) {
                return true;
            }
        }
        return false;
    }

    private void validarHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            throw new DiagnosticoException("Host ou domínio não pode ser vazio.");
        }
        if (host.length() > 255) {
            throw new DiagnosticoException("Host inválido ou muito longo.");
        }
        // Bloqueia injeção de comando (; & |) E caracteres de HTML/script (defesa em profundidade).
        for (char c : new char[]{' ', ';', '&', '|', '<', '>', '"', '\'', '`', '$', '\n', '\r'}) {
            if (host.indexOf(c) >= 0) {
                throw new DiagnosticoException("Caracteres inválidos detectados. Apenas hosts e IPs são permitidos.");
            }
        }
    }

    private String gerarIpAleatorio() {
        return (int) (Math.random() * 254 + 1) + "." +
                (int) (Math.random() * 255) + "." +
                (int) (Math.random() * 255) + "." +
                (int) (Math.random() * 254 + 1);
    }
}
