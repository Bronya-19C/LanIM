# LANIM Test Plan

## Overview
This test plan outlines the testing strategy for the LANIM (Local Area Network Instant Messaging) system.

## Test Levels
1. Unit Testing
2. Integration Testing
3. System Testing
4. Acceptance Testing

## Test Categories

### 1. Utility Classes
- HashUtil: SHA-512 and SHA-256 implementations
- JsonUtil: Gson serialization/deserialization
- Validator: Input validation (nickname, port, text length)
- Constants: Configuration values

### 2. Model Classes
- Envelope: Message encapsulation
- Payload classes: ChatPayload, FileMetaPayload, etc.
- Peer: Peer information model
- FileRecord: File transfer tracking

### 3. Data Access Layer
- PeerDao: Peer CRUD operations
- MessageDao: Message CRUD and sequence queries
- FileDao: File metadata operations
- DBUtil: Database connection and initialization

### 4. Business Logic Layer
- MdnsDiscoveryService: Service discovery and peer detection
- TransportFactory: Transport creation (plain/TLS)
- TlsTcpTransport: Secure communication
- PeerConnectionManager: Connection lifecycle
- SyncService: Periodic synchronization
- MessageService: Message routing by type
- FileTransferService: File chunking and reassembly

### 5. User Interface
- LoginController: Initial configuration
- MainController: Peer list and room management
- ChatController: Message display and file transfer UI
- CertConfirmDialog: TOFU certificate confirmation

### 6. Network Protocols
- mDNS discovery (_lanim._tcp.local.)
- TCP connection establishment
- TLS handshake and TOFU trust
- Length-prefixed framing (4-byte big-endian)
- Message types and payload structures

### 7. Persistence
- SQLite database schema compliance
- Peer table: peer_id, nickname, address, port, cert_fingerprint, last_seen
- Messages table: message_id, room_id, sender_id, sequence, type, timestamp, payload_json
- Files table: file tracking and transfer status
- Indexes for query performance

### 8. File Transfer
- Chunking (64KB pieces)
- Base64 encoding of binary data
- Missing chunk detection and retransmission
- SHA-256 verification of completed files
- Temporary file handling during transfer

## Test Environment
- Multiple peers on same local network
- Varied room secrets for isolation
- Different nickname and port configurations
- Plain TCP and TLS transport modes
- File transfers of various sizes
- Network disruption simulations

## Pass/Fail Criteria
- All unit tests must pass (≥80% coverage recommended)
- No critical defects in integration/system tests
- Performance benchmarks met (message latency <100ms locally)
- File transfer integrity verified (SHA-256 match)
- UI responsiveness maintained during operations