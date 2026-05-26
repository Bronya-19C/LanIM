package com.alpha.lanim.model;

import java.util.List;

public class FileChunkAckPayload {
    private String fileId;
    private List<Integer> missingChunks;

    public FileChunkAckPayload() {}

    public FileChunkAckPayload(String fileId, List<Integer> missingChunks) {
        this.fileId = fileId;
        this.missingChunks = missingChunks;
    }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public List<Integer> getMissingChunks() { return missingChunks; }
    public void setMissingChunks(List<Integer> missingChunks) { this.missingChunks = missingChunks; }
}
