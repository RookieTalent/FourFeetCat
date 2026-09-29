package org.fourfeetcat.core.schedule;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.profile.ProfileRegistry;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.core.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.CronTrigger;

/**
 * 第三种触发源（第25/28节课件）：CLI 与 Web 是"人推"，它是"钟推"——到点自己拼一条消息，交给跟人推 <b>完全相同</b>的处理入口（{@link
 * AgentService#process}）。循环、工具执行、模型调用、审计一概不感知这一轮是谁发起的。
 *
 * <p><b>第28节的改造</b>：把第25节"只在内存注册 cron"升级成带状态与历史的完整子系统——注册时登记进 {@code scheduled_tasks}（经 {@link
 * ScheduledTaskStore}），每次真正执行成败都记 {@code task_executions} 并更新任务状态；新增管理入口（启停 / 立即执行）。 一句话：它只干往
 * 常干的那件窄事，外加"记得住、管得了"。
 *
 * <p><b>三个坑的落点（续）</b>：①触发规则来自配置、按时钟驱动；②每个任务一把进程内锁防重叠（不是分布式锁）；③单次失败只记日志 + 记 execution，
 * 不崩调度器、不影响别的任务，且锁必被释放；④cron 与时区**一起**交给调度器——不让服务器时区替用户做主。
 *
 * <p><b>装配</b>：本类是纯 POJO（core 自第16节起零 Spring 注解），构造器与注册动作由 boot 的配置类以 {@code @Bean(initMethod =
 * "registerAll")} 接上；调度器实现（线程池容量）也在那里定——容量为 1 时不同任务会互相排队， 故装配下限是 2。
 */
public class AgentScheduler {

  private static final Logger log = LoggerFactory.getLogger(AgentScheduler.class);

  /** 钟推的会话身份：渠道与用户固定，只有 Agent 名随任务变（第18节定的三元组签名，拼接在会话管理器内部）。 */
  private static final String SCHEDULER_CHANNEL = "scheduler";

  private static final String SCHEDULER_USER = "scheduler";

  /** 审计 error_message 长度上限：它是给"人"查的，一坨堆栈既不人话也超长——已经截断要加标注（见 {@link #humanError}）。 */
  private static final int MAX_ERROR_MESSAGE_CHARS = 300;

  private final TaskScheduler taskScheduler;
  private final ProfileRegistry profileRegistry;
  private final AgentService agentService;
  private final SessionManager sessionManager;
  private final ScheduledTaskStore store;

  /**
   * 每个任务标识一把锁：拿不到即说明该任务上一次还在跑，本次触发直接跳过（不排队、不堆积）。键是**裸标识**， 与 {@link #lockFor} 同一口径——两者必须命中同一把锁。
   *
   * <p>{@code ponytail:} 这是进程内锁，只解决"同一进程内不重叠"；多实例归属由选主 / 租约解决，属扩展阶段。
   */
  private final ConcurrentMap<String, Lock> taskLocks = new ConcurrentHashMap<>();

  /**
   * 已注册定时任务的执行句柄表（第29节，为 30 节注销/更新铺路），键 = {@code schedule id}（与 {@link #lockFor} 同口径的裸标识）。
   * 每注册一条任务，在调用调度器之后将返回的 {@link ScheduledFuture} 以 id 存下；注销时据此 cancel。与 {@code taskLocks} 并存、各管其事。
   */
  private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

  public AgentScheduler(
      TaskScheduler taskScheduler,
      ProfileRegistry profileRegistry,
      AgentService agentService,
      SessionManager sessionManager,
      ScheduledTaskStore store) {
    this.taskScheduler = taskScheduler;
    this.profileRegistry = profileRegistry;
    this.agentService = agentService;
    this.sessionManager = sessionManager;
    this.store = store;
  }

  /**
   * 扫一遍所有已加载 Agent 的定时配置，逐条登记进 {@code scheduled_tasks} 并注册进调度器（启动期调一次，public 是因为容器经 {@code
   * initMethod} 反射调它）。
   *
   * <p>坏条目一律**记错误日志后跳过自己**：语法非法（触发规则方言不对、时区不存在）、必填项缺失、标识与别人重名。 一条坏配置不该拖垮整台——但也不许悄悄过去，日志里点名是哪条。
   */
  public void registerAll() {
    // 标识查重按"本次扫到的全体"算：执行权按标识分配，跨 Agent 重名会让两条不相干的任务互相挡掉下一次触发
    Set<String> registeredIds = new HashSet<>();
    int registered = 0;
    for (Profile profile : profileRegistry.all()) {
      registered += doRegisterProfile(profile, registeredIds);
    }
    if (registered > 0 && log.isInfoEnabled()) {
      log.info("定时任务注册完成，共 {} 条", registered);
    }
  }

  /**
   * 注册一个 Agent 的全部定时任务；返回成功注册的条数。条目之间互不牵连：坏的那条跳过，其余照常。
   *
   * <p><b>第29节改造</b>：抽出公开入口供运行时新增 Agent（30 节 API 上传走同一段代码）。本方法每次另起一个独立 id 查重集 （不跨 Agent
   * 查重）；启动全量扫描的跨 Agent id 查重仍由 {@link #registerAll} 经 {@link #doRegisterProfile} 完成。
   * 每次成功注册都会把执行句柄存入 {@link #scheduledTasks}。
   */
  public int registerProfile(Profile profile) {
    return doRegisterProfile(profile, new HashSet<>());
  }

  private int doRegisterProfile(Profile profile, Set<String> registeredIds) {
    int registered = 0;
    Set<String> activeKeys = new HashSet<>();
    for (int index = 0; index < profile.schedules().size(); index++) {
      Map<String, Object> entry = profile.schedules().get(index);
      ScheduleConfig config;
      CronTrigger trigger;
      try {
        config = ScheduleConfig.fromEntry(profile.name(), index, entry);
        // 触发规则的**语法**在这里由框架判：6 段表达式、合法时区标识。判不过就跳过该条，绝不静默按别的意思跑。
        // 时区缺省取系统时区是**显式**写出来的：两参构造器不收 null（实测 IllegalArgumentException），
        // 且"到底用了哪个时区"写在调用点上，比藏在构造器内部缺省里好读
        ZoneId zone = config.zone() == null ? ZoneId.systemDefault() : ZoneId.of(config.zone());
        trigger = new CronTrigger(config.cron(), zone);
      } catch (RuntimeException e) {
        if (log.isErrorEnabled()) {
          log.error("定时任务配置非法，跳过 Agent {} 的第 {} 条: {}", profile.name(), index, e.getMessage());
        }
        continue;
      }
      if (!registeredIds.add(config.id())) {
        if (log.isErrorEnabled()) {
          log.error("定时任务标识重复，跳过 Agent {} 的 {}（该标识已被先注册的任务占用）", profile.name(), config.id());
        }
        continue;
      }
      // 状态先落地：到点那一枪才有一行登记可读（含启用判定），也保证"停用即不跑"能被稳定读到
      store.register(
          new ScheduledTaskStore.ScheduleRegistration(
              profile.name(),
              config.id(),
              config.cron(),
              resolvedZone(config.zone()),
              config.message(),
              nextRun(config.cron(), config.zone())));
      activeKeys.add(config.id());
      // 句柄非空才入表（真实调度器总返回句柄；防御 null——ConcurrentHashMap 禁止 null 值，且 mock 替身会返回 null）
      ScheduledFuture<?> future = taskScheduler.schedule(() -> runOnce(profile, config), trigger);
      if (future != null) {
        scheduledTasks.put(config.id(), future);
      }
      if (log.isInfoEnabled()) {
        log.info(
            "已注册定时任务 {}（Agent {}）：cron={} zone={}",
            config.id(),
            profile.name(),
            config.cron(),
            config.zone() == null ? "服务器默认" : config.zone());
      }
      registered++;
    }
    // 定义协调：本 Agent 配置里已删/改 key 的旧登记退役（状态与历史保留，不算冒新任务）
    store.retireExcept(profile.name(), activeKeys);
    return registered;
  }

  /**
   * 触发一次（包内可见：包内测试直接调它，就能验全部行为逻辑，不必真等时间）。
   *
   * <p>先读登记：任务未登记或已停用 → 本次跳过（不执行、不记历史）。随后走 {@link #runTask}——拿锁 → 取钟推会话 → 交给处理入口 → 记 execution →
   * 放锁。
   */
  void runOnce(Profile profile, ScheduleConfig config) {
    Optional<ScheduledTaskView> task = store.findByProfileAndKey(profile.name(), config.id());
    if (task.isEmpty()) {
      if (log.isDebugEnabled()) {
        log.debug("定时任务 {}（Agent {}）未登记，跳过本次触发", config.id(), profile.name());
      }
      return;
    }
    if (!task.get().enabled()) {
      if (log.isInfoEnabled()) {
        log.info("定时任务 {}（Agent {}）已停用，跳过本次触发", config.id(), profile.name());
      }
      return;
    }
    runTask(profile, task.get(), config.message());
  }

  /**
   * 立即执行一次（管理台"立即执行"）：按运行态主键取任务直接开跑，**无视启用状态**（"手动触发一次"本就该无视开关）。 任务或它的 Agent 不存在时抛 {@link
   * IllegalArgumentException}，由 web 层按 404 映射。
   */
  public void runNow(long scheduleId) {
    ScheduledTaskView task = findRequire(scheduleId);
    Profile profile =
        profileRegistry
            .find(task.profileName())
            .orElseThrow(
                () -> new IllegalArgumentException("任务归属的 Agent 未加载: " + task.profileName()));
    runTask(profile, task, task.message());
  }

  /** 启停开关（管理台）：把 enabled 落到登记表，到点一枪读到的是最新值。 */
  public void setEnabled(long scheduleId, boolean enabled) {
    findRequire(scheduleId);
    store.setEnabled(scheduleId, enabled);
  }

  /** 全部任务的运行状态（管理台列表直接喂它）。 */
  public List<ScheduledTaskView> allTasks() {
    return store.findAll();
  }

  /** 某任务的历史（管理台直接喂它），按时间倒序。 */
  public List<TaskExecutionView> executionsOf(long scheduleId) {
    findRequire(scheduleId);
    return store.executionsOf(scheduleId);
  }

  private ScheduledTaskView findRequire(long scheduleId) {
    return store
        .findById(scheduleId)
        .orElseThrow(() -> new IllegalArgumentException("无该定时任务: " + scheduleId));
  }

  /**
   * 真正执行一条任务（runOnce 与 runNow 共用）：拿锁 → 取/建钟推会话 → 交给处理入口 → 记 execution → 放锁。
   *
   * <p>最后那步在 {@code finally} 里：一次失败也必须把执行权还回去，否则这条任务就永远"卡住"了。 成败都经 {@link
   * ScheduledTaskStore#recordExecution} 落库（审计 + 状态同步更新，一笔事务）——失败原因是给人看的，不存堆栈。
   */
  private void runTask(Profile profile, ScheduledTaskView task, String message) {
    Lock lock = lockFor(task.scheduleKey());
    if (!lock.tryLock()) {
      if (log.isInfoEnabled()) {
        log.info("定时任务 {} 上一次仍在跑，跳过本次触发", task.scheduleKey());
      }
      return;
    }
    Instant start = Instant.now();
    String sessionId = null;
    boolean success = true;
    String error = null;
    try {
      Session session =
          sessionManager.getOrCreate(SCHEDULER_CHANNEL, SCHEDULER_USER, profile.name());
      sessionId = session.getId();
      agentService.process(session, message);
    } catch (RuntimeException e) {
      // 失败只记日志 + 落 execution：调度器不崩，别的任务与它自己的下一次触发都不受影响。链路上抛的都是运行时异常
      success = false;
      error = humanError(e);
      if (log.isErrorEnabled()) {
        log.error("定时任务 {}（Agent {}）执行失败", task.scheduleKey(), profile.name(), e);
      }
    } finally {
      lock.unlock();
    }
    long durationMs = Duration.between(start, Instant.now()).toMillis();
    store.recordExecution(
        task.scheduleId(),
        sessionId,
        success,
        error,
        durationMs,
        nextRun(task.cron(), task.zone()));
  }

  /** 已注册任务的执行句柄表（包内可见：包内测试验"registerProfile 后 scheduledTasks 有句柄"；给外部调度 API 无正当用途）。 */
  Map<String, ScheduledFuture<?>> scheduledTasksView() {
    return scheduledTasks;
  }

  /** 取/建某个任务的执行权（包内可见：包内测试用它占住锁，构造"上一次还在跑"的场景；外部拿到锁没有正当用途）。 */
  Lock lockFor(String taskId) {
    return taskLocks.computeIfAbsent(taskId, id -> new ReentrantLock());
  }

  /** 落库口径的时区：配置没写就用服务器时区，登记与运行时行为一致（缺省表默认是 Asia/Shanghai，但那不含运行时语义）。 */
  private static String resolvedZone(String zone) {
    return zone == null ? ZoneId.systemDefault().toString() : zone;
  }

  /** cron + 时区 → 下次触发时刻（ISO-8601）；计算不出返回 null（宁可留空，不让一条登记卡死在启动期）。 */
  static String nextRun(String cron, String zone) {
    try {
      ZoneId resolved = zone == null ? ZoneId.systemDefault() : ZoneId.of(zone);
      CronTrigger trigger = new CronTrigger(cron, resolved);
      Instant last = Instant.now();
      Instant next =
          trigger.nextExecution(
              new TriggerContext() {
                @Override
                public Instant lastScheduledExecution() {
                  return last;
                }

                @Override
                public Instant lastActualExecution() {
                  return null;
                }

                @Override
                public Instant lastCompletion() {
                  return null;
                }
              });
      return next == null ? null : next.toString();
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** 失败原因转"人话"：优先取异常消息（沙箱拒域名时的"域名不在白名单内: evil.com"正是它），空则退到异常类名；超长截断。 */
  private static String humanError(Throwable error) {
    String message = error.getMessage();
    if (message == null || message.isBlank()) {
      message = error.getClass().getSimpleName();
    }
    if (message.length() > MAX_ERROR_MESSAGE_CHARS) {
      message = message.substring(0, MAX_ERROR_MESSAGE_CHARS) + "…";
    }
    return message;
  }
}
