package org.fourfeetcat.tool.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import org.fourfeetcat.tool.registry.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 外部工具服务的接入（第20节课件）：启动时把配置里声明的服务**全部**连上，取它们的工具清单，逐个包装成统一抽象注册进注册表。
 *
 * <p>为什么是"全量连接"而不是"按 Agent 逐个连"：注册表是实例级共享资源，同一个服务被 N 个 Agent 引用时不该起 N 份子进程， 连接的生命周期也不该跟某个 Agent
 * 的生死绑在一起。Agent 侧的隔离由它声明的工具名清单承担——一个机制够了。
 *
 * <p><b>外部依赖的可用性不是自己的可用性</b>：任一服务连不上、握手失败或取清单失败，都只记 WARN 并跳过它自己的工具，底座照常 启动、其余服务照常注册。这是本类最值钱的一条行为。
 *
 * <p>它自己持有连接（{@link AutoCloseable}），由容器在停机时关闭——每个客户端背后是一个子进程，不关就是停机后的孤儿进程。
 */
// PMD 看不出这些客户端是"延迟关闭"的：它们一建出来就进 connected，由 close() 在停机时统一关闭
// （局部关掉会让连接活不过一次方法调用）。仓库里 ChatCommand 对容器上下文有同款抑制。
@SuppressWarnings("PMD.CloseResource")
public class McpClientService implements AutoCloseable {

  /** 核心阶段唯一放行的传输方式；其他取值只记 WARN 跳过（不为此新增配置键）。 */
  public static final String STDIO_TRANSPORT = "stdio";

  private static final Logger log = LoggerFactory.getLogger(McpClientService.class);
  private static final McpJsonMapper JSON_MAPPER = new JacksonMcpJsonMapper(new ObjectMapper());

  private final McpServerConfigLoader loader;
  private final List<McpSyncClient> connected = new ArrayList<>();

  public McpClientService(McpServerConfigLoader loader) {
    this.loader = loader;
  }

  /** 生产路径：按配置连上全部服务，并把工具注册进注册表。 */
  public void connectAll(ToolRegistry registry) {
    connectAll(registry, McpClientService::connectStdio);
  }

  /** 测试缝：换一个"造客户端"的工厂，不真起进程、不碰外部服务。 */
  public void connectAll(ToolRegistry registry, McpClientFactory factory) {
    for (McpServerConfig server : loader.load()) {
      // 精确匹配、不做大小写折叠：取值本来就是本仓定的字面量，折叠只会在土耳其语等 Locale 下引入怪行为
      if (!STDIO_TRANSPORT.equals(server.transport().trim())) {
        if (log.isWarnEnabled()) {
          log.warn("MCP server {} 的传输方式 {} 核心阶段不支持，跳过它的工具", server.name(), server.transport());
        }
        continue;
      }
      try {
        McpSyncClient client = factory.connect(server);
        connected.add(client);
        List<McpSchema.Tool> specs = client.listTools().tools();
        for (McpSchema.Tool spec : specs) {
          registry.register(new McpToolAdapter(client, spec));
        }
        if (log.isInfoEnabled()) {
          log.info("MCP server {} 已连接，注册 {} 个工具", server.name(), specs.size());
        }
      } catch (RuntimeException e) {
        if (log.isWarnEnabled()) {
          // 外部依赖失联不拖垮自身启动：只 WARN 跳过它的工具，其余 server 照常注册
          log.warn("MCP server {} 连接失败，跳过它的工具: {}", server.name(), e.getMessage());
        }
      }
    }
  }

  /**
   * 停机时关掉所有已连接的客户端。
   *
   * <p>不在关闭失败上做文章：停机路径上抛异常只会把停机本身搞坏，关不掉的最坏后果是一个孤儿进程（记 WARN 留痕）。
   */
  @Override
  public void close() {
    for (McpSyncClient client : connected) {
      try {
        client.close();
      } catch (RuntimeException e) {
        if (log.isWarnEnabled()) {
          log.warn("关闭 MCP 客户端失败，已在停机路径上忽略: {}", e.getMessage());
        }
      }
    }
    connected.clear();
  }

  /** 起一个 stdio 子进程并立刻握手：连不上要在**启动时**暴露，而不是拖到第一次调工具。 */
  private static McpSyncClient connectStdio(McpServerConfig config) {
    if (config.command() == null || config.command().isBlank()) {
      throw new IllegalStateException("stdio 传输需要 command: " + config.name());
    }
    // 配置里只有 command 一个字段，参数写在它里面：按空白拆成"可执行文件 + 参数数组"，
    // 拆出来的东西直接交给 ProcessBuilder，不经 shell 解释（与技术方案 §6.2 对 shell 工具的口径一致）
    List<String> parts = List.of(config.command().trim().split("\\s+"));
    ServerParameters parameters =
        ServerParameters.builder(parts.get(0))
            .args(parts.subList(1, parts.size()))
            .env(config.env())
            .build();

    McpSyncClient client =
        McpClient.sync(new StdioClientTransport(parameters, JSON_MAPPER)).build();
    client.initialize();
    return client;
  }
}
