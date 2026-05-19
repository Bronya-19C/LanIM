# 局域网即时通信系统（局域网 IM）

局域网内发现并建立连接，支持与对等端收发即时消息（具体范围见下文「功能范围」）；课堂推荐工具链为 IntelliJ IDEA、UTF-8、相对路径，持久化建议使用 SQLite。

---

## 功能范围

- **网络环境**：限定同一局域网（同网段可互通）；不保证跨 NAT / 校园网隔离场景。
- **通信模型**：对等端发现（如 UDP 广播）+ 会话传输（如 TCP）；先以 **单聊** 为主，群组可作为扩展。
- **界面**：JavaFX。
- **持久化**：SQLite 存储聊天记录、本地昵称 / 监听端口等；源码中附带 **建库建表脚本**（见下文「SQLite」），运行时若库文件不存在则由程序或脚本初始化。
- **消息与载荷**：**首期对用户功能以收发聊天文本为主**；应用层协议自第一版即采用 **统一消息信封（必选类型字段 + 元数据 + 可变载荷）**，并 **预留枚举值**（如后续 `FILE_META` / `FILE_CHUNK`），便于在无伤传输层的前提下扩展 **文件分片传送**（参见下文「应用层协议」）。
- **传输保密（扩展）**：**默认不做加密**，局域网演示为明文载荷（见下文「已知限制」）。**可选**在首期跑通后加入 **TLS**（如 `SSLSocket`），由配置切换；不向作业提交版本隐瞒为「已加密」若实际未启用。

---

## 分层与模块结构

UI 层：只负责界面和用户交互（登录昵称、会话列表、聊天窗口），不写业务细节  
BLL 层：局域网发现（如 UDP）、连接与会话（如 TCP）、消息收发与路由、在线状态等业务逻辑  
DAL 层：只做数据持久化（SQLite：消息历史、配置等按需）  
Model 层：数据对象（实体类）  
Util 层：通用工具和常量（协议编解码、校验、线程/网络助手等）

```
com.xxx.lanim
 ├─ ui
 │   ├─ LoginController.java        （昵称 / 端口等本地配置入口）
 │   ├─ MainController.java         （会话列表、在线用户展示）
 │   └─ ChatController.java         （单聊会话窗口）
 ├─ bll
 │   ├─ PeerService.java             （本机对等端信息管理）
 │   ├─ UdpDiscoveryService.java    （局域网内发现其他客户端，可选心跳）
 │   ├─ TcpChatService.java          （对上封装会话生命周期；对内委托具体传输实现）
 │   ├─ transport 包（或与 Service 并列）
 │   │    ├─ DuplexTransport.java   （接口：建连 / 收发字节或帧）
 │   │    ├─ PlainTcpTransport.java （默认明文 TCP）
 │   │    └─ TlsTcpTransport.java   （可选：JDK TLS 封装，与 Plain 互换）
 │   └─ MessageService.java           （按消息类型分发：文本入主流程；预留文件收发分支）
 ├─ dal
 │   ├─ DBUtil.java
 │   └─ MessageDao.java              （首期存聊天会话与文本；扩展文件时仅存路径/元数据为宜）
 ├─ model
 │   ├─ Peer.java
 │   ├─ MessageType.java            （枚举：首期 `CHAT_TEXT` / 心跳或控制类等；预留 `FILE_META`、`FILE_CHUNK` 等）
 │   ├─ LanPayload.java             （或直接 `Envelope`/`AppMessage`：类型 + 会话 id + 载荷）
 │   └─ ChatMessage.java           （会话内展示的实体，或与 Envelope 按需转换）
 └─ util
     ├─ ProtoUtil.java              （或用 JsonUtil.java，视协议而定）
     ├─ Validator.java              （昵称、端口、文本长度等校验）
     └─ Constants.java
```

---

## 应用层协议（约定）

与设计讨论一致：**传输层**只搬运字节（`DuplexTransport`）；**应用层**用 **统一帧或可解析 PDU**，避免日后加文件时推倒重来。

- **首期必做**：`CHAT_TEXT`（或等价命名）；载荷为 UTF‑8 文本；信封内至少含 **发送方标识、会话/对端上下文、服务端时间或可排序序号**（按你们实际需要裁剪）。
- **扩展预留**：`FILE_META`（文件名、`Content-Type`/`mime`（可选）、总字节数、可选校验）、随后若干 `FILE_CHUNK`（序号、长度、原始字节）；收发在 `MessageService` 中分支，**大文件分片收发，避免整块读入内存**；落盘路径宜在 `data/files/`（或等价），**SQLite 仅存元数据与路径**，不必默认整文件 BLOB。
- **粘包**：TCP 上使用 **长度前缀**（或其它你们固定的成帧规则）包住每条 PDU；控制消息与文本/二进制 chunk **共用同一种成帧**，仅 `MessageType` 与载荷语义不同。
- **UI**：JavaFX 后台线程收发与写盘，**不占应用线程**；文件进度条等与 `FILE_*` 类型绑定的放在扩展迭代中实现即可。

---

## SQLite（数据库）

本机开发采用嵌入式 **SQLite**。

- **JDBC 驱动**：计划中通过 **Maven / Gradle** 引入 `sqlite-jdbc`（例如坐标 `org.xerial:sqlite-jdbc`，具体版本以 `pom.xml` 或 `build.gradle` 为准）。
- **库文件路径**：建议使用**相对路径**，例如仓库根目录下 `data/lanim.db`；首次运行可由程序自动创建目录与数据库文件。JDBC 连接串示例：`jdbc:sqlite:data/lanim.db`（若以工程根目录为工作目录）。
- **表结构脚本**：建议在 `resources/sql/schema.sql` 中维护建表语句，并在 README 或报告中说明与各 `*Dao` 的对应关系；提交产物需包含可直接执行的 SQL。
- **版本控制**：`*.db`、`data/`（含建议的 `data/files/` 接收目录）等生成文件建议加入 `.gitignore`，避免把个人聊天记录或接收文件提交进仓库；同学拉代码后本地会自动生成或通过脚本初始化。

---

## 运行环境与启动方式

- **JDK**：开发与验收：**JDK 25**。本仓库当前约定以负责人开发机检测结果为准：`java -version` 显示 **25.0.2 LTS**（Runtime build 25.0.2+10-LTS-69）。**请全组尽量统一主版本（25）**，减少语言特性与依赖行为不一致；若有成员必须使用其他版本，须在报告或本节注明兼容处理方式。
- **JavaFX**：自 JDK 11 起 JavaFX 不再内置，需在 **Maven / Gradle** 中引入 **OpenJFX**（具体版本、`javafx.controls` / `javafx.fxml` 等模块以实现后的构建文件为准；IDEA 如需 `--module-path` / `--add-modules`，实现后在此处补一句或写在运行配置说明里）。
- **主类**：（实现后填入，例如 `com.xxx.lanim.Launcher`）。
- **启动步骤**：用 IDEA 打开工程 → Maven/Gradle 同步依赖 → 运行主类；局域网内两台设备宜使用 **相同的 JDK 主版本** 做联调。

**账号与口令**：对等聊天场景下计划中 **不设默认服务端账号口令**；若后续增加本地锁或导出加密等涉及密码的功能，须在 README **明文写明默认口令与重置方式**。

---

## 可选扩展：传输层加密（TLS）

本期 **提交与验收默认以明文 TCP 为主**（满足大作业时间与调试成本）。若组员有余力且希望报告中有「安全增强」：**建议走 TLS**，由 JDK 提供协议与套件，而非在每条消息上手写 AES。

- **实现思路**：BLL 中为「字节管道」抽象接口（如上 `DuplexTransport`），默认实现用 `Socket`/`ServerSocket`，扩展实现内部换为 **`SSLSocket` / `SSLServerSocket`**（或等价的 `SSLEngine`，难度更高可先不选）。
- **开关方式**：配置文件或常量中 **`transport.plain | transport.tls`**，两端必须一致；UI 可加勾选框读取同一配置键。
- **证书与信任**：局域网课设常用 **自签名证书**；需约定信任存储（例如内置测试用 `cacerts` 或同源 keystore），并在 README/报告中说明 **仅限演示环境、不做 Web PKI 级别运维**。
- **与 SQLite**：库文件本地化存储与「链路加密」无关；若将来真有敏感需求，再在报告中单独列为扩展（仍为可选）。

---

## 配置说明

| 项 | 说明 |
|----|------|
| 监听端口 TCP | （实现后填入，与 Constants 一致） |
| 发现端口 UDP | （实现后填入） |
| SQLite 文件路径 | 相对路径，如 `data/lanim.db`，与 JDBC `jdbc:sqlite:data/lanim.db` 对应；仓库中勿提交 `.db`，见 `.gitignore` |
| 传输模式 | **默认**：`plain`（明文 TCP）。**扩展**：`tls`（JDK TLS；两端必须与证书/信任配置一致，参见上文「可选扩展」） |

---

## 组员与分工

`（待定）`

---

## 已知限制

- **首期可能仅实现文本聊天**：信封与类型已预留；若未实现 `FILE_*`，须在演示与 README 中与「仅文本载荷」保持一致。
- **默认不进行传输层加密**：同一局域网内仍可能被嗅探或伪造身份（演示环境）；若启用 TLS，仍不等于完整身份体系，须在报告中说明适用范围。
- Windows 防火墙可能拦截 UDP/TCP；测试时可临时放行或仅用本机双开/虚拟机同网段验证。
- 多网卡环境需约定绑定 IP 或让用户选择。
