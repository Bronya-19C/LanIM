package com.alpha.lanim.model;

public enum MessageType {
    JOIN,
    JOIN_ACK,
    USER_JOINED,
    USER_LEFT,
    CHAT_TEXT,
    FILE_META,
    FILE_CHUNK,
    FILE_CHUNK_ACK,
    HEARTBEAT
}
