package org.fourfeetcat.core.schedule;

/**
 * 一次定时任务执行的对外视图（第28节课件）：{@code task_executions} 表的一行，成功失败都记（与宪法 V 审计同理）。
 *
 * @param id 执行记录主键
 * @param scheduleId 关联的 {@link ScheduledTaskView#scheduleId()}；历史迁移前可能为「无主」——用 0 表达不可关
 * @param sessionId 本次触发所用的钟推 Session（scheduler:scheduler:&lt;profile&gt;）
 * @param startedAt 触发时刻（ISO-8601）
 * @param success 是否成功
 * @param errorMessage 失败原因（给人看的，非堆栈）；成功为 null
 * @param durationMs 从触发到收尾的耗时
 */
public record TaskExecutionView(
    long id,
    long scheduleId,
    String sessionId,
    String startedAt,
    boolean success,
    String errorMessage,
    long durationMs) {}
