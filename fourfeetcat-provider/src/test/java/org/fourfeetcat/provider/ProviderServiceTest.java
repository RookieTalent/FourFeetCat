package org.fourfeetcat.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.LlmCallRecorder;
import org.fourfeetcat.core.profile.Profile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

class ProviderServiceTest {

  private ChatModel deepseek;
  private ChatModel kimi;
  private ToolSchemaAdapter adapter;
  private LlmCallRecorder audit;
  private ProviderService service;

  @BeforeEach
  void setUp() {
    deepseek = mock(ChatModel.class);
    kimi = mock(ChatModel.class);
    adapter = mock(ToolSchemaAdapter.class);
    audit = mock(LlmCallRecorder.class);
    when(adapter.toSpringAiTools(any())).thenReturn(List.of());
    service = new ProviderService(Map.of("deepseek", deepseek, "kimi", kimi), adapter, audit);
  }

  @Test
  @DisplayName("按名路由_两个provider不串台")
  void routeByName_twoProvidersNoCrossTalk() {
    ChatResponse response = successfulResponse();
    when(kimi.call(any(Prompt.class))).thenReturn(response);

    service.chat("s-1", profileUsing("kimi"), List.of(), prompt());

    // 调的是 kimi；deepseek 一次都没被碰——"不串台"的直接证据
    verify(kimi, times(1)).call(any(Prompt.class));
    verify(deepseek, never()).call(any(Prompt.class));
  }

  @Test
  @DisplayName("引用未知名的provider_直接报错不悄悄用错")
  void unknownProvider_throwsWithProviderName() {
    assertThatThrownBy(() -> service.chat("s-1", profileUsing("nope"), List.of(), prompt()))
        .isInstanceOf(ProviderNotFoundException.class)
        .hasMessageContaining("nope");

    // 没挑到模型就报错，不发调用、不留审计假象
    verify(deepseek, never()).call(any(Prompt.class));
    verify(kimi, never()).call(any(Prompt.class));
    verify(audit, never()).record(any(), any(), any(), any(), anyBoolean(), any(), anyLong());
  }

  @Test
  @DisplayName("调用失败_审计必须留下success为false的记录")
  void callFailure_auditRecordsFailureWithReason() {
    when(deepseek.call(any(Prompt.class))).thenThrow(new RuntimeException("connect timeout"));

    // 异常继续上抛
    assertThatThrownBy(() -> service.chat("s-1", profileUsing("deepseek"), List.of(), prompt()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("connect timeout");

    // 但审计先落了账：success=false + 原因
    verify(audit)
        .record(
            eq("s-1"),
            eq("deepseek"),
            eq("test-model"),
            isNull(),
            eq(false),
            org.mockito.ArgumentMatchers.contains("timeout"),
            anyLong());
  }

  @Test
  @DisplayName("调用成功_审计记录token用量")
  void callSuccess_auditRecordsUsage() {
    Usage usage = mock(Usage.class);
    ChatResponse response = successfulResponse(usage);

    when(deepseek.call(any(Prompt.class))).thenReturn(response);

    ChatResponse result = service.chat("s-1", profileUsing("deepseek"), List.of(), prompt());

    assertThat(result).isSameAs(response);
    verify(audit)
        .record(
            eq("s-1"), eq("deepseek"), eq("test-model"), eq(usage), eq(true), isNull(), anyLong());
  }

  @Test
  @DisplayName("带工具schema调用_请求里关闭了自动执行")
  void callWithToolSchema_disablesAutoExecution() {
    // 坑二的回归测试：用真实适配器翻译工具，一旦有人改回自动执行，这里立刻红
    ProviderService realAdapterService =
        new ProviderService(Map.of("deepseek", deepseek), new ToolSchemaAdapter(), audit);
    ChatResponse response = successfulResponse();
    when(deepseek.call(any(Prompt.class))).thenReturn(response);

    realAdapterService.chat(
        "s-1",
        profileUsing("deepseek"),
        List.of(
            new org.fourfeetcat.core.ToolDescriptor(
                "http_get", "发起 HTTP GET 请求", "{\"type\":\"object\"}")),
        prompt());

    ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
    verify(deepseek).call(captor.capture());
    var options = captor.getValue().getOptions();

    // 自动执行必须显式关闭（Spring AI 默认开启）
    assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(options)).isFalse();
    // 翻译过的 schema 确实带上了
    assertThat(((ToolCallingChatOptions) options).getToolCallbacks()).isNotEmpty();
  }

  private Profile profileUsing(String provider) {
    return new Profile(
        "test-agent",
        null,
        null,
        new Profile.ProviderConfig(provider, "test-model", 0.7),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private Prompt prompt() {
    return new Prompt("今天天气怎么样");
  }

  private ChatResponse successfulResponse() {
    return successfulResponse(mock(Usage.class));
  }

  private ChatResponse successfulResponse(Usage usage) {
    ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
    when(metadata.getUsage()).thenReturn(usage);
    ChatResponse response = mock(ChatResponse.class);
    when(response.getMetadata()).thenReturn(metadata);
    return response;
  }
}
