package org.framework.net.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrfTokenServiceTest {

    @Test
    void tokenValidoAposEmissao() {
        CsrfTokenService service = new CsrfTokenService();
        inject(service, true, "test-secret");
        String token = service.issueToken();
        assertTrue(service.isValid(token));
    }

    @Test
    void tokenAlteradoEhInvalido() {
        CsrfTokenService service = new CsrfTokenService();
        inject(service, true, "test-secret");
        String token = service.issueToken() + "x";
        assertFalse(service.isValid(token));
    }

    @Test
    void desabilitadoAceitaVazio() {
        CsrfTokenService service = new CsrfTokenService();
        inject(service, false, "test-secret");
        assertTrue(service.isValid(""));
    }

    private static void inject(CsrfTokenService service, boolean enabled, String secret) {
        try {
            var enabledField = CsrfTokenService.class.getDeclaredField("csrfEnabled");
            enabledField.setAccessible(true);
            enabledField.set(service, enabled);
            var secretField = CsrfTokenService.class.getDeclaredField("csrfSecret");
            secretField.setAccessible(true);
            secretField.set(service, secret);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
