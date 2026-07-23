package org.framework.net.analiseDidatica.infrastructure.dns;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.config.DnsConfig;
import org.framework.net.analiseDidatica.exception.DnsResolucaoException;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.framework.net.shared.NetworkAddressGuard;
import org.framework.net.telemetria.TelemetriaLogger;
import org.jboss.logging.Logger;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class DnsResolver {

    private static final Logger LOG = Logger.getLogger(DnsResolver.class);

    @Inject
    DnsConfig dnsConfig;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /** Teto de entradas do cache DNS. Impede crescimento ilimitado dirigido por entrada externa. */
    private static final int MAX_CACHE = 1_000;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public String resolverComCache(String hostname) {
        String h = normalizar(hostname);
        long now = System.currentTimeMillis() / 1000;
        CacheEntry cached = cache.get(h);
        if (cached != null && cached.expiresAt > now) {
            telemetriaLogger.logEvent("info", "analiseDidatica", "dns_cache",
                    Map.of("status", "hit", "hostname", h));
            return cached.ip;
        }
        telemetriaLogger.logEvent("info", "analiseDidatica", "dns_cache",
                Map.of("status", "miss", "hostname", h));
        NetworkAddressGuard.rejectBlockedHostname(h);
        String ip = resolverLive(h);
        guardarCache(h, ip, now);
        return ip;
    }

    /**
     * Grava a resolução no cache aplicando teto de tamanho e expurgo de entradas vencidas.
     *
     * <p><b>Propósito de negócio:</b> o cache DNS existe para evitar reconsultar o mesmo domínio
     * a cada análise didática. Sem limite, porém, cada hostname distinto consultado por um
     * usuário (ou crawler) virava uma entrada permanente em heap — vazamento dirigido por
     * entrada externa, um dos fatores do consumo de memória em produção.
     *
     * <p><b>Invariantes do domínio:</b> o cache nunca ultrapassa {@link #MAX_CACHE} entradas;
     * entradas expiradas (TTL de {@link DnsConfig#cacheTtlSeconds()}) são removidas antes de
     * qualquer descarte por lotação, de modo que o TTL continua sendo a política primária.
     *
     * <p><b>Comportamento em caso de falha:</b> não lança. Se mesmo após o expurgo o cache
     * seguir cheio (todas as entradas válidas), o cache é limpo por completo — degrada para
     * cache-miss na próxima consulta, jamais para consumo ilimitado de memória.
     *
     * @param hostname hostname já normalizado
     * @param ip       endereço resolvido
     * @param nowSec   instante corrente em segundos (epoch)
     */
    private void guardarCache(String hostname, String ip, long nowSec) {
        if (cache.size() >= MAX_CACHE) {
            cache.entrySet().removeIf(e -> e.getValue().expiresAt <= nowSec);
            if (cache.size() >= MAX_CACHE) {
                cache.clear();
            }
        }
        cache.put(hostname, new CacheEntry(ip, nowSec + dnsConfig.cacheTtlSeconds()));
    }

    private String resolverLive(String hostname) {
        long started = System.nanoTime();
        try {
            var future = executor.submit(() -> {
                InetAddress resolved = InetAddress.getByName(hostname);
                NetworkAddressGuard.rejectNonPublicAddress(resolved, "resolução DNS");
                return resolved.getHostAddress();
            });
            String ip = future.get(dnsConfig.resolveTimeoutSeconds(), TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("status", "ok");
            fields.put("hostname", hostname);
            fields.put("ip", ip);
            fields.put("elapsedMs", elapsedMs);
            telemetriaLogger.logEvent("info", "analiseDidatica", "dns_resolve", fields);
            return ip;
        } catch (TimeoutException ex) {
            telemetriaLogger.logEvent("warn", "analiseDidatica", "dns_resolve",
                    Map.of("status", "timeout", "hostname", hostname));
            throw new DnsResolucaoException(
                    "Timeout ao resolver DNS do domínio informado. Tente novamente em alguns segundos.", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof java.net.UnknownHostException) {
                throw new DnsResolucaoException(
                        "Não foi possível resolver o domínio/hostname informado: " + hostname, cause);
            }
            throw new DnsResolucaoException("Erro interno ao resolver DNS. Tente novamente.", ex);
        } catch (Exception ex) {
            throw new DnsResolucaoException("Erro interno ao resolver DNS. Tente novamente.", ex);
        }
    }

    private static String normalizar(String hostname) {
        String normalized = hostname == null ? "" : hostname.strip().toLowerCase();
        if (normalized.isEmpty()) {
            throw new EntradaInvalidaException("Domínio/hostname vazio.");
        }
        return normalized;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private record CacheEntry(String ip, long expiresAt) {
    }
}
