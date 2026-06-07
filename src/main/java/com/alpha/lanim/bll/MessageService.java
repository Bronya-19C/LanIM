package com.alpha.lanim.bll;

import com.alpha.lanim.dal.MessageDao;
import com.alpha.lanim.model.*;
import com.alpha.lanim.util.JsonUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MessageService {

    public interface JoinCallback {
        void onJoinAck(JoinAckPayload ack);
    }

    public interface UserEventCallback {
        void onUserJoined(UserEventPayload payload);
        void onUserLeft(UserEventPayload payload);
    }

    private final Map<MessageType, Consumer<Envelope>> handlers;
    private final MessageDao messageDao;
    private JoinCallback joinCallback;
    private UserEventCallback userEventCallback;
    private FileTransferService fileTransferService;

    public MessageService() {
        this.handlers = new HashMap<>();
        this.messageDao = new MessageDao();
        initHandlers();
    }

    public void setJoinCallback(JoinCallback callback) {
        this.joinCallback = callback;
    }

    public void setUserEventCallback(UserEventCallback callback) {
        this.userEventCallback = callback;
    }

    public void setFileTransferService(FileTransferService fileTransferService) {
        this.fileTransferService = fileTransferService;
    }

    private void initHandlers() {
        handlers.put(MessageType.JOIN_ACK, this::handleJoinAck);
        handlers.put(MessageType.USER_JOINED, this::handleUserJoined);
        handlers.put(MessageType.USER_LEFT, this::handleUserLeft);
        handlers.put(MessageType.CHAT_TEXT, this::handleChatText);
        handlers.put(MessageType.FILE_META, this::handleFileMeta);
        handlers.put(MessageType.FILE_CHUNK, this::handleFileChunk);
        handlers.put(MessageType.FILE_CHUNK_ACK, this::handleFileChunkAck);
        handlers.put(MessageType.HEARTBEAT, this::handleHeartbeat);
    }

    public void dispatch(Envelope envelope) {
        if (envelope == null || envelope.getType() == null) return;
        try {
            Consumer<Envelope> handler = handlers.get(MessageType.valueOf(envelope.getType()));
            if (handler != null) {
                handler.accept(envelope);
            }
        } catch (IllegalArgumentException ignored) {}
    }

    private void handleJoinAck(Envelope env) {
        JoinAckPayload payload = JsonUtil.fromPayload(env.getPayload(), JoinAckPayload.class);
        if (payload == null) return;

        if (payload.getHistory() != null) {
            for (Envelope msg : payload.getHistory()) {
                messageDao.insert(msg);
            }
        }

        if (joinCallback != null) {
            joinCallback.onJoinAck(payload);
        }
    }

    private void handleUserJoined(Envelope env) {
        UserEventPayload payload = JsonUtil.fromPayload(env.getPayload(), UserEventPayload.class);
        if (payload == null || userEventCallback == null) return;
        userEventCallback.onUserJoined(payload);
    }

    private void handleUserLeft(Envelope env) {
        UserEventPayload payload = JsonUtil.fromPayload(env.getPayload(), UserEventPayload.class);
        if (payload == null || userEventCallback == null) return;
        userEventCallback.onUserLeft(payload);
    }

    private void handleChatText(Envelope env) {
        messageDao.insert(env);
    }

    private void handleFileMeta(Envelope env) {
        if (fileTransferService != null) {
            fileTransferService.handleFileMeta(env);
        }
        messageDao.insert(env);
    }

    private void handleFileChunk(Envelope env) {
        if (fileTransferService != null) {
            fileTransferService.handleFileChunk(env);
        }
    }

    private void handleFileChunkAck(Envelope env) {
        if (fileTransferService != null) {
            fileTransferService.handleFileChunkAck(env);
        }
    }

    private void handleHeartbeat(Envelope env) {
    }
}
