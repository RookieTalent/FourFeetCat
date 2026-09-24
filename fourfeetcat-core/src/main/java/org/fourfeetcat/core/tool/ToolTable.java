package org.fourfeetcat.core.tool;

import java.util.List;
import org.fourfeetcat.core.ToolDescriptor;

/**
 * 工具表（第17节课件原词）：从表里按名取工具描述、按名执行。
 *
 * <p>本节是最小占位；第20节立起统一工具抽象（{@link CatTool}）与真实注册表后，两个方法**语义一字不变**，实现方换成 {@code fourfeetcat-tool} 的
 * {@code ToolRegistry}。
 *
 * <p>这个端口本身留在 core 不是凑合：{@code ToolExecutor} 在 core，而注册表按技术方案 §10 归 tool 模块——core 直接依赖 注册表就会与
 * {@code tool → core} 成环。端口留在这里，依赖方向才对。
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
  ToolResult execute(String toolName, String inputJson);
}
