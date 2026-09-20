-- ---------------------------------------------------------------------
-- tool_invocations：每次 Tool 调用记录（第 17 节，审计表 day one 写入）
-- 成败都写；被沙箱拒绝的调用也走这张表（success=false + error_message）。
-- 逐字摘自 docs/class/schema.sql（课程参考建表脚本，SQLite 方言）。
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
