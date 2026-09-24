package org.fourfeetcat.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.ToolDescriptor;
import org.fourfeetcat.core.notify.NotifyChannelSource;
import org.fourfeetcat.core.react.ToolExecutor;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.core.tool.ToolInvocationRecorder;
import org.fourfeetcat.tool.registry.AnnotatedToolAdapter;
import org.fourfeetcat.tool.registry.ToolRegistry;
import org.fourfeetcat.tool.sandbox.ActionType;
import org.fourfeetcat.tool.sandbox.Sandbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * 通知工具的接线（第19节 harness 第二批 —— 那时登记为跨节任务，本节补齐）。
 *
 * <p>三条守点全部来自课件：渠道未配置要明确报错、渠道名缺省取第一个、**校验先于发送**。最后一条最要紧——顺序反了就是绕过白名单的 漏洞。
 */
class NotifyToolsTest {

  private final Sandbox sandbox = mock(Sandbox.class);
  private final NotifyChannelAdapter adapter = mock(NotifyChannelAdapter.class);

  @Test
  @DisplayName("渠道未配置_明确报错而不是静默成功")
  void noChannelConfigured_failsLoudly() {
    NotifyTools tools = new NotifyTools(sandbox, adapter, sourceOf());

    assertThatThrownBy(() -> tools.notify("你好", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未配置任何通知渠道");
  }

  @Test
  @DisplayName("渠道名缺省_取第一个渠道")
  void channelNameOmitted_usesFirstChannel() {
    NotifyTools tools =
        new NotifyTools(
            sandbox, adapter, sourceOf(channel("ops", "webhook", "https://ops.example/hook")));

    assertThat(tools.notify("日报", null)).isEqualTo("已推送");

    verify(adapter)
        .send(
            argThat(
                target ->
                    "webhook".equals(target.channelType())
                        && "https://ops.example/hook".equals(target.config().get("url"))),
            eq("日报"));
  }

  @Test
  @DisplayName("发送前必须先过白名单校验_顺序反了就是漏洞")
  void enforceHappensBeforeSend() {
    NotifyTools tools =
        new NotifyTools(
            sandbox, adapter, sourceOf(channel("ops", "webhook", "https://ops.example/hook")));

    tools.notify("hello", "ops");

    InOrder inOrder = inOrder(sandbox, adapter);
    inOrder.verify(sandbox).enforce(argThat(action -> action.type() == ActionType.HTTP_REQUEST));
    inOrder.verify(adapter).send(any(), eq("hello"));
  }

  @Test
  @DisplayName("渠道名给了但查不到_报错并指出名字")
  void unknownChannelName_failsWithTheName() {
    NotifyTools tools =
        new NotifyTools(
            sandbox, adapter, sourceOf(channel("ops", "webhook", "https://ops.example/hook")));

    assertThatThrownBy(() -> tools.notify("你好", "no-such-channel"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no-such-channel");
  }

  @Test
  @DisplayName("记忆工具就位时零改动：替身工具走通注册_过滤_执行_审计四步")
  void aFutureToolWalksTheWholePipelineUnchanged() {
    // 第22节的记忆工具落地时，走的就是这条已经跑通的管道——本用例证明它无需改动注册表
    ToolRegistry registry = new ToolRegistry();
    registry.registerAnnotated(
        new NotifyTools(
            sandbox, adapter, sourceOf(channel("ops", "webhook", "https://ops.example/hook"))));

    // ① 注册：以统一抽象身份进了表
    assertThat(registry.contains("notify")).isTrue();
    List<CatTool> adapted =
        AnnotatedToolAdapter.adapt(new NotifyTools(sandbox, adapter, sourceOf()));
    assertThat(adapted).hasSize(1);

    // ② 过滤：按 Agent 声明的工具名清单取子集，恰好相等
    assertThat(registry.descriptors(List.of("notify")))
        .extracting(ToolDescriptor::name)
        .containsExactly("notify");

    // ③ 执行 + ④ 审计：经 ToolExecutor 跑一次，成败都留痕
    ToolInvocationRecorder audit = mock(ToolInvocationRecorder.class);
    new ToolExecutor(registry, audit)
        .execute(
            "cli:wang:default",
            new AssistantMessage.ToolCall(
                "call-1", "function", "notify", "{\"content\":\"hi\",\"channel\":\"ops\"}"));

    verify(audit)
        .record(
            eq("cli:wang:default"),
            eq("notify"),
            anyString(),
            anyString(),
            eq(true),
            isNull(),
            anyLong());
  }

  private static NotifyChannelSource sourceOf(Map<String, String>... rows) {
    return () -> List.of(rows);
  }

  private static Map<String, String> channel(String name, String type, String url) {
    Map<String, String> row = new LinkedHashMap<>();
    row.put("name", name);
    row.put("type", type);
    row.put("url", url);
    row.put("description", null);
    return row;
  }
}
