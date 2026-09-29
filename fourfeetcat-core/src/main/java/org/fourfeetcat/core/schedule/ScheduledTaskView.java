package org.fourfeetcat.core.schedule;

/**
 * 一条定时任务的对外视图（第28节课件）：登记信息 + 运行状态，要么来自 {@code scheduled_tasks} 表的持久化形态， 要么来自管理台 /
 * 想表对账的调用方。只承载数据，与持久化实现解耦。
 *
 * @param scheduleId 全局运行态主键（表自增，同一配置任务跨重启保持稳定）
 * @param profileName 任务所属 Agent（会话身份按它拼接钟推三元组）
 * @param scheduleKey Agent 内配置键（schedule 的 id 或派生键），同一 Profile 内唯一
 * @param displayName 展示名称，不参与运行态定位（可为 null）
 * @param cron 6 段 cron 表达式
 * @param zone 时区标识（登记时已落库，缺省进 Asia/Shanghai）
 * @param message 到点发给 Agent 的消息
 * @param enabled 管理台开关；false 时到点直接跳过、不执行、不记历史
 * @param nextRunAt 下次触发时刻；null = 未计算（新登记但未触发前可能为空，不与"永不触发"混淆）
 * @param lastRunAt 最近一次触发时刻；null = 从未触发
 * @param lastStatus 最近一次结果：success / failed；null = 从未触发
 * @param runCount 已触发次数（含失败的）
 */
public record ScheduledTaskView(
    long scheduleId,
    String profileName,
    String scheduleKey,
    String displayName,
    String cron,
    String zone,
    String message,
    boolean enabled,
    String nextRunAt,
    String lastRunAt,
    String lastStatus,
    long runCount) {}
