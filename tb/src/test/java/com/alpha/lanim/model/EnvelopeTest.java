package com.alpha.lanim.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnvelopeTest {

    @Test
    void testEnvelopeCreation() {
        Envelope envelope = new Envelope();
        envelope.setType("CHAT_TEXT");
        envelope.setMessageId("test-id");
        envelope.setSenderId("sender-123");
        envelope.setRoomId("room-456");
        envelope.setSequence(10);
        envelope.setTimestamp(1234567890L);

        assertEquals("CHAT_TEXT", envelope.getType());
        assertEquals("test-id", envelope.getMessageId());
        assertEquals("sender-123", envelope.getSenderId());
        assertEquals("room-456", envelope.getRoomId());
        assertEquals(10, envelope.getSequence());
        assertEquals(1234567890L, envelope.getTimestamp());
    }

    @Test
    void testEnvelopeWithPayload() {
        ChatPayload payload = new ChatPayload();
        payload.setText("Test message");

        Envelope envelope = new Envelope();
        envelope.setType("CHAT_TEXT");
        envelope.setPayload(payload);

        assertEquals("CHAT_TEXT", envelope.getType());
        assertNotNull(envelope.getPayload());
        // Since payload is of type Object in Envelope, we can't directly access ChatPayload methods without casting
        // But we can check if it's an instance of ChatPayload
        assertTrue(envelope.getPayload() instanceof ChatPayload);
    }
}