package org.fourfeetcat.boot;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.profile.ProfileRegistry;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.session.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 定时任务的**装配冒烟**（第25节）：这是本节唯一无法由单测覆盖的接线点——{@code AgentSchedulerTest} 全程用替身调度器，
 * 所以"容器里到底装没装调度器""注册动作到底跑没跑"它一无所知。历史上正是这类接线漏了、单测照样全绿。
 *
 * <p>于是这里起**真容器**、用**真调度器**与**真会话管理器**，只有处理入口是替身（测试里不该真调模型）。等到的那一次调用 同时证明三件事：调度器 Bean
 * 装配到位、注册动作（{@code initMethod}）被触发、到点真的走的是与 CLI / Web 相同的入口。
 */
@SpringBootTest(properties = "FOURFEETCAT_ROOT=${user.dir}/target/scheduler-smoke")
class AgentSchedulerWiringTest {

  private static final String MESSAGE = "到点了";

  /** 本用例自己的工作区（与既有 boot 上下文测试分开：各用各的库，互不牵连）。 */
  private static final Path WORKSPACE =
      Path.of(System.getProperty("user.dir"), "target", "scheduler-smoke");

  static {
    // sqlite 只建文件、不建目录——生产路径上这一步由 main() 做，测试里得自己来，
    // 否则 clean 之后数据源打不开（SQLITE_CANTOPEN）
    try {
      Files.createDirectories(WORKSPACE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @MockitoBean private AgentService agentService;

  @Test
  @DisplayName("容器里的定时任务_到点把消息交给了与人推相同的入口")
  void scheduledTask_reachesTheSameEntryPointAsHumanTriggers() {
    // 守点是"到点**真的**交付了一次"，不是"恰好一次"：这条任务每秒触发一次，等待窗口内必然多于一次
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> verify(agentService, atLeastOnce()).process(any(Session.class), eq(MESSAGE)));
  }

  /**
   * 定时任务的**来源**换成内存里造的一份 Agent 配置。
   *
   * <p>为什么不用 {@code @MockitoBean} 打桩：注册动作发生在容器启动期（Bean 的 {@code initMethod}），那时测试方法还没进门，
   * 桩打不上。故这里给一份**真注册表**，里面装着一个带"每秒触发"任务的 Agent（触发规则 6 段、秒位为 `*`，冒烟不必等一分钟）。
   *
   * <p>这样也让本用例与工作区目录无关：它验的是"装配与注册有没有发生"，不是"文件扫得对不对"——后者归 {@code ProfileLoader} 的 测试管。（顺带避开了 {@code
   * workspaceRoot()} 读环境变量、而 {@code @SpringBootTest} 只设 Spring 属性这条分叉。）
   */
  @TestConfiguration
  static class ScheduledProfile {

    @Bean
    @Primary
    ProfileRegistry stubProfileRegistry() {
      ProfileRegistry registry = new ProfileRegistry();
      registry.register(smokeAgent());
      return registry;
    }

    private static Profile smokeAgent() {
      Map<String, Object> schedule =
          Map.of(
              "id", "smoke-tick",
              "cron", "* * * * * *",
              "zone", "Asia/Shanghai",
              "message", MESSAGE);
      return new Profile(
          "smoke-agent",
          "定时任务装配冒烟",
          new Profile.Identity("冒烟", "你是一个冒烟用的 Agent"),
          new Profile.ProviderConfig("deepseek", "deepseek-chat", null),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of(schedule),
          List.of(),
          Map.of());
    }
  }
}
