package org.fourfeetcat.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class ProfileLoaderTest {

  @TempDir Path profilesDir;

  private ListAppender<ILoggingEvent> logCapture;
  private ProfileLoader loader;

  @BeforeEach
  void setUp() {
    logCapture = new ListAppender<>();
    logCapture.start();
    ((Logger) LoggerFactory.getLogger(ProfileLoader.class)).addAppender(logCapture);
    // env 读取注入替身，不依赖真实环境变量
    Function<String, String> env = key -> "TEST_ENV_VALUE".equals(key) ? "resolved-key" : null;
    loader = new ProfileLoader(env, Set.of("deepseek", "kimi"));
  }

  @Test
  @DisplayName("合法YAML_全字段解析")
  void validYaml_parsesAllFields() throws IOException {
    writeYaml(
        "ops-agent.yaml",
        """
        name: ops-agent
        description: 运维助手
        identity:
          agent_name: 运维小欧
          prompt: 你是一个专业的运维助手
        provider:
          name: deepseek
          model: deepseek-chat
          temperature: 0.7
        tools:
          - read_file
          - shell
        skills:
          - ops-skill
        mcp_servers:
          - github-mcp
        channels:
          - cli
        notify_channels:
          - webhook-ops
        schedules:
          - cron: "0 9 * * *"
            message: 早安巡检
        bootstrap:
          - AGENTS.md
        settings:
          max_iterations: 10
          max_history_turns: 20
        """);

    List<Profile> loaded = loader.load(profilesDir);

    assertThat(loaded).hasSize(1);
    Profile profile = loaded.get(0);
    assertThat(profile.name()).isEqualTo("ops-agent");
    assertThat(profile.description()).isEqualTo("运维助手");
    assertThat(profile.identity().agentName()).isEqualTo("运维小欧");
    assertThat(profile.identity().prompt()).isEqualTo("你是一个专业的运维助手");
    assertThat(profile.provider().name()).isEqualTo("deepseek");
    assertThat(profile.provider().model()).isEqualTo("deepseek-chat");
    assertThat(profile.provider().temperature()).isEqualTo(0.7);
    assertThat(profile.tools()).containsExactly("read_file", "shell");
    assertThat(profile.skills()).containsExactly("ops-skill");
    assertThat(profile.mcpServers()).containsExactly("github-mcp");
    assertThat(profile.channels()).containsExactly("cli");
    assertThat(profile.notifyChannels()).containsExactly("webhook-ops");
    assertThat(profile.schedules()).hasSize(1);
    assertThat(profile.schedules().get(0)).containsEntry("cron", "0 9 * * *");
    assertThat(profile.bootstrap()).containsExactly("AGENTS.md");
    assertThat(profile.settings()).containsEntry("max_iterations", 10);
  }

  @Test
  @DisplayName("引用不存在的provider_报错清晰且不阻断其余加载")
  void unknownProviderReferenced_reportsClearlyAndSkips() throws IOException {
    writeYaml(
        "bad-ref.yaml",
        """
        name: bad-ref
        provider:
          name: no-such-provider
          model: whatever
        """);
    writeYaml(
        "good.yaml",
        """
        name: good
        provider:
          name: kimi
          model: moonshot-v1
        """);

    List<Profile> loaded = loader.load(profilesDir);

    // 坏的记错误日志跳过，好的照常加载
    assertThat(loaded).hasSize(1);
    assertThat(loaded.get(0).name()).isEqualTo("good");
    assertThat(logCapture.list)
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getFormattedMessage()).contains("no-such-provider");
            });
  }

  @Test
  @DisplayName("坏文件不阻断_其余Profile照常加载")
  void brokenFile_skippedWithoutBlockingOthers() throws IOException {
    writeYaml("broken.yaml", ":\n  - ][ not valid yaml");
    writeYaml(
        "good.yaml",
        """
        name: good
        provider:
          name: deepseek
          model: deepseek-chat
        """);

    List<Profile> loaded = loader.load(profilesDir);

    assertThat(loaded).hasSize(1);
    assertThat(loaded.get(0).name()).isEqualTo("good");
    assertThat(logCapture.list).anyMatch(event -> event.getLevel() == Level.ERROR);
  }

  @Test
  @DisplayName("ENV占位_从环境变量解析")
  void envPlaceholder_resolvedFromEnvironment() throws IOException {
    writeYaml(
        "with-env.yaml",
        """
        name: with-env
        description: key is ${TEST_ENV_VALUE}
        provider:
          name: deepseek
          model: deepseek-chat
        """);

    List<Profile> loaded = loader.load(profilesDir);

    assertThat(loaded).hasSize(1);
    assertThat(loaded.get(0).description()).isEqualTo("key is resolved-key");
  }

  @Test
  @DisplayName("ENV占位对应变量缺失_记错误日志跳过该Profile")
  void missingEnvVariable_loggedAndSkipped() throws IOException {
    writeYaml(
        "missing-env.yaml",
        """
        name: missing-env
        description: key is ${NO_SUCH_VAR}
        provider:
          name: deepseek
          model: deepseek-chat
        """);

    List<Profile> loaded = loader.load(profilesDir);

    // 不静默当空串跑过去
    assertThat(loaded).isEmpty();
    assertThat(logCapture.list)
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getFormattedMessage()).contains("NO_SUCH_VAR");
            });
  }

  private void writeYaml(String fileName, String content) throws IOException {
    Files.writeString(profilesDir.resolve(fileName), content);
  }
}
