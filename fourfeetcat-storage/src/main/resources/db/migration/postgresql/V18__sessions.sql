-- ---------------------------------------------------------------------
-- sessions：Session 元数据 + JSON 序列化的对话历史（第 18 节）
-- 与 sqlite 轨同版本号、同列名同约束，仅方言不同：
--   SQLite 时间戳落 TEXT(ISO-8601)；PostgreSQL 用原生 TIMESTAMP。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sessions (
    session_id      VARCHAR(64) PRIMARY KEY,
    profile_name    VARCHAR(64) NOT NULL,
    channel         VARCHAR(32) NOT NULL,
    user_id         VARCHAR(64) NOT NULL,
    messages_json   TEXT        NOT NULL DEFAULT '[]',   -- 对话历史
    status          VARCHAR(16) NOT NULL DEFAULT 'active', -- active/archived
    created_at      TIMESTAMP   NOT NULL,
    last_active_at  TIMESTAMP   NOT NULL,
    archived_at     TIMESTAMP                            -- 可空，归档时间
);
CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status);
