package com.alpha.lanim.dal;

import com.alpha.lanim.model.FileRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class FileDao {

    public void insert(FileRecord record) {
        String sql = "INSERT OR REPLACE INTO files (file_id, message_id, file_name, content_type, total_size, total_chunks, checksum, local_path, received_chunks, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBUtil.getDefaultConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getFileId());
            ps.setString(2, record.getMessageId());
            ps.setString(3, record.getFileName());
            ps.setString(4, record.getContentType());
            ps.setLong(5, record.getTotalSize());
            ps.setInt(6, record.getTotalChunks());
            ps.setString(7, record.getChecksum());
            ps.setString(8, record.getLocalPath());
            ps.setInt(9, record.getReceivedChunks());
            ps.setString(10, record.getStatus());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert file record: " + record.getFileId(), e);
        }
    }

    public void updateChunkReceived(String fileId, int receivedChunks, String status) {
        String sql = "UPDATE files SET received_chunks = ?, status = ? WHERE file_id = ?";

        try (Connection conn = DBUtil.getDefaultConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, receivedChunks);
            ps.setString(2, status);
            ps.setString(3, fileId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update chunk received: " + fileId, e);
        }
    }

    public FileRecord findByFileId(String fileId) {
        String sql = "SELECT file_id, message_id, file_name, content_type, total_size, total_chunks, checksum, local_path, received_chunks, status FROM files WHERE file_id = ?";

        try (Connection conn = DBUtil.getDefaultConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowToRecord(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find file record: " + fileId, e);
        }
        return null;
    }

    private FileRecord rowToRecord(ResultSet rs) throws SQLException {
        FileRecord rec = new FileRecord();
        rec.setFileId(rs.getString("file_id"));
        rec.setMessageId(rs.getString("message_id"));
        rec.setFileName(rs.getString("file_name"));
        rec.setContentType(rs.getString("content_type"));
        rec.setTotalSize(rs.getLong("total_size"));
        rec.setTotalChunks(rs.getInt("total_chunks"));
        rec.setChecksum(rs.getString("checksum"));
        rec.setLocalPath(rs.getString("local_path"));
        rec.setReceivedChunks(rs.getInt("received_chunks"));
        rec.setStatus(rs.getString("status"));
        return rec;
    }
}
