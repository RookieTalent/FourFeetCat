package org.fourfeetcat.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.schedule.AgentScheduler;
import org.fourfeetcat.core.schedule.ScheduledTaskStore;
import org.fourfeetcat.core.schedule.ScheduledTaskView;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.core.session.SessionManager;
import org.fourfeetcat.storage.LlmCall;
import org.fourfeetcat.storage.LlmCallRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * 跨重启恢复（第28节课件 2.2，@Tag integration 人工 / 按需与 gate 分离运行）：kill 再起，会话 / 记忆 / 定时 / 审计四样原样回来。
 *
 * <p>在同一个 JVM 里串起两个 context 模拟"重启"：第一个起真容器做业务 + 触发定时，close 掉（内存状态彻底清空）， 第二个复用这份工作区再起，
 * 断言四样都在——任何一项没回来，说明有状态偷偷赖在了进程内存里。用 mock provider： 确定性、无 key，跑哪台机器都成立。
 */
@Tag("integration")
class RestartRecoveryIT {

  private static final String MOCK_PROVIDER = "mock";

  private static final String MOCK_BASE_URL = "http://mock.invalid";

  private static final String ROOT =
      Path.of("target", "restart-recovery").toAbsolutePath().toString();

  private static final String SESSION_ID = "web:u-restart:recovery-agent";

  private static final String PROFILE_YAML =
      """
      name: recovery-agent
      description: 重启恢复
      identity:
        agent_name: 恢复助手
        prompt: 处理用户消息，需要时保存记忆。
      provider:
        name: mock
        model: mock-model
      tools:
        - save_memory
      schedules:
        - id: recovery-task
          cron: "0 0 9 * * *"
          zone: Asia/Shanghai
          message: 记住：重启后的事实
      bootstrap: []
      """;

  static {
    try {
      Path root = Path.of(ROOT);
      Files.createDirectories(root.resolve("profiles"));
      Files.writeString(root.resolve("profiles").resolve("recovery-agent.yaml"), PROFILE_YAML);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    System.setProperty("fourfeetcat.root", ROOT);
  }

  @AfterAll
  static void clearWorkspaceRootProperty() {
    System.clearProperty("fourfeetcat.root");
  }

  @Test
  @DisplayName("重启后_会话/记忆/定时/审计四样原样恢复")
  void restart_recoversSessionsMemorySchedulesAndAudit() throws IOException {
    // ① 第一次启动：做业务 + 触发一次定时任务
    try (ConfigurableApplicationContext first = boot()) {
      AgentService agentService = first.getBean(AgentService.class);
      SessionManager sessionManager = first.getBean(SessionManager.class);
      AgentScheduler scheduler = first.getBean(AgentScheduler.class);

      Session session = sessionManager.getOrCreate("web", "u-restart", "recovery-agent");
      agentService.process(session, "记住：重启前的一句话");

      ScheduledTaskStore store = first.getBean(ScheduledTaskStore.class);
      ScheduledTaskView task =
          store.findAll().stream()
              .filter(t -> t.scheduleKey().equals("recovery-task"))
              .findFirst()
              .orElseThrow();
      // runNow 会写 task_executions、更新 run_count、再把"重启后的事实"写进记忆——全落 SQLite / 文件
      scheduler.runNow(task.scheduleId());
    } // 关 context：内存状态（session 缓存、scheduler 锁、register 结果）全部随之清空

    // ② 第二次启动：同一份工作区，全部只能从库 / 文件重建
    try (ConfigurableApplicationContext second = boot()) {
      SessionManager sessionManager = second.getBean(SessionManager.class);
      Session recovered = sessionManager.get(SESSION_ID).orElseThrow();
      assertThat(SessionMessagesDump.read(recovered)).as("会话历史完整回到").contains("重启前的一句话");

      Path memory = Path.of(ROOT, "memory", "MEMORY.md");
      assertThat(Files.readString(memory)).as("记忆带上重启前写下的两句话").contains("重启前的一句话", "重启后的事实");

      ScheduledTaskStore store = second.getBean(ScheduledTaskStore.class);
      ScheduledTaskView task =
          store.findAll().stream()
              .filter(t -> t.scheduleKey().equals("recovery-task"))
              .findFirst()
              .orElseThrow();
      assertThat(task.runCount()).as("定时 run_count 跨重启不丢").isGreaterThanOrEqualTo(1);
      assertThat(task.lastStatus()).as("上次结果跨重启还在").isEqualTo("success");
      assertThat(store.executionsOf(task.scheduleId())).as("执行历史跨重启还在").isNotEmpty();

      LlmCallRepository llmCalls = second.getBean(LlmCallRepository.class);
      java.util.List<LlmCall> traces = llmCalls.findBySessionIdOrderByCreatedAtDesc(SESSION_ID);
      assertThat(traces).as("审计不断档：重启前那轮对话的 llm_calls 还在").isNotEmpty();
    }
  }

  private static ConfigurableApplicationContext boot() {
    // 程序化起容器：覆盖 application.yaml 里默认的 deepseek provider，必须用**最高优先级**的 property source
    // （.properties() 是默认源、压在 application.yaml 之下，mock 名不会进 fourfeetcat.providers，Profile 会因
    // "provider 名未定义"被拒）。与 @SpringBootTest(properties=...) 的测试属性源同优先级。
    Map<String, Object> overrides = new HashMap<>();
    overrides.put("FOURFEETCAT_ROOT", ROOT);
    overrides.put("fourfeetcat.providers[0].name", MOCK_PROVIDER);
    overrides.put("fourfeetcat.providers[0].base-url", MOCK_BASE_URL);
    overrides.put("fourfeetcat.providers[0].api-key", "");
    ConfigurableEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test-providers", overrides));
    return new SpringApplicationBuilder(FourFeetCatApplication.class)
        .web(WebApplicationType.NONE)
        .environment(environment)
        .run();
  }

  /** 会话历史的文本转储（断言"那句话回来了"）。 */
  private static final class SessionMessagesDump {
    static String read(Session session) {
      StringBuilder sb = new StringBuilder();
      for (var m : session.getMessages()) {
        sb.append(m.getText() == null ? "" : m.getText()).append('\n');
      }
      return sb.toString();
    }
  }
}
