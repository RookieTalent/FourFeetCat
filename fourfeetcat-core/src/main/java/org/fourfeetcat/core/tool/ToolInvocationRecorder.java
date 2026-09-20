package org.fourfeetcat.core.tool;

/**
 * 工具调用审计端口（依赖倒置）：core 的执行器经此留痕，storage 出实现、boot 装配。
 *
 * <p>成败都必须记（宪法原则五：审计 Day One 写入）——失败时 {@code resultJson} 为 null、{@code success} 记 false、{@code
 * errorMessage} 记原因。与第16节 {@code LlmCallRecorder} 同款手法。
 */
@FunctionalInterface
public interface ToolInvocationRecorder {

  void record(
      String sessionId,
      String toolName,
      String inputJson,
      String resultJson,
      boolean success,
      String errorMessage,
      long durationMs);
}
