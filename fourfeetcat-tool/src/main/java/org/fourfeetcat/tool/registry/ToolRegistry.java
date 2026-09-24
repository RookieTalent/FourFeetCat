package org.fourfeetcat.tool.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.ToolDescriptor;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.core.tool.ToolResult;
import org.fourfeetcat.core.tool.ToolTable;

/**
 * 工具注册表（第20节课件）：把三种来源的工具汇总成一张表，实现 core 侧的 {@link ToolTable} 端口。
 *
 * <p><b>执行权唯一。</b>本类是 {@code ToolExecutor} 唯一的下游——注册表拿到名字与入参、查出工具、执行、把结果原样返回；审计与 异常兜底都在 {@code
 * ToolExecutor} 那一层，这里不重复一份。多一条执行路径就多一份绕过审计与检查的调用。
 *
 * <p>顺序保持插入序（{@link LinkedHashMap}）：`tool list` 的输出稳定，便于人眼核对。
 */
public class ToolRegistry implements ToolTable {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final Map<String, CatTool> tools = new LinkedHashMap<>();

  /**
   * 注册一个工具。
   *
   * <p>重名**拒绝**而不是覆盖：静默覆盖会让"这次到底调了哪个"不可复现，配置写错时最难查。
   */
  public void register(CatTool tool) {
    CatTool existing = tools.putIfAbsent(tool.getName(), tool);
    if (existing != null) {
      throw new IllegalStateException("工具重名，拒绝覆盖: " + tool.getName());
    }
  }

  /**
   * 扫一个 Bean 上所有带 {@code @Tool} 注解的方法，逐个注册。
   *
   * <p>内置工具与业务方"方式三"的深度集成 Bean 都走这一条——加一个新工具就是"写一个类 + 这里多一行"，注册表、沙箱检查位、 审计、按清单过滤全都不用动。
   *
   * @return 本次注册的工具个数
   */
  public int registerAnnotated(Object beanWithToolMethods) {
    List<CatTool> tools = AnnotatedToolAdapter.adapt(beanWithToolMethods);
    for (CatTool tool : tools) {
      register(tool);
    }
    return tools.size();
  }

  /** 表里有没有这个名字。 */
  public boolean contains(String toolName) {
    return tools.containsKey(toolName);
  }

  /** 全部已注册工具（内省与契约守卫遍历用）。 */
  public List<CatTool> all() {
    return List.copyOf(tools.values());
  }

  @Override
  public List<ToolDescriptor> descriptors(List<String> names) {
    List<ToolDescriptor> descriptors = new ArrayList<>(names.size());
    for (String name : names) {
      descriptors.add(descriptorOf(require(name)));
    }
    return descriptors;
  }

  @Override
  public ToolResult execute(String toolName, String inputJson) {
    CatTool tool = require(toolName);
    JsonNode input;
    try {
      input = JSON.readTree(inputJson == null || inputJson.isBlank() ? "{}" : inputJson);
    } catch (JsonProcessingException e) {
      // 参数是模型生成的，坏 JSON 属常见失误：回一条模型能读懂的原因让它换招，别炸掉整轮
      return ToolResult.failure("工具入参不是合法 JSON: " + e.getOriginalMessage(), true);
    }
    return tool.execute(input);
  }

  /** 名字查不到即报错：Agent 声明的工具不存在是配置错误，静默少给会让模型无从下手。 */
  private CatTool require(String toolName) {
    CatTool tool = tools.get(toolName);
    if (tool == null) {
      throw new IllegalStateException("工具未注册: " + toolName);
    }
    return tool;
  }

  private static ToolDescriptor descriptorOf(CatTool tool) {
    return new ToolDescriptor(tool.getName(), tool.getDescription(), tool.getInputSchema());
  }
}
