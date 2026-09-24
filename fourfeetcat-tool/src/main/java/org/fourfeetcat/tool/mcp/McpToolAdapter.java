package org.fourfeetcat.tool.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.core.tool.ToolResult;

/**
 * 把外部工具服务暴露的一个工具适配成 {@link CatTool}（第20节课件）。
 *
 * <p>三条映射口径：名字、用途描述、参数说明**直接取自服务端的声明**（它说什么就是什么，我们不重写）；执行时把参数原样转发过去， 结果包成统一的 {@link ToolResult}。
 *
 * <p>适配之后，ReAct 循环与 {@code ToolExecutor} 看不出它与内置工具有什么区别——这正是统一抽象要的效果：加一种来源不改调用方。
 */
public class McpToolAdapter implements CatTool {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final McpJsonMapper SCHEMA_WRITER = new JacksonMcpJsonMapper(JSON);
  private static final TypeReference<Map<String, Object>> ARGUMENTS = new TypeReference<>() {};

  private final McpSyncClient client;
  private final McpSchema.Tool spec;

  public McpToolAdapter(McpSyncClient client, McpSchema.Tool spec) {
    this.client = client;
    this.spec = spec;
  }

  @Override
  public String getName() {
    return spec.name();
  }

  @Override
  public String getDescription() {
    return spec.description() == null ? "" : spec.description();
  }

  /**
   * 现算而不是构造时算：构造器抛异常会让这个类变得可被继承攻击（SpotBugs 的 CT_CONSTRUCTOR_THROW）， 而且 schema 只在组装 prompt
   * 时被要一次，序列化一个小 JSON 对象的成本可以忽略。
   */
  @Override
  public String getInputSchema() {
    return writeSchema(spec);
  }

  @Override
  public ToolResult execute(JsonNode input) {
    Map<String, Object> arguments = JSON.convertValue(input, ARGUMENTS);
    McpSchema.CallToolResult result =
        client.callTool(new McpSchema.CallToolRequest(spec.name(), arguments));
    String text = textOf(result);
    if (Boolean.TRUE.equals(result.isError())) {
      // 外部服务报错标可重试：这类失败常是瞬时的（限流、抖动），值得让循环再试一次
      return ToolResult.failure(text, true);
    }
    return ToolResult.success(text);
  }

  /** 把返回的内容块拼成一段文本；非文本块（图片等）原样 toString，至少不丢信息。 */
  private static String textOf(McpSchema.CallToolResult result) {
    List<McpSchema.Content> contents = result.content();
    if (contents == null || contents.isEmpty()) {
      return "";
    }
    StringBuilder text = new StringBuilder();
    for (McpSchema.Content content : contents) {
      if (content instanceof McpSchema.TextContent textContent) {
        text.append(textContent.text());
      } else {
        text.append(content);
      }
    }
    return text.toString();
  }

  /** 服务端声明的参数 schema 是结构化对象，转成文本交出参，与内置工具同一口径（{@code String}）。 */
  private static String writeSchema(McpSchema.Tool spec) {
    if (spec.inputSchema() == null) {
      return "{\"type\":\"object\"}";
    }
    try {
      return SCHEMA_WRITER.writeValueAsString(spec.inputSchema());
    } catch (IOException e) {
      throw new UncheckedIOException("MCP 工具的入参 schema 无法序列化: " + spec.name(), e);
    }
  }
}
