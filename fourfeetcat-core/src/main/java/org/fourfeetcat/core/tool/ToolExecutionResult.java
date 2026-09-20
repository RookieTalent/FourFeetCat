package org.fourfeetcat.core.tool;

/**
 * 一次工具执行的结果（第17节的前向契约）。
 *
 * <p>四个字段与第20节的 {@code ToolResult} 逐字对齐（success / content / errorMessage / retryable），届时按名
 * 合并。{@code retryable} 让循环拿到失败结果时能判断这错值不值得再调一次。
 */
public record ToolExecutionResult(
    boolean success, String content, String errorMessage, boolean retryable) {

  public static ToolExecutionResult success(String content) {
    return new ToolExecutionResult(true, content, null, false);
  }

  /** 失败结果照样子回填进对话（原因进上下文，模型能据此换招），不是抛异常。 */
  public static ToolExecutionResult failure(String errorMessage, boolean retryable) {
    return new ToolExecutionResult(false, null, errorMessage, retryable);
  }
}
