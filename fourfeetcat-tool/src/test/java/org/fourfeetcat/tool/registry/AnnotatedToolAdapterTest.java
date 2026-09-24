package org.fourfeetcat.tool.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.core.tool.ToolResult;
import org.fourfeetcat.tool.sandbox.SandboxViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 三档接入里的"方式三"（Java 注解 Bean）的契约守卫。
 *
 * <p>课件 harness 未点名这个类——它守的是"内置工具与业务方写的工具走的是同一条管道"这件事：适配之后必须是一个**完整的** {@link
 * CatTool}（三件套齐全），执行必须走**我们的**执行路径，异常必须原样浮出来而不是被框架包装埋掉。
 */
class AnnotatedToolAdapterTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  @DisplayName("注解方法被适配成统一抽象_名字描述参数说明三件套齐全")
  void annotatedMethod_becomesCompleteCatTool() {
    List<CatTool> tools = AnnotatedToolAdapter.adapt(new SampleBean());

    assertThat(tools).hasSize(1);
    CatTool tool = tools.get(0);
    assertThat(tool.getName()).isEqualTo("sample_echo");
    assertThat(tool.getDescription()).isNotBlank();
    // 参数说明由 Spring AI 生成——这里只确认它确实被生产出来了，不关心它的措辞
    assertThat(tool.getInputSchema()).contains("message");
  }

  @Test
  @DisplayName("执行走我们的统一抽象_参数树直接可执行")
  void execute_goesThroughOurAbstraction() throws Exception {
    CatTool tool = AnnotatedToolAdapter.adapt(new SampleBean()).get(0);

    ToolResult result = tool.execute(JSON.readTree("{\"message\":\"你好\"}"));

    assertThat(result.success()).isTrue();
    // 一字不差：返回值原样交给模型。框架默认会把 String 结果 JSON 化（变成 "\"echo: 你好\""），
    // 多一层引号、换行还会被转义——所以每个 @Tool 都挂了 PlainTextResultConverter
    assertThat(result.content()).isEqualTo("echo: 你好");
  }

  @Test
  @DisplayName("目标方法抛异常_原样抛出而不是埋进框架的包装异常")
  void targetThrows_originalExceptionSurfaces() throws Exception {
    CatTool tool = AnnotatedToolAdapter.adapt(new SampleBean()).get(0);

    // 沙箱拦截的原因要能原样到 ToolExecutor 的审计列里，不能被 "Error invoking method..." 这层壳盖住
    assertThatThrownBy(() -> tool.execute(JSON.readTree("{\"message\":\"boom\"}")))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("被沙箱拦下");
  }

  /** 业务方按"方式三"写的一个 Bean：跟内置工具一个写法，没有任何特殊之处。 */
  public static class SampleBean {

    @Tool(
        name = "sample_echo",
        description = "回显一段文字",
        resultConverter = PlainTextResultConverter.class)
    public String echo(@ToolParam(description = "要回显的文字") String message) {
      if ("boom".equals(message)) {
        throw new SandboxViolationException("被沙箱拦下");
      }
      return "echo: " + message;
    }
  }
}
