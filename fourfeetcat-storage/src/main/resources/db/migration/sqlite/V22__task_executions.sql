-- ---------------------------------------------------------------------
-- task_executions：定时任务每次执行的历史（第 28 节，§8.5 / §9.2）
-- 成功失败都记（与宪法 V 审计同理）；schedule_id 可空——迁移前无法
-- 可靠关联的历史不丢。
-- 方言：自增 AUTOINCREMENT，布尔 INTEGER(0/1)，时间戳 TEXT(ISO-8601)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS task_executions (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_id    INTEGER,                            -- 关联 scheduled_tasks.schedule_id，可空
    session_id     VARCHAR(64),                        -- 本次触发所用的钟推 Session
    started_at     TEXT        NOT NULL,               -- ISO-8601
    success        INTEGER     NOT NULL,               -- 0/1
    error_message  TEXT,                               -- 可空，人话原因
    duration_ms    BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_task_executions_schedule ON task_executions(schedule_id);
CREATE INDEX IF NOT EXISTS idx_task_executions_session ON task_executions(session_id);