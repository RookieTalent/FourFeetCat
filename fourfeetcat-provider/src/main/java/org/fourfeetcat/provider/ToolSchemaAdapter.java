package org.fourfeetcat.provider;

import java.util.List;
import org.fourfeetcat.core.ToolDescriptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * 工具格式适配器：把自有工具说明翻译成 Spring AI 的工具格式（Function Calling 的 schema）。
 *
 * <p>只翻译、不执行（宪法原则二）——产物 call() 抛异常，执行权在 ReActLoop + ToolExecutor。
 */
public class ToolSchemaAdapter {

  /** 翻译成 Spring AI 的 ToolCallback 列表：name/description/inputSchema 一一对齐。 */
  public List<ToolCallback> toSpringAiTools(List<ToolDescriptor> tools) {
    if (tools == null || tools.isEmpty()) {
      return List.of();
    }
    return tools.stream().map(ToolSchemaAdapter::schemaOnlyCallback).toList();
  }

  private static ToolCallback schemaOnlyCallback(ToolDescriptor tool) {
    ToolDefinition definition =
        DefaultToolDefinition.builder()
            .name(tool.name())
            .description(tool.description())
            .inputSchema(tool.inputSchema())
            .build();
    return new ToolCallback() {
      @Override
      public ToolDefinition getToolDefinition() {
        return definition;
      }

      @Override
      public String call(String toolInput) {
        throw new IllegalStateException("Provider 只翻译不执行工具: " + tool.name());
      }
    };
  }
}
