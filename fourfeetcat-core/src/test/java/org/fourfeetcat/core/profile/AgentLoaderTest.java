package org.fourfeetcat.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 第29节验收 harness：AgentLoader 拆出 AGENT.md 的 frontmatter/正文、认出附属资源、缺必填报错点名。 */
class AgentLoaderTest {

  @TempDir private Path workspace;

  private Path agentsRoot() throws IOException {
    return Files.createDirectories(workspace.resolve("agents"));
  }

  @Test
  @DisplayName("deriveProfile_拆出frontmatter配置与正文")
  void deriveProfile_splitsFrontmatterAndBody() throws IOException {
    Path dir =
        AgentFixtures.writeAgent(
            agentsRoot(), "recon", "name: recon\nprovider:\n  name: deepseek\n", "你是对账助手。\n多行正文。");

    Profile profile = new AgentLoader(AgentFixtures.PROVIDERS).deriveProfile(dir);

    assertThat(profile.name()).isEqualTo("recon");
    assertThat(profile.provider().name()).isEqualTo("deepseek");
    assertThat(AgentLoader.bodyOf(dir.resolve("AGENT.md"))).isEqualTo("你是对账助手。\n多行正文。");
  }

  @Test
  @DisplayName("detectResources_认出scripts_skills与REFERENCE_md")
  void detectResources_recognizesScriptsSkillsAndReference() throws IOException {
    Path dir =
        AgentFixtures.writeAgent(
            agentsRoot(), "recon", "name: recon\nprovider:\n  name: deepseek\n", "正文");
    Files.createDirectories(dir.resolve("scripts"));
    Files.writeString(dir.resolve("scripts").resolve("reconcile.py"), "print('hi')");
    Files.createDirectories(dir.resolve("skills"));
    Files.writeString(dir.resolve("skills").resolve("report-format.md"), "# 报告规范");
    Files.writeString(dir.resolve("REFERENCE.md"), "# 参考");

    AgentLoader.AgentResources resources =
        new AgentLoader(AgentFixtures.PROVIDERS).detectResources(dir);

    assertThat(resources.scriptsDir()).isNotNull();
    assertThat(resources.skillsDir()).isNotNull();
    assertThat(resources.referenceFile()).isNotNull();

    // 无附属资源的目录 → 全 null
    Path bare =
        AgentFixtures.writeAgent(
            agentsRoot(), "bare", "name: bare\nprovider:\n  name: deepseek\n", "正文");
    AgentLoader.AgentResources empty =
        new AgentLoader(AgentFixtures.PROVIDERS).detectResources(bare);
    assertThat(empty.scriptsDir()).isNull();
    assertThat(empty.skillsDir()).isNull();
    assertThat(empty.referenceFile()).isNull();
  }

  @Test
  @DisplayName("缺name_报错点名")
  void missingName_throwsPointingAtField() throws IOException {
    Path dir =
        AgentFixtures.writeAgent(agentsRoot(), "nope", "provider:\n  name: deepseek\n", "正文");

    assertThatThrownBy(() -> new AgentLoader(AgentFixtures.PROVIDERS).deriveProfile(dir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  @DisplayName("缺provider_报错点名")
  void missingProvider_throwsPointingAtField() throws IOException {
    Path dir = AgentFixtures.writeAgent(agentsRoot(), "nope", "name: nope\n", "正文");

    assertThatThrownBy(() -> new AgentLoader(AgentFixtures.PROVIDERS).deriveProfile(dir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("provider");
  }

  @Test
  @DisplayName("frontmatter未闭合_报错点名")
  void unclosedFrontmatter_throws() throws IOException {
    Path dir = Files.createDirectories(agentsRoot().resolve("bad"));
    Files.writeString(
        dir.resolve("AGENT.md"), "---\nname: bad\nprovider:\n  name: deepseek\n没有结束分隔符");

    assertThatThrownBy(() -> new AgentLoader(AgentFixtures.PROVIDERS).deriveProfile(dir))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("frontmatter");
  }
}
