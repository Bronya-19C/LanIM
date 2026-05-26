package com.alpha.lanim.bll;

import com.alpha.lanim.model.Envelope;
import com.alpha.lanim.util.JsonUtil;
import java.io.*;
import java.nio.charset.StandardCharsets;
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

        this.connectionManager.setMessageHandler((peerId, data) -> {
            try {
                Envelope envelope = JsonUtil.fromJson(data, Envelope.class);
                if (envelope.getSenderId() == null) return;

                if (peerId == null || peerId.isEmpty()) {
                    peerId = envelope.getSenderId();
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
            byte[] data = JsonUtil.toJsonBytes(envelope);
            connectionManager.sendToPeer(peerId, frameMessage(data));
        } catch (IOException e) {
            System.err.println("Failed to send to " + peerId + ": " + e.getMessage());
        }
    }

    public void broadcast(Envelope envelope) {
        try {
            byte[] data = JsonUtil.toJsonBytes(envelope);
            connectionManager.broadcast(frameMessage(data));
        } catch (IOException e) {
            System.err.println("Failed to broadcast: " + e.getMessage());
        }
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

    private byte[] frameMessage(byte[] payload) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeInt(payload.length);
        dos.write(payload);
        return bos.toByteArray();
    }
}
