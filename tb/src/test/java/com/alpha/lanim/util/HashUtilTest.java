package com.alpha.lanim.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HashUtilTest {

    @Test
    void testSha512() {
        String input = "test room secret";
        String hash = HashUtil.sha512(input);
        assertEquals(128, hash.length(), "SHA-512 hash should be 128 hex characters");
        assertTrue(hash.matches("[0-9a-fA-F]+"), "SHA-512 hash should contain only hex digits");
        // Test that same input produces same hash
        assertEquals(HashUtil.sha512(input), hash, "SHA-512 should be deterministic");
        // Test that different input produces different hash
        assertNotEquals(HashUtil.sha512(input + "x"), hash, "SHA-512 should be sensitive to input");
    }

    @Test
    void testSha256() {
        String input = "test data";
        String hash = HashUtil.sha256(input);
        assertEquals(64, hash.length(), "SHA-256 hash should be 64 hex characters");
        assertTrue(hash.matches("[0-9a-fA-F]+"), "SHA-256 hash should contain only hex digits");
        // Test that same input produces same hash
        assertEquals(HashUtil.sha256(input), hash, "SHA-256 should be deterministic");
        // Test that different input produces different hash
        assertNotEquals(HashUtil.sha256(input + "x"), hash, "SHA-256 should be sensitive to input");
    }
}