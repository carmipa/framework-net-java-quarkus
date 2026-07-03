package org.framework.net.shared;

import org.framework.net.analiseDidatica.exception.DnsResolucaoException;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.Locale;
import java.util.Set;

/**
 * Bloqueia resolução/consulta a endereços privados, reservados ou hostnames internos (mitiga SSRF).
 */
public final class NetworkAddressGuard {

    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
            "localhost",
            "metadata",
            "metadata.google.internal",
            "instance-data",
            "kubernetes.default",
            "kubernetes.default.svc"
    );

    private NetworkAddressGuard() {
    }

    public static void rejectBlockedHostname(String hostname) {
        String h = hostname == null ? "" : hostname.strip().toLowerCase(Locale.ROOT);
        if (h.isEmpty()) {
            return;
        }
        if (BLOCKED_HOSTNAMES.contains(h)) {
            throw new DnsResolucaoException("Hostname não permitido para resolução DNS: " + hostname);
        }
        if (h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".localhost")) {
            throw new DnsResolucaoException("Hostname interno não permitido: " + hostname);
        }
    }

    public static void rejectNonPublicAddress(InetAddress address, String context) {
        if (address == null) {
            throw new DnsResolucaoException("Endereço inválido em " + context + ".");
        }
        if (address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || address.isAnyLocalAddress()) {
            throw new DnsResolucaoException(
                    "Endereço privado, reservado ou local não permitido em " + context + ": "
                            + address.getHostAddress());
        }
        if (address instanceof Inet4Address inet4 && isIpv4NonPublic(inet4)) {
            throw new DnsResolucaoException(
                    "Endereço IPv4 não público não permitido em " + context + ": " + address.getHostAddress());
        }
        if (address instanceof Inet6Address inet6 && isIpv6NonPublic(inet6)) {
            throw new DnsResolucaoException(
                    "Endereço IPv6 não público não permitido em " + context + ": " + address.getHostAddress());
        }
    }

    private static boolean isIpv4NonPublic(Inet4Address address) {
        byte[] octets = address.getAddress();
        int b0 = octets[0] & 0xFF;
        int b1 = octets[1] & 0xFF;
        // 0.0.0.0/8, 10.0.0.0/8, 100.64.0.0/10, 127.0.0.0/8, 169.254.0.0/16, 172.16.0.0/12,
        // 192.0.0.0/24, 192.0.2.0/24, 192.168.0.0/16, 198.18.0.0/15, 198.51.100.0/24, 203.0.113.0/24, 240.0.0.0/4
        if (b0 == 0 || b0 == 10 || b0 == 127) {
            return true;
        }
        if (b0 == 100 && b1 >= 64 && b1 <= 127) {
            return true;
        }
        if (b0 == 169 && b1 == 254) {
            return true;
        }
        if (b0 == 172 && b1 >= 16 && b1 <= 31) {
            return true;
        }
        if (b0 == 192 && b1 == 0) {
            return true;
        }
        if (b0 == 192 && b1 == 0) {
            return true;
        }
        if (b0 == 192 && b1 == 168) {
            return true;
        }
        if (b0 == 198 && (b1 == 18 || b1 == 19)) {
            return true;
        }
        if (b0 == 198 && b1 == 51) {
            return true;
        }
        if (b0 == 203 && b1 == 0) {
            return true;
        }
        return b0 >= 240;
    }

    private static boolean isIpv6NonPublic(Inet6Address address) {
        if (address.isIPv4CompatibleAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        int b0 = bytes[0] & 0xFF;
        // Unique local fc00::/7, link-local fe80::/10
        return (b0 & 0xFE) == 0xFC || b0 == 0xFE;
    }
}
