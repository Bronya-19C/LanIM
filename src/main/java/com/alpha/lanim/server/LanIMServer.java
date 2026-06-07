package com.alpha.lanim.server;

import com.alpha.lanim.bll.crypto.CertManager;
import com.alpha.lanim.bll.transport.DuplexTransport;
import com.alpha.lanim.dal.DBUtil;
import com.alpha.lanim.util.Constants;

import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LanIMServer {

    private final int port;
    private final String transportMode;
    private final CertManager certManager;
    private final RoomManager roomManager;
    private final ExecutorService executor;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public LanIMServer(int port, String transportMode) {
        this.port = port;
        this.transportMode = transportMode;
        this.certManager = new CertManager();
        this.roomManager = new RoomManager();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("server-worker");
            return t;
        });
    }

    public void start() throws Exception {
        DBUtil.init();
        certManager.init();

        if (Constants.TRANSPORT_MODE_TLS.equals(transportMode)) {
            SSLServerSocketFactory ssf = certManager.createServerSSLContext().getServerSocketFactory();
            serverSocket = ssf.createServerSocket(port);
        } else {
            serverSocket = new ServerSocket(port);
        }

        running = true;
        System.out.println("LANIM Server started on port " + port
                + " (" + transportMode + " mode)");

        executor.submit(() -> {
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executor.submit(() -> handleConnection(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Accept error: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void handleConnection(Socket clientSocket) {
        DuplexTransport transport = null;
        try {
            if (Constants.TRANSPORT_MODE_TLS.equals(transportMode)) {
                transport = new com.alpha.lanim.bll.transport.TlsTcpTransport((SSLSocket) clientSocket);
            } else {
                transport = new com.alpha.lanim.bll.transport.PlainTcpTransport(clientSocket);
            }

            ClientHandler handler = new ClientHandler(transport, roomManager);
            handler.run();
        } catch (Exception e) {
            System.err.println("Failed to handle connection: " + e.getMessage());
            if (transport != null) transport.close();
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    public void shutdown() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
        roomManager.shutdown();
        executor.shutdownNow();
    }

    public static void main(String[] args) {
        int port = Constants.DEFAULT_SERVER_PORT;
        String mode = Constants.DEFAULT_TRANSPORT_MODE;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                case "-p":
                    if (i + 1 < args.length) {
                        port = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--plain":
                    mode = Constants.TRANSPORT_MODE_PLAIN;
                    break;
                case "--tls":
                    mode = Constants.TRANSPORT_MODE_TLS;
                    break;
                case "--help":
                case "-h":
                    System.out.println("Usage: LanIMServer [options]");
                    System.out.println("  -p, --port <port>   Server port (default: " + Constants.DEFAULT_SERVER_PORT + ")");
                    System.out.println("  --tls               Use TLS encryption (default)");
                    System.out.println("  --plain             Use plain TCP (no encryption)");
                    return;
            }
        }

        LanIMServer server = new LanIMServer(port, mode);
        try {
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));

            synchronized (server) {
                server.wait();
            }
        } catch (Exception e) {
            System.err.println("Server failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
