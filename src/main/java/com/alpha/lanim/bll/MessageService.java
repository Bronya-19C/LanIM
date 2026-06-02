package com.alpha.lanim.bll;

import com.alpha.lanim.model.Envelope;
import com.alpha.lanim.model.MessageType;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class MessageService {

    private final Map<MessageType, Consumer<Envelope>> handlers;
    private SyncService syncService;
    private FileTransferService fileTransferService;

    public MessageService() {
        this.handlers = new HashMap<>();
    }

    public void setSyncService(SyncService syncService) {
        this.syncService = syncService;
    }

    public void setFileTransferService(FileTransferService fileTransferService) {
        this.fileTransferService = fileTransferService;
    }

    public void initHandlers() {
        registerDefaultHandlers();
    }

    private void registerDefaultHandlers() {
        handlers.put(MessageType.SYNC_REQ, this::handleSyncReq);
        handlers.put(MessageType.SYNC_RESP, this::handleSyncResp);
        handlers.put(MessageType.CHAT_TEXT, this::handleChatText);
        handlers.put(MessageType.FILE_META, this::handleFileMeta);
        handlers.put(MessageType.FILE_CHUNK, this::handleFileChunk);
        handlers.put(MessageType.FILE_CHUNK_ACK, this::handleFileChunkAck);
        handlers.put(MessageType.HEARTBEAT, this::handleHeartbeat);
    }

    public void dispatch(Envelope envelope) {
        Consumer<Envelope> handler = handlers.get(MessageType.valueOf(envelope.getType()));
        if (handler != null) {
            handler.accept(envelope);
        }
    }

    public void registerHandler(MessageType type, Consumer<Envelope> handler) {
        handlers.put(type, handler);
    }

    private void handleSyncReq(Envelope env) {
        syncService.handleSyncRequest(env);
    }

    private void handleSyncResp(Envelope env) {
        syncService.handleSyncResponse(env);
    }

    private void handleChatText(Envelope env) {
        syncService.recordIncomingMessage(env);
    }

    private void handleFileMeta(Envelope env) {
        fileTransferService.handleFileMeta(env);
        syncService.recordIncomingMessage(env);
    }

    private void handleFileChunk(Envelope env) {
        fileTransferService.handleFileChunk(env);
    }

    private void handleFileChunkAck(Envelope env) {
        fileTransferService.handleFileChunkAck(env);
    }

    private void handleHeartbeat(Envelope env) {
    }
}
