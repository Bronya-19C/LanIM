# 局域网即时通信系统（LanIM）

LanIM 采用 **客户端—服务端（Client–Server）** 架构：在学校提供的 **远程服务器** 上运行 CLI 服务端；组员在 **校园网** 内运行 JavaFX 客户端，连接同一服务端并输入 **相同房间口令** 进入同一逻辑房间。

**课程部署服务端地址：`10.129.245.252:9090`**

---

## 部署拓扑

```
  校园网客户端 A ──┐
  校园网客户端 B ──┼── TCP ──► 学校远程服务器 10.129.245.252:9090
  校园网客户端 C ──┘              (runServer)  + SQLite 历史
```

- **服务端**：仅在学校远程机上运行 `runServer`（端口 **9090** 由学校网络统一放行）。
- **客户端**：各组员本机运行 `run`；登录页 **Server Address** 填 `10.129.245.252:9090`（或 `10.129.245.252`，端口默认 9090）。
- 客户端之间 **无需互访**，只需能访问上述服务端 IP，可规避校园 Wi‑Fi 的 AP 隔离问题。

---

## 功能范围

- **网络环境**：客户端须处于可访问 `10.129.245.252:9090` 的校园网（或学校规定的 VPN/内网）。
- **会话与拓扑**：中心服务端维护各房间在线成员、消息序号与历史；客户端本地 SQLite 缓存，断线重连经 `JOIN_ACK` 拉取历史。
- **房间口令**：相同 `roomSecret` → SHA-512 得 `roomId`，进入同一逻辑房间。
- **传输**：客户端与服务端单条 TCP 长连接；JSON Envelope + 长度前缀成帧；服务端向同房间其他成员中继消息。
- **界面**：JavaFX（服务端地址、昵称、口令、TLS）。
- **持久化**：服务端与客户端各自 `data/lanim.db`；服务端为权威历史来源。
- **传输保密**：默认 TLS（自签名证书）；与服务端 `--plain` 对应时可关闭。

---

## 分层与模块结构

```
com.alpha.lanim
 ├─ server                          （CLI 服务端，部署于远程服务器）
 │   ├─ LanIMServer.java
 │   ├─ ClientHandler.java
 │   └─ RoomManager.java
 ├─ ui
 │   ├─ LoginController.java
 │   └─ MainController.java
 ├─ bll
 │   ├─ ClientConnectionService.java
 │   ├─ TcpChatService.java
 │   ├─ MessageService.java
 │   ├─ FileTransferService.java
 │   ├─ PeerService.java
 │   ├─ transport/
 │   └─ crypto/CertManager.java
 ├─ dal / model / util
 └─ Launcher.java                    （客户端入口）
```

---

## 应用层协议

| 类型 | 方向 | 说明 |
|------|------|------|
| `JOIN` | 客户端 → 服务端 | 含 peerId、昵称、roomSecret |
| `JOIN_ACK` | 服务端 → 客户端 | roomId、历史、成员列表 |
| `USER_JOINED` / `USER_LEFT` | 服务端 → 客户端 | 成员上下线 |
| `CHAT_TEXT` | 经服务端中继 | 服务端赋序号并持久化 |
| `FILE_META` / `FILE_CHUNK` / `FILE_CHUNK_ACK` | 经服务端中继 | 文件分片（64 KiB） |
| `HEARTBEAT` | 预留 | 未强制使用 |

---

## SQLite

- 驱动：`org.xerial:sqlite-jdbc`（见 `build.gradle`）
- 路径：`data/lanim.db`；建表脚本 `src/main/resources/sql/schema.sql`
- 服务端（远程机）：权威聊天历史
- 客户端（本机）：本地缓存与展示

---

## 运行方式

- **JDK 25**；**JavaFX** OpenJFX 23.0.1
- Windows PowerShell 须在项目根目录使用 `.\gradlew`（见下）

### 服务端（仅在学校远程服务器 `10.129.245.252` 上）

```bash
./gradlew runServer
# 明文联调（客户端须取消 TLS）
./gradlew runServer --args="--plain"
```

主类：`com.alpha.lanim.server.LanIMServer`  
参数：`-p, --port`（默认 9090）、`--tls`（默认）、`--plain`

建议使用 `nohup` / systemd 等保持进程常驻；数据库与 `config/` 生成在远程机工作目录下。

### 客户端（组员本机，校园网）

```powershell
cd LanIM
.\gradlew run
```

| 字段 | 填写 |
|------|------|
| **Server Address** | `10.129.245.252:9090` |
| **Nickname** | 显示昵称 |
| **Room Secret** | 同房口令一致 |
| **Enable TLS** | 与服务端模式一致（默认勾选） |

本机双开调试时可填 `localhost:9090`（须本机同时跑着 `runServer`）。

---

## 配置说明

| 项 | 值 | 说明 |
|----|-----|------|
| 课程服务端地址 | `10.129.245.252` | 客户端 Server Address |
| 服务端 TCP 端口 | `9090` | `Constants.DEFAULT_SERVER_PORT` |
| 传输模式 | `tls`（默认） | 与客户端 TLS 勾选一致 |
| SQLite | `data/lanim.db` | 远程机与本机各自一份 |
| 接收文件 | `data/files/` | 客户端本机 |
| TLS 密钥库 | `config/keystore.jks` | 自签名，课设演示用 |

---

## 组员与分工

`（待定）`

---

## 已知限制

- 依赖远程服务端在线；进程退出后客户端需重连，历史在服务端 SQLite 保留。
- 单点服务端，无集群。
- `roomSecret` 仅划分房间，非强身份认证。
- 客户端须能 TCP 连通 `10.129.245.252:9090`；跨网段/VPN 策略以学校网络为准。
- `build.gradle` 中 `jmdns` 为遗留依赖，当前未使用。

---

## 架构变更说明

初版为 P2P + mDNS + 多端同步。联调困难后改为 Client–Server；部署上采用 **学校远程服务器 + 校园网客户端**，统一连接 `10.129.245.252:9090`。
