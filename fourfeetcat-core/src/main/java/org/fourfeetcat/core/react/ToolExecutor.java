package org.fourfeetcat.core.react;

import java.util.List;
import org.fourfeetcat.core.ToolDescriptor;
import org.fourfeetcat.core.tool.ToolInvocationRecorder;
import org.fourfeetcat.core.tool.ToolResult;
import org.fourfeetcat.core.tool.ToolTable;
import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * 工具执行的唯一入口（第17节课件配角二）：查表 → 过检查 → 执行 → 成败都落审计。
 *
 * <p>执行权只在这一个地方发生——这也是第16节要关掉 Spring AI 自动执行的原因：多一条执行路径就会多一份绕过 审计与检查的调用。
 */
public class ToolExecutor {

  private final ToolTable toolTable;
  private final ToolInvocationRecorder audit;

  public ToolExecutor(ToolTable toolTable, ToolInvocationRecorder audit) {
    this.toolTable = toolTable;
    this.audit = audit;
  }

  /** 本轮可用的工具描述清单（转交工具表），供组装请求时翻译成模型可读的工具说明。 */
  public List<ToolDescriptor> descriptors(List<String> names) {
    return toolTable.descriptors(names);
  }

  public ToolResult execute(String sessionId, AssistantMessage.ToolCall call) {
    long startedAt = System.currentTimeMillis();
    ToolResult result;
    try {
      // 沙箱/白名单检查位：工具执行的唯一检查点，第24节 Sandbox 接线（宪法原则六）
      result = toolTable.execute(call.name(), call.arguments());
    } catch (RuntimeException e) {
      // 异常不吞：原因进审计、也进返回结果回填对话；一次工具失败不该炸掉整个循环
      result = ToolResult.failure(reasonOf(e), false);
    }
    audit.record(
        sessionId,
        call.name(),
        call.arguments(),
        result.content(),
        result.success(),
        result.errorMessage(),
        System.currentTimeMillis() - startedAt);
    return result;
  }

  /** 异常可能没有 message，兜底用其 toString：审计列里不能出现"原因不明"的空记录。 */
  private static String reasonOf(RuntimeException e) {
    return e.getMessage() == null ? e.toString() : e.getMessage();
  }
}
