package org.framework.net.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class AdminApiKeyService {

    public static final String HEADER_NAME = "X-Admin-Api-Key";
    public static final String COOKIE_NAME = "ADMIN_API_KEY";

    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/export"
    );

    @ConfigProperty(name = "framework.security.admin-api-key", defaultValue = "")
    String configuredKey;

    @ConfigProperty(name = "framework.security.admin-api-key-required", defaultValue = "false")
    boolean adminApiKeyRequired;

    public boolean isEnforcementActive() {
        return adminApiKeyRequired && configuredKey != null && !configuredKey.isBlank();
    }

    public boolean isProtectedPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        return PROTECTED_PREFIXES.stream().anyMatch(prefix ->
                normalized.equals(prefix) || normalized.startsWith(prefix + "/"));
    }

    public boolean isValid(String submittedKey) {
        if (!isEnforcementActive()) {
            return true;
        }
        if (submittedKey == null || submittedKey.isBlank()) {
            return false;
        }
        return constantTimeEquals(configuredKey.strip(), submittedKey.strip());
    }

    public String extractFromCookie(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return "";
        }
        for (String chunk : cookieHeader.split(";")) {
            String trimmed = chunk.strip();
            if (trimmed.startsWith(COOKIE_NAME + "=")) {
                return trimmed.substring(COOKIE_NAME.length() + 1);
            }
        }
        return "";
    }

    public String configuredKeyForDisplay() {
        if (!isEnforcementActive()) {
            return "";
        }
        return configuredKey;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < left.length; i++) {
            diff |= left[i] ^ right[i];
        }
        return diff == 0;
    }
}
