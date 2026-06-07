package com.alpha.lanim.bll;

import com.alpha.lanim.bll.crypto.CertManager;
import com.alpha.lanim.bll.transport.DuplexTransport;
import com.alpha.lanim.bll.transport.PlainTcpTransport;
import com.alpha.lanim.bll.transport.TlsTcpTransport;
import com.alpha.lanim.util.Constants;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.Socket;

public class ClientConnectionService {

    public interface MessageHandler {
        void onMessage(byte[] data);
    }

    private final CertManager certManager;
    private final String transportMode;
    private DuplexTransport transport;
    private MessageHandler messageHandler;
    private volatile boolean running;

    public ClientConnectionService(CertManager certManager, String transportMode) {
        this.certManager = certManager;
        this.transportMode = transportMode;
    }

    public void connect(String host, int port) throws Exception {
        if (Constants.TRANSPORT_MODE_TLS.equals(transportMode)) {
            SSLSocketFactory factory = certManager.createClientSSLContext().getSocketFactory();
            SSLSocket sslSocket = (SSLSocket) factory.createSocket(host, port);
            sslSocket.startHandshake();
            transport = new TlsTcpTransport(sslSocket);
        } else {
            Socket socket = new Socket(host, port);
            transport = new PlainTcpTransport(socket);
        }

        running = true;

        Thread readerThread = new Thread(() -> {
            while (running && !transport.isClosed()) {
                try {
                    byte[] data = transport.receive();
                    if (messageHandler != null) {
                        messageHandler.onMessage(data);
                    }
                } catch (IOException e) {
                    break;
                }
            }
        });
        readerThread.setDaemon(true);
        readerThread.setName("server-reader");
        readerThread.start();
    }

    public void send(byte[] data) throws IOException {
        if (transport == null || transport.isClosed()) {
            throw new IOException("Not connected to server");
        }
        transport.send(data);
    }

    public boolean isConnected() {
        return transport != null && !transport.isClosed();
    }

    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    public void shutdown() {
        running = false;
        if (transport != null) {
            transport.close();
        }
    }
}
