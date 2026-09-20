package org.fourfeetcat.core.react;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.profile.Profile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class ContextLoaderTest {

  @TempDir private Path workspace;

  private ContextLoader loader;

  @BeforeEach
  void setUp() {
    loader = new ContextLoader(workspace);
  }

  @Test
  @DisplayName("改文件后下一次组装立即读到新内容_不缓存")
  void fileChanged_nextLoadSeesNewContent() throws IOException {
    Files.writeString(workspace.resolve("AGENTS.md"), "第一版说明");
    String first = loader.load(profile(List.of("AGENTS.md"), List.of()));

    Files.writeString(workspace.resolve("AGENTS.md"), "第二版说明");
    String second = loader.load(profile(List.of("AGENTS.md"), List.of()));

    assertThat(first).contains("第一版说明");
    assertThat(second).contains("第二版说明").doesNotContain("第一版说明");
  }

  @Test
  @DisplayName("Profile显式引用的Skill缺失_报错并指明引用的名字")
  void missingSkill_throwsWithReferencedName() {
    assertThatThrownBy(() -> loader.load(profile(List.of(), List.of("weather-skill"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("weather-skill");
  }

  @Test
  @DisplayName("Bootstrap文件缺失_告警但不阻断其余文件加载")
  void missingBootstrap_warnsAndContinues() throws IOException {
    Files.writeString(workspace.resolve("SOUL.md"), "人格：沉稳克制");
    ListAppender<ILoggingEvent> appender = attachAppender();

    String text = loader.load(profile(List.of("MISSING.md", "SOUL.md"), List.of()));

    assertThat(text).contains("人格：沉稳克制");
    // 静默跳过会造成"人格悄悄丢了"这种最难查的软故障，至少要留一条告警
    assertThat(appender.list).anyMatch(event -> event.getLevel() == Level.WARN);
  }

  @Test
  @DisplayName("绑定的Skill_注入名称描述与本地绝对读取路径_不预载正文")
  void boundSkill_injectsMetadataAndAbsolutePathOnly() throws IOException {
    Path skillDir = Files.createDirectories(workspace.resolve("skills").resolve("weather"));
    Files.writeString(
        skillDir.resolve("SKILL.md"), "---\nname: weather\ndescription: 查询天气\n---\n\n正文不该被预载进来");

    String text = loader.load(profile(List.of(), List.of("weather")));

    assertThat(text)
        .contains("weather")
        .contains("查询天气")
        .contains(skillDir.resolve("SKILL.md").toAbsolutePath().toString());
    // 渐进披露：正文与附属资源由模型按需读，组装 prompt 时不预载
    assertThat(text).doesNotContain("正文不该被预载进来");
  }

  @Test
  @DisplayName("Bootstrap清单为空_不报错")
  void emptyBootstrap_isNotAnError() {
    assertThat(loader.load(profile(List.of(), List.of()))).isEmpty();
  }

  private static ListAppender<ILoggingEvent> attachAppender() {
    Logger logger = (Logger) LoggerFactory.getLogger(ContextLoader.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private static Profile profile(List<String> bootstrap, List<String> skills) {
    return new Profile(
        "ops-agent",
        "运维助手",
        new Profile.Identity("运维小欧", "你是一个专业的运维助手"),
        new Profile.ProviderConfig("deepseek", "deepseek-chat", 0.7),
        List.of(),
        skills,
        List.of(),
        List.of("cli"),
        List.of(),
        List.of(),
        bootstrap,
        Map.of());
  }
}
