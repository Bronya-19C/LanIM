package com.alpha.lanim.bll;

import com.alpha.lanim.model.Envelope;
import com.alpha.lanim.util.JsonUtil;
import java.io.IOException;

public class TcpChatService {

    public interface MessageCallback {
        void onMessage(Envelope envelope);
    }

    private final ClientConnectionService connectionService;
    private final MessageService messageService;
    private MessageCallback callback;

    public TcpChatService(ClientConnectionService connectionService, MessageService messageService) {
        this.connectionService = connectionService;
        this.messageService = messageService;

        this.connectionService.setMessageHandler(data -> {
            try {
                Envelope envelope = JsonUtil.fromJson(data, Envelope.class);
                if (envelope == null) return;

                if (callback != null) {
                    callback.onMessage(envelope);
                }

                messageService.dispatch(envelope);
            } catch (Exception e) {
                System.err.println("Failed to handle message: " + e.getMessage());
            }
        });
    }

    public boolean isConnected() {
        return connectionService.isConnected();
    }

    public void sendToServer(Envelope envelope) {
        try {
            connectionService.send(JsonUtil.toJsonBytes(envelope));
        } catch (IOException e) {
            System.err.println("Failed to send: " + e.getMessage());
        }
    }

    public void setMessageCallback(MessageCallback callback) {
        this.callback = callback;
    }

    public void shutdown() {
        connectionService.shutdown();
    }
}
