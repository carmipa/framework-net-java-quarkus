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
        cache.put(h, new CacheEntry(ip, now + dnsConfig.cacheTtlSeconds()));
        return ip;
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
