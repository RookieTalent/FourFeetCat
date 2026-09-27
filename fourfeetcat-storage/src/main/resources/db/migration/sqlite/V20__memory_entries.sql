-- ---------------------------------------------------------------------
-- memory_entries：长期记忆条目（第 22 节，结构化库档的后端）
-- 与 MEMORY.md 同一套核心/归档分区语义，落到 scope 列：
--   核心区 = 全量取（不截断）；归档区 = 只带最近 N 条（查询条数上限，非删除）
--   检索 = 只在归档区里匹配
-- 时间戳落 TEXT（ISO-8601）——SQLite 无原生 TIMESTAMP，与既有几张表同口径。
--
-- 逐字摘自 docs/class/schema.sql（课程参考建表脚本，SQLite 方言）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS memory_entries (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    scope       VARCHAR(16) NOT NULL,                    -- CORE / ARCHIVAL
    content     TEXT        NOT NULL,
    created_at  TEXT        NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_memory_entries_scope ON memory_entries(scope);
