package org.fourfeetcat.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.tool.registry.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 外部工具服务的接入编排（课件 harness）：mock 客户端，不真起子进程。
 *
 * <p>最值钱的一条是**失联隔离**——课件原文即"某个 MCP server 失联不能拖垮启动和其他工具"。
 */
class McpClientServiceTest {

  @TempDir Path workspaceRoot;

  private final ToolRegistry registry = new ToolRegistry();

  private McpClientService service() {
    return new McpClientService(new McpServerConfigLoader(workspaceRoot));
  }

  @Test
  @DisplayName("某个 MCP server 失联_不能拖垮启动和其他工具")
  void oneServerDown_doesNotBreakStartupOrOtherTools() {
    writeConfig(
        """
        servers:
          - name: good
            transport: stdio
            command: good-server
          - name: bad
            transport: stdio
            command: bad-server
        """);
    McpSyncClient goodClient = clientExposing("good_mcp_tool");
    McpSyncClient badClient = mock(McpSyncClient.class);
    when(badClient.listTools()).thenThrow(new IllegalStateException("refused"));

    // 不抛异常——外部依赖的可用性不是自己的可用性
    assertThatCode(
            () ->
                service()
                    .connectAll(
                        registry, config -> "bad".equals(config.name()) ? badClient : goodClient))
        .doesNotThrowAnyException();

    assertThat(registry.contains("good_mcp_tool")).isTrue();
    assertThat(registry.contains("bad_mcp_tool")).isFalse();
  }

  @Test
  @DisplayName("连接阶段就失败_同样只跳过它自己")
  void connectionFailure_isIsolated() {
    writeConfig(
        """
        servers:
          - name: good
            transport: stdio
            command: good-server
          - name: unreachable
            transport: stdio
            command: nowhere
        """);
    McpSyncClient goodClient = clientExposing("good_mcp_tool");

    service()
        .connectAll(
            registry,
            config -> {
              if ("unreachable".equals(config.name())) {
                throw new IllegalStateException("连不上");
              }
              return goodClient;
            });

    assertThat(registry.contains("good_mcp_tool")).isTrue();
  }

  @Test
  @DisplayName("非 stdio 传输_只警告跳过_其余工具照常注册")
  void unsupportedTransport_isSkippedWithWarning() {
    writeConfig(
        """
        servers:
          - name: sse-one
            transport: sse
            command: whatever
          - name: good
            transport: stdio
            command: good-server
        """);
    McpSyncClient goodClient = clientExposing("good_mcp_tool");

    service().connectAll(registry, config -> goodClient);

    assertThat(registry.contains("good_mcp_tool")).isTrue();
    assertThat(registry.all()).hasSize(1);
  }

  @Test
  @DisplayName("没声明任何外部服务_零配置照常启动")
  void noConfig_startsWithNoTools() {
    assertThatCode(() -> service().connectAll(registry, config -> mock(McpSyncClient.class)))
        .doesNotThrowAnyException();

    assertThat(registry.all()).isEmpty();
  }

  @Test
  @DisplayName("停机_把开出去的连接都关掉")
  void close_closesEveryConnectedClient() {
    writeConfig(
        """
        servers:
          - name: one
            transport: stdio
            command: server-one
          - name: two
            transport: stdio
            command: server-two
        """);
    McpSyncClient first = clientExposing("first_tool");
    McpSyncClient second = clientExposing("second_tool");
    McpClientService service = service();
    service.connectAll(registry, config -> "one".equals(config.name()) ? first : second);

    service.close();

    // 每个客户端背后是一个子进程：不关就是停机后的孤儿进程
    verify(first).close();
    verify(second).close();
  }

  private void writeConfig(String yaml) {
    try {
      Files.writeString(workspaceRoot.resolve(McpServerConfigLoader.FILE_NAME), yaml);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static McpSyncClient clientExposing(String toolName) {
    McpSyncClient client = mock(McpSyncClient.class);
    when(client.listTools())
        .thenReturn(
            new McpSchema.ListToolsResult(
                List.of(
                    McpSchema.Tool.builder()
                        .name(toolName)
                        .description("替身工具")
                        .inputSchema(
                            new McpSchema.JsonSchema(
                                "object", Map.of(), List.of(), false, null, null))
                        .build()),
                null));
    return client;
  }
}
