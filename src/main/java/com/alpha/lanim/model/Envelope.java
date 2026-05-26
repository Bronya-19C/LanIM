package com.alpha.lanim.model;

public class Envelope {

    private String type;
    private String messageId;
    private String senderId;
    private String roomId;
    private int sequence;
    private long timestamp;
    private Object payload;

    public Envelope() {}

    public Envelope(String type, String messageId, String senderId, String roomId,
                    int sequence, long timestamp, Object payload) {
        this.type = type;
        this.messageId = messageId;
        this.senderId = senderId;
        this.roomId = roomId;
        this.sequence = sequence;
        this.timestamp = timestamp;
        this.payload = payload;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}
