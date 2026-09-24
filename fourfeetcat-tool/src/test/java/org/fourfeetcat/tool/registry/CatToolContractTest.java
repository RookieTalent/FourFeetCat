package org.fourfeetcat.tool.registry;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.core.tool.ToolResult;
import org.fourfeetcat.tool.builtin.FileTools;
import org.fourfeetcat.tool.builtin.HttpTools;
import org.fourfeetcat.tool.builtin.ShellTools;
import org.fourfeetcat.tool.sandbox.Sandbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestClient;

/**
 * 契约三件套的遍历守卫（第20节课件 harness）：注册表里**每个**工具的名字、用途描述、参数说明都不能为空。
 *
 * <p>它的价值在"新工具自动纳入"：任何新工具只要进了注册表就被这条守卫覆盖，漏实现 {@code getInputSchema()} 立刻红——缺了它，
 * 把工具翻译成模型可读格式那一步会直接卡死，而且卡在运行期、不在编译期。
 *
 * <p>{@link #allRegisteredTools()} 覆盖**生产装配那批真实工具**（不是专为测试造的听话样本），并随各阶段交付的 {@code NotifyTools} 与
 * MCP 适配器继续扩充。
 */
class CatToolContractTest {

  static Stream<CatTool> allRegisteredTools() {
    ToolRegistry registry = new ToolRegistry();
    for (CatTool tool : deliveredTools()) {
      registry.register(tool);
    }
    return registry.all().stream();
  }

  private static List<CatTool> deliveredTools() {
    Sandbox allowAll = action -> {};
    List<CatTool> tools = new ArrayList<>();
    // 真实的内置工具——守卫覆盖的是生产装配那批，不是专门为测试造的听话样本
    tools.addAll(AnnotatedToolAdapter.adapt(new FileTools(allowAll)));
    tools.addAll(
        AnnotatedToolAdapter.adapt(new ShellTools(allowAll, Duration.ofSeconds(30), 8000)));
    tools.addAll(AnnotatedToolAdapter.adapt(new HttpTools(allowAll, RestClient.create())));
    // 外部服务来源的替身：真实 MCP 适配器在后续阶段接进来
    tools.add(new ProbeTool("probe_mcp", "代表外部服务来源的替身"));
    return tools;
  }

  @ParameterizedTest
  @MethodSource("allRegisteredTools")
  @DisplayName("每个工具的契约三件套都不能缺")
  void everyToolExposesNameDescriptionAndInputSchema(CatTool tool) {
    assertThat(tool.getName()).as("工具名不能为空").isNotBlank();
    assertThat(tool.getDescription()).as("用途描述不能为空").isNotBlank();
    // 缺了参数说明，Provider 翻译 Function Calling 时直接卡死
    assertThat(tool.getInputSchema()).as("参数说明不能为空").isNotBlank();
  }

  /** 遍历用的替身。契约守卫只看形状，不看它干什么。 */
  private record ProbeTool(String name, String description) implements CatTool {

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public String getInputSchema() {
      return "{\"type\":\"object\"}";
    }

    @Override
    public ToolResult execute(JsonNode input) {
      return ToolResult.success("ok");
    }
  }
}
