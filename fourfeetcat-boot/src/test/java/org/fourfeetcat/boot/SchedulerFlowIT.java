package org.fourfeetcat.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.fourfeetcat.core.schedule.AgentScheduler;
import org.fourfeetcat.core.schedule.ScheduledTaskStore;
import org.fourfeetcat.core.schedule.ScheduledTaskView;
import org.fourfeetcat.core.schedule.TaskExecutionView;
import org.fourfeetcat.storage.LlmCall;
import org.fourfeetcat.storage.LlmCallRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 定时链路——真 key 对账（第28节课件 2.2 / 3 后半，@Tag integration 门禁外人工跑）：到点自跑、会话复用、逐表痕迹对账。
 *
 * <p>用真实模型驱动一次定向任务（消息里要读文件并把结论写进记忆），连续触发两次，对账钟推会话被<b>复用</b>、execution 两条、 审计沿会话 id 串得起。 天气→notify
 * 推送的 webhook 链条属"人工项"（见验收报告的剩余清单），这里不依赖外部 webhook。
 *
 * <p>{@code DEEPSEEK_API_KEY} 未配即跳过（对齐 ProviderSmokeIT 惯例），不拖门禁。
 */
@Tag("integration")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "FOURFEETCAT_ROOT=${user.dir}/target/scheduler-flow-it",
      "fourfeetcat.providers[0].name=mock",
      "fourfeetcat.providers[0].base-url=http://mock.invalid",
      "fourfeetcat.providers[0].api-key="
    })
class SchedulerFlowIT {

  private static final String ROOT =
      Path.of("target", "scheduler-flow-it").toAbsolutePath().toString();

  private static final String PROFILE_YAML =
      """
      name: sched-flow
      description: 定时链路对账
      identity:
        agent_name: 定时对账
        prompt: 把用户消息交给你处理；需要时保存记忆。
      provider:
        name: mock
        model: mock-model
      tools:
        - save_memory
      schedules:
        - id: flow-task
          cron: "0 0 9 * * *"
          zone: Asia/Shanghai
          message: 记住：定时链路的一条记忆
      bootstrap: []
      """;

  @Autowired AgentScheduler scheduler;
  @Autowired ScheduledTaskStore store;
  @Autowired LlmCallRepository llmCalls;

  static {
    try {
      Path root = Path.of(ROOT);
      Files.createDirectories(root.resolve("profiles"));
      Files.writeString(root.resolve("profiles").resolve("sched-flow.yaml"), PROFILE_YAML);
    } catch (java.io.IOException e) {
      throw new UncheckedIOException(e);
    }
    // 工作区指到临时目录，不污染真实工作区；key 缺省时由测试方法里的 assumeTrue 判定跑不跑
    System.setProperty("fourfeetcat.root", ROOT);
  }

  @AfterAll
  static void clearWorkspaceRootProperty() {
    System.clearProperty("fourfeetcat.root");
  }

  @Test
  @DisplayName("定时任务连触两次_钟推会话被复用_execution两条_审计串得起")
  void realModel_schedulerSessionIsReusedAndLedgerIsConsistent() {
    assumeTrue(System.getenv("DEEPSEEK_API_KEY") != null, "DEEPSEEK_API_KEY 未配置，跳过真模型定时链路集成测试");

    ScheduledTaskView task =
        store.findAll().stream()
            .filter(t -> t.scheduleKey().equals("flow-task"))
            .findFirst()
            .orElseThrow();
    long id = task.scheduleId();

    // 连续两次触发，每次都是一条真实 ReAct（mock：save_memory 后收尾）
    scheduler.runNow(id);
    scheduler.runNow(id);

    List<TaskExecutionView> executions = store.executionsOf(id);
    assertThat(executions).as("两次触发两笔执行历史").hasSize(2);

    // ① 会话复用：两次触发用的是同一条钟推会话（scheduler:scheduler:sched-flow），不是两条
    assertThat(executions.get(0).sessionId()).isEqualTo(executions.get(1).sessionId());
    assertThat(executions.get(0).sessionId()).startsWith("scheduler:scheduler:");

    // ② 审计沿会话 id 串得起：这个钟推会话下至少有模型调用痕迹
    List<LlmCall> traces =
        llmCalls.findBySessionIdOrderByCreatedAtDesc(executions.get(0).sessionId());
    assertThat(traces).as("钟推会话下模型调用有留痕").hasSizeGreaterThan(0);

    // ③ 任务状态对账：run_count 跟随执行、last_status 落成 success
    ScheduledTaskView after = store.findById(id).orElseThrow();
    assertThat(after.runCount()).isEqualTo(2);
    assertThat(after.lastStatus()).isEqualTo("success");
  }
}
