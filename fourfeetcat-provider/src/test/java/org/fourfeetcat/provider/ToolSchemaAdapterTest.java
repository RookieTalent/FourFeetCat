package org.fourfeetcat.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.fourfeetcat.core.ToolDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class ToolSchemaAdapterTest {

  private final ToolSchemaAdapter adapter = new ToolSchemaAdapter();

  @Test
  @DisplayName("schema翻译成SpringAI格式_字段一一对齐")
  void translate_fieldsAlignedOneToOne() {
    ToolDescriptor httpGet =
        new ToolDescriptor(
            "http_get",
            "发起 HTTP GET 请求",
            "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}}}");

    List<ToolCallback> callbacks = adapter.toSpringAiTools(List.of(httpGet));

    assertThat(callbacks).hasSize(1);
    var definition = callbacks.get(0).getToolDefinition();
    assertThat(definition.name()).isEqualTo("http_get");
    assertThat(definition.description()).isEqualTo("发起 HTTP GET 请求");
    assertThat(definition.inputSchema())
        .isEqualTo("{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}}}");
  }

  @Test
  @DisplayName("产物不含任何执行逻辑_call抛异常")
  void translatedCallbacks_areNotExecutable() {
    ToolDescriptor tool = new ToolDescriptor("shell", "执行命令", "{\"type\":\"object\"}");

    ToolCallback callback = adapter.toSpringAiTools(List.of(tool)).get(0);

    // 只翻译不执行：Provider 全程不存在工具执行路径
    assertThatThrownBy(() -> callback.call("{}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("shell");
  }

  @Test
  @DisplayName("空工具列表_翻译为空")
  void emptyTools_translatesToEmpty() {
    assertThat(adapter.toSpringAiTools(List.of())).isEmpty();
    assertThat(adapter.toSpringAiTools(null)).isEmpty();
  }
}
