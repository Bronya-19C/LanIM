package com.alpha.lanim.dal;

import com.alpha.lanim.model.Peer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PeerDaoTest {

    @TempDir
    File tempDir;

    private PeerDao peerDao;

    @BeforeEach
    void setUp() throws SQLException {
        // Set up a temporary database
        System.setProperty("sqlite.path", tempDir.getAbsolutePath() + "/test-peers.db");
        DBUtil dbUtil = new DBUtil();
        dbUtil.initializeDatabase();

        peerDao = new PeerDao();
    }

    @Test
    void testSaveAndFindPeer() throws SQLException {
        // Create a peer
        Peer peer = new Peer();
        peer.setPeerId("peer123");
        peer.setNickname("TestUser");
        peer.setAddress("192.168.1.100");
        peer.setPort(12345);
        peer.setCertFingerprint("abcdef1234567890");

        // Save the peer
        peerDao.save(peer);

        // Retrieve the peer
        Peer found = peerDao.findById("peer123");
        assertNotNull(found);
        assertEquals("peer123", found.getPeerId());
        assertEquals("TestUser", found.getNickname());
        assertEquals("192.168.1.100", found.getAddress());
        assertEquals(12345, found.getPort());
        assertEquals("abcdef1234567890", found.getCertFingerprint());
    }

    @Test
    void testUpdatePeer() throws SQLException {
        // Initial save
        Peer peer = new Peer();
        peer.setPeerId("peer123");
        peer.setNickname("InitialName");
        peer.setAddress("192.168.1.100");
        peer.setPort(12345);
        peerDao.save(peer);

        // Update
        peer.setNickname("UpdatedName");
        peer.setPort(54321);
        peerDao.save(peer);

        // Retrieve and check
        Peer found = peerDao.findById("peer123");
        assertNotNull(found);
        assertEquals("UpdatedName", found.getNickname());
        assertEquals(54321, found.getPort());
    }

    @Test
    void testFindAllPeers() throws SQLException {
        // Save two peers
        Peer peer1 = new Peer();
        peer1.setPeerId("peer1");
        peer1.setNickname("User1");
        peer1.setAddress("192.168.1.101");
        peer1.setPort(10001);
        peerDao.save(peer1);

        Peer peer2 = new Peer();
        peer2.setPeerId("peer2");
        peer2.setNickname("User2");
        peer2.setAddress("192.168.1.102");
        peer2.setPort(10002);
        peerDao.save(peer2);

        // Retrieve all
        List<Peer> peers = peerDao.findAll();
        assertEquals(2, peers.size());
        // Check that both are present (order not guaranteed)
        boolean foundPeer1 = peers.stream().anyMatch(p -> "peer1".equals(p.getPeerId()));
        boolean foundPeer2 = peers.stream().anyMatch(p -> "peer2".equals(p.getPeerId()));
        assertTrue(foundPeer1 && foundPeer2);
    }

    @Test
    void testDeletePeer() throws SQLException {
        // Save a peer
        Peer peer = new Peer();
        peer.setPeerId("peer123");
        peer.setNickname("ToDelete");
        peer.setAddress("192.168.1.100");
        peer.setPort(12345);
        peerDao.save(peer);

        // Delete
        peerDao.delete("peer123");

        // Verify deletion
        Peer found = peerDao.findById("peer123");
        assertNull(found);
    }
}