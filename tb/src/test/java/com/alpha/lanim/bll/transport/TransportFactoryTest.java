package com.alpha.lanim.bll.transport;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransportFactoryTest {

    @Test
    void testCreatePlainTcpTransport() {
        DuplexTransport transport = TransportFactory.create("plain");
        assertNotNull(transport);
        assertTrue(transport instanceof PlainTcpTransport);
    }

    @Test
    void testCreateTlsTcpTransport() {
        DuplexTransport transport = TransportFactory.create("tls");
        assertNotNull(transport);
        assertTrue(transport instanceof TlsTcpTransport);
    }

    @Test
    void testCreateInvalidMode() {
        // Assuming the factory defaults to TLS for invalid modes, or throws an exception.
        // Based on the document, the default is TLS. Let's assume it returns TLS for unknown.
        DuplexTransport transport = TransportFactory.create("unknown");
        assertNotNull(transport);
        assertTrue(transport instanceof TlsTcpTransport);
    }
}