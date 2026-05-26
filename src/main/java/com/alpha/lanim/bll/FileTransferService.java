package com.alpha.lanim.bll;

import com.alpha.lanim.dal.FileDao;
import com.alpha.lanim.model.*;
import com.alpha.lanim.util.Constants;
import com.alpha.lanim.util.JsonUtil;
import com.google.gson.JsonObject;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public class FileTransferService {

    private final FileDao fileDao;
    private TcpChatService tcpChatService;
    private final PeerService peerService;
    private final String filesPath;

    public FileTransferService(PeerService peerService) {
        this.fileDao = new FileDao();
        this.peerService = peerService;
        this.filesPath = Constants.DEFAULT_FILES_PATH;

        try { Files.createDirectories(Paths.get(filesPath)); } catch (IOException ignored) {}
    }

    public void setTcpChatService(TcpChatService tcpChatService) {
        this.tcpChatService = tcpChatService;
    }

    public void sendFile(String targetPeerId, File file) throws IOException {
        String fileId = UUID.randomUUID().toString();
        long totalSize = file.length();
        int totalChunks = (int) Math.ceil((double) totalSize / Constants.FILE_CHUNK_SIZE);
        String checksum = computeFileChecksum(file);

        FileMetaPayload meta = new FileMetaPayload(
                fileId, file.getName(),
                Files.probeContentType(file.toPath()),
                totalSize, totalChunks, checksum
        );

        String messageId = UUID.randomUUID().toString();
        JsonObject payload = JsonUtil.gson().toJsonTree(meta).getAsJsonObject();

        Envelope envelope = new Envelope(
                MessageType.FILE_META.name(),
                messageId,
                peerService.getLocalPeerId(),
                peerService.getRoomId(),
                0,
                System.currentTimeMillis(),
                payload
        );

        if (targetPeerId != null && !targetPeerId.isEmpty()) {
            tcpChatService.sendToPeer(targetPeerId, envelope);
        } else {
            tcpChatService.broadcast(envelope);
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[Constants.FILE_CHUNK_SIZE];
            int chunkIndex = 0;
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) > 0) {
                byte[] chunkData = bytesRead < Constants.FILE_CHUNK_SIZE
                        ? Arrays.copyOf(buffer, bytesRead)
                        : buffer.clone();

                String base64Data = Base64.getEncoder().encodeToString(chunkData);

                FileChunkPayload chunk = new FileChunkPayload(
                        fileId, chunkIndex, totalChunks, base64Data
                );

                JsonObject chunkPayload = JsonUtil.gson().toJsonTree(chunk).getAsJsonObject();
                Envelope chunkEnv = new Envelope(
                        MessageType.FILE_CHUNK.name(),
                        UUID.randomUUID().toString(),
                        peerService.getLocalPeerId(),
                        peerService.getRoomId(),
                        0,
                        System.currentTimeMillis(),
                        chunkPayload
                );

                if (targetPeerId != null && !targetPeerId.isEmpty()) {
                    tcpChatService.sendToPeer(targetPeerId, chunkEnv);
                } else {
                    tcpChatService.broadcast(chunkEnv);
                }

                chunkIndex++;
            }
        }
    }

    public void handleFileMeta(Envelope envelope) {
        FileMetaPayload meta = JsonUtil.fromPayload(envelope.getPayload(), FileMetaPayload.class);

        Path fileDir = Paths.get(filesPath, meta.getFileId());
        Path filePath = fileDir.resolve(meta.getFileName());

        try {
            Files.createDirectories(fileDir);
        } catch (IOException e) {
            System.err.println("Failed to create file directory: " + e.getMessage());
            return;
        }

        FileRecord record = new FileRecord(
                meta.getFileId(),
                envelope.getMessageId(),
                meta.getFileName(),
                meta.getContentType(),
                meta.getTotalSize(),
                meta.getTotalChunks(),
                meta.getChecksum()
        );
        record.setLocalPath(filePath.toString());
        record.setStatus(FileRecord.STATUS_PENDING);
        fileDao.insert(record);
    }

    public void handleFileChunk(Envelope envelope) {
        FileChunkPayload chunk = JsonUtil.fromPayload(envelope.getPayload(), FileChunkPayload.class);

        FileRecord record = fileDao.findByFileId(chunk.getFileId());
        if (record == null || record.isComplete()) return;

        Path filePath = Paths.get(record.getLocalPath());

        try {
            byte[] chunkData = Base64.getDecoder().decode(chunk.getData());
            Files.write(filePath, chunkData,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);

            int newReceivedCount = record.getReceivedChunks() + 1;
            String status = newReceivedCount >= record.getTotalChunks()
                    ? FileRecord.STATUS_COMPLETE : FileRecord.STATUS_PENDING;

            fileDao.updateChunkReceived(chunk.getFileId(), newReceivedCount, status);

            if (FileRecord.STATUS_COMPLETE.equals(status)) {
                String actualChecksum = computeFileChecksum(filePath.toFile());
                if (record.getChecksum() != null && !record.getChecksum().equals(actualChecksum)) {
                    fileDao.updateChunkReceived(chunk.getFileId(), newReceivedCount, FileRecord.STATUS_ERROR);
                    System.err.println("Checksum mismatch for file: " + record.getFileName());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to write file chunk: " + e.getMessage());
        }
    }

    public void handleFileChunkAck(Envelope envelope) {
        FileChunkAckPayload ack = JsonUtil.fromPayload(envelope.getPayload(), FileChunkAckPayload.class);
        List<Integer> missingChunks = ack.getMissingChunks();
        if (missingChunks == null || missingChunks.isEmpty()) return;

        // Re-request or resend logic would go here
        System.out.println("Peer requested missing chunks: " + missingChunks.size());
    }

    private String computeFileChecksum(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            byte[] buffer = new byte[Constants.FILE_CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) > 0) {
                md.update(buffer, 0, bytesRead);
            }
            return bytesToHex(md.digest());
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
