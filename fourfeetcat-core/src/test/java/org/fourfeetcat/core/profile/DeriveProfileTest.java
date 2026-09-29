package org.fourfeetcat.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 第29节验收 harness：frontmatter 各字段正确映射到 Profile；schedules 原样带进（定时来自 Agent 的直接证据）。 */
class DeriveProfileTest {

  @TempDir private Path workspace;

  @Test
  @DisplayName("frontmatter全字段映射到Profile")
  void frontmatter_allFieldsMapped() throws IOException {
    Path agentsRoot = Files.createDirectories(workspace.resolve("agents"));
    String fm =
        "name: daily-reconcile\n"
            + "description: 每日对账\n"
            + "identity:\n"
            + "  agent_name: 对账小欧\n"
            + "  prompt: 只依据脚本数据下结论\n"
            + "provider:\n"
            + "  name: deepseek\n"
            + "  model: deepseek-chat\n"
            + "  temperature: 0.2\n"
            + "tools:\n"
            + "  - shell\n"
            + "  - read_file\n"
            + "mcp_servers:\n"
            + "  - github-mcp\n"
            + "channels:\n"
            + "  - cli\n"
            + "notify_channels:\n"
            + "  - ops-webhook\n"
            + "bootstrap:\n"
            + "  - AGENTS.md\n"
            + "settings:\n"
            + "  max_iterations: 10\n"
            + "schedules:\n"
            + "  - id: recon-morning\n"
            + "    cron: 0 0 9 * * *\n"
            + "    zone: Asia/Shanghai\n"
            + "    message: 到点对账\n";
    Path dir = AgentFixtures.writeAgent(agentsRoot, "daily-reconcile", fm, "正文");

    Profile profile = new AgentLoader(AgentFixtures.PROVIDERS).deriveProfile(dir);

    assertThat(profile.name()).isEqualTo("daily-reconcile");
    assertThat(profile.description()).isEqualTo("每日对账");
    assertThat(profile.identity().agentName()).isEqualTo("对账小欧");
    assertThat(profile.identity().prompt()).contains("只依据脚本数据");
    assertThat(profile.provider().name()).isEqualTo("deepseek");
    assertThat(profile.provider().model()).isEqualTo("deepseek-chat");
    assertThat(profile.provider().temperature()).isEqualTo(0.2);
    assertThat(profile.tools()).containsExactly("shell", "read_file");
    assertThat(profile.mcpServers()).containsExactly("github-mcp");
    assertThat(profile.channels()).containsExactly("cli");
    assertThat(profile.notifyChannels()).containsExactly("ops-webhook");
    assertThat(profile.bootstrap()).containsExactly("AGENTS.md");
    assertThat(profile.settings()).containsEntry("max_iterations", 10);
  }

  @Test
  @DisplayName("schedules原样带进派生的Profile")
  void schedules_carriedVerbatimIntoDerivedProfile() throws IOException {
    Path agentsRoot = Files.createDirectories(workspace.resolve("agents"));
    String fm =
        "name: daily-reconcile\n"
            + "provider:\n"
            + "  name: deepseek\n"
            + "schedules:\n"
            + "  - id: recon-morning\n"
            + "    cron: 0 0 9 * * *\n"
            + "    zone: Asia/Shanghai\n"
            + "    message: 到点对账\n"
            + "  - id: recon-note\n"
            + "    cron: 0 0 12 * * *\n"
            + "    zone: Asia/Shanghai\n"
            + "    message: 午间提醒\n";
    Path dir = AgentFixtures.writeAgent(agentsRoot, "daily-reconcile", fm, "正文");

    Profile profile = new AgentLoader(AgentFixtures.PROVIDERS).deriveProfile(dir);

    assertThat(profile.schedules()).hasSize(2);
    Map<String, Object> first = profile.schedules().get(0);
    assertThat(first.get("id")).isEqualTo("recon-morning");
    assertThat(first.get("cron")).isEqualTo("0 0 9 * * *");
    assertThat(first.get("zone")).isEqualTo("Asia/Shanghai");
    assertThat(first.get("message")).isEqualTo("到点对账");
  }
}
