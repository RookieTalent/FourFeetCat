package org.fourfeetcat.core.react;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.profile.Profile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 第29节验收 harness：Agent 正文进 system prompt；技能经软连接只注入元数据、Skill 正文不预载；参考/脚本按需经 read_file 与 shell。 */
class ProgressiveDisclosureTest {

  @TempDir private Path workspace;

  @Test
  @DisplayName("Agent正文进prompt_脚本与参考不预载")
  void agentBodyEnterPrompt_scriptsAndReferenceNotPreloaded() throws IOException {
    writeAgentWithBody("recon", "你是对账助手，只依据脚本数据下结论。");
    Files.createDirectories(workspace.resolve("agents").resolve("recon").resolve("scripts"));
    Files.writeString(
        workspace.resolve("agents").resolve("recon").resolve("scripts").resolve("reconcile.py"),
        "orders = load(ORDERS_CSV)\ndiffs = compare(orders, settle)");
    Files.writeString(workspace.resolve("agents").resolve("recon").resolve("REFERENCE.md"), "字段字典");

    String text = new ContextLoader(workspace).load(profile("recon"));

    assertThat(text).contains("你是对账助手，只依据脚本数据下结论。");
    // 参考与脚本代码不预载——模型需要时用底座的 read_file / shell 按需取
    assertThat(text).doesNotContain("orders = load").doesNotContain("字段字典");
  }

  @Test
  @DisplayName("绑定Skill经软连接_只注入元数据不预载正文")
  void boundSkill_viaSymlink_injectsMetadataOnly_notBody() throws IOException {
    Path skillDir = Files.createDirectories(workspace.resolve("skills").resolve("weather"));
    Files.writeString(
        skillDir.resolve("SKILL.md"), "---\nname: weather\ndescription: 查询天气\n---\n\n正文不该被预载进来");

    writeAgentWithBody("recon", "你是对账助手。");
    Path agentSkills =
        Files.createDirectories(workspace.resolve("agents").resolve("recon").resolve("skills"));
    // 宪法四：Agent 可见的公共 Skill 只由指向公共 skills/ 根的相对软连接表达
    try {
      Files.createSymbolicLink(agentSkills.resolve("weather"), skillDir);
    } catch (IOException | UnsupportedOperationException e) {
      // Windows 无建软链权限（既有记忆约定）：本地跳过、CI 真跑，不误删断言
      Assumptions.assumeTrue(false, "无软链接权限，跳过（CI 真跑）：" + e.getMessage());
    }

    String text = new ContextLoader(workspace).load(profile("recon"));

    assertThat(text)
        .contains("weather")
        .contains("查询天气")
        .contains(skillDir.toAbsolutePath().toString());
    // 渐进披露：Skill 正文由模型经 read_file 按需读，组装 prompt 时不预载
    assertThat(text).doesNotContain("正文不该被预载进来");
  }

  @Test
  @DisplayName("Agent目录skills下的普通子指令文件_不进prompt由模型read_file按需读")
  void internalSubinstruction_inAgentSkills_notInjected() throws IOException {
    writeAgentWithBody("recon", "写报告前读 skills/report-format.md。");
    Files.createDirectories(workspace.resolve("agents").resolve("recon").resolve("skills"));
    Files.writeString(
        workspace.resolve("agents").resolve("recon").resolve("skills").resolve("report-format.md"),
        "# 报告规范\n- P0 立即处理");

    String text = new ContextLoader(workspace).load(profile("recon"));

    // 子指令不预载进 prompt（非软链接、非公共 Skill）：模型按正文指引用 read_file 现读
    assertThat(text).doesNotContain("# 报告规范");
  }

  private void writeAgentWithBody(String name, String body) throws IOException {
    Path dir = Files.createDirectories(workspace.resolve("agents").resolve(name));
    String content = "---\nname: " + name + "\nprovider:\n  name: deepseek\n---\n" + body;
    Files.writeString(dir.resolve("AGENT.md"), content, StandardOpenOption.CREATE_NEW);
  }

  private static Profile profile(String name) {
    return new Profile(
        name,
        "desc",
        new Profile.Identity(name + "市", "人格 prompt"),
        new Profile.ProviderConfig("deepseek", "deepseek-chat", 0.2),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Map.of());
  }
}
