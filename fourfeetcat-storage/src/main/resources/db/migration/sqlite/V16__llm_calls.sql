-- ---------------------------------------------------------------------
-- llm_calls：每次 LLM 调用记录（第 16 节，审计表 day one 写入）
-- 成败都写：success 记 false、error_message 记原因——失败不留痕，
-- 一次真实事故在系统里就完全没有痕迹。
-- 逐字摘自 docs/class/schema.sql（课程参考建表脚本，SQLite 方言）。
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
