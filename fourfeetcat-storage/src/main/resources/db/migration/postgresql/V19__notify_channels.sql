-- ---------------------------------------------------------------------
-- notify_channels：通知渠道全局注册表（第 19 节，§6.8）
-- 与 sqlite 轨同版本号、同列名同约束。本表全部是字符型列（无布尔、
-- 无时间戳、无自增主键），两轨不存在方言差异，故正文逐字相同。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notify_channels (
    name          VARCHAR(64) PRIMARY KEY,               -- 全局注册名
    type          VARCHAR(32) NOT NULL,                  -- webhook/feishu/wecom/dingtalk/email
    url           TEXT,                                  -- HTTP 类渠道的 webhook 地址
    description   TEXT,                                  -- 可空
    config        TEXT                                   -- 类型相关多字段（JSON），email 的 host/port/from/to/username/password 等
);
