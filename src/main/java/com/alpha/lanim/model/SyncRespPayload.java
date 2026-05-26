package com.alpha.lanim.model;

import java.util.List;

public class SyncRespPayload {
    private List<Envelope> messages;

    public SyncRespPayload() {}

    public SyncRespPayload(List<Envelope> messages) {
        this.messages = messages;
    }

    public List<Envelope> getMessages() { return messages; }
    public void setMessages(List<Envelope> messages) { this.messages = messages; }
}
