package org.fourfeetcat.tool.mcp;

import java.util.Map;

/**
 * 一条外部工具服务声明（第20节课件）：名字 / 传输方式 / 命令 / 环境变量。
 *
 * <p>落 {@code .fourfeetcat/mcp_servers.yaml}，不落库——它是实例级配置，不是运行时数据。四个字段与技术方案 §6.4 逐字对齐，
 * 没有第五个；需要参数的服务把参数写在 {@code command} 里（按空白拆开）。
 */
public record McpServerConfig(
    String name, String transport, String command, Map<String, String> env) {

  public McpServerConfig {
    env = env == null ? Map.of() : Map.copyOf(env);
  }
}
