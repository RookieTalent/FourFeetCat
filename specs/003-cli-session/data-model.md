# Phase 1 Data Model: CLI 入口层与会话持久化（第18节）

**Input**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

---

## 1. `sessions` 表（本节唯一新增的表）

字段与约束**逐字摘自** `docs/class/schema.sql`（课程参考建表脚本）的 `sessions` 段；双轨脚本同版本号、同列名同约束、方言各自正确。

| 列 | sqlite 轨 | postgresql 轨 | 约束 | 说明 |
|---|---|---|---|---|
| `session_id` | `VARCHAR(64)` | `VARCHAR(64)` | PK | 会话标识，由 `channel + user + profile` 联合生成（生成规则见 §3） |
| `profile_name` | `VARCHAR(64)` | `VARCHAR(64)` | NOT NULL | 所属 Agent |
| `channel` | `VARCHAR(32)` | `VARCHAR(32)` | NOT NULL | 接入渠道（`cli` / 后续 `web` / `scheduler`） |
| `user_id` | `VARCHAR(64)` | `VARCHAR(64)` | NOT NULL | 用户标识 |
| `messages_json` | `TEXT` | `TEXT` | NOT NULL DEFAULT `'[]'` | 序列化的对话历史（格式见 §2） |
| `status` | `VARCHAR(16)` | `VARCHAR(16)` | NOT NULL DEFAULT `'active'` | `active` / `archived` |
| `created_at` | `TEXT` | `TIMESTAMP` | NOT NULL | 创建时间（sqlite 落 ISO-8601 文本） |
| `last_active_at` | `TEXT` | `TIMESTAMP` | NOT NULL | 最后活跃时间 |
| `archived_at` | `TEXT` | `TIMESTAMP` | 可空 | 归档时间 |

索引：`idx_sessions_status ON sessions(status)`（双轨一致）。

> **跨轨口径风险（记录，不在本节修）**：实体侧时间戳字段按仓库既有先例（`ToolInvocation.createdAt`、`LlmCall.createdAt`）声明为 `String`，在 postgres 轨的 `TIMESTAMP` 列上读写会不匹配。属前序节遗留口径，见 [research.md](research.md) D8，写进验收报告的交人部分。

---

## 2. `messages_json` 的编码格式（会话历史）

一条 JSON 数组，按发生顺序排列。会话里只会出现三种消息（system 消息每轮由 `PromptBuilder` 现拼、不入会话）：

```json
[
  {"type": "user", "text": "今天天气怎么样"},
  {"type": "assistant", "text": "", "toolCalls": [
      {"id": "call_1", "type": "function", "name": "http_get", "arguments": "{\"url\":\"...\"}"}]},
  {"type": "tool", "responses": [
      {"id": "call_1", "name": "http_get", "responseData": "{\"temp\":26}"}]}
]
```

**校验规则**：

| 规则 | 口径 |
|---|---|
| 未知 `type` | **抛异常**，不静默丢弃（丢消息 = 下一轮模型看到残缺上下文） |
| `text` 缺失 | 按空串处理（`AssistantMessage` 可以只有工具调用、没有文本） |
| `toolCalls` / `responses` 缺失 | 按空列表处理 |
| 空历史 | 合法，写 `[]` |
| 往返保真 | 写入再读回，消息**条数与顺序**完全一致，`id` / `name` / `arguments` / `responseData` 逐字不变 |

---

## 3. 会话标识（`session_id`）的生成规则

**唯一实现处**：`JpaSessionManager`（`SessionManager` 端口在 `fourfeetcat-core`，实现在 `fourfeetcat-storage`）。外部入口（CLI 传 `cli`、后续 Web 传 `web`、定时传 `scheduler`）**只提供三元组**，不自己拼字符串（H4 不变量④）。

- 同一 `(channel, user, profileName)` 三元组 → 同一个 `session_id`（幂等）
- 三个分量任一不同 → 不同 `session_id`
- 拼接结果长度必须落在 `VARCHAR(64)` 内；三元组被 `\u0000`-安全的分隔符连接（具体分隔符是实现细节，由 harness 钉住"幂等 + 隔离"，不引入对外约定）

---

## 4. 会话实体（`SessionEntity`）

| 字段 | Java 类型 | 列 | 备注 |
|---|---|---|---|
| `sessionId` | `String` | `session_id` | `@Id`（**不用** `@GeneratedValue`：标识由会话层生成，不是数据库自增） |
| `profileName` | `String` | `profile_name` | |
| `channel` | `String` | `channel` | |
| `userId` | `String` | `user_id` | |
| `messagesJson` | `String` | `messages_json` | 默认 `"[]"` |
| `status` | `String` | `status` | 默认 `"active"` |
| `createdAt` | `String` | `created_at` | ISO-8601 文本 |
| `lastActiveAt` | `String` | `last_active_at` | 每次保存刷新 |
| `archivedAt` | `String` | `archived_at` | 可空；本节无归档入口（`session archive` 归后续节），先建列 |

`@Entity @Table(name = "sessions")`，`ddl-auto: none`——表只由 Flyway 脚本建。

---

## 5. 内存态会话 ↔ 持久化记录的映射

| 内存态（`core.session.Session`） | 持久化记录（`SessionEntity`） |
|---|---|
| `id` | `sessionId` |
| `profileName` / `channel` / `userId` | 同名 |
| `List<Message> messages` | `messagesJson`（§2 编解码） |
| （无） | `status` / `createdAt` / `lastActiveAt` / `archivedAt` |

**读取（`get` / `getOrCreate`）**：查库 → 有则用 `messages_json` 解码出消息列表构出内存态会话；无则新建一条内存态会话（空历史）。
**写入（`save`）**：把内存态会话的 id/身份/消息列表编码后 upsert 进表，`last_active_at` 刷成当前时刻，`created_at` 保留首次值。

## 6. 状态迁移

```text
（不存在） --getOrCreate--> active --save--> active（last_active_at 刷新）
```

`archived` 是兼容位：本节不提供迁移入口（归档命令/端点归后续节），列与默认值按 §1 建好。
