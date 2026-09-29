package org.fourfeetcat.core.schedule;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 定时任务的持久化出口（依赖倒置）：core 的调度器依赖它，存储侧（storage 的 JPA 实现）提供实现。
 *
 * <p>它管三件事：任务登记（{@code scheduled_tasks}）、执行历史（{@code task_executions}）、按 key
 * 的"定义与运行态协调"（reconcile）—— 定义仍是 Agent 配置的 {@code schedules} 字段，这张表不是第二个定义源，只存"状态与历史，且重启不丢"。
 */
public interface ScheduledTaskStore {

  /** 全部定时任务及其运行状态（管理台列表直接喂它）。 */
  List<ScheduledTaskView> findAll();

  /** 按运行态主键取一条；不存在返回空。 */
  Optional<ScheduledTaskView> findById(long scheduleId);

  /** 按「Agent + 配置 key」取一条——策略：一个 Profile 内一台定时任务只对应一行登记，靠它做幂等 upsert 与启用判定。 */
  Optional<ScheduledTaskView> findByProfileAndKey(String profileName, String scheduleKey);

  /** 某次执行的历史（按时间倒序：最近的一次在最前，管理台直接喂它）。 */
  List<TaskExecutionView> executionsOf(long scheduleId);

  /**
   * 登记（或更新）一条任务：同一 {@code (profileName, scheduleKey)} 幂等——首次建行、再遇只刷配置与时区。返回带 {@code scheduleId}
   * 的视图， 调度器用它做后续执行。
   */
  ScheduledTaskView register(ScheduleRegistration registration);

  /** 协调：把本 Agent 配置里已不在的 key 标记退役（配置删除/改 key 后状态与历史保留，不算重新一条）。 */
  void retireExcept(String profileName, Set<String> activeScheduleKeys);

  /** 管理台开关：false = 到点跳过、不执行、不记历史。 */
  void setEnabled(long scheduleId, boolean enabled);

  /**
   * 记一次执行：插一行 {@code task_executions}，并同步更新任务行的 {@code last_run_at / last_status / run_count /
   * next_run_at}—— 两处必须是一笔事务，否则审计与状态会不一致。
   */
  void recordExecution(
      long scheduleId,
      String sessionId,
      boolean success,
      String errorMessage,
      long durationMs,
      String nextRunAt);

  /** 恰好够登记一条任务的定义，去重后能还原可不依赖配置原文。 */
  record ScheduleRegistration(
      String profileName,
      String scheduleKey,
      String cron,
      String zone,
      String message,
      String nextRunAt) {}
}
