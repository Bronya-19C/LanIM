package com.alpha.lanim.model;

import java.util.Map;

public class SyncReqPayload {
    private Map<String, Integer> lastSequences;

    public SyncReqPayload() {}

    public SyncReqPayload(Map<String, Integer> lastSequences) {
        this.lastSequences = lastSequences;
    }

    public Map<String, Integer> getLastSequences() { return lastSequences; }
    public void setLastSequences(Map<String, Integer> lastSequences) { this.lastSequences = lastSequences; }
}
