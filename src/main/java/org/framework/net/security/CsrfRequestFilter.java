package org.framework.net.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Provider
@Priority(Priorities.AUTHENTICATION + 10)
public class CsrfRequestFilter implements ContainerRequestFilter {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "DELETE", "PATCH");

    @Inject
    CsrfTokenService csrfTokenService;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!csrfTokenService.isEnabled() || !MUTATING.contains(requestContext.getMethod())) {
            return;
        }
        String path = requestContext.getUriInfo().getPath();
        if (path != null && (path.startsWith("/q/") || path.startsWith("/dev-ui"))) {
            return;
        }

        String cookieToken = csrfTokenService.tokenFromCookie(requestContext.getHeaderString("Cookie"));
        String submitted = firstNonBlank(
                requestContext.getHeaderString("X-CSRF-Token"),
                extractFormToken(requestContext)
        );

        if (!isValidPair(cookieToken, submitted)) {
            requestContext.abortWith(Response.status(403)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"erro\":\"Token CSRF inválido ou ausente. Recarregue a página e tente novamente.\"}")
                    .build());
        }
    }

    private String extractFormToken(ContainerRequestContext ctx) throws IOException {
        MediaType mediaType = ctx.getMediaType();
        if (mediaType == null || !MediaType.APPLICATION_FORM_URLENCODED_TYPE.isCompatible(mediaType)) {
            return "";
        }
        InputStream entityStream = ctx.getEntityStream();
        if (entityStream == null) {
            return "";
        }
        byte[] body = entityStream.readAllBytes();
        ctx.setEntityStream(new ByteArrayInputStream(body));
        return parseUrlEncodedParam(new String(body, StandardCharsets.UTF_8), "csrf_token");
    }

    private boolean isValidPair(String cookieToken, String submitted) {
        return csrfTokenService.isValid(cookieToken)
                && csrfTokenService.isValid(submitted)
                && constantTimeEquals(cookieToken, submitted);
    }

    private static String parseUrlEncodedParam(String body, String name) {
        if (body == null || body.isBlank()) {
            return "";
        }
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8) : pair;
            if (!name.equals(key)) {
                continue;
            }
            String value = idx >= 0 ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8) : "";
            return value;
        }
        return "";
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.strip();
        }
        if (b != null && !b.isBlank()) {
            return b.strip();
        }
        return "";
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
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
