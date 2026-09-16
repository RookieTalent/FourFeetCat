# Data Model: Agent Provider（第16节）

## llm_calls 表（逐字保真自 `docs/class/schema.sql` L28-41）

```sql
CREATE TABLE IF NOT EXISTS llm_calls (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id         VARCHAR(64)  NOT NULL,
    provider           VARCHAR(64)  NOT NULL,
    model              VARCHAR(128) NOT NULL,
    prompt_tokens      INTEGER      NOT NULL DEFAULT 0,
    completion_tokens  INTEGER      NOT NULL DEFAULT 0,
    total_tokens       INTEGER      NOT NULL DEFAULT 0,
    success            INTEGER      NOT NULL,           -- 0/1
    error_message      TEXT,                             -- 可空，失败原因
    duration_ms        BIGINT       NOT NULL DEFAULT 0,
    created_at         TEXT         NOT NULL             -- ISO-8601
);
CREATE INDEX IF NOT EXISTS idx_llm_calls_session ON llm_calls(session_id);
```

- JPA 实体 `LlmCall`：`success` Java 侧 boolean ↔ 列 INTEGER(0/1) 手转；`createdAt` 存 TEXT ISO-8601；`errorMessage` 可空。失败调用 token 列落 0（default）。
- 校验规则：审计写入由 ProviderService 成/败两路触发，无独立生命周期。

## Profile（内存对象，YAML 派生）

字段全集（课件第16节点名，本节类建全、只消费 provider 段）：

| 字段 | 形态 | 说明 |
|---|---|---|
| `name` | String | 唯一名，索引键；重名后加载覆盖先加载（记 warn） |
| `description` | String | 描述 |
| `identity` | 嵌套 record `{agentName, prompt}` | 呈现身份（YAML 的 agent_name/prompt），本节只承载 |
| `provider` | 嵌套 `{name, model, temperature}` | **本节消费**；name 必须在全局 provider 名单内 |
| `tools` | List\<String\> | 工具名清单，后续节消费 |
| `skills` / `mcpServers` / `channels` / `notifyChannels` / `schedules` / `bootstrap` | List | 承载，后续节消费 |
| `settings` | 嵌套 | max_iterations / max_history_turns 等，后续节消费 |

- 校验规则（本节仅此一条，后续节各自补）：`provider.name` 找不到 → 该 Profile 记错误日志被拒，报错含所引用名字。
- `${ENV}` 占位：对全部标量值做 `${NAME}` 正则替换，从注入的 env 读取函数取值；占位对应变量缺失按校验问题记日志。

## Provider 全局声明（application.yaml，`fourfeetcat.providers`）

每项：`name`（唯一，显式映射表 key）+ `api-key: ${ENV_VAR}` + OpenAI 兼容 `base-url` + 可选 `model` 缺省。不落明文 key。

## 关系

`Profile.provider.name` ──引用──▶ `fourfeetcat.providers[].name` ──映射──▶ `Map<String, ChatModel>` 条目。
`llm_calls.session_id` ──预留关联──▶ 会话（会话实体第18节交付）。
