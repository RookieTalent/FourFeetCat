package org.fourfeetcat.core.profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 第29节 harness 共用的测试夹具：写 Agent 目录、构造 Profile 与 schedule 条目（包内可见，仅测试用）。 */
final class AgentFixtures {

  private AgentFixtures() {}

  /** 测试统一的 provider 白名单：frontmatter 里 provider.name 必须落在它里面。 */
  static final Set<String> PROVIDERS = Set.of("deepseek");

  /**
   * 在 {@code agentsRoot} 下写一个 {@code name}/{@code AGENT.md}，frontmatter 主体为 {@code
   * frontmatterFields}、正文为 {@code body}。
   */
  static Path writeAgent(Path agentsRoot, String name, String frontmatterFields, String body)
      throws IOException {
    Path dir = Files.createDirectories(agentsRoot.resolve(name));
    String content = "---\n" + frontmatterFields + "---\n" + body;
    Files.writeString(dir.resolve("AGENT.md"), content, StandardCharsets.UTF_8);
    return dir;
  }

  static Profile profile(String name, List<Map<String, Object>> schedules) {
    return new Profile(
        name,
        "desc",
        new Profile.Identity(name + "Elective", "人格 prompt"),
        new Profile.ProviderConfig("deepseek", "deepseek-chat", 0.2),
        List.of("shell"),
        List.of(),
        List.of(),
        List.of("cli"),
        List.of(),
        schedules,
        List.of(),
        Map.of());
  }

  static Profile profile(String name) {
    return profile(name, List.of());
  }

  /** 一条合法 schedule 条目（id/cron/zone/message 全齐；message 非空，否则会被判坏跳过）。 */
  static Map<String, Object> schedule(String id, String cron, String zone) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("id", id);
    entry.put("cron", cron);
    entry.put("zone", zone);
    entry.put("message", "到点执行");
    return entry;
  }

  static Map<String, Object> schedule(String id, String cron) {
    return schedule(id, cron, "Asia/Shanghai");
  }
}
