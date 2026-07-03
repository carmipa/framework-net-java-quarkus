package org.framework.net.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpCidrInputNormalizerTest {

    @Test
    void splitIpAndCidrExtraiBarraDoCampoIp() {
        IpCidrInputNormalizer.SplitResult result =
                IpCidrInputNormalizer.splitIpAndCidr("172.19.0.0/16", "");
        assertEquals("172.19.0.0", result.ip());
        assertEquals("16", result.cidrRaw());
    }

    @Test
    void splitIpAndCidrPreservaCidrExplicito() {
        IpCidrInputNormalizer.SplitResult result =
                IpCidrInputNormalizer.splitIpAndCidr("172.19.0.0/16", "24");
        assertEquals("172.19.0.0", result.ip());
        assertEquals("24", result.cidrRaw());
    }

    @Test
    void looksLikeIpv4OrCidrReconheceNotacaoCidr() {
        assertTrue(IpCidrInputNormalizer.looksLikeIpv4OrCidr("192.168.1.1/24"));
        assertFalse(IpCidrInputNormalizer.looksLikeIpv4OrCidr("google.com"));
    }
}
