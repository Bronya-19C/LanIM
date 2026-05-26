package com.alpha.lanim.model;

public class FileRecord {

    private String fileId;
    private String messageId;
    private String fileName;
    private String contentType;
    private long totalSize;
    private int totalChunks;
    private String checksum;
    private String localPath;
    private int receivedChunks;
    private String status;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETE = "COMPLETE";
    public static final String STATUS_ERROR = "ERROR";

    public FileRecord() {}

    public FileRecord(String fileId, String messageId, String fileName, String contentType,
                      long totalSize, int totalChunks, String checksum) {
        this.fileId = fileId;
        this.messageId = messageId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.totalSize = totalSize;
        this.totalChunks = totalChunks;
        this.checksum = checksum;
        this.status = STATUS_PENDING;
        this.receivedChunks = 0;
    }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

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

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public int getReceivedChunks() { return receivedChunks; }
    public void setReceivedChunks(int receivedChunks) { this.receivedChunks = receivedChunks; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isComplete() {
        return STATUS_COMPLETE.equals(status) || receivedChunks >= totalChunks;
    }
}
