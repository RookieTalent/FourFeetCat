package org.fourfeetcat.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 定时任务子系统全流程（第28节课件的主验 harness）：定义→执行→持久→管理，在 gate 内自动跑通。
 *
 * <p>hermetic 三件套对齐 {@code MockAgentE2ETest}：工作区 + 数据源落同一临时目录、provider 换成本地 mock——无 key、不联网、
 * 不污染真实工作区。只有"模型"是假的，ReActLoop / ToolExecutor / Memory / 审计 / 定时登记与执行记录全走真实装配。
 *
 * <p>cron 设远期、靠 POST run 手动触发而非等时间：验证点集中在"登记→立即执行→落库→停用"这条可确定性的对账链。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "FOURFEETCAT_ROOT=${user.dir}/target/sched-e2e",
      "fourfeetcat.providers[0].name=mock",
      "fourfeetcat.providers[0].base-url=http://mock.invalid",
      "fourfeetcat.providers[0].api-key="
    })
class ScheduledTaskE2ETest {

  private static final String AGENT = "sched-agent";

  private static final String E2E_ROOT = Path.of("target", "sched-e2e").toAbsolutePath().toString();

  private static final String PROFILE_YAML =
      """
      name: sched-agent
      description: mock 定时示例
      identity:
        agent_name: 定时助手
        prompt: 你是测试定时任务的助手。
      provider:
        name: mock
        model: mock-model
      tools:
        - save_memory
      schedules:
        - id: daily-digest
          cron: "0 0 9 * * *"
          zone: Asia/Shanghai
          message: 记住：定时任务测试记忆
      bootstrap: []
      """;

  static {
    try {
      Path root = Path.of(E2E_ROOT);
      // 每次全新：定时任务有固定 scheduleKey，不清掉旧的 run_count/历史会把本次的确定性断言（0→1）打破
      deleteRecursively(root);
      Files.createDirectories(root.resolve("profiles"));
      Files.writeString(root.resolve("profiles").resolve(AGENT + ".yaml"), PROFILE_YAML);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    System.setProperty("fourfeetcat.root", E2E_ROOT);
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(p);
      }
    }
  }

  @AfterAll
  static void clearWorkspaceRootProperty() {
    System.clearProperty("fourfeetcat.root");
  }

  @Autowired TestRestTemplate rest;
  @Autowired ObjectMapper mapper;

  @Test
  @DisplayName("定时任务_启动即登记_立即执行后run_count与历史落库_停用后列表显示停用")
  void scheduledTask_fullLifecycleViaWeb() {
    // ① 启动即登记：列表有这条任务、run_count=0、enabled=true
    long id = requireSingleTaskId();
    JsonNode before = getSchedule(id);
    assertThat(before.path("runCount").asLong()).isZero();
    assertThat(before.path("enabled").asBoolean()).isTrue();

    // ② 立即执行：走真实 ReAct（mock 驱动一轮 save_memory）
    ResponseEntity<String> run =
        rest.postForEntity("/api/v1/schedules/" + id + "/run", null, String.class);
    assertThat(run.getStatusCode()).isEqualTo(HttpStatus.OK);

    // ③ 落库断言：run_count=1 且 success；executions 有一条成功；记忆真的写进了
    JsonNode after = getSchedule(id);
    assertThat(after.path("runCount").asLong()).isEqualTo(1);
    assertThat(after.path("lastStatus").asText()).isEqualTo("success");
    JsonNode executions = getArray("/api/v1/schedules/" + id + "/executions");
    assertThat(executions).as("执行历史恰一条且成功").hasSize(1);
    assertThat(executions.get(0).path("success").asBoolean()).isTrue();
    ResponseEntity<String> memory = rest.getForEntity("/api/v1/memory", String.class);
    assertThat(memory.getBody()).as("这次触发把事实写进了长期记忆，重启也不丢").contains("定时任务测试记忆");

    // ④ 停用 → 列表显示已停用
    rest.put(
        "/api/v1/schedules/" + id,
        new org.springframework.http.HttpEntity<Map<String, Boolean>>(Map.of("enabled", false)));
    JsonNode disabled = getSchedule(id);
    assertThat(disabled.path("enabled").asBoolean()).isFalse();
  }

  private long requireSingleTaskId() {
    JsonNode list = getArray("/api/v1/schedules");
    assertThat(list).as("临时工作区里应恰好一条登记任务").hasSize(1);
    return list.get(0).path("scheduleId").asLong();
  }

  /** 控制器不提供单查端点（本节四端点只到列表/历史/立即执行/启停），从列表里按 id 挑出那条。 */
  private JsonNode getSchedule(long id) {
    JsonNode list = getArray("/api/v1/schedules");
    for (JsonNode task : list) {
      if (task.path("scheduleId").asLong() == id) {
        return task;
      }
    }
    throw new AssertionError("列表里找不到任务 " + id);
  }

  private JsonNode getArray(String path) {
    ResponseEntity<String> response = rest.getForEntity(path, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return read(response.getBody()).path("data");
  }

  private JsonNode read(String body) {
    try {
      return mapper.readTree(body);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
