package com.alpha.lanim.dal;

import com.alpha.lanim.model.Envelope;
import com.alpha.lanim.util.JsonUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MessageDao {

    public void insert(Envelope envelope) {
        String sql = "INSERT OR IGNORE INTO messages (message_id, room_id, sender_id, sequence, type, timestamp, payload_json) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBUtil.getDefaultConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, envelope.getMessageId());
            ps.setString(2, envelope.getRoomId());
            ps.setString(3, envelope.getSenderId());
            ps.setInt(4, envelope.getSequence());
            ps.setString(5, envelope.getType());
            ps.setLong(6, envelope.getTimestamp());
            ps.setString(7, envelope.getPayload() != null ? envelope.getPayload().toString() : "{}");
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert message: " + envelope.getMessageId(), e);
        }
    }

    public void insertAll(List<Envelope> envelopes) {
        String sql = "INSERT OR IGNORE INTO messages (message_id, room_id, sender_id, sequence, type, timestamp, payload_json) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBUtil.getDefaultConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Envelope env : envelopes) {
                    ps.setString(1, env.getMessageId());
                    ps.setString(2, env.getRoomId());
                    ps.setString(3, env.getSenderId());
                    ps.setInt(4, env.getSequence());
                    ps.setString(5, env.getType());
                    ps.setLong(6, env.getTimestamp());
                    ps.setString(7, env.getPayload() != null ? env.getPayload().toString() : "{}");
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to batch insert messages", e);
        }
    }

    public List<Envelope> findByRoomId(String roomId) {
        String sql = "SELECT message_id, room_id, sender_id, sequence, type, timestamp, payload_json FROM messages WHERE room_id = ? ORDER BY sender_id, sequence";

        List<Envelope> result = new ArrayList<>();
        try (Connection conn = DBUtil.getDefaultConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rowToEnvelope(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find messages by roomId", e);
        }
        return result;
    }

    public List<Envelope> findBySenderAfter(String roomId, String senderId, int afterSequence) {
        String sql = "SELECT message_id, room_id, sender_id, sequence, type, timestamp, payload_json FROM messages WHERE room_id = ? AND sender_id = ? AND sequence > ? ORDER BY sequence";

        List<Envelope> result = new ArrayList<>();
        try (Connection conn = DBUtil.getDefaultConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId);
            ps.setString(2, senderId);
            ps.setInt(3, afterSequence);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rowToEnvelope(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find messages after sequence", e);
        }
        return result;
    }

    public int getMaxSequence(String roomId, String senderId) {
        String sql = "SELECT COALESCE(MAX(sequence), -1) FROM messages WHERE room_id = ? AND sender_id = ?";

        try (Connection conn = DBUtil.getDefaultConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId);
            ps.setString(2, senderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get max sequence", e);
        }
        return -1;
    }

    public int getNextSequence(String roomId, String senderId) {
        return getMaxSequence(roomId, senderId) + 1;
    }

    private Envelope rowToEnvelope(ResultSet rs) throws SQLException {
        Envelope env = new Envelope();
        env.setMessageId(rs.getString("message_id"));
        env.setRoomId(rs.getString("room_id"));
        env.setSenderId(rs.getString("sender_id"));
        env.setSequence(rs.getInt("sequence"));
        env.setType(rs.getString("type"));
        env.setTimestamp(rs.getLong("timestamp"));
        String payloadJson = rs.getString("payload_json");
        if (payloadJson != null && !payloadJson.isEmpty()) {
            env.setPayload(com.google.gson.JsonParser.parseString(payloadJson).getAsJsonObject());
        }
        return env;
    }
}
