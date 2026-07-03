package org.framework.net.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@ApplicationScoped
public class CsrfTokenService {

    private static final String COOKIE_NAME = "XSRF-TOKEN";
    private static final long TTL_SECONDS = 3600;

    @ConfigProperty(name = "framework.security.csrf-enabled", defaultValue = "true")
    boolean csrfEnabled;

    @ConfigProperty(name = "framework.security.csrf-secret", defaultValue = "framework-net-dev-csrf-secret")
    String csrfSecret;

    private final SecureRandom random = new SecureRandom();

    public boolean isEnabled() {
        return csrfEnabled;
    }

    public String cookieName() {
        return COOKIE_NAME;
    }

    public String issueToken() {
        long expiry = Instant.now().getEpochSecond() + TTL_SECONDS;
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String payload = expiry + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        String sig = sign(payload);
        return payload + "." + sig;
    }

    public boolean isValid(String token) {
        if (!csrfEnabled || token == null || token.isBlank()) {
            return !csrfEnabled;
        }
        String[] parts = token.strip().split("\\.");
        if (parts.length != 3) {
            return false;
        }
        try {
            long expiry = Long.parseLong(parts[0]);
            if (Instant.now().getEpochSecond() > expiry) {
                return false;
            }
            String payload = parts[0] + "." + parts[1];
            return constantTimeEquals(sign(payload), parts[2]);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public String tokenFromCookie(String cookieHeader) {
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

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(deriveKey(), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao assinar token CSRF.", ex);
        }
    }

    private byte[] deriveKey() {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(csrfSecret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao derivar chave CSRF.", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
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
