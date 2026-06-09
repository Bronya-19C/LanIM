# LANIM 技术架构 (FRAME)

> 本文档为局域网 IM 系统的**技术架构决策记录**，反映当前 **Client–Server** 实现。若实现偏离本文档，须同步更新。

---

## 1. 项目身份

| 项 | 决策 |
|---|------|
| 包名 | `com.alpha.lanim` |
| 构建工具 | Gradle（Groovy DSL） |
| JDK | **25** |
| 编码 | UTF-8 |
| UI 框架 | JavaFX（OpenJFX 23.0.1） |
| 工作目录 | 工程根目录，相对路径以此为准 |

---

## 2. 网络架构

### 2.1 总体拓扑 — Client–Server

**废弃 P2P  mesh（mDNS 发现 + 多端周期性同步），改用中心 CLI 服务端。**

| 项 | 决策 |
|---|------|
| 服务端进程 | `com.alpha.lanim.server.LanIMServer`（CLI，无 GUI） |
| 客户端进程 | `com.alpha.lanim.Launcher`（JavaFX） |
| 发现方式 | 客户端登录页 **手动填写** `host:port` |
| 默认端口 | **9090**（`Constants.DEFAULT_SERVER_PORT`） |
| 连接模型 | 每客户端 **一条 TCP 长连接** 至服务端 |
| 房间隔离 | `roomId = SHA-512(roomSecret)` hex；服务端按 `roomId` 分房间 |

**变更原因（记录）：** 原 P2P 方案在防火墙、校园 AP 隔离等环境下联调困难；改为单端口 C–S，并由学校远程机集中承载服务端。

### 2.1.1 课程部署环境

| 项 | 值 |
|---|-----|
| 服务端主机 | 学校提供的远程服务器 |
| 服务端 IP | **`10.129.245.252`** |
| 端口 | **9090** |
| 客户端默认连接串 | **`10.129.245.252:9090`** |
| 服务端进程 | 仅在远程机上 `runServer` |
| 客户端进程 | 组员校园网本机 `run`（Launcher） |

```
  校园网 Client A ──┐
  校园网 Client B ──┼── TCP/TLS ──► 10.129.245.252:9090 (LanIMServer)
  校园网 Client C ──┘                      │
                                           ▼
                                    SQLite (远程机)
```

客户端登录页 **Server Address** 须指向上述地址；留空时代码默认 `localhost:9090`，**仅适用于本机双开调试**，正式联调必须填写远程 IP。

**模块对应：**
- `com.alpha.lanim.server.LanIMServer` — 监听、accept、线程池
- `com.alpha.lanim.server.ClientHandler` — 单连接消息处理与转发
- `com.alpha.lanim.server.RoomManager` — 房间成员、序号、服务端持久化
- `com.alpha.lanim.bll.ClientConnectionService` — 客户端出站连接

---

### 2.2 传输层 — TCP + TLS

| 项 | 决策 |
|---|------|
| 成帧 | `[4 byte big-endian length][UTF-8 JSON]` |
| 默认模式 | **TLS**（`Constants.DEFAULT_TRANSPORT_MODE = tls`） |
| 明文 fallback | 服务端 `--plain`；客户端登录页取消 TLS 勾选 |
| TLS 实现 | `SSLSocket` / `SSLServerSocket` |
| 证书 | 自签名 RSA 2048，`config/keystore.jks` |
| 客户端信任 | `TofuTrustManager`（接受有效自签服务端证书） |

**抽象层次：**
```
DuplexTransport (接口)
 ├── PlainTcpTransport
 └── TlsTcpTransport
```

服务端 accept 后按模式包装 `DuplexTransport`；客户端 `ClientConnectionService.connect()` 同理。

**Gradle 任务：**
- `gradlew run` → 客户端 `Launcher`
- `gradlew runServer` → 服务端 `LanIMServer`

---

## 3. 应用层协议

### 3.1 统一信封（JSON，Gson）

```json
{
  "type": "CHAT_TEXT",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "senderId": "peer-uuid",
  "roomId": "sha512-hex-128chars",
  "sequence": 42,
  "timestamp": 1716672000000,
  "payload": { }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | `MessageType` 枚举 |
| `messageId` | string | UUID，去重主键 |
| `senderId` | string | 客户端 peer UUID |
| `roomId` | string | SHA-512(roomSecret) hex |
| `sequence` | int | **服务端分配的房间级全局递增序号**（聊天/FILE_META） |
| `timestamp` | long | UTC 毫秒，辅助展示 |
| `payload` | object | 按 type 解析 |

---

### 3.2 消息类型

| 类型 | 方向 | Payload | 说明 |
|------|------|---------|------|
| `JOIN` | Client → Server | `{peerId, nickname, roomSecret}` | 首条业务消息；服务端算 roomId |
| `JOIN_ACK` | Server → Client | `{assignedId, roomId, history[], members[]}` | 历史 + 成员列表 |
| `USER_JOINED` | Server → Clients | `{peerId, nickname}` | 广播给房间内其他人 |
| `USER_LEFT` | Server → Clients | `{peerId, nickname}` | 连接断开时广播 |
| `CHAT_TEXT` | Client → Server → Others | `{text}` | 服务端赋 seq、持久化、转发 |
| `FILE_META` | 经 Server 中继 | 文件名、大小、分片数、checksum 等 | 服务端赋 seq 并持久化 |
| `FILE_CHUNK` | 经 Server 中继 | Base64 分片数据 | 不单独持久化全文 |
| `FILE_CHUNK_ACK` | 经 Server 中继 | 缺失分片列表 | 请求重传 |
| `HEARTBEAT` | 预留 | `{}` | 当前未强制 |

**已废弃（P2P 遗留 model，代码中可能仍存在类文件）：** `SYNC_REQ`、`SYNC_RESP`

---

### 3.3 会话流程

```
Client                          Server                         Other Clients
  │ connect TCP/TLS               │                                │
  │──────── JOIN ──────────────────►│                                │
  │◄─────── JOIN_ACK ─────────────│                                │
  │         (history, members)    │──── USER_JOINED ──────────────►│
  │                               │                                │
  │──────── CHAT_TEXT ───────────►│ persist, assign seq            │
  │                               │──── CHAT_TEXT ────────────────►│
  │                               │                                │
  │ disconnect                    │──── USER_LEFT ────────────────►│
```

---

### 3.4 消息排序

| 项 | 决策 |
|---|------|
| 序号来源 | 服务端 `RoomManager.nextSequence(roomId)` |
| 排序 | 同房间内按 `sequence` 升序展示 |
| 去重 | 客户端/服务端 `INSERT OR IGNORE`，按 `messageId` |
| 历史拉取 | `JOIN_ACK.history` 全量下发该 room 已有消息 |

---

## 4. 持久化 (SQLite)

### 4.1 双端数据库

| 角色 | 路径 | 职责 |
|------|------|------|
| 服务端 | `data/lanim.db`（工作目录） | **权威**聊天历史、FILE_META |
| 客户端 | `data/lanim.db`（工作目录） | 本地缓存、UI 展示、文件元数据 |

> 服务端与客户端若在同一目录运行，会共用同一 db 文件；联调时通常分目录或分机器。

### 4.2 表结构

脚本：`src/main/resources/sql/schema.sql`

- `peers` — 客户端本地成员缓存（`PeerDao`）
- `messages` — 聊天与 FILE_META（`message_id` PK，`UNIQUE(sender_id, sequence)`）
- `files` — 文件传输进度与路径

索引：`idx_messages_room_sender_seq`、`idx_messages_room_time`

### 4.3 文件存储

| 项 | 决策 |
|---|------|
| 接收目录 | `data/files/` |
| 分片大小 | 65536 字节 |
| 策略 | SQLite 存元数据，文件落盘，无 BLOB |

---

## 5. 安全

### 5.1 房间口令

```
roomSecret → SHA-512 → roomId (hex)
```

- `roomSecret` 仅在 `JOIN` 载荷中出现（TLS 关闭时链路明文可见）
- 服务端从不存储原始口令，只使用 `roomId` 分房间

### 5.2 TLS

| 项 | 决策 |
|---|------|
| 密钥库 | `config/keystore.jks`，密码 `lanim-local-dev`（课设固定） |
| 生成 | BouncyCastle 自签，CN=LANIM-Peer |
| 适用范围 | 客户端 ↔ 服务端链路；**非** Web PKI |

### 5.3 已知限制

- 单服务端单点，无集群
- 口令非身份认证，仅分房间
- 跨 NAT / 异网段未专门适配
- `build.gradle` 中 `jmdns` 依赖 **未使用**，待清理

---

## 6. 模块结构

```
com.alpha.lanim
 ├─ Launcher.java                     (JavaFX 客户端入口)
 │
 ├─ server
 │   ├─ LanIMServer.java              (CLI 服务端入口)
 │   ├─ ClientHandler.java            (单连接：JOIN/聊天/文件/转发)
 │   └─ RoomManager.java              (房间、序号、服务端 DAO)
 │
 ├─ ui
 │   ├─ LoginController.java          (serverAddress, nickname, roomSecret, TLS)
 │   ├─ MainController.java           (成员列表、聊天、发消息/文件)
 │   └─ CertConfirmDialog.java        (TOFU 确认，预留)
 │
 ├─ bll
 │   ├─ ClientConnectionService.java  (客户端 TCP/TLS + 读线程)
 │   ├─ TcpChatService.java           (Envelope 收发桥接)
 │   ├─ MessageService.java           (type 分发、JOIN_ACK 写库)
 │   ├─ FileTransferService.java      (分片收发)
 │   ├─ PeerService.java              (localPeerId、远程成员 Map)
 │   ├─ transport/
 │   │    ├─ DuplexTransport.java
 │   │    ├─ PlainTcpTransport.java
 │   │    ├─ TlsTcpTransport.java
 │   │    └─ TransportFactory.java
 │   └─ crypto/
 │        └─ CertManager.java
 │
 ├─ dal/
 │   ├─ DBUtil.java
 │   ├─ MessageDao.java               (含 getMaxGlobalSequence)
 │   ├─ FileDao.java
 │   └─ PeerDao.java
 │
 ├─ model/
 │   ├─ Envelope.java, MessageType.java
 │   ├─ JoinPayload.java, JoinAckPayload.java
 │   ├─ UserEventPayload.java, ChatPayload.java
 │   ├─ FileMetaPayload.java, FileChunkPayload.java, FileChunkAckPayload.java
 │   ├─ Peer.java, FileRecord.java
 │   └─ SyncReqPayload.java, SyncRespPayload.java  (P2P 遗留，未使用)
 │
 └─ util/
     ├─ JsonUtil.java, HashUtil.java, Validator.java
     └─ Constants.java
```

---

## 7. Gradle 构建

```groovy
application {
    mainClass = 'com.alpha.lanim.Launcher'
}

tasks.register('runServer', JavaExec) {
    mainClass = 'com.alpha.lanim.server.LanIMServer'
}
```

**依赖：** sqlite-jdbc, gson, bouncycastle (bcpkix), openjfx；`jmdns` 为遗留依赖。

---

## 8. 运行时配置

| 配置 | 默认值 | 说明 |
|------|--------|------|
| 课程服务端 IP | 10.129.245.252 | 文档约定；客户端 Server Address |
| `DEFAULT_SERVER_PORT` | 9090 | 服务端监听；完整地址 `10.129.245.252:9090` |
| `DEFAULT_TRANSPORT_MODE` | tls | 服务端默认；CLI `--plain` 覆盖 |
| `DEFAULT_DB_PATH` | data/lanim.db | SQLite |
| `DEFAULT_FILES_PATH` | data/files/ | 客户端收文件 |
| `FILE_CHUNK_SIZE` | 65536 | 分片字节数 |
| `DEFAULT_KEYSTORE_PATH` | config/keystore.jks | TLS |

---

## 9. 启动流程

**服务端：**
1. `DBUtil.init()`、`CertManager.init()`
2. 绑定 `ServerSocket` / `SSLServerSocket`（port 9090）
3. accept 循环 → 每连接 `ClientHandler.run()`

**客户端：**
1. `DBUtil.init()`、`CertManager.init()`
2. 登录页收集 serverAddress / nickname / roomSecret / TLS
3. `ClientConnectionService.connect(host, port)`
4. 发送 `JOIN` → 等待 `JOIN_ACK` → 进入 `MainController`
5. 收发消息经 `TcpChatService` → `MessageService`

---

## 10. 开发约定

- 网络 I/O 在后台线程；UI 更新 `Platform.runLater`
- 服务端 `ClientHandler` 转发不含发送者（`relayToRoomExcept`）
- 测试：`./gradlew test`（单元测试在 `src/test/java` 与 `tb/src/test/java`）
- 勿提交 `data/`、`*.db`、`config/keystore.jks`

---

*本文档为 Client–Server 架构的权威技术记录。*
