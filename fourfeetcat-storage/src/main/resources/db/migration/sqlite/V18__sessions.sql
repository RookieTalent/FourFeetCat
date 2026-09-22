-- ---------------------------------------------------------------------
-- sessions：Session 元数据 + JSON 序列化的对话历史（第 18 节）
-- session_id 由 channel + user + profile 联合生成，且只在会话层拼接一次
-- （两处各拼一遍、格式差一个分隔符，同一个人就会出现两条互不相认的历史）。
-- 历史整体序列化进 messages_json 一列，核心阶段不做按条拆表。
-- 逐字摘自 docs/class/schema.sql（课程参考建表脚本，SQLite 方言）。
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
