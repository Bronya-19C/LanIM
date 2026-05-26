package com.alpha.lanim.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

class SyncReqPayloadTest {

    @Test
    void testSyncReqPayload() {
        SyncReqPayload payload = new SyncReqPayload();
        Map<String, Integer> lastSequences = new HashMap<>();
        lastSequences.put("peer1", 10);
        lastSequences.put("peer2", 5);
        payload.setLastSequences(lastSequences);

        assertNotNull(payload.getLastSequences());
        assertEquals(2, payload.getLastSequences().size());
        assertEquals(10, payload.getLastSequences().get("peer1"));
        assertEquals(5, payload.getLastSequences().get("peer2"));
    }
}