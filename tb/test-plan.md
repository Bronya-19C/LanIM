# LANIM Test Plan

## Overview

LANIM 采用 **Client–Server** 架构。本测试计划覆盖单元测试、集成/联调及验收场景。P2P/mDNS/SyncService 相关项已标记为 **废弃**，不再纳入必测范围。

## Test Levels

1. Unit Testing
2. Integration Testing
3. System Testing（服务端 + 多客户端联调）
4. Acceptance Testing

## Test Categories

### 1. Utility Classes

- HashUtil: SHA-512（roomId）、SHA-256（文件校验）
- JsonUtil: Gson 序列化/反序列化
- Validator: nickname、roomSecret、文本长度
- Constants: 端口 9090、传输模式、路径常量

### 2. Model Classes

- Envelope: 统一信封
- JoinPayload / JoinAckPayload / UserEventPayload
- ChatPayload, FileMetaPayload, FileChunkPayload, FileChunkAckPayload
- Peer, FileRecord
- SyncReqPayload / SyncRespPayload（遗留类，无运行时路径）

### 3. Data Access Layer

- DBUtil: 建库建表
- MessageDao: insert、findByRoomId、getMaxGlobalSequence
- PeerDao: 客户端成员缓存
- FileDao: 文件元数据

### 4. Business Logic Layer

- **ClientConnectionService**: TCP/TLS 连接、读线程回调
- **TcpChatService**: Envelope 收发桥接
- **MessageService**: JOIN_ACK / USER_* / CHAT_TEXT / FILE_* 分发
- **FileTransferService**: 分片调度
- **PeerService**: localPeerId、远程成员列表
- TransportFactory / PlainTcpTransport / TlsTcpTransport
- CertManager: 自签证书、SSLContext

### 5. Server Layer

- **LanIMServer**: 端口绑定、accept、shutdown
- **RoomManager**: join/leave、nextSequence、persistMessage、relay
- **ClientHandler**: JOIN 流程、CHAT 赋序号转发、断开 USER_LEFT

### 6. User Interface

- LoginController: serverAddress、nickname、roomSecret、TLS
- MainController: 成员列表、消息展示、发送与文件按钮
- CertConfirmDialog: TOFU（若接入）

### 7. Network Protocols

- TCP 长连接（客户端 → 服务端）
- TLS 握手（默认）/ Plain 模式一致性问题
- 长度前缀成帧（4-byte big-endian + JSON）
- 消息类型：JOIN、JOIN_ACK、USER_JOINED、USER_LEFT、CHAT_TEXT、FILE_*

### 8. Persistence

- 服务端 SQLite 为房间历史权威来源
- 客户端 SQLite 为本地缓存
- schema 合规：peers、messages、files 及索引

### 9. File Transfer

- 64 KiB 分片
- 经服务端中继 FILE_META / FILE_CHUNK / FILE_CHUNK_ACK
- SHA-256 完整性校验
- `data/files/` 落盘

## Test Environment

- **生产/验收**：远程服务端 `10.129.245.252` 运行 `runServer`；至少 2 台校园网客户端连接 `10.129.245.252:9090`
- **开发调试**：本机 `runServer` + `run`，地址 `localhost:9090`
- 相同 roomSecret；TLS 与 plain 各测一轮
- 客户端连通性：`Test-NetConnection 10.129.245.252 -Port 9090`

## Pass/Fail Criteria

- 所有单元测试通过（`gradlew test`）
- 双客户端经同一服务端互通消息，延迟局域网内可接受（&lt;100ms 量级）
- 成员上下线事件正确
- 重连后 JOIN_ACK 历史与线上一致
- 文件传输 SHA-256 一致
- 无 UI 线程阻塞导致的明显卡顿

## Deprecated (P2P era)

- mDNS / jmdns 发现
- SyncService 500ms 水位线同步
- PeerConnectionManager 多 peer 长连接
- 客户端入站 TCP 端口与 UDP 5353
