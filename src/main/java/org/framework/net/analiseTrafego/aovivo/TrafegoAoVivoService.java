package org.framework.net.analiseTrafego.aovivo;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo.DispositivoBluetooth;
import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo.PacoteResumo;
import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo.PontoTempo;
import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo.RedeWifi;
import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo.TopHost;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Estado do tráfego ao vivo. Dois modos:
 * <ul>
 *   <li><b>demo</b>: simulação evoluída no servidor (VPS-safe, para ver o dashboard sem agente);</li>
 *   <li><b>agente</b>: dados reais enviados por um agente local (pcap4j + Wi-Fi/BT) via {@link #ingerir}.</li>
 * </ul>
 */
@ApplicationScoped
public class TrafegoAoVivoService {

    private static final DateTimeFormatter HMS =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int MAX_SERIE = 30;
    private static final int MAX_PACOTES = 25;
    private static final String[] PROTOS = {"TCP", "UDP", "TLS", "DNS", "ICMP", "ARP"};
    private static final int[] PESOS = {55, 18, 12, 8, 4, 3};

    private final ReentrantLock lock = new ReentrantLock();

    // ---- estado do modo demo ----
    private final AtomicLong demoTotal = new AtomicLong();
    private final Deque<PontoTempo> demoSerie = new ArrayDeque<>();
    private final Deque<PacoteResumo> demoPacotes = new ArrayDeque<>();
    private final Map<String, Long> demoProto = new LinkedHashMap<>();
    private final Map<String, Long> demoHosts = new LinkedHashMap<>();
    private long demoBytesTick;

    // ---- estado do modo agente (real) ----
    private volatile long agenteUltimoIngest;
    private final AtomicLong agenteTotal = new AtomicLong();
    private final Deque<PacoteResumo> agentePacotes = new ArrayDeque<>();
    private final Map<String, Long> agenteProto = new LinkedHashMap<>();
    private final Map<String, Long> agenteHosts = new LinkedHashMap<>();
    private final Deque<PontoTempo> agenteSerie = new ArrayDeque<>();
    private volatile List<RedeWifi> agenteWifi = List.of();
    private volatile List<DispositivoBluetooth> agenteBt = List.of();

    /** Avança um "tick" da simulação e devolve o snapshot demo. */
    public SnapshotAoVivo snapshotDemo() {
        lock.lock();
        try {
            int novos = ThreadLocalRandom.current().nextInt(8, 45);
            demoBytesTick = 0;
            for (int i = 0; i < novos; i++) {
                gerarPacoteDemo();
            }
            demoTotal.addAndGet(novos);
            demoSerie.addLast(new PontoTempo(HMS.format(Instant.now()), novos));
            while (demoSerie.size() > MAX_SERIE) {
                demoSerie.removeFirst();
            }
            double kbps = demoBytesTick * 8.0 / 1000.0;
            List<RedeWifi> wifi = wifiDemo();
            return new SnapshotAoVivo(
                    "demo", true, demoTotal.get(), novos, arredondar(kbps),
                    new LinkedHashMap<>(demoProto),
                    new ArrayList<>(demoSerie),
                    topHosts(demoHosts),
                    new ArrayList<>(demoPacotes),
                    wifi, bluetoothDemo(),
                    (int) wifi.stream().filter(RedeWifi::aberta).count(),
                    HMS.format(Instant.now()));
        } finally {
            lock.unlock();
        }
    }

    /** Snapshot do modo agente (dados reais). Vazio até o agente enviar algo. */
    public SnapshotAoVivo snapshotAgente() {
        lock.lock();
        try {
            boolean conectado = agenteUltimoIngest > 0
                    && (System.currentTimeMillis() - agenteUltimoIngest) < 10_000;
            long ppsAtual = agenteSerie.isEmpty() ? 0 : agenteSerie.peekLast().valor();
            return new SnapshotAoVivo(
                    "agente", conectado, agenteTotal.get(), ppsAtual, 0.0,
                    new LinkedHashMap<>(agenteProto),
                    new ArrayList<>(agenteSerie),
                    topHosts(agenteHosts),
                    new ArrayList<>(agentePacotes),
                    agenteWifi, agenteBt,
                    (int) agenteWifi.stream().filter(RedeWifi::aberta).count(),
                    HMS.format(Instant.now()));
        } finally {
            lock.unlock();
        }
    }

    /** Ingestão de dados reais vindos do agente local. */
    @SuppressWarnings("unchecked")
    public void ingerir(Map<String, Object> payload) {
        if (payload == null) {
            return;
        }
        lock.lock();
        try {
            agenteUltimoIngest = System.currentTimeMillis();
            long noTick = 0;
            Object pacotes = payload.get("pacotes");
            if (pacotes instanceof List<?> lista) {
                for (Object item : lista) {
                    if (item instanceof Map<?, ?> mapa) {
                        PacoteResumo p = mapToPacote((Map<String, Object>) mapa, agenteTotal.incrementAndGet());
                        agentePacotes.addFirst(p);
                        while (agentePacotes.size() > MAX_PACOTES) {
                            agentePacotes.removeLast();
                        }
                        agenteProto.merge(p.protocolo(), 1L, Long::sum);
                        if (p.origem() != null) {
                            agenteHosts.merge(p.origem(), 1L, Long::sum);
                        }
                        noTick++;
                    }
                }
            }
            agenteSerie.addLast(new PontoTempo(HMS.format(Instant.now()), noTick));
            while (agenteSerie.size() > MAX_SERIE) {
                agenteSerie.removeFirst();
            }
            Object wifi = payload.get("wifi");
            if (wifi instanceof List<?> lista) {
                List<RedeWifi> out = new ArrayList<>();
                for (Object item : lista) {
                    if (item instanceof Map<?, ?> mapa) {
                        out.add(mapToWifi((Map<String, Object>) mapa));
                    }
                }
                agenteWifi = out;
            }
            Object bt = payload.get("bluetooth");
            if (bt instanceof List<?> lista) {
                List<DispositivoBluetooth> out = new ArrayList<>();
                for (Object item : lista) {
                    if (item instanceof Map<?, ?> mapa) {
                        out.add(mapToBt((Map<String, Object>) mapa));
                    }
                }
                agenteBt = out;
            }
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------ demo helpers

    private void gerarPacoteDemo() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String proto = protoAleatorio();
        boolean saindo = r.nextBoolean();
        String local = "192.168.0." + r.nextInt(2, 40);
        String remoto = ipRemoto(proto);
        String origem = saindo ? local : remoto;
        String destino = saindo ? remoto : local;
        Integer sp = portaPara(proto, saindo, true);
        Integer dp = portaPara(proto, saindo, false);
        int tam = tamanhoPara(proto);
        demoBytesTick += tam;
        demoProto.merge(proto, 1L, Long::sum);
        demoHosts.merge(remoto, 1L, Long::sum);
        String info = infoPara(proto, dp);
        demoPacotes.addFirst(new PacoteResumo(demoTotal.get() + 1, HMS.format(Instant.now()),
                proto, origem, destino, sp, dp, tam, info));
        while (demoPacotes.size() > MAX_PACOTES) {
            demoPacotes.removeLast();
        }
    }

    private static String protoAleatorio() {
        int soma = 0;
        for (int p : PESOS) {
            soma += p;
        }
        int x = ThreadLocalRandom.current().nextInt(soma);
        int acc = 0;
        for (int i = 0; i < PROTOS.length; i++) {
            acc += PESOS[i];
            if (x < acc) {
                return PROTOS[i];
            }
        }
        return "TCP";
    }

    private static String ipRemoto(String proto) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String[] conhecidos = {"142.250.79.14", "20.190.159.0", "104.16.85.20", "8.8.8.8",
                "13.107.42.14", "151.101.1.140", "35.190.247.1"};
        return conhecidos[r.nextInt(conhecidos.length)];
    }

    private static Integer portaPara(String proto, boolean saindo, boolean origem) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int efemera = r.nextInt(49152, 65535);
        int servico = switch (proto) {
            case "TLS" -> 443;
            case "DNS" -> 53;
            case "UDP" -> r.nextBoolean() ? 443 : 123;
            case "TCP" -> r.nextBoolean() ? 80 : 443;
            default -> -1;
        };
        if (proto.equals("ICMP") || proto.equals("ARP")) {
            return null;
        }
        boolean ladoServico = saindo == !origem;
        return ladoServico ? servico : efemera;
    }

    private static int tamanhoPara(String proto) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return switch (proto) {
            case "ARP" -> 60;
            case "ICMP" -> 98;
            case "DNS" -> r.nextInt(70, 180);
            default -> r.nextInt(60, 1460);
        };
    }

    private static String infoPara(String proto, Integer dp) {
        return switch (proto) {
            case "TLS" -> ThreadLocalRandom.current().nextBoolean() ? "Application Data" : "Client Hello";
            case "DNS" -> "Standard query A";
            case "ARP" -> "Who has ...? Tell ...";
            case "ICMP" -> "Echo (ping) request";
            case "TCP" -> dp != null && dp == 80 ? "GET / HTTP/1.1" : "ACK";
            default -> "";
        };
    }

    private static List<RedeWifi> wifiDemo() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return List.of(
                new RedeWifi("CAFE_FREE", "a4:2b:8c:11:22:33", "Aberta", -55 + r.nextInt(-5, 6), true),
                new RedeWifi("AEROPORTO-WIFI", "de:ad:be:ef:00:01", "Aberta", -70 + r.nextInt(-5, 6), true),
                new RedeWifi("MinhaCasa_5G", "88:99:aa:bb:cc:dd", "WPA2-PSK", -42 + r.nextInt(-4, 5), false),
                new RedeWifi("NET_2G4", "10:20:30:40:50:60", "WPA2-PSK", -60 + r.nextInt(-5, 6), false),
                new RedeWifi("Vizinho6", "aa:bb:cc:dd:ee:ff", "WPA3-SAE", -75 + r.nextInt(-5, 6), false),
                new RedeWifi("IoT_Camera", "12:34:56:78:9a:bc", "WEP", -66 + r.nextInt(-5, 6), true));
    }

    private static List<DispositivoBluetooth> bluetoothDemo() {
        return List.of(
                new DispositivoBluetooth("Fone JBL Tune", "00:1A:7D:DA:71:11", "Áudio", true),
                new DispositivoBluetooth("Mouse Logitech", "F0:9E:4A:12:34:56", "Periférico", true),
                new DispositivoBluetooth("Galaxy Buds", "C8:3D:D4:AB:CD:EF", "Áudio", false),
                new DispositivoBluetooth("Smart TV Samsung", "5C:49:7D:00:11:22", "Descoberto", false));
    }

    // ------------------------------------------------------------------ shared helpers

    private List<TopHost> topHosts(Map<String, Long> hosts) {
        return hosts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .map(e -> new TopHost(e.getKey(), e.getValue()))
                .toList();
    }

    private static double arredondar(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static PacoteResumo mapToPacote(Map<String, Object> m, long seq) {
        return new PacoteResumo(seq,
                str(m.getOrDefault("timestamp", HMS.format(Instant.now()))),
                str(m.getOrDefault("protocolo", "?")),
                str(m.get("origem")), str(m.get("destino")),
                intOrNull(m.get("portaOrigem")), intOrNull(m.get("portaDestino")),
                intOrZero(m.get("tamanho")), str(m.getOrDefault("info", "")));
    }

    private static RedeWifi mapToWifi(Map<String, Object> m) {
        String seg = str(m.getOrDefault("seguranca", ""));
        boolean aberta = m.get("aberta") instanceof Boolean b ? b
                : seg.isBlank() || seg.equalsIgnoreCase("Aberta") || seg.equalsIgnoreCase("Open");
        return new RedeWifi(str(m.get("ssid")), str(m.getOrDefault("bssid", "")),
                seg.isBlank() ? "Aberta" : seg, intOrZero(m.get("sinal")), aberta);
    }

    private static DispositivoBluetooth mapToBt(Map<String, Object> m) {
        return new DispositivoBluetooth(str(m.get("nome")), str(m.getOrDefault("endereco", "")),
                str(m.getOrDefault("tipo", "")), m.get("pareado") instanceof Boolean b && b);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer intOrNull(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return o == null ? null : Integer.valueOf(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int intOrZero(Object o) {
        Integer v = intOrNull(o);
        return v == null ? 0 : v;
    }
}
