-- =====================================================================
-- OryxOS 课程参考建表脚本（SQLite 方言）
--
-- 依据：docs/TechnicalSolution.md §9.2（sessions / tool_invocations /
--       llm_calls / scheduled_tasks / task_executions）、§6.8
--       （notify_channels）、§5.1（memory_entries）；llm_calls 的
--       success / error_message 两列来自第 16 节课件（审计成败都留痕）。
--
-- 用法：这是 16~31 节全部会建的表的完整参考，逐节开发时按本节交付物
--       只摘取对应的表（16→llm_calls；17→tool_invocations；
--       18→sessions；19→notify_channels；22→memory_entries；
--       28→scheduled_tasks + task_executions），测试里执行本脚本建表，
--       不依赖 hibernate.ddl-auto=update。
--
-- 口径取舍：trace_id（021）、blocked_by / tool_policy_rules（020）、
--       providers 落库加密（022）是 FourFeetCat 扩展阶段特性，课程
--       16~31 节不覆盖，不入本脚本。
--
-- SQLite 说明：无原生 BOOLEAN/TIMESTAMP——布尔落 INTEGER(0/1)，
--       时间戳落 TEXT（ISO-8601）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- llm_calls：每次 LLM 调用记录（第 16 节，审计表 day one 写入）
-- 成败都写：success 记 false、error_message 记原因——失败不留痕，
-- 一次真实事故在系统里就完全没有痕迹。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS llm_calls (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id         VARCHAR(64)  NOT NULL,
    provider           VARCHAR(64)  NOT NULL,
    model              VARCHAR(128) NOT NULL,
    prompt_tokens      INTEGER      NOT NULL DEFAULT 0,
    completion_tokens  INTEGER      NOT NULL DEFAULT 0,
    total_tokens        INTEGER      NOT NULL DEFAULT 0,
    success            INTEGER      NOT NULL,           -- 0/1
    error_message      TEXT,                             -- 可空，失败原因
    duration_ms        BIGINT       NOT NULL DEFAULT 0,
    created_at         TEXT         NOT NULL             -- ISO-8601
);
CREATE INDEX IF NOT EXISTS idx_llm_calls_session ON llm_calls(session_id);

-- ---------------------------------------------------------------------
-- tool_invocations：每次 Tool 调用记录（第 17 节，审计表 day one 写入）
-- 成败都写；被沙箱拒绝的调用也走这张表（success=false + error_message）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tool_invocations (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id     VARCHAR(64) NOT NULL,
    tool_name      VARCHAR(64) NOT NULL,
    input_json     TEXT,                                 -- 调用参数
    result_json    TEXT,                                 -- 执行结果
    success        INTEGER     NOT NULL,                 -- 0/1
    error_message  TEXT,                                 -- 可空
    duration_ms    BIGINT      NOT NULL DEFAULT 0,
    created_at     TEXT        NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_tool_invocations_session ON tool_invocations(session_id);

-- ---------------------------------------------------------------------
-- sessions：Session 元数据 + JSON 序列化的对话历史（第 18 节）
-- session_id 由 channel + user + profile 联合生成（只在 SessionManager
-- 内拼接）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sessions (
    session_id      VARCHAR(64) PRIMARY KEY,
    profile_name    VARCHAR(64) NOT NULL,
    channel         VARCHAR(32) NOT NULL,
    user_id         VARCHAR(64) NOT NULL,
    messages_json   TEXT        NOT NULL DEFAULT '[]',   -- 对话历史
    status          VARCHAR(16) NOT NULL DEFAULT 'active', -- active/archived
    created_at      TEXT        NOT NULL,
    last_active_at  TEXT        NOT NULL,
    archived_at     TEXT                                 -- 可空，归档时间
);
CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status);

-- ---------------------------------------------------------------------
-- notify_channels：通知渠道全局注册表（第 19 节，§6.8）
-- Agent 在 AGENT.md 正文中按名引用；具体 webhook 地址不进入对话。
-- email 类渠道不用 url，用 config 的 host/port/from/to 等多字段。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notify_channels (
    name          VARCHAR(64) PRIMARY KEY,               -- 全局注册名
    type          VARCHAR(32) NOT NULL,                  -- webhook/feishu/wecom/dingtalk/email
    url           TEXT,                                 -- HTTP 类渠道的 webhook 地址
    description   TEXT,                                 -- 可空
    config        TEXT                                  -- 类型相关多字段（JSON），email 的 host/port/from/to/username/password 等
);

-- ---------------------------------------------------------------------
-- memory_entries：SQLite 档长期记忆（第 22 节，§5.1 SqliteMemoryStore）
-- 与 MEMORY.md 同一套核心/归档分区语义，落到 scope 列；截断=归档查询
-- LIMIT N，检索=LIKE，核心区=WHERE scope='CORE' 全量取。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS memory_entries (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    scope       VARCHAR(16) NOT NULL,                    -- CORE / ARCHIVAL
    content     TEXT        NOT NULL,
    created_at  TEXT        NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_memory_entries_scope ON memory_entries(scope);

-- ---------------------------------------------------------------------
-- scheduled_tasks：定时任务登记信息与运行状态（第 28 节，§8.5/§9.2）
-- 定义来源仍是 AGENT.md frontmatter 的 schedules——这张表只存
-- “状态”，不作为定义源，重启时从文件重新协调（reconcile）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scheduled_tasks (
    schedule_id    INTEGER PRIMARY KEY AUTOINCREMENT,   -- 全局运行态主键，同一配置任务保持稳定
    profile_name   VARCHAR(64) NOT NULL,
    schedule_key   VARCHAR(64) NOT NULL,                 -- Agent 内配置键，同一 Profile 内唯一
    display_name   TEXT,                                 -- 展示名称，不参与运行态定位
    cron           TEXT        NOT NULL,
    zone           TEXT        NOT NULL DEFAULT 'Asia/Shanghai',
    message        TEXT        NOT NULL,                 -- 到点发给 Agent 的消息
    enabled        INTEGER     NOT NULL DEFAULT 1,      -- 0/1，管理台开关
    retired        INTEGER     NOT NULL DEFAULT 0,      -- 0/1，配置删除/改 key 后退役；状态与历史保留
    next_run_at    TEXT,
    last_run_at    TEXT,
    last_status    TEXT,                                 -- success / failed
    run_count      BIGINT      NOT NULL DEFAULT 0,
    updated_at     TEXT        NOT NULL,
    UNIQUE(profile_name, schedule_key)
);

-- ---------------------------------------------------------------------
-- task_executions：定时任务每次执行的历史（第 28 节，§8.5/§9.2）
-- 成功失败都记。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS task_executions (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_id   INTEGER,                              -- 可空：迁移前无法可靠关联的历史
    session_id    VARCHAR(64),                           -- 本次触发所用的钟推 Session
    started_at    TEXT        NOT NULL,
    success       INTEGER     NOT NULL,                  -- 0/1
    error_message  TEXT,                                -- 可空
    duration_ms   BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_task_executions_schedule ON task_executions(schedule_id);
