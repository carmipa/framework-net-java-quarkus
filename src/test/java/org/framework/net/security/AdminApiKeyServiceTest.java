package org.framework.net.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminApiKeyServiceTest {

    @Test
    void validaChaveCorreta() {
        AdminApiKeyService service = new AdminApiKeyService();
        inject(service, "segredo-123", true);
        assertTrue(service.isValid("segredo-123"));
        assertFalse(service.isValid("errado"));
    }

    @Test
    void enforcementDesligadoAceitaQualquerCoisa() {
        AdminApiKeyService service = new AdminApiKeyService();
        inject(service, "segredo-123", false);
        assertTrue(service.isValid(""));
    }

    @Test
    void detectaRotasProtegidas() {
        AdminApiKeyService service = new AdminApiKeyService();
        inject(service, "k", true);
        assertFalse(service.isProtectedPath("/telemetria/api/resumo"));
        assertFalse(service.isProtectedPath("/telemetria"));
        assertFalse(service.isProtectedPath("/history"));
        assertFalse(service.isProtectedPath("/history/catalog"));
        assertTrue(service.isProtectedPath("/export/json"));
        assertFalse(service.isProtectedPath("/documentacao"));
    }

    private static void inject(AdminApiKeyService service, String key, boolean required) {
        try {
            var keyField = AdminApiKeyService.class.getDeclaredField("configuredKey");
            keyField.setAccessible(true);
            keyField.set(service, key);
            var reqField = AdminApiKeyService.class.getDeclaredField("adminApiKeyRequired");
            reqField.setAccessible(true);
            reqField.set(service, required);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
