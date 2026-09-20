package org.fourfeetcat.core.react;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

class PromptBuilderTest {

  private ContextLoader contextLoader;
  private Session session;

  @BeforeEach
  void setUp() {
    contextLoader = mock(ContextLoader.class);
    when(contextLoader.load(any())).thenReturn("启动信息：项目说明");
    session = new Session("cli:u-1:ops-agent", "ops-agent", "cli", "u-1");
  }

  @Test
  @DisplayName("四部分按固定顺序组装_角色与启动信息在最前_记忆次之_历史最后")
  void parts_inFixedOrder() {
    session.appendUserMessage("今天穿什么");
    PromptBuilder builder = new PromptBuilder(contextLoader, profile -> "用户偏好：只穿深色");

    Prompt prompt = builder.build(session, profile(20));

    List<Message> instructions = prompt.getInstructions();
    assertThat(instructions).hasSize(3);
    String system = instructions.get(0).getText();
    assertThat(system).contains("运维小欧").contains("你是一个专业的运维助手").contains("启动信息：项目说明");
    assertThat(instructions.get(1).getText()).isEqualTo("用户偏好：只穿深色");
    assertThat(instructions.get(2)).isInstanceOf(UserMessage.class);
  }

  @Test
  @DisplayName("system prompt 末尾附当前日期时间")
  void systemPrompt_endsWithCurrentDateTime() {
    PromptBuilder builder = new PromptBuilder(contextLoader);

    String system = builder.build(session, profile(20)).getInstructions().get(0).getText();

    // 模型自己不知道今天几号，定时场景的"今天"全靠这一行——必须在末尾
    String trimmed = system.trim();
    String lastLine = trimmed.substring(trimmed.lastIndexOf('\n') + 1);
    assertThat(lastLine).startsWith("当前时间：").contains(LocalDate.now().toString());
  }

  @Test
  @DisplayName("未启用长期记忆_该部分整体跳过不占位不报错")
  void memoryDisabled_skipsMemoryPart() {
    session.appendUserMessage("在吗");
    PromptBuilder builder = new PromptBuilder(contextLoader);

    List<Message> instructions = builder.build(session, profile(20)).getInstructions();

    assertThat(instructions).hasSize(2);
    assertThat(instructions.get(0)).isInstanceOf(SystemMessage.class);
    assertThat(instructions.get(1)).isInstanceOf(UserMessage.class);
  }

  @Test
  @DisplayName("历史超过上限_只留最近N轮")
  void historyBeyondLimit_keepsRecentTurnsOnly() {
    for (int turn = 1; turn <= 25; turn++) {
      session.appendUserMessage("第" + turn + "轮");
      session.appendAssistantMessage(new AssistantMessage("答" + turn));
    }
    PromptBuilder builder = new PromptBuilder(contextLoader);

    List<Message> instructions = builder.build(session, profile(20)).getInstructions();

    // 1 条 system + 20 轮历史（每轮 user + assistant）
    assertThat(instructions).hasSize(1 + 20 * 2);
    assertThat(instructions.get(1).getText()).isEqualTo("第6轮");
    assertThat(instructions.get(instructions.size() - 1).getText()).isEqualTo("答25");
  }

  @Test
  @DisplayName("历史正好等于上限_不截断")
  void historyExactlyAtLimit_isNotTrimmed() {
    for (int turn = 1; turn <= 20; turn++) {
      session.appendUserMessage("第" + turn + "轮");
      session.appendAssistantMessage(new AssistantMessage("答" + turn));
    }
    PromptBuilder builder = new PromptBuilder(contextLoader);

    List<Message> instructions = builder.build(session, profile(20)).getInstructions();

    assertThat(instructions).hasSize(1 + 20 * 2);
    assertThat(instructions.get(1).getText()).isEqualTo("第1轮");
  }

  @Test
  @DisplayName("截断切点落在用户消息之前_不切断工具调用与回填")
  void trimming_cutsBeforeUserMessage_keepsToolRoundIntact() {
    session.appendUserMessage("第一轮");
    session.appendAssistantMessage(new AssistantMessage("答一"));
    session.appendUserMessage("第二轮");
    session.appendAssistantMessage(new AssistantMessage("答二"));

    List<Message> kept =
        new PromptBuilder(contextLoader).build(session, profile(1)).getInstructions();

    // 只留最近 1 轮：切点必须落在"第二轮"这条用户消息上，而不是切出半截助手/工具消息
    assertThat(kept).hasSize(3);
    assertThat(((UserMessage) kept.get(1)).getText()).isEqualTo("第二轮");
    assertThat(kept.get(2)).isInstanceOf(AssistantMessage.class);
  }

  @Test
  @DisplayName("上下文组装器每轮现拼_同样输入两次结果一致且互不干扰")
  void build_isStatelessPerCall() {
    session.appendUserMessage("在吗");
    PromptBuilder builder = new PromptBuilder(contextLoader);

    assertThat(builder.build(session, profile(20)).getInstructions())
        .hasSize(builder.build(session, profile(20)).getInstructions().size());
  }

  static Profile profile(int maxHistoryTurns) {
    return new Profile(
        "ops-agent",
        "运维助手",
        new Profile.Identity("运维小欧", "你是一个专业的运维助手"),
        new Profile.ProviderConfig("deepseek", "deepseek-chat", 0.7),
        List.of("http_get"),
        List.of(),
        List.of(),
        List.of("cli"),
        List.of(),
        List.of(),
        List.of("AGENTS.md"),
        Map.of("max_iterations", 10, "max_history_turns", maxHistoryTurns));
  }
}
