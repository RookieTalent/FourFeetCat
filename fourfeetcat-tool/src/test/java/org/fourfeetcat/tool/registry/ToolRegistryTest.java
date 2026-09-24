package org.fourfeetcat.tool.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.fourfeetcat.core.ToolDescriptor;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.core.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

  private ToolRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new ToolRegistry();
  }

  @Test
  @DisplayName("三种来源的工具都以统一抽象身份注册_按名取描述与执行结果都对")
  void toolsFromThreeSources_shareOneAbstraction() {
    // 三个替身代表三种来源：内置、@Tool 注解、外部服务。注册之后调用方看不出差别——这正是本抽象存在的理由。
    registry.register(new ProbeTool("read_file", "读一个文件"));
    registry.register(new ProbeTool("shell", "跑一条命令"));
    registry.register(new ProbeTool("mcp_echo", "外部服务暴露的回应"));

    assertThat(registry.all()).hasSize(3);
    assertThat(registry.contains("read_file")).isTrue();

    List<ToolDescriptor> descriptors = registry.descriptors(List.of("shell", "mcp_echo"));
    assertThat(descriptors).extracting(ToolDescriptor::name).containsExactly("shell", "mcp_echo");

    ToolResult result = registry.execute("shell", "{\"command\":\"echo\"}");
    assertThat(result.success()).isTrue();
    assertThat(result.content()).isEqualTo("shell 收到: {\"command\":\"echo\"}");
  }

  @Test
  @DisplayName("按声明清单过滤_子集恰好相等_多一个与少一个都判失败")
  void descriptors_returnsExactlyTheDeclaredSubset() {
    registry.register(new ProbeTool("read_file", "读一个文件"));
    registry.register(new ProbeTool("shell", "跑一条命令"));
    registry.register(new ProbeTool("http_get", "发 GET"));

    List<String> declared = List.of("read_file", "http_get");

    // 集合相等断言同时钉住两侧：注册了但没声明的（shell）不能混进来，声明了却取不到的也不能少
    assertThat(registry.descriptors(declared))
        .extracting(ToolDescriptor::name)
        .containsExactlyInAnyOrderElementsOf(declared);
  }

  @Test
  @DisplayName("名字查不到_明确报错并指出名字_不静默少给")
  void unknownName_throwsWithTheName() {
    registry.register(new ProbeTool("read_file", "读一个文件"));

    assertThatThrownBy(() -> registry.descriptors(List.of("no_such_tool")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no_such_tool");

    assertThatThrownBy(() -> registry.execute("no_such_tool", "{}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no_such_tool");
  }

  @Test
  @DisplayName("重名注册_明确拒绝而不是静默覆盖")
  void duplicateName_isRejected() {
    registry.register(new ProbeTool("read_file", "读一个文件"));

    assertThatThrownBy(() -> registry.register(new ProbeTool("read_file", "另一个读文件")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("read_file");
  }

  @Test
  @DisplayName("入参不是合法 JSON_返回失败结果而不是炸掉循环")
  void malformedInput_becomesFailureResult() {
    registry.register(new ProbeTool("read_file", "读一个文件"));

    ToolResult result = registry.execute("read_file", "{不是 JSON");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).isNotBlank();
  }

  /** 测试替身：只回显收到的入参，用来验证注册、过滤与执行三条链路。 */
  private static final class ProbeTool implements CatTool {

    private final String name;
    private final String description;

    private ProbeTool(String name, String description) {
      this.name = name;
      this.description = description;
    }

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
      return ToolResult.success(name + " 收到: " + input);
    }
  }
}
