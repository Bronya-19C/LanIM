# TestBench for LANIM

This directory contains test bench files for testing the LANIM (Local Area Network Instant Messaging) system.

## Test Categories

1. **Unit Tests** - Testing individual components in isolation
2. **Integration Tests** - Testing interactions between components
3. **Protocol Tests** - Testing message encoding/decoding and framing
4. **Network Tests** - Testing mDNS discovery and TCP/TLS connections
5. **Persistence Tests** - Testing SQLite database operations
6. **File Transfer Tests** - Testing file chunking and reassembly

## Test Structure

Each test file should follow naming conventions:
- `*_test.java` for JUnit tests
- `*_test.txt` for test data or expected outputs
- `*_test.json` for JSON test fixtures

## Running Tests

Tests can be run using Gradle:
```bash
./gradlew test
```