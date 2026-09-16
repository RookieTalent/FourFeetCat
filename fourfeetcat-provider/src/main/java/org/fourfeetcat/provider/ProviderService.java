package org.fourfeetcat.provider;

import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.LlmCallRecorder;
import org.fourfeetcat.core.ToolDescriptor;
import org.fourfeetcat.core.profile.Profile;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * Provider 前台：Agent 与大模型之间的统一抽象层。按 Profile 声明的 provider 名从显式映射表 （宪法原则三）取
 * ChatModel，组装一次调用、成败都落审计（宪法原则五）。
 *
 * <p>职责刻意划窄：挑对模型、发起一次调用、把结果拿回来。循环、工具执行、上下文都不归它管。
 */
public class ProviderService {

  private final Map<String, ChatModel> providerMap;
  private final ToolSchemaAdapter adapter;
  private final LlmCallRecorder audit;

  public ProviderService(
      Map<String, ChatModel> providerMap, ToolSchemaAdapter adapter, LlmCallRecorder audit) {
    this.providerMap = Map.copyOf(providerMap);
    this.adapter = adapter;
    this.audit = audit;
  }

  public ChatResponse chat(
      String sessionId, Profile profile, List<ToolDescriptor> tools, Prompt prompt) {
    Profile.ProviderConfig providerConfig = profile.provider();
    ChatModel model = providerMap.get(providerConfig.name());
    if (model == null) {
      throw new ProviderNotFoundException(providerConfig.name());
    }

    // 坑二：Spring AI 的自动工具执行内嵌在 ChatModel 实现层且默认开启，必须在此唯一构建点
    // 显式关闭；关闭后响应里的工具调用请求原样交回上层，由 ReActLoop + ToolExecutor 执行。
    ToolCallingChatOptions options =
        ToolCallingChatOptions.builder()
            .model(providerConfig.model())
            .temperature(providerConfig.temperature())
            .toolCallbacks(adapter.toSpringAiTools(tools))
            .internalToolExecutionEnabled(false)
            .build();

    long startedAt = System.currentTimeMillis();
    try {
      ChatResponse response = model.call(new Prompt(prompt.getInstructions(), options));
      audit.record(
          sessionId,
          providerConfig.name(),
          providerConfig.model(),
          response.getMetadata().getUsage(),
          true,
          null,
          System.currentTimeMillis() - startedAt);
      return response;
    } catch (RuntimeException e) {
      // 调用失败也留痕，再把错误抛给上层处理
      audit.record(
          sessionId,
          providerConfig.name(),
          providerConfig.model(),
          null,
          false,
          e.getMessage(),
          System.currentTimeMillis() - startedAt);
      throw e;
    }
  }
}
