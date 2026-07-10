package org.framework.net.telemetria;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@ApplicationScoped
public class TelemetriaDashboardService {

    private static final ZoneId ZONA = ZoneId.systemDefault();
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONA);
    private static final DateTimeFormatter DDMM_HHMM = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZONA);
    private static final DateTimeFormatter ATUALIZADO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZONA);

    private static final Set<String> EXT_ESTATICAS = Set.of(
            "css", "js", "png", "jpg", "jpeg", "gif", "svg", "ico", "webp", "woff", "woff2", "ttf", "map");

    @Inject
    TelemetriaStore store;

    @Inject
    TelemetriaConsoleBuffer console;

    public TelemetriaDashboard montar(int limiteConsole, int janelaMinutos) {
        TelemetriaResumo resumo = store.gerarResumo(500);
        List<TelemetriaEvent> todos = store.snapshotEventos();

        Instant agora = Instant.now();
        Instant desde = janelaMinutos > 0 ? agora.minus(janelaMinutos, ChronoUnit.MINUTES) : Instant.EPOCH;

        List<TelemetriaEvent> janela = new ArrayList<>();
        List<TelemetriaEvent> http = new ArrayList<>();
        for (TelemetriaEvent e : todos) {
            if (e.timestamp() == null || e.timestamp().isBefore(desde)) {
                continue;
            }
            janela.add(e);
            if ("http_access".equals(e.evento()) && e.httpStatus() != null && !isRuido(e.httpPath())) {
                http.add(e);
            }
        }

        long http2xx = 0, http3xx = 0, http4xx = 0, http5xx = 0;
        Map<String, Long> metodos = new LinkedHashMap<>();
        List<Long> duracoes = new ArrayList<>();
        Map<String, long[]> modAgg = new LinkedHashMap<>();       // modulo -> [total, ok, erro]
        Map<String, List<Long>> modDur = new LinkedHashMap<>();    // modulo -> durações
        Map<String, EndpointAcc> endpoints = new LinkedHashMap<>();

        for (TelemetriaEvent e : http) {
            int st = e.httpStatus();
            switch (st / 100) {
                case 2 -> http2xx++;
                case 3 -> http3xx++;
                case 4 -> http4xx++;
                case 5 -> http5xx++;
                default -> { }
            }
            if (e.httpMethod() != null) {
                metodos.merge(e.httpMethod(), 1L, Long::sum);
            }
            Long dur = e.durationMs();
            if (dur != null) {
                duracoes.add(dur);
            }

            String modulo = moduloDePath(e.httpPath());
            long[] agg = modAgg.computeIfAbsent(modulo, k -> new long[3]);
            agg[0]++;
            if (st >= 400) {
                agg[2]++;
            } else {
                agg[1]++;
            }
            if (dur != null) {
                modDur.computeIfAbsent(modulo, k -> new ArrayList<>()).add(dur);
            }

            String chave = (e.httpMethod() == null ? "GET" : e.httpMethod()) + " " + normalizarPath(e.httpPath());
            EndpointAcc acc = endpoints.computeIfAbsent(chave, k -> new EndpointAcc());
            acc.chamadas++;
            if (dur != null) {
                acc.duracoes.add(dur);
            }
            if (st >= 500) {
                acc.err5xx++;
            } else if (st >= 400) {
                acc.err4xx++;
            }
        }

        long httpTotal = http.size();
        long ok = http2xx + http3xx;
        double taxaSucesso = httpTotal == 0 ? 0 : arredondar(ok * 100.0 / httpTotal);
        double taxaErroServidor = httpTotal == 0 ? 0 : arredondar(http5xx * 100.0 / httpTotal);

        duracoes.sort(Comparator.naturalOrder());
        TelemetriaDashboard.Latencia latencia = new TelemetriaDashboard.Latencia(
                percentil(duracoes, 50),
                percentil(duracoes, 90),
                percentil(duracoes, 95),
                percentil(duracoes, 99),
                duracoes.isEmpty() ? 0 : duracoes.get(duracoes.size() - 1),
                media(duracoes));

        List<TelemetriaDashboard.ModuloStat> porModulo = new ArrayList<>();
        for (Map.Entry<String, long[]> en : modAgg.entrySet()) {
            List<Long> ord = new ArrayList<>(modDur.getOrDefault(en.getKey(), List.of()));
            ord.sort(Comparator.naturalOrder());
            long[] v = en.getValue();
            porModulo.add(new TelemetriaDashboard.ModuloStat(en.getKey(), v[0], v[1], v[2], percentil(ord, 95)));
        }
        porModulo.sort(Comparator.comparingLong(TelemetriaDashboard.ModuloStat::total).reversed());

        List<TelemetriaDashboard.EndpointStat> stats = new ArrayList<>();
        for (Map.Entry<String, EndpointAcc> en : endpoints.entrySet()) {
            EndpointAcc a = en.getValue();
            List<Long> ord = new ArrayList<>(a.duracoes);
            ord.sort(Comparator.naturalOrder());
            String[] mp = en.getKey().split(" ", 2);
            stats.add(new TelemetriaDashboard.EndpointStat(
                    mp[0], mp.length > 1 ? mp[1] : "", a.chamadas, percentil(ord, 95),
                    a.err4xx + a.err5xx, a.err4xx, a.err5xx));
        }
        List<TelemetriaDashboard.EndpointStat> topLentos = stats.stream()
                .sorted(Comparator.comparingLong(TelemetriaDashboard.EndpointStat::p95)
                        .thenComparingLong(TelemetriaDashboard.EndpointStat::chamadas).reversed())
                .limit(8)
                .toList();
        List<TelemetriaDashboard.EndpointStat> topErros = stats.stream()
                .filter(s -> s.erros() > 0)
                .sorted(Comparator.comparingLong(TelemetriaDashboard.EndpointStat::erros).reversed())
                .limit(8)
                .toList();

        List<TelemetriaDashboard.AtividadeMinuto> atividade = montarAtividade(janela, janelaMinutos);
        List<String> consoleLinhas = montarConsole(limiteConsole);

        return new TelemetriaDashboard(
                resumo,
                janelaMinutos,
                ATUALIZADO.format(agora),
                janela.size(),
                httpTotal, http2xx, http3xx, http4xx, http5xx,
                taxaSucesso, taxaErroServidor,
                latencia, metodos, porModulo, topLentos, topErros, atividade,
                consoleLinhas,
                store.pastaLogs().toAbsolutePath().toString());
    }

    private List<TelemetriaDashboard.AtividadeMinuto> montarAtividade(List<TelemetriaEvent> janela, int janelaMinutos) {
        int bucketMin = Math.max(1, (int) Math.ceil((janelaMinutos > 0 ? janelaMinutos : 120) / 120.0));
        boolean rotuloLongo = janelaMinutos > 1440 || janelaMinutos == 0;
        Map<Long, long[]> buckets = new TreeMap<>();
        for (TelemetriaEvent e : janela) {
            if (e.timestamp() == null) {
                continue;
            }
            long epochMin = e.timestamp().getEpochSecond() / 60;
            long chave = (epochMin / bucketMin) * bucketMin;
            long[] b = buckets.computeIfAbsent(chave, k -> new long[2]);
            b[0]++;
            if ("http_access".equals(e.evento()) && e.httpStatus() != null && e.httpStatus() >= 400) {
                b[1]++;
            }
        }
        List<TelemetriaDashboard.AtividadeMinuto> atividade = new ArrayList<>();
        for (Map.Entry<Long, long[]> en : buckets.entrySet()) {
            Instant inst = Instant.ofEpochSecond(en.getKey() * 60);
            String rotulo = (rotuloLongo ? DDMM_HHMM : HHMM).format(inst);
            atividade.add(new TelemetriaDashboard.AtividadeMinuto(rotulo, en.getValue()[0], en.getValue()[1]));
        }
        return atividade;
    }

    public List<String> montarConsole(int limite) {
        int max = Math.max(1, Math.min(limite, 500));
        List<String> buffer = console.snapshot(max);
        List<String> arquivo = store.lerUltimasLinhasArquivoLog(max);
        if (buffer.isEmpty()) {
            return arquivo;
        }
        if (arquivo.isEmpty()) {
            return buffer;
        }
        Set<String> vistos = new LinkedHashSet<>();
        List<String> mesclado = new ArrayList<>();
        for (String linha : arquivo) {
            if (vistos.add(linha)) {
                mesclado.add(linha);
            }
        }
        for (String linha : buffer) {
            if (vistos.add(linha)) {
                mesclado.add(linha);
            }
        }
        int inicio = Math.max(0, mesclado.size() - max);
        return List.copyOf(mesclado.subList(inicio, mesclado.size()));
    }

    public void limparConsole() {
        console.limpar();
    }

    // ---- helpers de agregação ----

    private static boolean isRuido(String path) {
        if (path == null || path.isBlank()) {
            return true;
        }
        if (path.startsWith("/telemetria/api") || path.startsWith("/q/") || path.startsWith("/web/")) {
            return true;
        }
        int dot = path.lastIndexOf('.');
        if (dot > -1 && dot < path.length() - 1) {
            String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
            return EXT_ESTATICAS.contains(ext);
        }
        return false;
    }

    private static String moduloDePath(String path) {
        if (path == null || path.isBlank() || path.equals("/")) {
            return "Início";
        }
        String p = path.startsWith("/") ? path.substring(1) : path;
        int barra = p.indexOf('/');
        String seg = barra > -1 ? p.substring(0, barra) : p;
        return switch (seg) {
            case "localizacao" -> "Localização";
            case "portas" -> "Portas";
            case "protocolos" -> "Protocolos";
            case "resolucao-problemas" -> "Resolução";
            case "trafego" -> "Tráfego";
            case "diagnostico" -> "Diagnóstico";
            case "seguranca" -> "Segurança ACL";
            case "telemetria" -> "Telemetria";
            case "documentacao" -> "Documentação";
            case "informacoes" -> "GeoIP";
            case "api" -> apiModulo(p);
            default -> "Análise Didática";
        };
    }

    private static String apiModulo(String p) {
        String[] segs = p.split("/");
        if (segs.length >= 2) {
            return switch (segs[1]) {
                case "informacoes" -> "GeoIP";
                case "localizacao" -> "Localização";
                case "diagnostico" -> "Diagnóstico";
                case "trafego" -> "Tráfego";
                default -> "API";
            };
        }
        return "API";
    }

    private static String normalizarPath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path;
    }

    private static long percentil(List<Long> ordenado, double p) {
        if (ordenado == null || ordenado.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * ordenado.size()) - 1;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= ordenado.size()) {
            idx = ordenado.size() - 1;
        }
        return ordenado.get(idx);
    }

    private static long media(List<Long> valores) {
        if (valores.isEmpty()) {
            return 0;
        }
        long soma = 0;
        for (long v : valores) {
            soma += v;
        }
        return soma / valores.size();
    }

    private static double arredondar(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static final class EndpointAcc {
        long chamadas;
        long err4xx;
        long err5xx;
        final List<Long> duracoes = new ArrayList<>();
    }
}
