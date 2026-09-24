package org.fourfeetcat.core.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 统一工具抽象（第20节课件）：不管工具从哪来，都长成这个样子，执行入口只跟它打交道。
 *
 * <p>三种来源——底座自带的内置 Tool、业务方用 {@code @Tool} 注解写的 Java 方法、通过 MCP 接进来的外部工具——都被包装成本接口 的实例注册进工具表。{@code
 * ToolExecutor} 因此完全不感知工具的来源，不为来源分支：这是"加一个新工具的成本恒定在写一个类" 成立的前提。
 *
 * <p>四个方法缺一不可，尤其 {@link #getInputSchema()}：早期实现常只顾着三个"看得见"的方法把它漏掉，漏了就没地方把工具翻译成 模型可读的参数说明——那一步会直接卡死。
 */
public interface CatTool {

  /** 工具名：模型靠它点名要调谁；在工具表内唯一。 */
  String getName();

  /** 干什么用的：给模型看的说明，模型据此决定调不调它。 */
  String getDescription();

  /** 参数长什么样：JSON Schema 文本，与 {@code ToolDescriptor.inputSchema} 同一口径（不另造 schema 类型）。 */
  String getInputSchema();

  /**
   * 真正执行。
   *
   * <p>涉外工具（文件 / 命令 / 网络）的第一行必须过沙箱校验位，校验不过即抛异常、动作不发生。执行成败不在这里落审计—— 那是 {@code ToolExecutor}
   * 唯一的职责，多写一处就多一条绕过审计的路径。
   *
   * @param input 模型给出的调用参数（已解析成树）
   */
  ToolResult execute(JsonNode input);
}
