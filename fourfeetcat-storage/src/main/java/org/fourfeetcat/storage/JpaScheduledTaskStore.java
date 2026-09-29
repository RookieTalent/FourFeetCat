package org.fourfeetcat.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.fourfeetcat.core.schedule.ScheduledTaskStore;
import org.fourfeetcat.core.schedule.ScheduledTaskView;
import org.fourfeetcat.core.schedule.TaskExecutionView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ScheduledTaskStore} 端口的存储实现（第28节）：经 JPA 仓储读写 scheduled_tasks / task_executions 两张表。
 *
 * <p>定义源仍是 Agent 配置——这里的 register / retireExcept 只做"状态 + 历史的持久化协调"，不生成定义。时间戳一律 Instant 的 ISO-8601
 * 文本（SQLite 无原生 TIMESTAMP，与既有审计实体同口径）。
 */
@Component
public class JpaScheduledTaskStore implements ScheduledTaskStore {

  private final ScheduledTaskRepository tasks;
  private final TaskExecutionRepository executions;

  public JpaScheduledTaskStore(ScheduledTaskRepository tasks, TaskExecutionRepository executions) {
    this.tasks = tasks;
    this.executions = executions;
  }

  @Override
  public List<ScheduledTaskView> findAll() {
    return tasks.findAll().stream().map(JpaScheduledTaskStore::toView).toList();
  }

  @Override
  public Optional<ScheduledTaskView> findById(long scheduleId) {
    return tasks.findById(scheduleId).map(JpaScheduledTaskStore::toView);
  }

  @Override
  public Optional<ScheduledTaskView> findByProfileAndKey(String profileName, String scheduleKey) {
    return tasks
        .findByProfileNameAndScheduleKey(profileName, scheduleKey)
        .map(JpaScheduledTaskStore::toView);
  }

  @Override
  public List<TaskExecutionView> executionsOf(long scheduleId) {
    return executions.findByScheduleIdOrderByIdDesc(scheduleId).stream()
        .map(JpaScheduledTaskStore::toView)
        .toList();
  }

  @Override
  @Transactional
  public ScheduledTaskView register(ScheduleRegistration registration) {
    ScheduledTask entity =
        tasks
            .findByProfileNameAndScheduleKey(registration.profileName(), registration.scheduleKey())
            .orElseGet(JpaScheduledTaskStore::newTask);
    entity.setProfileName(registration.profileName());
    entity.setScheduleKey(registration.scheduleKey());
    entity.setCron(registration.cron());
    entity.setZone(registration.zone());
    entity.setMessage(registration.message());
    entity.setNextRunAt(registration.nextRunAt());
    entity.setRetired(false); // 配置回来 = 复活；enabled 保留上一次（管理台开关是运营决定，不因重启而丢）
    entity.setUpdatedAt(Instant.now().toString());
    return toView(tasks.save(entity));
  }

  @Override
  @Transactional
  public void retireExcept(String profileName, Set<String> activeScheduleKeys) {
    Set<String> active = activeScheduleKeys == null ? Set.of() : Set.copyOf(activeScheduleKeys);
    for (ScheduledTask entity : tasks.findByProfileName(profileName)) {
      if (!entity.isRetired() && !active.contains(entity.getScheduleKey())) {
        entity.setRetired(true);
        entity.setUpdatedAt(Instant.now().toString());
        tasks.save(entity);
      }
    }
  }

  @Override
  @Transactional
  public void setEnabled(long scheduleId, boolean enabled) {
    tasks
        .findById(scheduleId)
        .ifPresent(
            entity -> {
              entity.setEnabled(enabled);
              entity.setUpdatedAt(Instant.now().toString());
              tasks.save(entity);
            });
  }

  @Override
  @Transactional
  public void recordExecution(
      long scheduleId,
      String sessionId,
      boolean success,
      String errorMessage,
      long durationMs,
      String nextRunAt) {
    Instant now = Instant.now();
    TaskExecution execution = new TaskExecution();
    execution.setScheduleId(scheduleId);
    execution.setSessionId(sessionId);
    // 触发时刻 ≈ 记录时刻减执行耗时：让"时间戳"反映这一枪真实的开火点，而不是审计跑完的时刻
    execution.setStartedAt(now.minusMillis(durationMs).toString());
    execution.setSuccess(success);
    execution.setErrorMessage(errorMessage);
    execution.setDurationMs(durationMs);
    executions.save(execution);

    tasks
        .findById(scheduleId)
        .ifPresent(
            entity -> {
              entity.setLastRunAt(now.toString());
              entity.setLastStatus(success ? STATUS_SUCCESS : STATUS_FAILED);
              entity.setRunCount(entity.getRunCount() + 1);
              entity.setNextRunAt(nextRunAt);
              entity.setUpdatedAt(now.toString());
              tasks.save(entity);
            });
  }

  private static final String STATUS_SUCCESS = "success";

  private static final String STATUS_FAILED = "failed";

  private static ScheduledTask newTask() {
    ScheduledTask task = new ScheduledTask();
    task.setEnabled(true);
    task.setRetired(false);
    task.setRunCount(0);
    return task;
  }

  private static ScheduledTaskView toView(ScheduledTask entity) {
    return new ScheduledTaskView(
        entity.getScheduleId() == null ? 0L : entity.getScheduleId(),
        entity.getProfileName(),
        entity.getScheduleKey(),
        entity.getDisplayName(),
        entity.getCron(),
        entity.getZone(),
        entity.getMessage(),
        entity.isEnabled(),
        entity.getNextRunAt(),
        entity.getLastRunAt(),
        entity.getLastStatus(),
        entity.getRunCount());
  }

  private static TaskExecutionView toView(TaskExecution entity) {
    return new TaskExecutionView(
        entity.getId() == null ? 0L : entity.getId(),
        entity.getScheduleId() == null ? 0L : entity.getScheduleId(),
        entity.getSessionId(),
        entity.getStartedAt(),
        entity.isSuccess(),
        entity.getErrorMessage(),
        entity.getDurationMs());
  }
}
