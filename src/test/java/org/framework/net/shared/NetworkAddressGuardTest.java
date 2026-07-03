package org.framework.net.shared;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkAddressGuardTest {

    @Test
    void rejeitaLocalhost() {
        assertThrows(Exception.class, () -> NetworkAddressGuard.rejectBlockedHostname("localhost"));
    }

    @Test
    void rejeitaIpv4Privado() throws Exception {
        InetAddress addr = InetAddress.getByName("10.0.0.1");
        assertThrows(Exception.class, () -> NetworkAddressGuard.rejectNonPublicAddress(addr, "teste"));
    }

    @Test
    void aceitaIpv4Publico() throws Exception {
        InetAddress addr = InetAddress.getByName("8.8.8.8");
        assertDoesNotThrow(() -> NetworkAddressGuard.rejectNonPublicAddress(addr, "teste"));
    }
}
