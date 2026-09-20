package org.fourfeetcat.core.react;

import java.util.ArrayList;
import java.util.List;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.core.tool.ToolExecutionResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Agent 的调度内核（第17节课件主角、宪法原则一：自实现，不用框架的 Agent 抽象）。
 *
 * <p>只做三件事：转圈、判断这轮该不该停、把每轮结果累积回会话。拼上下文交 {@link PromptBuilder}、调模型交 {@link LlmCaller}、执行工具交 {@link
 * ToolExecutor}——循环里塞的东西越少，越好读、越不容易出 bug。
 */
public class ReActLoop {

  /** 转满上限的收尾信号（课件字面量）：让上层知道这次是被上限截断，而不是拿到了正常答复。 */
  static final String MAX_ITERATIONS_REPLY = "达到最大轮数，已停止";

  private final PromptBuilder promptBuilder;
  private final LlmCaller llmCaller;
  private final ToolExecutor toolExecutor;

  public ReActLoop(PromptBuilder promptBuilder, LlmCaller llmCaller, ToolExecutor toolExecutor) {
    this.promptBuilder = promptBuilder;
    this.llmCaller = llmCaller;
    this.toolExecutor = toolExecutor;
  }

  public String run(Session session, String userMessage, Profile profile) {
    session.appendUserMessage(userMessage);
    // 上限兜底：模型反复要调工具也转不出去，坑一（死循环）在这里拦住
    int maxIterations = ProfileSettings.maxIterations(profile);
    for (int iteration = 0; iteration < maxIterations; iteration++) {
      Prompt prompt = promptBuilder.build(session, profile);
      ChatResponse response =
          llmCaller.chat(
              session.getId(), profile, toolExecutor.descriptors(profile.tools()), prompt);
      AssistantMessage output = response.getResult().getOutput();
      // 先把响应存回会话：每轮都留痕，事后能审计（坑三）
      session.appendAssistantMessage(output);
      if (!output.hasToolCalls()) {
        // 模型这轮没提工具 = 它能给答复了，这就是停止条件
        return output.getText();
      }
      session.appendToolResponses(execute(session, output.getToolCalls()));
    }
    return MAX_ITERATIONS_REPLY;
  }

  /** 一次响应里的多个工具调用按顺序逐个执行（核心阶段不做并行，明确不做）。 */
  private List<ToolResponseMessage.ToolResponse> execute(
      Session session, List<AssistantMessage.ToolCall> toolCalls) {
    List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>(toolCalls.size());
    for (AssistantMessage.ToolCall call : toolCalls) {
      ToolExecutionResult result = toolExecutor.execute(session.getId(), call);
      responses.add(
          new ToolResponseMessage.ToolResponse(call.id(), call.name(), contentOf(result)));
    }
    return responses;
  }

  /** 失败也回填：原因进上下文，模型能据此换招，而不是撞上一堵没有信息的墙。 */
  private static String contentOf(ToolExecutionResult result) {
    return result.success() ? result.content() : result.errorMessage();
  }
}
