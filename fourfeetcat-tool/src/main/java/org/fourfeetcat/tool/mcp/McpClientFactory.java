package org.fourfeetcat.tool.mcp;

import io.modelcontextprotocol.client.McpSyncClient;

/**
 * 造一个已连接的 MCP 客户端（第20节）。
 *
 * <p>存在的理由就一个：给单测留一道缝。真起一个 MCP 子进程需要外部可执行文件、可能还要联网，不该压在单测的启动路径上；测试注入
 * 替身，验的是"连接、取清单、包装注册、失联隔离"这套编排逻辑本身，而不是 MCP 协议实现。
 */
@FunctionalInterface
public interface McpClientFactory {

  /** 连上并握手完成；连不上就抛异常，由调用方决定怎么隔离。 */
  McpSyncClient connect(McpServerConfig config);
}
