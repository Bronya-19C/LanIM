package com.alpha.lanim.dal;

import com.alpha.lanim.model.Peer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PeerDao {

    public void save(Peer peer) {
        upsert(peer);
    }

    public void upsert(Peer peer) {
        String sql = "INSERT OR REPLACE INTO peers (peer_id, nickname, address, port, cert_fingerprint, last_seen) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBUtil.getDefaultConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, peer.getPeerId());
            ps.setString(2, peer.getNickname());
            ps.setString(3, peer.getAddress());
            ps.setInt(4, peer.getPort());
            ps.setString(5, peer.getCertFingerprint());
            ps.setLong(6, peer.getLastSeen());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert peer: " + peer.getPeerId(), e);
        }
    }

    public void updateLastSeen(String peerId, long lastSeen) {
        String sql = "UPDATE peers SET last_seen = ? WHERE peer_id = ?";

        try (Connection conn = DBUtil.getDefaultConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lastSeen);
            ps.setString(2, peerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update last seen: " + peerId, e);
        }
    }

    public Peer findById(String peerId) {
        return findByPeerId(peerId);
    }

    public Peer findByPeerId(String peerId) {
        String sql = "SELECT peer_id, nickname, address, port, cert_fingerprint, last_seen FROM peers WHERE peer_id = ?";

        try (Connection conn = DBUtil.getDefaultConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, peerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowToPeer(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find peer: " + peerId, e);
        }
        return null;
    }

    public List<Peer> findAll() {
        String sql = "SELECT peer_id, nickname, address, port, cert_fingerprint, last_seen FROM peers";

        List<Peer> result = new ArrayList<>();
        try (Connection conn = DBUtil.getDefaultConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rowToPeer(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all peers", e);
        }
        return result;
    }

    public void delete(String peerId) {
        String sql = "DELETE FROM peers WHERE peer_id = ?";

        try (Connection conn = DBUtil.getDefaultConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, peerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete peer: " + peerId, e);
        }
    }

    private Peer rowToPeer(ResultSet rs) throws SQLException {
        Peer peer = new Peer();
        peer.setPeerId(rs.getString("peer_id"));
        peer.setNickname(rs.getString("nickname"));
        peer.setAddress(rs.getString("address"));
        peer.setPort(rs.getInt("port"));
        peer.setCertFingerprint(rs.getString("cert_fingerprint"));
        peer.setLastSeen(rs.getLong("last_seen"));
        return peer;
    }
}
