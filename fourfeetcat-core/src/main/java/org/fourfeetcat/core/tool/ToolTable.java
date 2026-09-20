package org.fourfeetcat.core.tool;

import java.util.List;
import org.fourfeetcat.core.ToolDescriptor;

/**
 * 工具表（第17节课件原词）：从表里按名取工具描述、按名执行。
 *
 * <p>本节的最小执行端口：第20节立统一工具抽象（{@code OryxTool} + {@code ToolRegistry}）后由它取代，两个方法的 语义不变。
 */
public interface ToolTable {

  /**
   * 按名单取工具描述，供组装请求时翻译成模型可读的工具说明。
   *
   * <p>名字查不到即失败报错——Profile 声明的工具不存在是配置错误，静默少给一个工具会让模型无从下手。
   */
  List<ToolDescriptor> descriptors(List<String> names);

  /**
   * 按名执行一次。
   *
   * @param inputJson 模型给出的调用参数（JSON 字符串原样，审计列 {@code input_json} 直接落它）
   */
  ToolExecutionResult execute(String toolName, String inputJson);
}
