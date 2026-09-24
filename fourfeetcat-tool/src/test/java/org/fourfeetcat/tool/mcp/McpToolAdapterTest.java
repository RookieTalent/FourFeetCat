package org.fourfeetcat.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.core.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 外部工具服务的适配（课件 harness）：mock 客户端，不碰网、不起进程。
 *
 * <p>守两件事：工具声明**一一映射**（名字、描述、参数说明都取自服务端，我们不重写）；执行时参数**原样转发**、结果包成统一结果值 对象。
 */
class McpToolAdapterTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final McpSyncClient client = mock(McpSyncClient.class);

  @Test
  @DisplayName("工具声明一一映射_名字描述参数说明都取自服务端")
  void toolSpec_isMappedVerbatim() {
    CatTool tool = adapter(remoteTool("search_docs", "在文档里搜一段话"));

    assertThat(tool.getName()).isEqualTo("search_docs");
    assertThat(tool.getDescription()).isEqualTo("在文档里搜一段话");
    assertThat(tool.getInputSchema()).contains("query");
  }

  @Test
  @DisplayName("执行_参数原样转发_结果包成统一结果值对象")
  void execute_forwardsArgumentsVerbatim() throws Exception {
    when(client.callTool(any()))
        .thenReturn(
            new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("命中 3 条")), false));

    ToolResult result =
        adapter(remoteTool("search_docs", "搜"))
            .execute(JSON.readTree("{\"query\":\"排期\",\"top\":5}"));

    ArgumentCaptor<McpSchema.CallToolRequest> request =
        ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
    verify(client).callTool(request.capture());
    assertThat(request.getValue().name()).isEqualTo("search_docs");
    assertThat(request.getValue().arguments()).containsEntry("query", "排期").containsEntry("top", 5);

    assertThat(result.success()).isTrue();
    assertThat(result.content()).isEqualTo("命中 3 条");
  }

  @Test
  @DisplayName("服务端报错_返回可重试的失败结果而不是抛异常")
  void serverError_becomesRetryableFailure() throws Exception {
    when(client.callTool(any()))
        .thenReturn(new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("限流了")), true));

    ToolResult result = adapter(remoteTool("search_docs", "搜")).execute(JSON.readTree("{}"));

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("限流了");
    // 外部服务的失败常是瞬时的（限流、抖动），标可重试让循环再试一次
    assertThat(result.retryable()).isTrue();
  }

  private McpToolAdapter adapter(McpSchema.Tool spec) {
    return new McpToolAdapter(client, spec);
  }

  private static McpSchema.Tool remoteTool(String name, String description) {
    return McpSchema.Tool.builder()
        .name(name)
        .description(description)
        .inputSchema(
            new McpSchema.JsonSchema(
                "object",
                Map.of("query", Map.of("type", "string")),
                List.of("query"),
                false,
                null,
                null))
        .build();
  }
}
