package org.fourfeetcat.core.schedule;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
import org.springframework.scheduling.support.CronTrigger;

/**
 * 第三种触发源（第25节课件）：CLI 与 Web 是"人推"，它是"钟推"——到点自己拼一条消息，交给跟人推 <b>完全相同</b>的处理入口（{@link
 * AgentService#process}）。循环、工具执行、模型调用、审计一概不感知这一轮是谁发起的。
 *
 * <p><b>它只干一件窄事</b>：读配置里的触发规则、按规则注册、到点拿锁、拼消息、交上去、放锁。消息说什么话归 Agent 配置，
 * 消息怎么处理归循环，多实例下这条任务归谁执行归后续阶段的分布式协调——都不在这里。
 *
 * <p><b>四个坑的落点</b>：①触发规则来自配置，用 {@link TaskScheduler#schedule} 动态注册，不用编译期写死的静态注解；
 * ②每个任务一把进程内锁防重叠（<b>不是分布式锁</b>）；③单次失败只记日志，不崩调度器、不影响别的任务，且锁必被释放； ④cron 与时区**一起**交给调度器——不让服务器时区替用户做主。
 *
 * <p><b>装配</b>：本类是纯 POJO（core 自第16节起零 Spring 注解），构造器与注册动作由 boot 的配置类以 {@code @Bean(initMethod =
 * "registerAll")} 接上；调度器实现（线程池容量）也在那里定——容量为 1 时不同任务会互相排队， 故装配下限是 2。
 */
public class AgentScheduler {

  private static final Logger log = LoggerFactory.getLogger(AgentScheduler.class);

  /** 钟推的会话身份：渠道与用户固定，只有 Agent 名随任务变（第18节定的三元组签名，拼接在会话管理器内部）。 */
  private static final String SCHEDULER_CHANNEL = "scheduler";

  private static final String SCHEDULER_USER = "scheduler";

  private final TaskScheduler taskScheduler;
  private final ProfileRegistry profileRegistry;
  private final AgentService agentService;
  private final SessionManager sessionManager;

  /**
   * 每个任务标识一把锁：拿不到即说明该任务上一次还在跑，本次触发直接跳过（不排队、不堆积）。键是**裸标识**， 与 {@link #lockFor} 同一口径——两者必须命中同一把锁。
   *
   * <p>{@code ponytail:} 这是进程内锁，只解决"同一进程内不重叠"；多实例归属由选主 / 租约解决，属扩展阶段。
   */
  private final ConcurrentMap<String, Lock> taskLocks = new ConcurrentHashMap<>();

  public AgentScheduler(
      TaskScheduler taskScheduler,
      ProfileRegistry profileRegistry,
      AgentService agentService,
      SessionManager sessionManager) {
    this.taskScheduler = taskScheduler;
    this.profileRegistry = profileRegistry;
    this.agentService = agentService;
    this.sessionManager = sessionManager;
  }

  /**
   * 扫一遍所有已加载 Agent 的定时配置，逐条注册进调度器（启动期调一次，public 是因为容器经 {@code initMethod} 反射调它）。
   *
   * <p>坏条目一律**记错误日志后跳过自己**：语法非法（触发规则方言不对、时区不存在）、必填项缺失、标识与别人重名。 一条坏配置不该拖垮整台——但也不许悄悄过去，日志里点名是哪条。
   */
  public void registerAll() {
    // 标识查重按"本次扫到的全体"算：执行权按标识分配，跨 Agent 重名会让两条不相干的任务互相挡掉下一次触发
    Set<String> registeredIds = new HashSet<>();
    int registered = 0;
    for (Profile profile : profileRegistry.all()) {
      registered += registerProfile(profile, registeredIds);
    }
    if (registered > 0 && log.isInfoEnabled()) {
      log.info("定时任务注册完成，共 {} 条", registered);
    }
  }

  /** 注册一个 Agent 的全部定时任务；返回成功注册的条数。条目之间互不牵连：坏的那条跳过，其余照常。 */
  private int registerProfile(Profile profile, Set<String> registeredIds) {
    int registered = 0;
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
      taskScheduler.schedule(() -> runOnce(profile, config), trigger);
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
    return registered;
  }

  /**
   * 触发一次（包内可见：包内测试直接调它，就能验全部行为逻辑，不必真等时间）。
   *
   * <p>拿锁 → 取/建钟推会话 → 交给处理入口 → 放锁。最后那步在 {@code finally} 里：一次失败也必须把执行权还回去， 否则这条任务就永远"卡住"了。
   */
  void runOnce(Profile profile, ScheduleConfig config) {
    Lock lock = lockFor(config.id());
    if (!lock.tryLock()) {
      if (log.isInfoEnabled()) {
        log.info("定时任务 {} 上一次仍在跑，跳过本次触发", config.id());
      }
      return;
    }
    try {
      Session session =
          sessionManager.getOrCreate(SCHEDULER_CHANNEL, SCHEDULER_USER, profile.name());
      agentService.process(session, config.message());
    } catch (RuntimeException e) {
      // 失败只记日志：调度器不崩，别的任务与它自己的下一次触发都不受影响。链路上抛的都是运行时异常
      // （处理入口与会话管理器都不声明受检异常），故不写 catch (Exception) 那条永远不会走的分支
      if (log.isErrorEnabled()) {
        log.error("定时任务 {}（Agent {}）执行失败", config.id(), profile.name(), e);
      }
    } finally {
      lock.unlock();
    }
  }

  /** 取/建某个任务的执行权（包内可见：包内测试用它占住锁，构造"上一次还在跑"的场景；外部拿到锁没有正当用途）。 */
  Lock lockFor(String taskId) {
    return taskLocks.computeIfAbsent(taskId, id -> new ReentrantLock());
  }
}
