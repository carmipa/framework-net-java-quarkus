package org.framework.net.security;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class RequestRateLimiter {

    @ConfigProperty(name = "framework.security.rate-limit-enabled", defaultValue = "true")
    boolean rateLimitEnabled;

    @ConfigProperty(name = "framework.security.rate-limit-per-minute", defaultValue = "120")
    int defaultLimitPerMinute;

    @ConfigProperty(name = "framework.security.rate-limit-heavy-per-minute", defaultValue = "30")
    int heavyLimitPerMinute;

    @ConfigProperty(name = "quarkus.http.proxy.proxy-address-forwarding", defaultValue = "false")
    boolean proxyAddressForwarding;

    @Inject
    CurrentVertxRequest currentVertxRequest;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong lastPruneMinute = new java.util.concurrent.atomic.AtomicLong(-1);

    public boolean allow(ContainerRequestContext ctx, boolean heavy) {
        if (!rateLimitEnabled) {
            return true;
        }
        int limit = heavy ? heavyLimitPerMinute : defaultLimitPerMinute;
        String key = clientKey(ctx) + "|" + normalizePath(ctx.getUriInfo().getPath()) + (heavy ? "|heavy" : "");
        long windowMinute = System.currentTimeMillis() / 60_000L;
        // Limpeza oportunista (sem scheduler): no máximo uma vez por minuto, evita crescimento
        // ilimitado do mapa de buckets (memory leak / vetor de DoS).
        long previousPrune = lastPruneMinute.get();
        if (windowMinute != previousPrune && lastPruneMinute.compareAndSet(previousPrune, windowMinute)) {
            prune();
        }
        Bucket bucket = buckets.compute(key, (k, existing) -> {
            if (existing == null || existing.windowMinute != windowMinute) {
                return new Bucket(windowMinute, new AtomicInteger(0));
            }
            return existing;
        });
        return bucket.counter.incrementAndGet() <= limit;
    }

    public void prune() {
        long current = System.currentTimeMillis() / 60_000L;
        buckets.entrySet().removeIf(e -> e.getValue().windowMinute < current - 2);
    }

    private String clientKey(ContainerRequestContext ctx) {
        if (proxyAddressForwarding) {
            String xff = ctx.getHeaderString("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].strip();
            }
            String realIp = ctx.getHeaderString("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.strip();
            }
        }
        try {
            if (currentVertxRequest != null && currentVertxRequest.getCurrent() != null
                    && currentVertxRequest.getCurrent().request() != null
                    && currentVertxRequest.getCurrent().request().remoteAddress() != null) {
                return currentVertxRequest.getCurrent().request().remoteAddress().host();
            }
        } catch (Exception ignored) {
            // fallback abaixo
        }
        return "anonymous";
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
    }

    private record Bucket(long windowMinute, AtomicInteger counter) {
    }
}
