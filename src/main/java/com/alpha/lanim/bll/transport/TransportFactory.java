package com.alpha.lanim.bll.transport;

import com.alpha.lanim.bll.crypto.CertManager;
import com.alpha.lanim.util.Constants;
import java.io.IOException;
import java.net.Socket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class TransportFactory {

    private TransportFactory() {}

    /**
     * Creates a lightweight unconnected transport for testing.
     * The transport cannot send/receive until connected via {@link #createOutgoing}.
     */
    public static DuplexTransport create(String mode) {
        try {
            if (Constants.TRANSPORT_MODE_TLS.equals(mode) || !Constants.TRANSPORT_MODE_PLAIN.equals(mode)) {
                return new TlsTcpTransport(null);
            }
            return new PlainTcpTransport(null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static DuplexTransport createOutgoing(String mode, String host, int port,
                                                 CertManager certManager) throws Exception {
        if (Constants.TRANSPORT_MODE_TLS.equals(mode)) {
            SSLSocketFactory factory = certManager.createClientSSLContext().getSocketFactory();
            SSLSocket sslSocket = (SSLSocket) factory.createSocket(host, port);
            sslSocket.startHandshake();
            return new TlsTcpTransport(sslSocket);
        } else {
            Socket socket = new Socket(host, port);
            return new PlainTcpTransport(socket);
        }
    }

    public static DuplexTransport wrapAccepted(String mode, Socket acceptedSocket,
                                                CertManager certManager) throws Exception {
        if (Constants.TRANSPORT_MODE_TLS.equals(mode)) {
            SSLSocketFactory factory = certManager.createServerSSLContext().getSocketFactory();
            SSLSocket sslSocket = (SSLSocket) factory.createSocket(
                    acceptedSocket,
                    acceptedSocket.getInetAddress().getHostAddress(),
                    acceptedSocket.getPort(),
                    true);
            sslSocket.setUseClientMode(false);
            sslSocket.startHandshake();
            return new TlsTcpTransport(sslSocket);
        } else {
            return new PlainTcpTransport(acceptedSocket);
        }
    }

    public static DuplexTransport wrapPlainAccepted(Socket acceptedSocket) throws IOException {
        return new PlainTcpTransport(acceptedSocket);
    }
}
