package com.alpha.lanim.model;

public class FileChunkPayload {
    private String fileId;
    private int chunkIndex;
    private int totalChunks;
    private String data;

    public FileChunkPayload() {}

    public FileChunkPayload(String fileId, int chunkIndex, int totalChunks, String data) {
        this.fileId = fileId;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.data = data;
    }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }

    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}
