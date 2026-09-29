package org.fourfeetcat.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 第29节验收 harness：运行时 register 后立即可见；非法配置报错与启动路径完全一致（同异常类型、同消息）。 */
class ProfileRegistryRuntimeTest {

  @TempDir private Path workspace;

  @Test
  @DisplayName("register后立即find可见_remove后消失")
  void register_thenImmediatelyFindable_removeMakesItDisappear() {
    ProfileRegistry registry = new ProfileRegistry();
    Profile profile = AgentFixtures.profile("ops-agent");

    assertThat(registry.find("ops-agent")).isEmpty();
    assertThat(registry.exists("ops-agent")).isFalse();

    registry.register(profile);

    assertThat(registry.find("ops-agent")).isPresent();
    assertThat(registry.exists("ops-agent")).isTrue();

    registry.remove("ops-agent");
    assertThat(registry.exists("ops-agent")).isFalse();
    assertThat(registry.find("ops-agent")).isEmpty();
  }

  @Test
  @DisplayName("同名重复注册_后加载覆盖先加载")
  void duplicateName_laterOverwritesEarlier() {
    ProfileRegistry registry = new ProfileRegistry();
    registry.register(AgentFixtures.profile("dupe"));
    registry.register(AgentFixtures.profile("dupe"));

    // 同键后注册覆盖先注册：只剩下一条
    assertThat(registry.all()).hasSize(1);
    assertThat(registry.find("dupe")).isPresent();
  }

  @Test
  @DisplayName("非法配置报错_与启动加载路径完全一致")
  void invalidConfig_errorMatchesStartupPath() throws IOException {
    // provider 名不可解析（'nope' 不在白名单）：Agent 目录派生 与 手写 Profile 加载 走同一校验、同一消息
    ProfileLoader loader = new ProfileLoader(AgentFixtures.PROVIDERS);
    Map<String, Object> badProvider =
        new LinkedHashMap<>(Map.of("name", "bad", "provider", Map.of("name", "nope")));

    Throwable viaProfileLoader =
        catchThrowable(() -> loader.fromYamlMap(Path.of("bad.yaml"), badProvider));

    Path agentsRoot = Files.createDirectories(workspace.resolve("agents"));
    Path dir =
        AgentFixtures.writeAgent(agentsRoot, "bad", "name: bad\nprovider:\n  name: nope\n", "正文");
    Throwable viaAgentLoader =
        catchThrowable(() -> new AgentLoader(AgentFixtures.PROVIDERS).deriveProfile(dir));

    // 同一异常类型 + 同一消息：两条来源同规矩
    assertThat(viaAgentLoader).isInstanceOf(IllegalArgumentException.class);
    assertThat(viaProfileLoader).isInstanceOf(IllegalArgumentException.class);
    assertThat(viaAgentLoader.getMessage()).isEqualTo(viaProfileLoader.getMessage());
  }
}
