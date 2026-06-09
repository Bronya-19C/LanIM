package com.alpha.lanim.server;

import com.alpha.lanim.bll.crypto.CertManager;
import com.alpha.lanim.bll.transport.DuplexTransport;
import com.alpha.lanim.dal.DBUtil;
import com.alpha.lanim.util.Constants;
import com.alpha.lanim.util.WindowsFirewallHelper;

import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LanIMServer {

    private final int port;
    private final String transportMode;
    private final boolean manageWindowsFirewall;
    private final CertManager certManager;
    private final RoomManager roomManager;
    private final ExecutorService executor;
    private volatile boolean running;
    private volatile boolean firewallRuleAdded;
    private ServerSocket serverSocket;

    public LanIMServer(int port, String transportMode) {
        this(port, transportMode, true);
    }

    public LanIMServer(int port, String transportMode, boolean manageWindowsFirewall) {
        this.port = port;
        this.transportMode = transportMode;
        this.manageWindowsFirewall = manageWindowsFirewall;
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

        if (manageWindowsFirewall && WindowsFirewallHelper.isWindows()) {
            firewallRuleAdded = WindowsFirewallHelper.openInboundTcp(port);
        }

        running = true;
        System.out.println("LANIM Server started on port " + port
                + " (" + transportMode + " mode)");
        printConnectHints(port);

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

    private static void printConnectHints(int port) {
        System.out.println("Clients on this LAN should use Server Address:");
        for (String ip : listLocalIpv4Addresses()) {
            System.out.println("  -> " + ip + ":" + port);
        }
        System.out.println("TLS: clients must match server mode (default TLS on, or both use --plain).");
        if (!WindowsFirewallHelper.isWindows()) {
            System.out.println("If remote clients cannot connect, allow inbound TCP " + port
                    + " in the host firewall.");
        }
    }

    private static List<String> listLocalIpv4Addresses() {
        List<String> ips = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                for (var addr : Collections.list(nic.getInetAddresses())) {
                    if (addr instanceof Inet4Address inet4 && !inet4.isLoopbackAddress()) {
                        ips.add(inet4.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (ips.isEmpty()) {
            ips.add("127.0.0.1");
        }
        return ips;
    }

    public void shutdown() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
        roomManager.shutdown();
        executor.shutdownNow();
        if (firewallRuleAdded) {
            WindowsFirewallHelper.removeInboundTcp(port);
            firewallRuleAdded = false;
        }
    }

    public static void main(String[] args) {
        int port = Constants.DEFAULT_SERVER_PORT;
        String mode = Constants.DEFAULT_TRANSPORT_MODE;
        boolean manageFirewall = true;

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
                case "--no-firewall":
                    manageFirewall = false;
                    break;
                case "--help":
                case "-h":
                    printUsage();
                    return;
            }
        }

        LanIMServer server = new LanIMServer(port, mode, manageFirewall);
        try {
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));

            synchronized (server) {
                server.wait();
            }
        } catch (Exception e) {
            server.shutdown();
            System.err.println("Server failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printUsage() {
        System.out.println("Usage: LanIMServer [options]");
        System.out.println("  -p, --port <port>   Server port (default: " + Constants.DEFAULT_SERVER_PORT + ")");
        System.out.println("  --tls               Use TLS encryption (default)");
        System.out.println("  --plain             Use plain TCP (no encryption)");
        System.out.println("  --no-firewall       Do not auto add/remove Windows firewall rule");
        System.out.println();
        System.out.println("On Windows, auto firewall management needs an Administrator terminal.");
    }
}
