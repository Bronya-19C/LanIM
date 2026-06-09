# TestBench for LANIM

本目录存放 LANIM（Local Area Network Instant Messaging）的测试基准与补充单元测试。

当前系统为 **Client–Server** 架构：CLI 服务端 + JavaFX 客户端。测试重点从 mDNS/P2P 同步转向协议编解码、DAO、传输层与服务端房间逻辑。

## Test Categories

1. **Unit Tests** — 工具类、Model、DAO、Transport 工厂等隔离测试
2. **Integration Tests** — 客户端连接服务、MessageService 分发、服务端 RoomManager（待补充）
3. **Protocol Tests** — Envelope 序列化、长度前缀成帧、JOIN/JOIN_ACK 载荷
4. **Network Tests** — 客户端到服务端的 TCP/TLS 连接（非 mDNS）
5. **Persistence Tests** — SQLite 建表、MessageDao 插入与 room 查询
6. **File Transfer Tests** — 分片载荷编解码与 FileDao（端到端待联调）

## Test Structure

- Java 测试：`tb/src/test/java/com/alpha/lanim/...`（Gradle 已合并到 `test` sourceSet）
- 命名：`*Test.java`（JUnit 5）
- 测试数据：`tb/test-data/`（若需要）

## Running Tests

```bash
./gradlew test
# Windows
gradlew test
```

## Manual / System Tests (Client–Server)

**课程环境：**

1. 远程服务器 `10.129.245.252`：`./gradlew runServer`
2. 校园网客户端 B/C：`.\gradlew run`，Server Address 填 **`10.129.245.252:9090`**，Room Secret 相同
3. 验证：成员列表、消息互通、断线 USER_LEFT、重连 JOIN_ACK 历史
4. 连通性：`Test-NetConnection 10.129.245.252 -Port 9090`
5. 可选：`--plain` 与客户端取消 TLS，排除证书因素

**本机双开调试：** 本机 `runServer` + `run`，地址填 `localhost:9090`。
