package org.fourfeetcat.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 免 key 的脚本化 provider（第27节）：挂在显式映射表的 {@code mock} 名下，不连任何真实模型、不需要 key。
 *
 * <p>全链路里只有"模型"是假的——ReActLoop / ToolExecutor / Memory / Session / 审计走的是与真实对话一模一样 的路径，这是把"全链路能不能跑通"
 * 搬进 gate 而无 key 可用的前提（宪法原则三的 mock 槽位）。
 *
 * <p>按脚本驱动一次确定性的 ReAct：prompt 里还没有工具结果时，请求一次 {@code save_memory}（内容取最后一条用户消息、 剥掉"记住"前缀——
 * 让"记住…"这句真写进 MEMORY.md，给全链路一个可观测的落盘行为）；一旦看见工具结果（PromptBuilder 已把执行 结果渲染回对话），直接给最终答复收尾。 两轮各自带一个非零
 * usage，llm_calls 因此能记到不为零的 token 数。
 */
public final class MockChatModel implements ChatModel {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** mock 固定驱动的那个工具（第22节内置记忆工具）。 */
  static final String TOOL_NAME = "save_memory";

  @Override
  public ChatResponse call(Prompt prompt) {
    boolean sawToolResult =
        prompt.getInstructions().stream()
            .anyMatch(message -> message instanceof ToolResponseMessage);
    String content = rememberContent(prompt);
    AssistantMessage output =
        sawToolResult
            ? new AssistantMessage("已记住：" + content)
            : AssistantMessage.builder()
                .content("")
                .toolCalls(
                    List.of(
                        new AssistantMessage.ToolCall(
                            "mock-1", "function", TOOL_NAME, saveMemoryArgs(content))))
                .build();
    return new ChatResponse(List.of(new Generation(output)), metadataWithUsage());
  }

  /** 最后一条用户消息里要记的事："记住：xxx" → xxx；不带前缀就整句当内容。 */
  private static String rememberContent(Prompt prompt) {
    List<Message> messages = prompt.getInstructions();
    for (int i = messages.size() - 1; i >= 0; i--) {
      if (messages.get(i) instanceof UserMessage user) {
        return user.getText().replaceFirst("^记住[:：]?\\s*", "").trim();
      }
    }
    return "测试记忆";
  }

  private static String saveMemoryArgs(String content) {
    Map<String, String> args = new LinkedHashMap<>();
    args.put("content", content);
    args.put("scope", "core");
    try {
      return JSON.writeValueAsString(args);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("mock save_memory 参数序列化失败", e);
    }
  }

  /** 元数据带非零 usage：审计走 {@code response.getMetadata().getUsage()}，null 会记成 0 token。 */
  private static ChatResponseMetadata metadataWithUsage() {
    Usage usage =
        new Usage() {
          @Override
          public Integer getPromptTokens() {
            return 42;
          }

          @Override
          public Integer getCompletionTokens() {
            return 7;
          }

          @Override
          public Integer getTotalTokens() {
            return 49;
          }

          @Override
          public Object getNativeUsage() {
            return null;
          }
        };
    return ChatResponseMetadata.builder().usage(usage).build();
  }
}
