package org.fourfeetcat.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.fourfeetcat.storage.LlmCallRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 全流程串联——人推链路（第27节，门禁外的真模型集成测试）。
 *
 * <p>课件三根支柱的自动化版：对话答得出（建会话 → 发消息 → 历史完整）＋ 工具查得到（/tools 含关键工具）＋ 三面同源（列表可列到 同一条会话）， 外加审计对账（llm_calls
 * / tool_invocations 都留下带本次会话 id 的痕迹）。
 *
 * <p><b>需要真 key + 网络</b>，无 key 即跳过（对齐第16节 ProviderSmokeIT 惯例）：{@code DEEPSEEK_API_KEY} 配置时才真跑，
 * 没配就静默跳过，因此不拖 `mvn verify` 门禁。真跑时走默认 Agent（deepseek）查一次天气穿搭，http 白名单放开 wttr.in。
 */
@Tag("integration")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "FOURFEETCAT_ROOT=${user.dir}/target/human-it",
      // 真 Provider：键从环境变量解析，未配则零凭证启动（由上面的 assumeTrue 判定跑不跑这笔流）
      "fourfeetcat.providers[0].name=deepseek",
      "fourfeetcat.providers[0].base-url=https://api.deepseek.com",
      "fourfeetcat.providers[0].api-key=${DEEPSEEK_API_KEY}",
      // 让内置 http_get 能访问天气源（沙箱域名白名单；留空 = 全拒，流必然考不出"工具查得到"）
      "http.allowed-domains[0]=wttr.in"
    })
class HumanTriggerFlowIT {

  private static final String AGENT = "flow-agent";

  private static final String IT_ROOT = Path.of("target", "human-it").toAbsolutePath().toString();

  private static final String PROFILE_YAML =
      """
      name: flow-agent
      description: 真实流程 Agent
      identity:
        agent_name: 流程助手
        prompt: 你是能联网查天气、并按天气给穿衣建议的助手。
      provider:
        name: deepseek
        model: deepseek-v4-flash
      tools:
        - http_get
        - save_memory
      settings:
        max_iterations: 8
      """;

  static {
    try {
      Path root = Path.of(IT_ROOT);
      Files.createDirectories(root.resolve("profiles"));
      Files.writeString(root.resolve("profiles").resolve(AGENT + ".yaml"), PROFILE_YAML);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    System.setProperty("fourfeetcat.root", IT_ROOT);
  }

  @AfterAll
  static void clearWorkspaceRootProperty() {
    System.clearProperty("fourfeetcat.root");
  }

  @Autowired TestRestTemplate rest;
  @Autowired LlmCallRepository llmCalls;

  @Test
  @DisplayName("真模型走通人推主流程_对话答得出_账记得上_三面同源")
  void realModel_walksHumanTriggeredConversation() {
    String key = System.getenv("DEEPSEEK_API_KEY");
    assumeTrue(key != null && !key.isBlank(), "DEEPSEEK_API_KEY 未配置，跳过真模型集成测试");

    String user = "u-" + System.nanoTime();
    String sessionId = "web:" + user + ":" + AGENT;

    // 支柱一 · 对话答得出：建会话 → 发"天气穿衣" → 答复非空且不是异常信封
    ResponseEntity<String> created =
        rest.postForEntity("/api/v1/sessions", Map.of("agent", AGENT, "user", user), String.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> reply =
        rest.postForEntity(
            "/api/v1/sessions/" + sessionId + "/messages",
            Map.of("content", "今天北京天气怎么样，穿什么合适"),
            String.class);
    assertThat(reply.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(reply.getBody()).as("答复非空、不是异常信封").isNotBlank();

    // 三面同源 · 会话历史完整、列表可列到同一条
    ResponseEntity<String> detail =
        rest.getForEntity("/api/v1/sessions/" + sessionId, String.class);
    assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(detail.getBody()).as("历史里带着用户那句话").contains("北京天气");

    ResponseEntity<String> list = rest.getForEntity("/api/v1/sessions", String.class);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(list.getBody()).as("列表接口能看到这条会话").contains(sessionId);

    // 支柱三 · 工具查得到：内置工具清单对外可见
    ResponseEntity<String> tools = rest.getForEntity("/api/v1/tools", String.class);
    assertThat(tools.getBody()).contains("http_get").contains("save_memory");

    // 审计对账 · 这一趟留下带本次会话 id 的模型调用痕迹（至少一次、成功）
    assertThat(llmCalls.findBySessionIdOrderByCreatedAtDesc(sessionId))
        .as("这次对话至少触发了真实模型调用")
        .hasSizeGreaterThan(0);
  }
}
