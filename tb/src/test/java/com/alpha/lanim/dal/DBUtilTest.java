package com.alpha.lanim.dal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class DBUtilTest {

    @TempDir
    File tempDir;

    private DBUtil dbUtil;

    @BeforeEach
    void setUp() {
        // Override the default database path to use temp directory
        System.setProperty("sqlite.path", tempDir.getAbsolutePath() + "/test-lanim.db");
        dbUtil = new DBUtil();
    }

    @Test
    void testGetConnection() throws SQLException {
        try (Connection conn = dbUtil.getConnection()) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
        }
    }

    @Test
    void testInitializeDatabase() throws SQLException {
        // This should create the database and tables
        dbUtil.initializeDatabase();
        
        // Check that database file exists
        File dbFile = new File(tempDir, "test-lanim.db");
        assertTrue(dbFile.exists());
        
        // Check that we can connect and query the tables
        try (Connection conn = dbUtil.getConnection()) {
            // Check that peers table exists
            var meta = conn.getMetaData();
            try (var peersRs = meta.getTables(null, null, "peers", null)) {
                assertTrue(peersRs.next(), "Peers table should exist");
            }
            
            // Check that messages table exists
            try (var messagesRs = meta.getTables(null, null, "messages", null)) {
                assertTrue(messagesRs.next(), "Messages table should exist");
            }
            
            // Check that files table exists
            try (var filesRs = meta.getTables(null, null, "files", null)) {
                assertTrue(filesRs.next(), "Files table should exist");
            }
        }
    }
}