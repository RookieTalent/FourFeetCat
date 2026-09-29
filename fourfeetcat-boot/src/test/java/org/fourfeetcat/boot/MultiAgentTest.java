package org.fourfeetcat.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.profile.ProfileRegistry;
import org.fourfeetcat.tool.registry.ToolRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 多 Agent 并存（第28节课件 2.3）：两个差异明显的 Profile 跑在同一实例上，工具 / 会话 / 定时三条隔离边界都守得住。
 *
 * <p>gate 内 mock：alpha 只持文件工具、beta 只持 HTTP 工具，另有各自专属的定时任务。无 key、不联网（HTTP 工具只登记不真调）。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "FOURFEETCAT_ROOT=${user.dir}/target/multi-agent",
      "fourfeetcat.providers[0].name=mock",
      "fourfeetcat.providers[0].base-url=http://mock.invalid",
      "fourfeetcat.providers[0].api-key="
    })
class MultiAgentTest {

  private static final String ROOT = Path.of("target", "multi-agent").toAbsolutePath().toString();

  /** alpha：文件工具；beta：HTTP 工具。都带 save_memory（mock 固定驱动那个工具），各自一条互不相同的定时任务。 */
  private static final String ALPHA_YAML = agentYaml("alpha", "read_file", "alpha-digest");

  private static final String BETA_YAML = agentYaml("beta", "http_get", "beta-digest");

  static {
    try {
      Path root = Path.of(ROOT);
      // 每次全新：会话用户固定，不清掉旧库会让"恰一条会话"的隔离断言随重跑累积失效
      deleteRecursively(root);
      Files.createDirectories(root.resolve("profiles"));
      Files.writeString(root.resolve("profiles").resolve("alpha.yaml"), ALPHA_YAML);
      Files.writeString(root.resolve("profiles").resolve("beta.yaml"), BETA_YAML);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    System.setProperty("fourfeetcat.root", ROOT);
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
  @Autowired ProfileRegistry profileRegistry;
  @Autowired ToolRegistry toolRegistry;

  @Test
  @DisplayName("两个Profile并存_工具/会话/定时三条隔离边界都守得住")
  void multiAgent_isolatesToolsSessionsAndSchedules() {
    // ① 会话隔离：各自建会话、各自答，列表里两条互不相同、profileName 各归各
    String alphaSession = "web:u-alpha:alpha";
    String betaSession = "web:u-beta:beta";
    ResponseEntity<String> createdA =
        rest.postForEntity(
            "/api/v1/sessions", Map.of("agent", "alpha", "user", "u-alpha"), String.class);
    assertThat(createdA.getStatusCode()).isEqualTo(HttpStatus.OK);
    ResponseEntity<String> createdB =
        rest.postForEntity(
            "/api/v1/sessions", Map.of("agent", "beta", "user", "u-beta"), String.class);
    assertThat(createdB.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(send(alphaSession, "记住：alpha 的记忆")).contains("已记住");
    assertThat(send(betaSession, "记住：beta 的记忆")).contains("已记住");

    JsonNode sessions = getArray("/api/v1/sessions");
    long alpha = countByProfile(sessions, "alpha");
    long beta = countByProfile(sessions, "beta");
    assertThat(alpha).as("alpha 的会话按它自己的 Agent 归集").isEqualTo(1);
    assertThat(beta).as("beta 的会话按它自己的 Agent 归集").isEqualTo(1);

    // ② 工具隔离：各 Profile 的可用工具清单不含对方专属工具（descriptors 按名过滤 = 模型真正看到那批）
    Profile profileAlpha = requireProfile("alpha");
    Profile profileBeta = requireProfile("beta");
    assertThat(toolRegistry.descriptors(profileAlpha.tools()))
        .as("alpha 拿不到 beta 专属的 http_get")
        .extracting("name")
        .contains("read_file")
        .doesNotContain("http_get");
    assertThat(toolRegistry.descriptors(profileBeta.tools()))
        .as("beta 拿不到 alpha 专属的 read_file")
        .extracting("name")
        .contains("http_get")
        .doesNotContain("read_file");

    // ③ 定时隔离：两条任务各归各 Agent，互不共享一份配置文件
    JsonNode schedules = getArray("/api/v1/schedules");
    assertThat(countByProfile(schedules, "alpha")).as("alpha 一条定时在自己名下").isEqualTo(1);
    assertThat(countByProfile(schedules, "beta")).as("beta 一条定时在自己名下").isEqualTo(1);
  }

  private String send(String sessionId, String content) {
    ResponseEntity<String> reply =
        rest.postForEntity(
            "/api/v1/sessions/" + sessionId + "/messages",
            Map.of("content", content),
            String.class);
    assertThat(reply.getStatusCode()).isEqualTo(HttpStatus.OK);
    return reply.getBody() == null ? "" : reply.getBody();
  }

  private long countByProfile(JsonNode array, String profile) {
    long count = 0;
    for (JsonNode node : array) {
      if (node.path("profileName").asText().equals(profile)) {
        count++;
      }
    }
    return count;
  }

  private Profile requireProfile(String name) {
    return profileRegistry.find(name).orElseThrow(() -> new AssertionError("Profile 未加载: " + name));
  }

  private JsonNode getArray(String path) {
    ResponseEntity<String> response = rest.getForEntity(path, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    try {
      return mapper.readTree(response.getBody()).path("data");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String agentYaml(String name, String tool, String scheduleId) {
    return """
        name: %s
        description: %s 隔离示例
        identity:
          agent_name: %s
          prompt: 你是 %s 助手。
        provider:
          name: mock
          model: mock-model
        tools:
          - save_memory
          - %s
        schedules:
          - id: %s
            cron: "0 0 9 * * *"
            zone: Asia/Shanghai
            message: 记住：%s 的定时记忆
        bootstrap: []
        """
        .formatted(name, name, name, name, tool, scheduleId, name);
  }
}
