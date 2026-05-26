# LANIM 技术架构 (FRAME)

> 本文档为局域网 IM 系统的**技术架构决策记录**，包含所有已确认的技术选型、模块结构、协议约定和构建配置。

---

## 1. 项目身份

| 项 | 决策 |
|---|------|
| 包名 | `com.alpha.lanim` |
| 构建工具 | Gradle（推荐 Groovy DSL） |
| JDK | **25.0.2 LTS** |
| 编码 | UTF-8 |
| UI 框架 | JavaFX（OpenJFX，版本不限偏好） |
| 工作目录 | 工程根目录，所有相对路径以此为准 |

---

## 2. 网络架构

### 2.1 发现层 —— mDNS / DNS-SD

**废弃裸 UDP 自定义协议，改用标准 mDNS。**

| 项 | 决策 |
|---|------|
| 库 | `org.jmdns:jmdns` |
| 服务类型 | `_lanim._tcp.local.` |
| 注册内容 | 主机名、TCP 端口、TXT 记录 |
| TXT 记录 | `roomId=<SHA-512 hex>`、`nickname=<昵称>` |
| 发现方式 | 浏览器持续监听同类型服务，按 `roomId` 过滤同房 peer |
| 端口 | mDNS 标准端口 5353（由 jmdns 库内部处理，无需显式配置） |

**流程：**
1. 每个 peer 启动时注册一个 `_lanim._tcp.local.` 服务，TXT 记录含 `roomId`
2. 同时启动 ServiceListener 浏览同类型服务
3. 当发现同 `roomId` 的服务 → 加入已知 peer 列表 → 主动建立 TCP 连接
4. 当服务消失 → 标记 peer 离线

**模块对应：** `com.alpha.lanim.bll.MdnsDiscoveryService`

---

### 2.2 传输层 —— TCP + TLS

**首期直接实现 TLS，PlainTCP 保留作为 debug/fallback。**

| 项 | 决策 |
|---|------|
| 连接模型 | **长连接**（每 peer 一条持久 TCP） |
| TCP 端口 | **自动分配**（设为 0，mDNS 通告实际绑定端口，零冲突） |
| TLS 实现 | `SSLSocket` / `SSLServerSocket` |
| 证书 | 自签名证书，首次启动时自动生成 |
| 信任模型 | **TOFU（Trust-On-First-Use）**：首次连接接受对端证书并缓存公钥指纹，后续验证一致性 |
| 配置开关 | `transport.mode=tls`（默认）/ `transport.mode=plain` |
| keystore 路径 | `config/keystore.jks` |

**抽象层次：**
```
DuplexTransport (接口)
 ├── PlainTcpTransport  (明文，debug)
 └── TlsTcpTransport    (TLS，生产)
         │
TransportFactory.create(mode) → 返回对应实现
```

**TOFU 信任流程：**
1. 首次连接某 peer → 提取对方证书公钥 SHA-256 指纹
2. 存入本地信任库 `config/trusted_peers.json`
3. 后续连接同一 peer → 验证指纹是否匹配
4. 首次连接时弹出 UI 确认框（可选，可默认接受）

**模块对应：**
- `com.alpha.lanim.bll.transport.DuplexTransport`
- `com.alpha.lanim.bll.transport.PlainTcpTransport`
- `com.alpha.lanim.bll.transport.TlsTcpTransport`
- `com.alpha.lanim.bll.transport.TransportFactory`
- `com.alpha.lanim.bll.crypto.CertManager`
- `com.alpha.lanim.bll.crypto.HashUtil`

---

### 2.3 成帧规则

**所有 TCP 字节流统一使用长度前缀成帧：**

```
[4 字节 big-endian 长度] [JSON UTF-8 载荷]
```

- 长度 = JSON 载荷的字节数（不含 4 字节前缀自身）
- 控制消息、聊天文本、文件分片 **共用同一成帧规则**
- 仅在 `MessageType` 与载荷语义上区分

---

## 3. 应用层协议

### 3.1 统一信封（JSON，Gson 序列化）

```json
{
  "type": "CHAT_TEXT",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "senderId": "peer-uuid",
  "roomId": "sha512-hex-64chars",
  "sequence": 42,
  "timestamp": 1716672000000,
  "payload": { ... }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | `string` | `MessageType` 枚举值 |
| `messageId` | `string` | UUID，全局唯一主键 |
| `senderId` | `string` | 发送方 peer UUID |
| `roomId` | `string` | `SHA-512(roomSecret)` 的 hex 字符串 |
| `sequence` | `int` | 发送方单调递增序号（每个 sender 从 0 自增） |
| `timestamp` | `long` | 发送时刻 UTC 毫秒时间戳（仅辅助显示，不用于排序） |
| `payload` | `object` | 可变载荷，按 `type` 不同 |

---

### 3.2 消息类型枚举

| 类型 | 方向 | Payload 结构 | 说明 |
|------|------|-------------|------|
| `SYNC_REQ` | Peer→Peer | `{"lastSequences": {"peerA": 15, "peerB": 7}}` | 请求增量同步，携带各方水位线 |
| `SYNC_RESP` | Peer→Peer | `{"messages": [Envelope, ...]}` | 返回对方未见的消息列表 |
| `CHAT_TEXT` | Peer→Peer | `{"text": "hello"}` | 聊天文本消息 |
| `FILE_META` | Peer→Peer | `{"fileId": "uuid", "fileName": "doc.pdf", "contentType": "application/pdf", "totalSize": 1048576, "totalChunks": 16, "checksum": "sha256-hex"}` | 文件传输通告 |
| `FILE_CHUNK` | Peer→Peer | `{"fileId": "uuid", "chunkIndex": 3, "totalChunks": 16, "data": "base64..."}` | 文件数据分片 |
| `FILE_CHUNK_ACK` | Peer→Peer | `{"fileId": "uuid", "missingChunks": [5, 7]}` | 请求重传缺失分片 |
| `HEARTBEAT` | Peer↔Peer | `{}` | 长连接保活（可选，TCP keepalive 亦可） |

**文件传输分片参数：**
- 每片 **64 KB**（`Constants.FILE_CHUNK_SIZE = 65536`）
- 大文件分片读取，**绝不整块读入内存**
- 接收方逐片写盘，收齐后校验 SHA-256

---

### 3.3 消息全局排序

| 项 | 决策 |
|---|------|
| 排序键 | `(roomId, senderId, sequence)` |
| sequence 语义 | 每个 sender 对本房间的**单调递增计数器**，自己每发一条消息（含 `CHAT_TEXT`、`FILE_META`）自增 |
| 同步消息 | `SYNC_REQ` / `SYNC_RESP` / `HEARTBEAT` / `FILE_CHUNK_ACK` **不占 sequence** |
| 去重 | 按 `messageId`（UUID）upsert，保证幂等 |
| 时间戳 | 仅用于 UI 展示，**不参与排序决策** |

---

## 4. 同步模型

### 4.1 水位线增量同步

```
每个 peer 本地维护 Map<String, Integer> lastSeenFromPeer
即：{ "peer_uuid_A": 15, "peer_uuid_B": 7 }

SYNC_REQ 携带此 Map → 对端处理：
  for each peerId in request:
    查询本地 messages WHERE sender_id=peerId AND sequence > lastSeen
    返回给请求方

收到 SYNC_RESP → 逐条 upsert 本地 DB（按 messageId 去重）
```

### 4.2 同步调度

| 项 | 决策 |
|---|------|
| 间隔 | **500ms**，配置键 `sync.interval.ms` |
| 调度器 | `ScheduledExecutorService` |
| 每次同步 | 对每个已知在线 peer 发送 `SYNC_REQ` |
| 复杂度 | *N* 个成员时每轮 *N-1* 次请求，小局域网可接受 |

### 4.3 消息追加与 UI 刷新

- 新消息入库后触发回调 → JavaFX `Platform.runLater` 更新 UI
- 按 `(roomId, senderId, sequence)` 排序渲染

---

## 5. 持久化 (SQLite)

### 5.1 表结构

> 建表脚本位于 `resources/sql/schema.sql`

```sql
CREATE TABLE IF NOT EXISTS peers (
    peer_id    TEXT PRIMARY KEY,
    nickname   TEXT NOT NULL,
    address    TEXT NOT NULL,
    port       INTEGER NOT NULL,
    cert_fingerprint TEXT,
    last_seen  INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS messages (
    message_id   TEXT PRIMARY KEY,
    room_id      TEXT NOT NULL,
    sender_id    TEXT NOT NULL,
    sequence     INTEGER NOT NULL,
    type         TEXT NOT NULL,
    timestamp    INTEGER NOT NULL,
    payload_json TEXT NOT NULL,
    UNIQUE(sender_id, sequence)
);

CREATE TABLE IF NOT EXISTS files (
    file_id         TEXT PRIMARY KEY,
    message_id      TEXT NOT NULL,
    file_name       TEXT NOT NULL,
    content_type    TEXT,
    total_size      INTEGER NOT NULL,
    total_chunks    INTEGER NOT NULL,
    checksum        TEXT,
    local_path      TEXT,
    received_chunks INTEGER NOT NULL DEFAULT 0,
    status          TEXT NOT NULL DEFAULT 'PENDING',
    FOREIGN KEY (message_id) REFERENCES messages(message_id)
);

CREATE INDEX IF NOT EXISTS idx_messages_room_sender_seq
    ON messages(room_id, sender_id, sequence);
CREATE INDEX IF NOT EXISTS idx_messages_room_time
    ON messages(room_id, timestamp);
```

### 5.2 文件存储

| 项 | 决策 |
|---|------|
| 数据库路径 | `data/lanim.db`（JDBC: `jdbc:sqlite:data/lanim.db`） |
| 接收文件目录 | `data/files/<fileId>/<originalFileName>` |
| 策略 | SQLite 仅存元数据 + 路径，**无 BLOB** |
| 分片接收 | 逐片追加写入临时文件，收齐后校验 → 重命名为目标路径 |
| 自动建库 | 首次运行若 `data/` 目录或 `lanim.db` 不存在，程序自动创建 |

### 5.3 JDBC 驱动

| 项 | 决策 |
|---|------|
| 坐标 | `org.xerial:sqlite-jdbc` |
| 连接串 | `jdbc:sqlite:data/lanim.db` |
| .gitignore | `*.db`、`data/`、`config/keystore.jks` |

---

## 6. 安全

### 6.1 房间口令处理

```
roomSecret (用户输入明文)
     │
     ▼ SHA-512
roomId (128 字符 hex)
     │
     ├── 写入 mDNS TXT 记录
     ├── 写入消息 Envelope.roomId
     └── 本地 SQLite 中标记消息所属房间
```

- **绝不**在网络中传输原始 `roomSecret`
- `roomId` 在 mDNS 广播中可见（同一局域网），但原始口令不可逆

### 6.2 TLS 证书

| 项 | 决策 |
|---|------|
| 生成 | 首次启动自动生成自签名 RSA 2048 密钥对 + X.509 证书 |
| 存储 | `config/keystore.jks`，密码随机生成（存入本地配置） |
| 信任 | TOFU：指纹存入 `config/trusted_peers.json` |
| 指纹算法 | SHA-256（证书公钥指纹） |

### 6.3 已知限制

- mDNS 限于同一广播域（同子网）
- 自签名 TLS 仅限 LAN 可信环境
- SHA-512 哈希碰撞概率极低但理论上存在
- TOFU 存在首次连接中间人风险（局域网课设可接受）

---

## 7. 模块结构

```
com.alpha.lanim
 ├─ Launcher.java                       (main 入口)
 │
 ├─ ui
 │   ├─ LoginController.java            (昵称/端口/TLS模式等配置)
 │   ├─ MainController.java             (房间成员列表与会话入口)
 │   ├─ ChatController.java             (聊天窗口：文本 + 文件进度)
 │   └─ CertConfirmDialog.java          (TOFU 首次连接确认弹窗)
 │
 ├─ bll
 │   ├─ PeerService.java                (本机及已知 peer 信息管理)
 │   ├─ MdnsDiscoveryService.java       (mDNS 注册 + 浏览)【替换原 UdpDiscoveryService】
 │   ├─ PeerConnectionManager.java      (TCP 长连接池管理)
 │   ├─ SyncService.java                (周期水位线同步调度)
 │   ├─ TcpChatService.java             (收发与会话生命周期管理)
 │   ├─ MessageService.java             (按 type 分发消息)
 │   ├─ FileTransferService.java        (文件分片收发调度)
 │   ├─ transport
 │   │    ├─ DuplexTransport.java        (接口)
 │   │    ├─ PlainTcpTransport.java      (明文实现)
 │   │    ├─ TlsTcpTransport.java        (TLS 实现)
 │   │    └─ TransportFactory.java
 │   └─ crypto
 │        ├─ CertManager.java            (自签名证书生成 / TOFU 校验)
 │        └─ HashUtil.java               (SHA-512 / SHA-256 工具)
 │
 ├─ dal
 │   ├─ DBUtil.java                     (SQLite 连接管理 / 初始化建表)
 │   ├─ MessageDao.java                 (消息 CRUD / 水位线查询)
 │   ├─ FileDao.java                    (文件元数据 CRUD)
 │   └─ PeerDao.java                    (peer 信息与证书指纹 CRUD)
 │
 ├─ model
 │   ├─ Peer.java                       (peerId, nickname, address, port, certFingerprint)
 │   ├─ MessageType.java                (枚举)
 │   ├─ Envelope.java                   (统一信封：type, messageId, senderId, roomId, sequence, timestamp, payload)
 │   ├─ ChatPayload.java                (CHAT_TEXT 载荷)
 │   ├─ FileMetaPayload.java            (FILE_META 载荷)
 │   ├─ FileChunkPayload.java           (FILE_CHUNK 载荷)
 │   ├─ FileChunkAckPayload.java        (FILE_CHUNK_ACK 载荷)
 │   ├─ SyncReqPayload.java             (SYNC_REQ 载荷)
 │   ├─ SyncRespPayload.java            (SYNC_RESP 载荷)
 │   └─ FileRecord.java                 (文件传输持久化实体)
 │
 └─ util
     ├─ JsonUtil.java                   (Gson 单例封装)
     ├─ Validator.java                  (昵称、端口、文本长度校验)
     └─ Constants.java                  (默认值、配置键)
```

---

## 8. Gradle 构建配置

### 8.1 依赖清单

```groovy
plugins {
    id 'application'
    id 'org.openjfx.javafxplugin'
}

repositories {
    mavenCentral()
}

javafx {
    version = "23.0.1"
    modules = ['javafx.controls', 'javafx.fxml']
}

dependencies {
    implementation 'org.xerial:sqlite-jdbc:3.46.1.0'
    implementation 'org.jmdns:jmdns:3.5.9'
    implementation 'com.google.code.gson:gson:2.11.0'
}

application {
    mainClass = 'com.alpha.lanim.Launcher'
}

// 可选：如果需要单独跑单元测试
tasks.test {
    useJUnitPlatform()
}
```

### 8.2 目录布局

```
LANIM/
 ├─ build.gradle
 ├─ settings.gradle
 ├─ .gitignore
 ├─ README.md
 ├─ FRAME.md
 │
 ├─ config/
 │    ├─ keystore.jks          (生成，gitignore)
 │    └─ trusted_peers.json    (生成，gitignore)
 │
 ├─ data/                      (生成，整体 gitignore)
 │    ├─ lanim.db
 │    └─ files/
 │
 ├─ resources/
 │    └─ sql/
 │         └─ schema.sql
 │
 └─ src/
      ├─ main/java/com/alpha/lanim/
      │    ├─ Launcher.java
      │    ├─ ui/ ...
      │    ├─ bll/ ...
      │    ├─ dal/ ...
      │    ├─ model/ ...
      │    └─ util/ ...
      └─ test/java/com/alpha/lanim/
           └─ ...
```

---

## 9. 运行时配置

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `transport.mode` | `tls` | `plain` 或 `tls` |
| `sync.interval.ms` | `500` | 同步周期（毫秒） |
| `sqlite.path` | `data/lanim.db` | 数据库相对路径 |
| `files.path` | `data/files/` | 接收文件根目录 |
| `keystore.path` | `config/keystore.jks` | TLS 私钥存储路径 |
| `nickname` | `User-XXXX` | 默认昵称（XXXX 为随机 4 位 hex） |
| `file.chunk.size` | `65536` | 文件分片大小（字节） |

---

## 10. 启动流程

```
1. 解析/加载本地配置
2. 初始化 SQLite（建库建表若不存在）
3. 加载或生成 TLS 证书 (CertManager)
4. 绑定 TCP 端口 (port=0, 自动获取)
5. 启动 mDNS 注册 (_lanim._tcp.local.)
6. 启动 mDNS 浏览 (ServiceListener)
7. 用户输入昵称 + 房间口令 → roomId=SHA-512(secret)
8. 对 mDNS 发现的同 roomId peer 建立 TCP(TLS) 连接
9. 启动 SyncService 周期同步
10. 进入聊天界面
```

---

## 11. 开发约定

- **日志**：使用 `java.util.logging`（或 SLF4J），关键节点打 INFO/DEBUG 日志
- **线程**：网络 I/O 和文件 I/O 在后台线程；UI 更新通过 `Platform.runLater`
- **错误处理**：连接断开自动重连（指数退避），异常不崩溃主线程
- **测试**：单元测试覆盖核心逻辑（编解码、DAO、同步算法）
- **提交**：`.db`、`data/`、`config/keystore.jks`、`config/trusted_peers.json` 加入 `.gitignore`

---

*本文档为项目技术架构的唯一权威记录。实现过程中如有偏离，须同步更新本文档。*
