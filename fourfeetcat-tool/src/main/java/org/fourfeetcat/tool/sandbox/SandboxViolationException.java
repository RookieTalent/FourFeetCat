package org.fourfeetcat.tool.sandbox;

/**
 * 沙箱校验不通过（第20节前向接口）。
 *
 * <p>抛它就等于动作被拦下——工具里但凡发出这个异常，真正的 IO **根本没发生**。它不单独落库：{@code ToolExecutor} 已有的 失败审计路径会把它记成一条 {@code
 * success=false} 的 {@code tool_invocations}，原因取自本异常的 message。
 */
public class SandboxViolationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SandboxViolationException(String message) {
    super(message);
  }
}
