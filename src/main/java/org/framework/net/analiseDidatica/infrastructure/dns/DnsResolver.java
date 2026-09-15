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

import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.ResolverConfig;
import org.xbill.DNS.Type;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * Resolve o registro <b>AAAA</b> (IPv6) de um hostname, com as mesmas guardas do caminho IPv4.
     *
     * <p><b>Propósito de negócio:</b> alimenta a aba "Domínio → AAAA" da Calculadora IPv6 — mostra o
     * endereço IPv6 real que um nome publica, o análogo IPv6 da resolução A já usada na Análise.
     *
     * <p><b>Invariantes do domínio:</b> passa pela mesma lista de hostnames bloqueados
     * ({@link NetworkAddressGuard#rejectBlockedHostname}) e recusa endereço não-público resolvido
     * ({@link NetworkAddressGuard#rejectNonPublicAddress}) — mitiga SSRF exatamente como o A record.
     * Usa o mesmo cache (chave prefixada {@code AAAA|}) e teto de tamanho, e o mesmo timeout.
     *
     * <p><b>Comportamento em caso de falha:</b> domínio sem AAAA, inexistente ou não-público lança
     * {@link DnsResolucaoException}; timeout também. Nunca devolve endereço privado nem trava a thread.
     *
     * @param hostname domínio/hostname a resolver
     * @return o endereço IPv6 (AAAA) em texto, sem zone index
     */
    public String resolverAaaaComCache(String hostname) {
        String h = normalizar(hostname);
        long now = System.currentTimeMillis() / 1000;
        String key = "AAAA|" + h;
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt > now) {
            telemetriaLogger.logEvent("info", "ipv6", "dns_cache",
                    Map.of("status", "hit", "hostname", h, "tipo", "AAAA"));
            return cached.ip;
        }
        telemetriaLogger.logEvent("info", "ipv6", "dns_cache",
                Map.of("status", "miss", "hostname", h, "tipo", "AAAA"));
        NetworkAddressGuard.rejectBlockedHostname(h);
        String ip = resolverAaaaLive(h);
        guardarCache(key, ip, now);
        return ip;
    }

    private String resolverAaaaLive(String hostname) {
        long started = System.nanoTime();
        try {
            var future = executor.submit(() -> consultarAaaa(hostname));
            // Teto folgado: o ExtendedResolver pode tentar vários servidores em sequência, cada um com
            // o timeout da config; o future só corta um travamento total, não o fallback normal.
            long tetoSegundos = Math.max(10L, dnsConfig.resolveTimeoutSeconds() * 4L + 2L);
            String ip = future.get(tetoSegundos, TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("status", "ok");
            fields.put("hostname", hostname);
            fields.put("ip", ip);
            fields.put("tipo", "AAAA");
            fields.put("elapsedMs", elapsedMs);
            telemetriaLogger.logEvent("info", "ipv6", "dns_resolve", fields);
            return ip;
        } catch (TimeoutException ex) {
            telemetriaLogger.logEvent("warn", "ipv6", "dns_resolve",
                    Map.of("status", "timeout", "hostname", hostname, "tipo", "AAAA"));
            throw new DnsResolucaoException(
                    "Timeout ao resolver o AAAA do domínio informado. Tente novamente em alguns segundos.", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof DnsResolucaoException dre) {
                throw dre;
            }
            telemetriaLogger.logEvent("warn", "ipv6", "dns_resolve",
                    Map.of("status", "erro", "hostname", hostname, "tipo", "AAAA"));
            throw new DnsResolucaoException("Erro interno ao resolver AAAA. Tente novamente.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DnsResolucaoException("Resolução AAAA interrompida. Tente novamente.", ex);
        }
    }

    /**
     * Consulta o registro AAAA via DNS diretamente (JNDI DNS), <b>não</b> pela pilha
     * {@code getaddrinfo}/{@code InetAddress.getAllByName}.
     *
     * <p><b>Porquê:</b> {@code getaddrinfo} usa {@code AI_ADDRCONFIG} — omite registros AAAA quando o
     * host (ou o container Docker) não tem endereço IPv6 global configurado. Medido em produção: o
     * container não tem IPv6, então a resolução por {@code getAllByName} devolvia "sem AAAA" para
     * QUALQUER domínio, inclusive dual-stack. A consulta DNS direta lê o RDATA do registro AAAA
     * independente da conectividade IPv6 local — que é o que a aba precisa mostrar.</p>
     *
     * <p><b>Segurança:</b> só uma consulta DNS de registro AAAA é feita (dnsjava), sobre um nome que
     * já passou por {@link NetworkAddressGuard#rejectBlockedHostname} e foi validado como hostname
     * (sem esquema, sem {@code /}) — não há JNDI nem interpretação de URL LDAP/RMI. O endereço
     * retornado ainda passa por {@link NetworkAddressGuard#rejectNonPublicAddress} (mitiga SSRF/rebinding).</p>
     */
    private String consultarAaaa(String hostname) throws Exception {
        Lookup lookup = new Lookup(hostname, Type.AAAA);
        lookup.setResolver(resolverAaaa());
        Record[] registros = lookup.run();
        if (lookup.getResult() != Lookup.SUCCESSFUL || registros == null || registros.length == 0) {
            throw new DnsResolucaoException("Não foi possível resolver um endereço IPv6 (AAAA) para: "
                    + hostname + ". O domínio pode não publicar registro AAAA.");
        }
        for (Record registro : registros) {
            if (registro instanceof AAAARecord aaaa) {
                InetAddress addr = aaaa.getAddress();
                NetworkAddressGuard.rejectNonPublicAddress(addr, "resolução AAAA");
                String ip = addr.getHostAddress();
                int pct = ip.indexOf('%');
                return pct >= 0 ? ip.substring(0, pct) : ip;
            }
        }
        throw new DnsResolucaoException("Não foi possível resolver um endereço IPv6 (AAAA) para: "
                + hostname + ". O domínio pode não publicar registro AAAA.");
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

    /**
     * Monta o resolver da consulta AAAA: os servidores DNS do sistema primeiro (no container, o DNS
     * do Docker que a aba de Domínio IPv4 já usa em produção), com DNS públicos anycast como fallback
     * para ambientes cuja detecção do SO falha (medido: dev Windows devolvia "network error"). O
     * {@link ExtendedResolver} passa ao próximo servidor quando um não responde.
     */
    private Resolver resolverAaaa() throws Exception {
        List<String> servidores = new ArrayList<>();
        try {
            for (InetSocketAddress isa : ResolverConfig.getCurrentConfig().servers()) {
                servidores.add(isa.getAddress().getHostAddress());
            }
        } catch (RuntimeException ignore) {
            // detecção de resolver do SO indisponível — segue só com os públicos abaixo
        }
        servidores.add("1.1.1.1");
        servidores.add("8.8.8.8");
        servidores.add("9.9.9.9");
        Resolver resolver = new ExtendedResolver(servidores.toArray(new String[0]));
        resolver.setTimeout(Duration.ofSeconds(Math.max(1, dnsConfig.resolveTimeoutSeconds())));
        return resolver;
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
