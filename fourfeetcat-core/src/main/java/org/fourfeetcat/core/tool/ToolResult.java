package org.fourfeetcat.core.tool;

/**
 * 一次工具执行的结果（第20节课件）。
 *
 * <p>由第17节交付的 {@code ToolExecutionResult} **按名合并**而来——那个类的 javadoc 原文即"四个字段与第20节的 {@code
 * ToolResult} 逐字对齐……届时按名合并"，四个字段与工厂语义一字未改。
 *
 * <p>{@code retryable} 让循环拿到失败结果时能判断这错值不值得再调一次。
 */
public record ToolResult(boolean success, String content, String errorMessage, boolean retryable) {

  public static ToolResult success(String content) {
    return new ToolResult(true, content, null, false);
  }

  /** 失败结果照样子回填进对话（原因进上下文，模型能据此换招），不是抛异常。 */
  public static ToolResult failure(String errorMessage, boolean retryable) {
    return new ToolResult(false, null, errorMessage, retryable);
  }
}
