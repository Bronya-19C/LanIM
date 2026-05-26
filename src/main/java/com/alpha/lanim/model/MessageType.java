package com.alpha.lanim.model;

public enum MessageType {
    SYNC_REQ,
    SYNC_RESP,
    CHAT_TEXT,
    FILE_META,
    FILE_CHUNK,
    FILE_CHUNK_ACK,
    HEARTBEAT
}
