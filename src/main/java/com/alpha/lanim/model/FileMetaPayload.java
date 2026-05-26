package com.alpha.lanim.model;

public class FileMetaPayload {
    private String fileId;
    private String fileName;
    private String contentType;
    private long totalSize;
    private int totalChunks;
    private String checksum;

    public FileMetaPayload() {}

    public FileMetaPayload(String fileId, String fileName, String contentType,
                           long totalSize, int totalChunks, String checksum) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.totalSize = totalSize;
        this.totalChunks = totalChunks;
        this.checksum = checksum;
    }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getTotalSize() { return totalSize; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }

    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
}
