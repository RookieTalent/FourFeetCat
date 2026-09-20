package org.fourfeetcat.core.react;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.fourfeetcat.core.ToolDescriptor;
import org.fourfeetcat.core.tool.ToolExecutionResult;
import org.fourfeetcat.core.tool.ToolInvocationRecorder;
import org.fourfeetcat.core.tool.ToolTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

class ToolExecutorTest {

  private static final String SESSION_ID = "cli:u-1:ops-agent";

  private ToolTable toolTable;
  private ToolInvocationRecorder audit;
  private ToolExecutor executor;

  @BeforeEach
  void setUp() {
    toolTable = mock(ToolTable.class);
    audit = mock(ToolInvocationRecorder.class);
    executor = new ToolExecutor(toolTable, audit);
  }

  @Test
  @DisplayName("执行成功_审计落success为true并带工具名入参结果耗时")
  void executionSuccess_auditsSuccessWithNameInputAndResult() {
    when(toolTable.execute("http_get", "{\"url\":\"https://example.com\"}"))
        .thenReturn(ToolExecutionResult.success("{\"temp\":10}"));

    ToolExecutionResult result =
        executor.execute(SESSION_ID, call("http_get", "{\"url\":\"https://example.com\"}"));

    assertThat(result.success()).isTrue();
    assertThat(result.content()).isEqualTo("{\"temp\":10}");
    verify(audit)
        .record(
            eq(SESSION_ID),
            eq("http_get"),
            eq("{\"url\":\"https://example.com\"}"),
            eq("{\"temp\":10}"),
            eq(true),
            isNull(),
            anyLong());
  }

  @Test
  @DisplayName("执行失败_审计也落一条success为false并带原因")
  void executionFailure_auditsSuccessFalseWithReason() {
    when(toolTable.execute(any(), any())).thenReturn(ToolExecutionResult.failure("连接超时", true));

    ToolExecutionResult result = executor.execute(SESSION_ID, call("http_get", "{}"));

    assertThat(result.success()).isFalse();
    assertThat(result.retryable()).isTrue();
    // 失败不留痕，一次真实事故在系统里就完全没痕迹——这是最容易被漏掉的一条
    verify(audit)
        .record(
            eq(SESSION_ID), eq("http_get"), eq("{}"), isNull(), eq(false), eq("连接超时"), anyLong());
  }

  @Test
  @DisplayName("工具表抛异常_原因进审计与失败结果_异常不吞")
  void tableThrows_reasonGoesToAuditAndResult() {
    when(toolTable.execute(any(), any())).thenThrow(new IllegalStateException("工具表不可用"));

    ToolExecutionResult result = executor.execute(SESSION_ID, call("http_get", "{}"));

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("工具表不可用");
    verify(audit)
        .record(
            eq(SESSION_ID),
            eq("http_get"),
            eq("{}"),
            isNull(),
            eq(false),
            contains("工具表不可用"),
            anyLong());
  }

  @Test
  @DisplayName("工具描述清单_原样转交工具表")
  void descriptors_delegatesToToolTable() {
    ToolDescriptor descriptor = new ToolDescriptor("http_get", "发 GET", "{}");
    when(toolTable.descriptors(List.of("http_get"))).thenReturn(List.of(descriptor));

    assertThat(executor.descriptors(List.of("http_get"))).containsExactly(descriptor);
  }

  private static AssistantMessage.ToolCall call(String name, String arguments) {
    return new AssistantMessage.ToolCall("call-1", "function", name, arguments);
  }
}
