package com.alpha.lanim.bll;

import com.alpha.lanim.bll.crypto.CertManager;
import com.alpha.lanim.bll.transport.DuplexTransport;
import com.alpha.lanim.bll.transport.TransportFactory;
import com.alpha.lanim.util.Constants;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerConnectionManager {

    public interface MessageHandler {
        void onMessage(String peerId, byte[] data);
    }

    private final CertManager certManager;
    private final String transportMode;
    private ServerSocket serverSocket;
    private final Map<String, DuplexTransport> transports;
    private final ExecutorService executor;
    private final Object lock = new Object();
    private MessageHandler messageHandler;
    private volatile boolean running;
    private int localPort;

    public PeerConnectionManager(CertManager certManager, String transportMode) {
        this.certManager = certManager;
        this.transportMode = transportMode;
        this.transports = new ConcurrentHashMap<>();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized int startServer() throws IOException {
        serverSocket = new ServerSocket(0);
        localPort = serverSocket.getLocalPort();
        running = true;

        executor.submit(() -> {
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executor.submit(() -> handleAcceptedConnection(clientSocket));
                } catch (IOException e) {
                    if (!running) break;
                }
            }
        });

        return localPort;
    }

    private void handleAcceptedConnection(Socket clientSocket) {
        try {
            DuplexTransport transport = TransportFactory.wrapAccepted(
                    transportMode, clientSocket, certManager);
            startReaderThread(null, transport);
        } catch (Exception e) {
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    public void connectToPeer(String peerId, String host, int port) {
        synchronized (lock) {
            if (transports.containsKey(peerId)) return;
        }

        executor.submit(() -> {
            try {
                DuplexTransport transport = TransportFactory.createOutgoing(
                        transportMode, host, port, certManager);
                synchronized (lock) {
                    transports.put(peerId, transport);
                }
                startReaderThread(peerId, transport);
            } catch (Exception e) {
                System.err.println("Failed to connect to " + peerId + ": " + e.getMessage());
            }
        });
    }

    private void startReaderThread(String peerId, DuplexTransport transport) {
        executor.submit(() -> {
            while (!transport.isClosed()) {
                try {
                    byte[] data = transport.receive();
                    String senderId = peerId;
                    if (messageHandler != null) {
                        messageHandler.onMessage(senderId != null ? senderId : "", data);
                    }
                } catch (IOException e) {
                    break;
                }
            }
            // Connection closed
            if (peerId != null) {
                synchronized (lock) {
                    transports.remove(peerId);
                }
            }
            transport.close();
        });
    }

    public void sendToPeer(String peerId, byte[] data) throws IOException {
        DuplexTransport transport = transports.get(peerId);
        if (transport == null || transport.isClosed()) {
            throw new IOException("Not connected to peer: " + peerId);
        }
        transport.send(data);
    }

    public void broadcast(byte[] data) {
        for (Map.Entry<String, DuplexTransport> entry : transports.entrySet()) {
            try {
                if (!entry.getValue().isClosed()) {
                    entry.getValue().send(data);
                }
            } catch (IOException e) {
                System.err.println("Failed to send to " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }

    public void disconnectPeer(String peerId) {
        DuplexTransport transport;
        synchronized (lock) {
            transport = transports.remove(peerId);
        }
        if (transport != null) {
            transport.close();
        }
    }

    public boolean isConnected(String peerId) {
        DuplexTransport t = transports.get(peerId);
        return t != null && !t.isClosed();
    }

    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void shutdown() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
        for (DuplexTransport t : transports.values()) {
            t.close();
        }
        transports.clear();
        executor.shutdownNow();
    }
}
