package org.fourfeetcat.core.react;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.ToolDescriptor;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.core.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class ReActLoopTest {

  private static final String SESSION_ID = "cli:u-1:ops-agent";

  private PromptBuilder promptBuilder;
  private LlmCaller llmCaller;
  private ToolExecutor toolExecutor;
  private ReActLoop loop;
  private Session session;

  @BeforeEach
  void setUp() {
    promptBuilder = mock(PromptBuilder.class);
    llmCaller = mock(LlmCaller.class);
    toolExecutor = mock(ToolExecutor.class);
    when(promptBuilder.build(any(), any())).thenReturn(new Prompt("这一轮的上下文"));
    when(toolExecutor.descriptors(anyList())).thenReturn(List.of(httpGetDescriptor()));
    loop = new ReActLoop(promptBuilder, llmCaller, toolExecutor);
    session = new Session(SESSION_ID, "ops-agent", "cli", "u-1");
  }

  @Test
  @DisplayName("没有工具调用_一轮就收尾")
  void plainAnswer_finishesInOneRound() {
    when(llmCaller.chat(any(), any(), any(), any())).thenReturn(textResponse("今天晴，穿短袖"));

    String reply = loop.run(session, "今天穿什么", profile(10));

    assertThat(reply).isEqualTo("今天晴，穿短袖");
    // 模型没提工具，就一轮——不多转
    verify(llmCaller, times(1)).chat(eq(SESSION_ID), any(), any(), any());
    verify(toolExecutor, never()).execute(any(), any());
  }

  @Test
  @DisplayName("有工具调用_执行后回填进下一轮")
  void toolCall_executesToolAndFeedsResultIntoNextRound() {
    when(llmCaller.chat(any(), any(), any(), any()))
        .thenReturn(toolCallResponse(httpGetCall()), textResponse("10 度，穿外套"));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.success("{\"temp\":10}"));

    String reply = loop.run(session, "今天穿什么", profile(10));

    assertThat(reply).isEqualTo("10 度，穿外套");
    // 工具被执行一次、带的是本次会话 id（tool_invocations 靠它关联）
    verify(toolExecutor, times(1))
        .execute(eq(SESSION_ID), argThat(call -> "http_get".equals(call.name())));
    // 第二轮是在拿到工具结果之后发的，不是凭空再问一次
    verify(llmCaller, times(2)).chat(eq(SESSION_ID), any(), any(), any());
  }

  @Test
  @DisplayName("每轮响应和工具结果都累积进会话_可审计可续接")
  void eachRound_accumulatesResponseAndToolResult() {
    when(llmCaller.chat(any(), any(), any(), any()))
        .thenReturn(toolCallResponse(httpGetCall()), textResponse("10 度，穿外套"));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.success("{\"temp\":10}"));

    loop.run(session, "今天穿什么", profile(10));

    List<Message> messages = session.getMessages();
    assertThat(messages).hasSize(4);
    assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
    assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
    ToolResponseMessage toolMessage = (ToolResponseMessage) messages.get(2);
    assertThat(toolMessage.getResponses()).hasSize(1);
    assertThat(toolMessage.getResponses().get(0).name()).isEqualTo("http_get");
    assertThat(toolMessage.getResponses().get(0).responseData()).isEqualTo("{\"temp\":10}");
    assertThat(((AssistantMessage) messages.get(3)).getText()).isEqualTo("10 度，穿外套");
  }

  @Test
  @DisplayName("一次响应里多个工具调用_按顺序逐个执行")
  void multipleToolCalls_executedInOrder() {
    AssistantMessage.ToolCall first =
        new AssistantMessage.ToolCall("call-1", "function", "http_get", "{}");
    AssistantMessage.ToolCall second =
        new AssistantMessage.ToolCall("call-2", "function", "read_file", "{}");
    when(llmCaller.chat(any(), any(), any(), any()))
        .thenReturn(toolCallResponse(first, second), textResponse("好了"));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.success("{}"));

    loop.run(session, "一起查", profile(10));

    InOrder order = inOrder(toolExecutor);
    order.verify(toolExecutor).execute(SESSION_ID, first);
    order.verify(toolExecutor).execute(SESSION_ID, second);
    // 一次响应里的多个结果合成一条工具消息（分条追加会造成非法消息序列）
    assertThat(((ToolResponseMessage) session.getMessages().get(2)).getResponses()).hasSize(2);
  }

  @Test
  @DisplayName("工具执行失败_原因回填进对话让模型能接着处理")
  void toolFailure_failureReasonFeedsBackIntoConversation() {
    when(llmCaller.chat(any(), any(), any(), any()))
        .thenReturn(toolCallResponse(httpGetCall()), textResponse("那我看不了天气了"));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.failure("连接超时", true));

    String reply = loop.run(session, "今天穿什么", profile(10));

    assertThat(reply).isEqualTo("那我看不了天气了");
    ToolResponseMessage toolMessage = (ToolResponseMessage) session.getMessages().get(2);
    assertThat(toolMessage.getResponses().get(0).responseData()).isEqualTo("连接超时");
  }

  @Test
  @DisplayName("模型不带响应体也不带工具调用_返回空文本而不是崩")
  void responseWithoutTextAndToolCalls_returnsEmptyText() {
    when(llmCaller.chat(any(), any(), any(), any())).thenReturn(textResponse(""));

    assertThat(loop.run(session, "在吗", profile(10))).isEmpty();
  }

  @Test
  @DisplayName("会话 id 只由调用方给出_循环不做拼接")
  void sessionId_isOnlyPassedThrough() {
    when(llmCaller.chat(any(), any(), any(), any())).thenReturn(textResponse("好"));

    loop.run(session, "在吗", profile(10));

    verify(llmCaller).chat(eq("cli:u-1:ops-agent"), any(), any(), any());
  }

  @Test
  @DisplayName("Profile 声明的工具_原样交给 Provider 翻译")
  void declaredTools_passedToLlmCallerAsDescriptors() {
    when(llmCaller.chat(any(), any(), any(), any())).thenReturn(textResponse("好"));

    loop.run(session, "在吗", profile(10));

    verify(toolExecutor).descriptors(List.of("http_get"));
  }

  @Test
  @DisplayName("模型给出工具调用但工具表拒绝执行_异常上抛不静默")
  void toolTableThrows_propagatesInsteadOfSwallowing() {
    when(llmCaller.chat(any(), any(), any(), any())).thenReturn(toolCallResponse(httpGetCall()));
    when(toolExecutor.execute(any(), any())).thenThrow(new IllegalStateException("工具表不可用"));

    assertThatThrownBy(() -> loop.run(session, "今天穿什么", profile(10)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("工具表不可用");
  }

  @Test
  @DisplayName("模型一直要调工具_转满最大轮数强制停")
  void loopingToolCalls_stopsExactlyAtMaxIterations() {
    when(llmCaller.chat(any(), any(), any(), any()))
        .thenReturn(toolCallResponse(httpGetCall())); // 每轮都要调工具，永不收敛
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.success("{}"));

    String reply = loop.run(session, "查天气", profile(10));

    verify(llmCaller, times(10)).chat(eq(SESSION_ID), any(), any(), any()); // 恰好 10 轮，一轮不多
    assertThat(reply).contains("达到最大轮数");
  }

  @Test
  @DisplayName("最大轮数可配置_按配置生效")
  void maxIterations_isConfigurableFromProfile() {
    when(llmCaller.chat(any(), any(), any(), any())).thenReturn(toolCallResponse(httpGetCall()));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.success("{}"));

    loop.run(session, "查天气", profile(3));

    verify(llmCaller, times(3)).chat(eq(SESSION_ID), any(), any(), any());
  }

  @Test
  @DisplayName("最大轮数配置缺失或非法_回落到默认十轮")
  void invalidMaxIterations_fallsBackToDefault() {
    when(llmCaller.chat(any(), any(), any(), any())).thenReturn(toolCallResponse(httpGetCall()));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.success("{}"));

    loop.run(
        new Session(SESSION_ID, "ops-agent", "cli", "u-1"), "查天气", profileWithSettings(Map.of()));
    loop.run(
        new Session(SESSION_ID, "ops-agent", "cli", "u-1"),
        "查天气",
        profileWithSettings(Map.of("max_iterations", 0)));
    loop.run(
        new Session(SESSION_ID, "ops-agent", "cli", "u-1"),
        "查天气",
        profileWithSettings(Map.of("max_iterations", "十")));

    // 配置写错不该让循环不设上限
    verify(llmCaller, times(30)).chat(eq(SESSION_ID), any(), any(), any());
  }

  static Profile profile(int maxIterations) {
    return profileWithSettings(Map.of("max_iterations", maxIterations, "max_history_turns", 20));
  }

  static Profile profileWithSettings(Map<String, Object> settings) {
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
        settings);
  }

  static ToolDescriptor httpGetDescriptor() {
    return new ToolDescriptor("http_get", "发一个 GET 请求", "{\"type\":\"object\"}");
  }

  static AssistantMessage.ToolCall httpGetCall() {
    return new AssistantMessage.ToolCall(
        "call-1", "function", "http_get", "{\"url\":\"https://example.com/weather\"}");
  }

  static ChatResponse textResponse(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  static ChatResponse toolCallResponse(AssistantMessage.ToolCall... calls) {
    return new ChatResponse(
        List.of(
            new Generation(
                AssistantMessage.builder().content("").toolCalls(List.of(calls)).build())));
  }
}
