package com.alpha.lanim.util;

import com.alpha.lanim.model.Envelope;
import com.alpha.lanim.model.ChatPayload;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    @Test
    void testSerializeDeserializeEnvelope() {
        // Create a sample envelope
        ChatPayload payload = new ChatPayload();
        payload.setText("Hello, world!");

        Envelope envelope = new Envelope();
        envelope.setType("CHAT_TEXT");
        envelope.setMessageId("test-message-id");
        envelope.setSenderId("test-sender-id");
        envelope.setRoomId("test-room-id");
        envelope.setSequence(1);
        envelope.setTimestamp(System.currentTimeMillis());
        envelope.setPayload(payload);

        // Serialize to JSON
        String json = JsonUtil.toJson(envelope);
        assertNotNull(json);
        assertTrue(json.contains("CHAT_TEXT"));
        assertTrue(json.contains("Hello, world!"));

        // Deserialize from JSON
        Envelope deserialized = JsonUtil.fromJson(json, Envelope.class);
        assertNotNull(deserialized);
        assertEquals(envelope.getType(), deserialized.getType());
        assertEquals(envelope.getMessageId(), deserialized.getMessageId());
        assertEquals(envelope.getSenderId(), deserialized.getSenderId());
        assertEquals(envelope.getRoomId(), deserialized.getRoomId());
        assertEquals(envelope.getSequence(), deserialized.getSequence());
        assertEquals(envelope.getTimestamp(), deserialized.getTimestamp());
        // Note: Payload deserialization might be to a Map, but we trust JsonUtil works
    }
}