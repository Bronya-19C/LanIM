package com.alpha.lanim.server;

import com.alpha.lanim.dal.MessageDao;
import com.alpha.lanim.model.Envelope;
import com.alpha.lanim.model.JoinAckPayload;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RoomManager {

    private final MessageDao messageDao;
    private final Map<String, Map<String, ClientHandler>> roomClients;
    private final Map<String, Map<String, String>> roomMembers;
    private final Map<String, AtomicInteger> roomSequences;

    public RoomManager() {
        this.messageDao = new MessageDao();
        this.roomClients = new ConcurrentHashMap<>();
        this.roomMembers = new ConcurrentHashMap<>();
        this.roomSequences = new ConcurrentHashMap<>();
    }

    public void joinRoom(String roomId, String peerId, String nickname, ClientHandler handler) {
        roomClients.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .put(peerId, handler);

        roomMembers.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .put(peerId, nickname);

        roomSequences.computeIfAbsent(roomId, k -> {
            int maxSeq = messageDao.getMaxGlobalSequence(roomId);
            return new AtomicInteger(maxSeq + 1);
        });
    }

    public void leaveRoom(String roomId, String peerId, ClientHandler handler) {
        Map<String, ClientHandler> clients = roomClients.get(roomId);
        if (clients != null) {
            clients.remove(peerId);
            if (clients.isEmpty()) {
                roomClients.remove(roomId);
            }
        }

        Map<String, String> members = roomMembers.get(roomId);
        if (members != null) {
            members.remove(peerId);
            if (members.isEmpty()) {
                roomMembers.remove(roomId);
                roomSequences.remove(roomId);
            }
        }
    }

    public void relayToRoomExcept(String roomId, ClientHandler exclude, byte[] data) {
        Map<String, ClientHandler> clients = roomClients.get(roomId);
        if (clients == null) return;

        for (ClientHandler client : clients.values()) {
            if (client == exclude) continue;
            client.send(data);
        }
    }

    public int nextSequence(String roomId) {
        AtomicInteger seq = roomSequences.computeIfAbsent(roomId,
                k -> new AtomicInteger(messageDao.getMaxGlobalSequence(k) + 1));
        return seq.getAndIncrement();
    }

    public void persistMessage(Envelope envelope) {
        messageDao.insert(envelope);
    }

    public List<Envelope> getMessageHistory(String roomId) {
        return messageDao.findByRoomId(roomId);
    }

    public List<JoinAckPayload.MemberInfo> getRoomMembers(String roomId) {
        List<JoinAckPayload.MemberInfo> result = new ArrayList<>();
        Map<String, String> members = roomMembers.get(roomId);
        if (members != null) {
            for (Map.Entry<String, String> entry : members.entrySet()) {
                result.add(new JoinAckPayload.MemberInfo(entry.getKey(), entry.getValue()));
            }
        }
        return result;
    }

    public void shutdown() {
        for (Map<String, ClientHandler> clients : roomClients.values()) {
            for (ClientHandler handler : clients.values()) {
                try { handler.closeTransport(); } catch (Exception ignored) {}
            }
        }
        roomClients.clear();
        roomMembers.clear();
        roomSequences.clear();
    }
}
