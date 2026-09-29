package org.fourfeetcat.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.LlmCallRecorder;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.react.LlmCaller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * mock provider 脚本行为（第27节）：把"第一轮请求 save_memory、第二轮收尾"这条确定性脚本钉死， 全链路测试（MockProviderFlowTest /
 * MockAgentE2ETest）都以此为假屏幕——脚本变了，网关最先红。
 */
class MockChatModelTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  @DisplayName("还没有工具结果_请求一次 save_memory_内容剥掉记住前缀_分区 core")
  void noToolResult_requestsSaveMemoryWithStrippedContent() throws Exception {
    MockChatModel model = new MockChatModel();
    Prompt prompt = new Prompt(List.of(new SystemMessage("你是助手"), new UserMessage("记住：我喜欢喝美式咖啡")));

    ChatResponse response = model.call(prompt);

    AssistantMessage output = response.getResult().getOutput();
    assertThat(output.getToolCalls()).hasSize(1);
    AssistantMessage.ToolCall call = output.getToolCalls().get(0);
    assertThat(call.name()).isEqualTo("save_memory");
    JsonNode args = JSON.readTree(call.arguments());
    assertThat(args.path("content").asText()).isEqualTo("我喜欢喝美式咖啡");
    assertThat(args.path("scope").asText()).isEqualTo("core");
  }

  @Test
  @DisplayName("已见工具结果_直接给最终答复不再调工具")
  void afterToolResult_returnsFinalAnswer() {
    MockChatModel model = new MockChatModel();
    // 组装第二轮该有的一张 prompt：用户消息 + 第一轮的工具调用 + 它的执行结果
    AssistantMessage firstRound =
        AssistantMessage.builder()
            .content("")
            .toolCalls(
                List.of(
                    new AssistantMessage.ToolCall(
                        "mock-1", "function", "save_memory", "{\"content\":\"xxx\"}")))
            .build();
    ToolResponseMessage toolResult =
        ToolResponseMessage.builder()
            .responses(
                List.of(new ToolResponseMessage.ToolResponse("mock-1", "save_memory", "已记住")))
            .build();
    Prompt prompt = new Prompt(List.of(new UserMessage("记住：我喜欢喝美式咖啡"), firstRound, toolResult));

    ChatResponse response = model.call(prompt);

    AssistantMessage output = response.getResult().getOutput();
    assertThat(output.getToolCalls()).isEmpty();
    assertThat(output.getText()).isEqualTo("已记住：我喜欢喝美式咖啡");
  }

  @Test
  @DisplayName("元数据带非零 usage_审计才能记到不为零的 token")
  void usage_isNonZero() {
    MockChatModel model = new MockChatModel();
    ChatResponse response = model.call(new Prompt(List.of(new UserMessage("记住：x"))));

    Usage usage = response.getMetadata().getUsage();
    assertThat(usage).isNotNull();
    assertThat(usage.getPromptTokens()).isGreaterThan(0);
    assertThat(usage.getCompletionTokens()).isGreaterThan(0);
    assertThat(usage.getTotalTokens()).isGreaterThan(0);
  }

  @Test
  @DisplayName("mock 名声明_装配进显式映射表_并经前台一次调用通")
  void mockSpec_isWiredIntoExplicitMap() {
    ProviderConfiguration configuration = new ProviderConfiguration();
    LlmCallRecorder noop = (sid, p, m, u, s, e, d) -> {};
    LlmCaller service =
        configuration.providerService(
            new ProviderConfiguration.ProviderProperties(
                List.of(new ProviderConfiguration.ProviderSpec("mock", "http://mock.invalid", ""))),
            noop);

    Profile profile = mockProfile();
    ChatResponse response =
        service.chat("s-1", profile, List.of(), new Prompt(List.of(new UserMessage("记住：x"))));

    // 走真实 SpringAiProviderServiceImpl 前台：mock 的 save_memory 请求原样回到上层
    AssistantMessage output = response.getResult().getOutput();
    assertThat(output.getToolCalls()).hasSize(1);
    assertThat(output.getToolCalls().get(0).name()).isEqualTo("save_memory");
  }

  private static Profile mockProfile() {
    return new Profile(
        "mock-agent",
        "mock 测试 Agent",
        new Profile.Identity("mock助手", "测试用"),
        new Profile.ProviderConfig("mock", "mock-model", 0.7),
        List.of("save_memory"),
        List.of(),
        List.of(),
        List.of("cli"),
        List.of(),
        List.of(),
        List.of(),
        Map.of());
  }
}
