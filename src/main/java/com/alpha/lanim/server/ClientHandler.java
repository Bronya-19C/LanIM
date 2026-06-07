package com.alpha.lanim.server;

import com.alpha.lanim.bll.transport.DuplexTransport;
import com.alpha.lanim.model.*;
import com.alpha.lanim.util.JsonUtil;
import com.alpha.lanim.util.HashUtil;

import java.io.IOException;

public class ClientHandler {

    private final DuplexTransport transport;
    private final RoomManager roomManager;
    private String peerId;
    private String nickname;
    private String roomId;
    private volatile boolean closed;

    public ClientHandler(DuplexTransport transport, RoomManager roomManager) {
        this.transport = transport;
        this.roomManager = roomManager;
    }

    public void run() {
        try {
            while (!transport.isClosed() && !closed) {
                byte[] data = transport.receive();
                handleRawMessage(data);
            }
        } catch (IOException e) {
            // connection closed
        } finally {
            disconnect();
        }
    }

    private void handleRawMessage(byte[] data) {
        try {
            Envelope envelope = JsonUtil.fromJson(data, Envelope.class);
            if (envelope.getType() == null) return;

            MessageType type = MessageType.valueOf(envelope.getType());

            switch (type) {
                case JOIN:
                    handleJoin(envelope);
                    break;
                case CHAT_TEXT:
                    handleChatMessage(envelope);
                    break;
                case FILE_META:
                    handleFileMessage(envelope);
                    break;
                case FILE_CHUNK:
                    handleFileMessage(envelope);
                    break;
                case FILE_CHUNK_ACK:
                    roomManager.relayToRoomExcept(roomId, this, data);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            System.err.println("Message handling error: " + e.getMessage());
        }
    }

    private void handleJoin(Envelope envelope) {
        JoinPayload payload = JsonUtil.fromPayload(envelope.getPayload(), JoinPayload.class);
        if (payload == null) return;

        this.peerId = envelope.getSenderId() != null ? envelope.getSenderId() : payload.getPeerId();
        this.nickname = payload.getNickname();
        this.roomId = HashUtil.sha512Hex(payload.getRoomSecret());

        roomManager.joinRoom(roomId, peerId, nickname, this);

        // Send JOIN_ACK with history and members
        JoinAckPayload ack = new JoinAckPayload(
                peerId,
                roomId,
                roomManager.getMessageHistory(roomId),
                roomManager.getRoomMembers(roomId)
        );

        Envelope ackEnv = new Envelope(
                MessageType.JOIN_ACK.name(),
                java.util.UUID.randomUUID().toString(),
                "SERVER",
                roomId,
                0,
                System.currentTimeMillis(),
                JsonUtil.gson().toJsonTree(ack).getAsJsonObject()
        );

        sendEnvelope(ackEnv);

        // Broadcast USER_JOINED to all other members
        UserEventPayload joined = new UserEventPayload(peerId, nickname);
        Envelope joinedEnv = new Envelope(
                MessageType.USER_JOINED.name(),
                java.util.UUID.randomUUID().toString(),
                "SERVER",
                roomId,
                0,
                System.currentTimeMillis(),
                JsonUtil.gson().toJsonTree(joined).getAsJsonObject()
        );

        roomManager.relayToRoomExcept(roomId, this, JsonUtil.toJsonBytes(joinedEnv));

        System.out.println("[" + roomId.substring(0, 8) + "] " + nickname + " joined");
    }

    private void handleChatMessage(Envelope envelope) {
        if (roomId == null) return;

        int seq = roomManager.nextSequence(roomId);
        envelope.setSequence(seq);
        envelope.setTimestamp(System.currentTimeMillis());
        envelope.setRoomId(roomId);

        roomManager.persistMessage(envelope);

        roomManager.relayToRoomExcept(roomId, this, JsonUtil.toJsonBytes(envelope));
    }

    private void handleFileMessage(Envelope envelope) {
        if (roomId == null) return;

        if (MessageType.FILE_META.name().equals(envelope.getType())) {
            int seq = roomManager.nextSequence(roomId);
            envelope.setSequence(seq);
            envelope.setTimestamp(System.currentTimeMillis());
            envelope.setRoomId(roomId);
            roomManager.persistMessage(envelope);
        }

        roomManager.relayToRoomExcept(roomId, this, JsonUtil.toJsonBytes(envelope));
    }

    private void sendEnvelope(Envelope envelope) {
        try {
            transport.send(JsonUtil.toJsonBytes(envelope));
        } catch (IOException e) {
            System.err.println("Failed to send to " + nickname + ": " + e.getMessage());
        }
    }

    public void send(byte[] data) {
        try {
            transport.send(data);
        } catch (IOException e) {
            System.err.println("Failed to send to " + nickname);
        }
    }

    public void closeTransport() {
        if (transport != null) {
            transport.close();
        }
    }

    public String getPeerId() { return peerId; }
    public String getNickname() { return nickname; }

    private void disconnect() {
        if (closed) return;
        closed = true;
        transport.close();

        if (roomId != null && peerId != null) {
            roomManager.leaveRoom(roomId, peerId, this);

            UserEventPayload left = new UserEventPayload(peerId, nickname);
            Envelope leftEnv = new Envelope(
                    MessageType.USER_LEFT.name(),
                    java.util.UUID.randomUUID().toString(),
                    "SERVER",
                    roomId,
                    0,
                    System.currentTimeMillis(),
                    JsonUtil.gson().toJsonTree(left).getAsJsonObject()
            );

            roomManager.relayToRoomExcept(roomId, null, JsonUtil.toJsonBytes(leftEnv));

            System.out.println("[" + roomId.substring(0, 8) + "] " + nickname + " left");
        }
    }
}
