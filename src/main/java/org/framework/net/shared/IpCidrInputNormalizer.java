package org.framework.net.shared;

/**
 * Normaliza entradas IPv4 com notação CIDR opcional no mesmo campo (ex.: 172.19.0.0/16).
 * Espelha {@code _build_form_data} do projeto Python de referência.
 */
public final class IpCidrInputNormalizer {

    private IpCidrInputNormalizer() {
    }

    public record SplitResult(String ip, String cidrRaw) {
    }

    public static SplitResult splitIpAndCidr(String ipRaw, String cidrRaw) {
        String ip = ipRaw == null ? "" : ipRaw.strip();
        String cidr = cidrRaw == null ? "" : cidrRaw.strip();
        if (ip.contains("/")) {
            String[] parts = ip.split("/", 2);
            ip = parts[0].strip();
            if (cidr.isEmpty()) {
                cidr = parts[1].strip();
            }
        }
        return new SplitResult(ip, cidr);
    }

    /** Evita resolver DNS quando o usuário digitou IPv4 ou IPv4/CIDR. */
    public static boolean looksLikeIpv4OrCidr(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (char c : value.strip().toCharArray()) {
            if (!(Character.isDigit(c) || c == '.' || c == '/')) {
                return false;
            }
        }
        return true;
    }
}
