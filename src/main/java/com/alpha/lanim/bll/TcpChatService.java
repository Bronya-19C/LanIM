package com.alpha.lanim.bll;

import com.alpha.lanim.bll.transport.DuplexTransport;
import com.alpha.lanim.model.Envelope;
import com.alpha.lanim.util.JsonUtil;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TcpChatService {

    public interface MessageCallback {
        void onMessage(Envelope envelope);
    }

    private final PeerConnectionManager connectionManager;
    private final MessageService messageService;
    private final Map<String, String> pendingPeerIds;
    private MessageCallback callback;

    public TcpChatService(PeerConnectionManager connectionManager, MessageService messageService) {
        this.connectionManager = connectionManager;
        this.messageService = messageService;
        this.pendingPeerIds = new ConcurrentHashMap<>();

        this.connectionManager.setMessageHandler((peerId, data, transport) -> {
            try {
                Envelope envelope = JsonUtil.fromJson(data, Envelope.class);
                if (envelope.getSenderId() == null) return;

                if (peerId == null || peerId.isEmpty()) {
                    peerId = envelope.getSenderId();
                }
                if (transport != null && peerId != null && !peerId.isEmpty()) {
                    connectionManager.bindTransportIfAbsent(peerId, transport);
                }
                pendingPeerIds.putIfAbsent(peerId, peerId);

                if (callback != null) {
                    callback.onMessage(envelope);
                }

                messageService.dispatch(envelope);
            } catch (Exception e) {
                System.err.println("Failed to handle message: " + e.getMessage());
            }
        });
    }

    public void start(int port) throws IOException {
        connectionManager.startServer();
    }

    public int getLocalPort() {
        return connectionManager.getLocalPort();
    }

    public void connectToPeer(String peerId, String host, int port) {
        connectionManager.connectToPeer(peerId, host, port);
    }

    public void sendToPeer(String peerId, Envelope envelope) {
        try {
            connectionManager.sendToPeer(peerId, JsonUtil.toJsonBytes(envelope));
        } catch (IOException e) {
            System.err.println("Failed to send to " + peerId + ": " + e.getMessage());
        }
    }

    public void broadcast(Envelope envelope) {
        connectionManager.broadcast(JsonUtil.toJsonBytes(envelope));
    }

    public boolean isConnected(String peerId) {
        return connectionManager.isConnected(peerId);
    }

    public void disconnectPeer(String peerId) {
        connectionManager.disconnectPeer(peerId);
    }

    public void setMessageCallback(MessageCallback callback) {
        this.callback = callback;
    }

    public void shutdown() {
        connectionManager.shutdown();
    }
}
