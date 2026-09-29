package org.fourfeetcat.core.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.profile.ProfileRegistry;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.core.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.CronTrigger;

/**
 * 定时任务的验收 harness（第25节课件：一个类覆盖四个坑）。
 *
 * <p>诀窍是**别真等时间**：{@code runOnce} 是独立方法，直接调它就能验全部行为逻辑；cron 触发本身是调度框架的事， 本类只验"注册参数传对了"。
 */
class AgentSchedulerTest {

  private static final String PROFILE_NAME = "ops-agent";
  private static final String MESSAGE = "早安巡检";

  /** 固定一个"上次跑完"的时刻，让"下一次执行落在哪个瞬间"变成可复现的断言（不依赖跑测试的当下几点）。 */
  private static final Instant LAST_RUN = Instant.parse("2026-09-28T00:00:00Z");

  private TaskScheduler taskScheduler;
  private ProfileRegistry profileRegistry;
  private AgentService agentService;
  private SessionManager sessionManager;
  private ScheduledTaskStore store;
  private AgentScheduler scheduler;

  @BeforeEach
  void setUp() {
    taskScheduler = mock(TaskScheduler.class);
    profileRegistry = mock(ProfileRegistry.class);
    agentService = mock(AgentService.class);
    sessionManager = mock(SessionManager.class);
    store = mock(ScheduledTaskStore.class);
    scheduler =
        new AgentScheduler(taskScheduler, profileRegistry, agentService, sessionManager, store);
    // 默认"已登记且启用"：runOnce 先读登记、默认放行；针对停用/未登记的测试单独覆盖
    when(store.findByProfileAndKey(any(), any()))
        .thenAnswer(
            invocation ->
                Optional.of(
                    new ScheduledTaskView(
                        1L,
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        null,
                        "0 0 9 * * *",
                        "Asia/Shanghai",
                        MESSAGE,
                        true,
                        null,
                        null,
                        null,
                        0L)));
    // 钟推会话默认给一条真的：runTask 里 session.getId() 才能成立（mock 默认返 null 会空指针）
    when(sessionManager.getOrCreate(eq("scheduler"), eq("scheduler"), eq(PROFILE_NAME)))
        .thenReturn(
            new Session(
                "scheduler:scheduler:" + PROFILE_NAME, PROFILE_NAME, "scheduler", "scheduler"));
  }

  @Test
  @DisplayName("注册时_CronTrigger带上了配置的cron和时区")
  void registration_carriesConfiguredCronAndZone() {
    // 三条：显式上海、显式纽约、不写时区（应落到服务器时区）
    Profile profile =
        agentWith(
            List.of(
                entry("0 0 9 * * *", "Asia/Shanghai"),
                entry("0 30 8 * * *", "America/New_York"),
                entry("0 0 7 * * *", null)));
    when(profileRegistry.all()).thenReturn(List.of(profile));

    scheduler.registerAll();

    ArgumentCaptor<Trigger> triggers = ArgumentCaptor.forClass(Trigger.class);
    verify(taskScheduler, times(3)).schedule(any(Runnable.class), triggers.capture());
    List<Trigger> captured = triggers.getAllValues();

    // cron 逐字保真（不做方言转换、不补位）
    assertThat(((CronTrigger) captured.get(0)).getExpression()).isEqualTo("0 0 9 * * *");
    assertThat(((CronTrigger) captured.get(1)).getExpression()).isEqualTo("0 30 8 * * *");
    assertThat(((CronTrigger) captured.get(2)).getExpression()).isEqualTo("0 0 7 * * *");

    // 时区真的生效：同一"上次跑完"时刻下，下一次执行落在当地该点（该 API 无时区读取方法，只能这样验）
    assertThat(captured.get(0).nextExecution(lastRunAt(LAST_RUN)))
        .isEqualTo(Instant.parse("2026-09-28T01:00:00Z")); // 上海 09:00 = 01:00Z
    assertThat(captured.get(1).nextExecution(lastRunAt(LAST_RUN)))
        .isEqualTo(Instant.parse("2026-09-28T12:30:00Z")); // 纽约 08:30 EDT = 12:30Z

    // 不写时区 = 服务器时区：只断言"当地 7 点"，不写死瞬间（CI 机器的时区不该决定这条断言）
    Instant withoutZone = captured.get(2).nextExecution(lastRunAt(LAST_RUN));
    ZonedDateTime local = ZonedDateTime.ofInstant(withoutZone, ZoneId.systemDefault());
    assertThat(local.getHour()).isEqualTo(7);
    assertThat(local.getMinute()).isZero();
  }

  @Test
  @DisplayName("条目没写标识_按Agent名与序号派生")
  void entryWithoutId_derivesIdFromProfileAndIndex() {
    assertThat(ScheduleConfig.fromEntry(PROFILE_NAME, 0, entry("0 0 9 * * *", null)).id())
        .isEqualTo("ops-agent#0");
    assertThat(ScheduleConfig.fromEntry(PROFILE_NAME, 2, entry("0 0 9 * * *", null)).id())
        .isEqualTo("ops-agent#2");
  }

  @Test
  @DisplayName("条目写了标识_用显式标识；缺必填项_当场拒绝")
  void entryWithId_keepsIt_andMissingRequiredIsRejected() {
    Map<String, Object> withId = entry("0 0 9 * * *", null);
    withId.put("id", "daily-digest");
    assertThat(ScheduleConfig.fromEntry(PROFILE_NAME, 0, withId).id()).isEqualTo("daily-digest");

    Map<String, Object> noCron = new LinkedHashMap<>();
    noCron.put("message", MESSAGE);
    assertThatThrownBy(() -> ScheduleConfig.fromEntry(PROFILE_NAME, 0, noCron))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cron");

    Map<String, Object> blankMessage = entry("0 0 9 * * *", null);
    blankMessage.put("message", "   ");
    assertThatThrownBy(() -> ScheduleConfig.fromEntry(PROFILE_NAME, 0, blankMessage))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("message");
  }

  @Test
  @DisplayName("同一任务两次触发_拿到同一条会话")
  void twoTriggers_shareSameSchedulerSession() {
    ScheduleConfig config = config("task-1");
    Session session =
        new Session("scheduler:scheduler:ops-agent", PROFILE_NAME, "scheduler", "scheduler");
    when(sessionManager.getOrCreate("scheduler", "scheduler", PROFILE_NAME)).thenReturn(session);

    scheduler.runOnce(agent(), config);
    scheduler.runOnce(agent(), config);

    // 渠道与用户固定为定时专用身份、Agent 名取任务所属 Agent；历次触发复用同一条会话
    verify(sessionManager, times(2)).getOrCreate("scheduler", "scheduler", PROFILE_NAME);
    ArgumentCaptor<Session> sessions = ArgumentCaptor.forClass(Session.class);
    verify(agentService, times(2)).process(sessions.capture(), eq(MESSAGE));
    assertThat(sessions.getAllValues()).containsOnly(session);
  }

  @Test
  @DisplayName("没有定时配置的Agent_一条都不注册")
  void noSchedules_registersNothing() {
    when(profileRegistry.all()).thenReturn(List.of(agent()));

    scheduler.registerAll();

    verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
  }

  @Test
  @DisplayName("坏配置被跳过_且不牵连同一Agent的其他条目")
  void invalidEntries_areSkippedWithoutAffectingNeighbours() {
    Profile profile =
        agentWith(
            List.of(
                entry("0 9 * * *", "Asia/Shanghai"), // 5 段 Unix 写法：框架硬拒
                entry("0 0 10 * * *", "Mars/Olympus"), // 时区不存在
                Map.of("cron", "0 0 11 * * *", "message", "   "), // 消息是空白
                entry("0 0 12 * * *", "Asia/Shanghai"))); // 合法的那条
    when(profileRegistry.all()).thenReturn(List.of(profile));

    scheduler.registerAll();

    // 只有合法那条进了调度器——坏的三条各自记错误日志后跳过，进程照常启动
    ArgumentCaptor<Trigger> triggers = ArgumentCaptor.forClass(Trigger.class);
    verify(taskScheduler, times(1)).schedule(any(Runnable.class), triggers.capture());
    assertThat(((CronTrigger) triggers.getValue()).getExpression()).isEqualTo("0 0 12 * * *");
  }

  @Test
  @DisplayName("标识重复_跳过后来者而不是静默覆盖")
  void duplicateId_isSkipped() {
    Map<String, Object> first = entry("0 0 9 * * *", "Asia/Shanghai");
    Map<String, Object> second = entry("0 0 10 * * *", "Asia/Shanghai");
    first.put("id", "daily");
    second.put("id", "daily");
    when(profileRegistry.all()).thenReturn(List.of(agentWith(List.of(first, second))));

    scheduler.registerAll();

    ArgumentCaptor<Trigger> triggers = ArgumentCaptor.forClass(Trigger.class);
    verify(taskScheduler, times(1)).schedule(any(Runnable.class), triggers.capture());
    assertThat(((CronTrigger) triggers.getValue()).getExpression()).isEqualTo("0 0 9 * * *");
  }

  @Test
  @DisplayName("上一次还没跑完_本次触发直接跳过")
  void earlierRunStillInFlight_thisTriggerIsSkipped() {
    withTaskLockHeld(
        "task-1",
        () -> {
          scheduler.runOnce(agent(), config("task-1"));

          verify(agentService, never()).process(any(), any()); // 没有叠加执行，也不排队
        });
  }

  @Test
  @DisplayName("占住一条任务的执行权_别的任务照常触发")
  void differentTask_isNotBlockedByAnOccupiedOne() {
    withTaskLockHeld(
        "task-1",
        () -> {
          scheduler.runOnce(agent(), config("task-2"));

          verify(agentService).process(any(), any());
        });
  }

  /**
   * 让"上一次触发"真的在**另一个线程**上占着执行权，再跑 {@code action}。
   *
   * <p>为什么非得换线程：执行权是 {@link java.util.concurrent.locks.ReentrantLock}，**对同线程可重入**——在当前 测试线程里自己
   * `lock()` 再调 {@code runOnce}，`tryLock()` 会成功，"上一次还在跑"这个场景根本没构造出来。 真实触发跑在调度线程上，所以这里也照那个样子：占用线程守到
   * {@code action} 跑完才放锁。
   */
  private void withTaskLockHeld(String taskId, Runnable action) {
    Lock lock = scheduler.lockFor(taskId);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Thread holder =
        new Thread(
            () -> {
              lock.lock();
              locked.countDown();
              try {
                release.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                lock.unlock();
              }
            });
    holder.setDaemon(true); // 占用线程只作测试脚手架，不许把测试 JVM 吊住
    holder.start();
    try {
      assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
      action.run();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("等待占用执行权的线程被中断", e);
    } finally {
      release.countDown();
      try {
        holder.join(TimeUnit.SECONDS.toMillis(5));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Test
  @DisplayName("任务抛异常_不外抛且锁必须被释放")
  void processThrows_doesNotPropagateAndLockIsReleased() {
    when(agentService.process(any(), any())).thenThrow(new IllegalStateException("boom"));

    assertThatCode(() -> scheduler.runOnce(agent(), config("task-1")))
        .doesNotThrowAnyException(); // 调度器不死

    scheduler.runOnce(agent(), config("task-1"));

    // 二进宫：能第二次进来才证明 finally 里的 unlock 真跑了（光断言"不抛异常"抓不住漏 unlock 的 bug）
    verify(agentService, times(2)).process(any(), any());
  }

  @Test
  @DisplayName("一条任务失败_不影响另一条任务的下一次触发")
  void oneTaskFailing_doesNotAffectAnotherTask() {
    when(agentService.process(any(), any()))
        .thenThrow(new IllegalStateException("boom"))
        .thenReturn("到点了");

    scheduler.runOnce(agent(), config("task-1"));
    scheduler.runOnce(agent(), config("task-2"));

    verify(agentService, times(2)).process(any(), any());
  }

  @Test
  @DisplayName("任务已停用_本次触发跳过_不执行也不记")
  void disabledTask_skipsTriggerWithoutRunningOrRecording() {
    when(store.findByProfileAndKey(any(), any()))
        .thenReturn(
            Optional.of(
                new ScheduledTaskView(
                    1L,
                    PROFILE_NAME,
                    "task-1",
                    null,
                    "0 0 9 * * *",
                    "Asia/Shanghai",
                    MESSAGE,
                    false,
                    null,
                    null,
                    null,
                    0L)));

    scheduler.runOnce(agent(), config("task-1"));

    verify(agentService, never()).process(any(), any());
    verify(store, never()).recordExecution(anyLong(), any(), anyBoolean(), any(), anyLong(), any());
  }

  @Test
  @DisplayName("任务成功执行_记一条历史并把成功状态写回")
  void taskRun_success_recordsExecution() {
    when(agentService.process(any(), eq(MESSAGE))).thenReturn("到点了");

    scheduler.runOnce(agent(), config("task-1"));

    ArgumentCaptor<Long> scheduleId = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<String> sessionId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Boolean> success = ArgumentCaptor.forClass(Boolean.class);
    verify(store)
        .recordExecution(
            scheduleId.capture(), sessionId.capture(), success.capture(), any(), anyLong(), any());
    assertThat(scheduleId.getValue()).isEqualTo(1L);
    assertThat(sessionId.getValue()).isEqualTo("scheduler:scheduler:" + PROFILE_NAME);
    assertThat(success.getValue()).isTrue();
  }

  @Test
  @DisplayName("任务执行失败_记一条 failed 历史且原因是人话")
  void taskRun_failure_recordsExecutionWithReadableCause() {
    when(agentService.process(any(), eq(MESSAGE)))
        .thenThrow(new IllegalStateException("域名不在白名单内: evil.com"));

    scheduler.runOnce(agent(), config("task-1"));

    ArgumentCaptor<Boolean> success = ArgumentCaptor.forClass(Boolean.class);
    ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
    verify(store)
        .recordExecution(anyLong(), any(), success.capture(), error.capture(), anyLong(), any());
    assertThat(success.getValue()).isFalse();
    assertThat(error.getValue()).isEqualTo("域名不在白名单内: evil.com");
  }

  @Test
  @DisplayName("runNow_无视启用状态_立即执行一次并记下成功历史")
  void runNow_ignoresEnabledStateAndExecutes() {
    ScheduledTaskView disabled =
        new ScheduledTaskView(
            7L,
            PROFILE_NAME,
            "task-7",
            null,
            "0 0 9 * * *",
            "Asia/Shanghai",
            MESSAGE,
            false,
            null,
            null,
            null,
            0L);
    when(store.findById(7L)).thenReturn(Optional.of(disabled));
    when(profileRegistry.find(PROFILE_NAME)).thenReturn(Optional.of(agent()));
    when(agentService.process(any(), eq(MESSAGE))).thenReturn("到点了");

    assertThatCode(() -> scheduler.runNow(7L)).doesNotThrowAnyException();

    // 停用的任务也能被手动触发
    verify(agentService).process(any(), eq(MESSAGE));
    ArgumentCaptor<Long> scheduleId = ArgumentCaptor.forClass(Long.class);
    verify(store)
        .recordExecution(scheduleId.capture(), any(), anyBoolean(), any(), anyLong(), any());
    assertThat(scheduleId.getValue()).isEqualTo(7L);
  }

  /** "上次跑完"的触发上下文：三个时刻都设成同一个固定瞬间，下一次执行因此可复现。 */
  private static TriggerContext lastRunAt(Instant instant) {
    return new TriggerContext() {
      @Override
      public Instant lastScheduledExecution() {
        return instant;
      }

      @Override
      public Instant lastCompletion() {
        return instant;
      }

      @Override
      public Instant lastActualExecution() {
        return instant;
      }
    };
  }

  private static ScheduleConfig config(String id) {
    return new ScheduleConfig(id, "0 0 9 * * *", "Asia/Shanghai", MESSAGE);
  }

  /** 被测任务所属的 Agent（{@code runOnce} 只用到它的名字，配置留空即可）。 */
  private static Profile agent() {
    return agentWith(List.of());
  }

  private static Profile agentWith(List<Map<String, Object>> schedules) {
    return new Profile(
        PROFILE_NAME,
        "运维助手",
        new Profile.Identity("运维小欧", "你是一个专业的运维助手"),
        new Profile.ProviderConfig("deepseek", "deepseek-chat", null),
        List.of("read_file"),
        List.of(),
        List.of(),
        List.of("cli"),
        List.of(),
        schedules,
        List.of("AGENTS.md"),
        Map.of());
  }

  /** 一条完整的定时条目（zone 为 null 即不写该键）。 */
  private static Map<String, Object> entry(String cron, String zone) {
    Map<String, Object> entry = entryWithoutZone(cron);
    if (zone != null) {
      entry.put("zone", zone);
    }
    return entry;
  }

  private static Map<String, Object> entryWithoutZone(String cron) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("cron", cron);
    entry.put("message", MESSAGE);
    return entry;
  }
}
