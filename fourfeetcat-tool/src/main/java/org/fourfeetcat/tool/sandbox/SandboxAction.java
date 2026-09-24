package org.fourfeetcat.tool.sandbox;

/**
 * 一次待校验的涉外动作（第20节前向接口）：动作类型 + 目标。
 *
 * <p>目标是路径、可执行文件名还是完整 URL，由 {@link ActionType} 决定怎么解释——本记录不携带任何某一档实现特有的字段。
 */
public record SandboxAction(ActionType type, String target) {

  public SandboxAction {
    if (type == null) {
      throw new IllegalArgumentException("校验动作必须有类型");
    }
  }
}
