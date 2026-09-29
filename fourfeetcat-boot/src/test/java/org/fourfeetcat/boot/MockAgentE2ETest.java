package org.fourfeetcat.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.storage.LlmCall;
import org.fourfeetcat.storage.LlmCallRepository;
import org.fourfeetcat.storage.ToolInvocation;
import org.fourfeetcat.storage.ToolInvocationRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 免 key 整机端到端（第27节）：{@code @SpringBootTest} 起真实 HTTP 端口 + SQLite，从测试里 POST 建会话、发对话，再把会话 / 记忆 / 工具 /
 * 审计全查回来——跟人手动点一遍完全一样，只是自动化了。
 *
 * <p>hermetic 三件套：工作区根走系统属性（第27节新增）指到临时目录、数据源同一临时目录、provider 换成本地 mock—— 无 key、不联网、
 * 不污染真实工作区。只有"模型"是假的，ReActLoop / ToolExecutor / Memory / 审计全走真实装配。
 *
 * <p>工作区与数据源都落到 {@code target/mock-e2e}（clean 时一并清掉），会话用户每次唯一，审计因此按会话精确计账， 不被历史库残留干扰。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      // 数据源 → 同一个临时工作区（application.yaml 的 ${FOURFEETCAT_ROOT} 占位从此解析）
      "FOURFEETCAT_ROOT=${user.dir}/target/mock-e2e",
      // Provider 整个换成 mock：显式映射表只装免 key 桩，摸不到真实模型
      "fourfeetcat.providers[0].name=mock",
      "fourfeetcat.providers[0].base-url=http://mock.invalid",
      "fourfeetcat.providers[0].api-key="
    })
class MockAgentE2ETest {

  private static final String AGENT = "mock-agent";

  private static final String E2E_ROOT = Path.of("target", "mock-e2e").toAbsolutePath().toString();

  private static final String PROFILE_YAML =
      """
      name: mock-agent
      description: mock 测试 Agent
      identity:
        agent_name: mock助手
        prompt: 你是一个测试助手。
      provider:
        name: mock
        model: mock-model
      tools:
        - save_memory
      bootstrap: []
      """;

  static {
    try {
      Path root = Path.of(E2E_ROOT);
      Files.createDirectories(root.resolve("profiles"));
      Files.writeString(root.resolve("profiles").resolve(AGENT + ".yaml"), PROFILE_YAML);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    // 工作区根走系统属性（第27节新增）——boot 装配据此指到临时目录；数据源由上面的 FOURFEETCAT_ROOT 属性同址
    System.setProperty("fourfeetcat.root", E2E_ROOT);
  }

  @AfterAll
  static void clearWorkspaceRootProperty() {
    // JVM 级系统属性是跨测试上下文的，跑完必须清掉，避免污染同进程后续测试（surefire 单 JVM 顺序执行）
    System.clearProperty("fourfeetcat.root");
  }

  @Autowired TestRestTemplate rest;
  @Autowired LlmCallRepository llmCalls;
  @Autowired ToolInvocationRepository toolInvocations;

  @Test
  @DisplayName("mock 走通全链路_会话/记忆/工具/审计四张证_记账不多不少")
  void fullConversation_withMock_walksThroughEveryStation() {
    String user = "u-" + System.nanoTime();
    String sessionId = "web:" + user + ":" + AGENT;

    // ① 建会话
    ResponseEntity<String> created =
        rest.postForEntity("/api/v1/sessions", Map.of("agent", AGENT, "user", user), String.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

    // ② 发"记住…" → mock 驱动两轮 ReAct（一次 save_memory）
    ResponseEntity<String> reply = sendMessage(sessionId, "记住：我喜欢喝美式咖啡");
    assertThat(reply.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(reply.getBody()).contains("已记住");

    // ③ 会话详情：历史里有两次模型回复 + 一次工具结果（user/assistant/tool/assistant 四条）
    ResponseEntity<String> detail =
        rest.getForEntity("/api/v1/sessions/" + sessionId, String.class);
    assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(detail.getBody()).contains("已记住：我喜欢喝美式咖啡");

    // ④ 会话列表能列到这一条（三面同源 / 列表接口可见）
    ResponseEntity<String> list = rest.getForEntity("/api/v1/sessions", String.class);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(list.getBody()).contains(sessionId);

    // ⑤ 记忆写得进、读得出：GET /memory 里查得到刚写下的事实
    ResponseEntity<String> memory = rest.getForEntity("/api/v1/memory", String.class);
    assertThat(memory.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(memory.getBody()).contains("美式咖啡");

    // ⑥ 工具清单对外可见、含关键工具
    ResponseEntity<String> tools = rest.getForEntity("/api/v1/tools", String.class);
    assertThat(tools.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(tools.getBody()).contains("save_memory");

    // ⑦ 审计对账：llm 恰 2 条（两轮）、全成功、token 非零；tool 恰 1 条 save_memory、成功
    List<LlmCall> llm = llmCalls.findBySessionIdOrderByCreatedAtDesc(sessionId);
    assertThat(llm).as("本次会话的模型调用恰两轮").hasSize(2);
    assertThat(llm).as("两轮都应成功").allMatch(LlmCall::isSuccess);
    assertThat(llm).as("mock 给了非零 token").allMatch(c -> c.getTotalTokens() > 0);
    List<ToolInvocation> toolsDone =
        toolInvocations.findAll().stream().filter(t -> t.getSessionId().equals(sessionId)).toList();
    assertThat(toolsDone).as("本次会话的工具调用恰一次").hasSize(1);
    assertThat(toolsDone.get(0).getToolName()).isEqualTo("save_memory");
    assertThat(toolsDone.get(0).isSuccess()).isTrue();
  }

  private ResponseEntity<String> sendMessage(String sessionId, String content) {
    ResponseEntity<String> response =
        rest.postForEntity(
            "/api/v1/sessions/" + sessionId + "/messages",
            Map.of("content", content),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response;
  }
}
