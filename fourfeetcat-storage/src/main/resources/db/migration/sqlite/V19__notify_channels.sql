-- ---------------------------------------------------------------------
-- notify_channels：通知渠道全局注册表（第 19 节，§6.8）
-- Agent 在配置与正文中按名引用；具体 webhook 地址不进入对话上下文。
-- 地址里的 token 就是凭证本身（拿到 URL 就能往群里发消息）——走环境变量
-- 占位解析，不明文写进配置、不入日志、不进 git。
--
-- 注意：本文件里不要出现"美元符号紧跟花括号"的占位式写法——Flyway 默认会把
-- 它当占位符替换，写在注释里同样会让整个迁移解析失败（本项目实测踩过）。
--
-- 逐字摘自 docs/class/schema.sql（课程参考建表脚本，SQLite 方言）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notify_channels (
    name          VARCHAR(64) PRIMARY KEY,               -- 全局注册名
    type          VARCHAR(32) NOT NULL,                  -- webhook/feishu/wecom/dingtalk/email
    url           TEXT,                                  -- HTTP 类渠道的 webhook 地址
    description   TEXT,                                  -- 可空
    config        TEXT                                   -- 类型相关多字段（JSON），email 的 host/port/from/to/username/password 等
);
