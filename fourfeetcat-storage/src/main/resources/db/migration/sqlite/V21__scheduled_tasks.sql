-- ---------------------------------------------------------------------
-- scheduled_tasks：定时任务登记信息与运行状态（第 28 节，§8.5 / §9.2）
-- 与 sqlite 轨同版本号、同列名同约束；方言差异照既有 V17~V20 的口径：
--   自增主键 → AUTOINCREMENT
--   布尔 → INTEGER(0/1)         时间戳 → TEXT(ISO-8601)
--
-- 定义来源仍是 Agent 配置的 schedules 字段——这张表只存"状态与历史"，
-- 不作为定义源；重启时从配置重新协调（reconcile），一次性登记。
--
-- 逐字摘自 docs/class/schema.sql（课程参考建表脚本，SQLite 方言）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scheduled_tasks (
    schedule_id    INTEGER PRIMARY KEY AUTOINCREMENT,   -- 全局运行态主键，同一配置任务保持稳定
    profile_name   VARCHAR(64) NOT NULL,
    schedule_key   VARCHAR(64) NOT NULL,                -- Agent 内配置键（schedule 的 id 或派生键），同一 Profile 内唯一
    display_name   TEXT,                                -- 展示名称，不参与运行态定位
    cron           TEXT        NOT NULL,                -- 6 段 cron 表达式
    zone           TEXT        NOT NULL DEFAULT 'Asia/Shanghai',
    message        TEXT        NOT NULL,                -- 到点发给 Agent 的消息
    enabled        INTEGER     NOT NULL DEFAULT 1,      -- 0/1，管理台开关；关到点即跳过
    retired        INTEGER     NOT NULL DEFAULT 0,      -- 0/1，配置删除/改 key 后退役；状态与历史保留
    next_run_at    TEXT,                                -- 下次触发时刻（ISO-8601）
    last_run_at    TEXT,                                -- 最近一次触发时刻
    last_status    TEXT,                                -- success / failed
    run_count      BIGINT      NOT NULL DEFAULT 0,
    updated_at     TEXT        NOT NULL,
    UNIQUE(profile_name, schedule_key)
);
CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_profile ON scheduled_tasks(profile_name);