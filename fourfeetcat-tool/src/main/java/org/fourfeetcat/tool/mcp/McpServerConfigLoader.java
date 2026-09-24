package org.fourfeetcat.tool.mcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * 读工作区里的 {@code mcp_servers.yaml}（第20节课件）。
 *
 * <p>三种情况都返回空列表而不是报错：文件不存在、{@code servers} 段缺失、{@code servers} 为空——"没声明任何外部服务"是合法的
 * 零配置状态，不是故障。声明本身写坏了（缺 name / 缺 transport）才明确报错：那属于配置错误，静默跳过会让人以为连上了。
 */
public class McpServerConfigLoader {

  /** 配置文件名（仓库与 init 模板里已有这个空壳）。 */
  public static final String FILE_NAME = "mcp_servers.yaml";

  private final Path workspaceRoot;

  public McpServerConfigLoader(Path workspaceRoot) {
    this.workspaceRoot = workspaceRoot;
  }

  public List<McpServerConfig> load() {
    Path file = workspaceRoot.resolve(FILE_NAME);
    if (!Files.isRegularFile(file)) {
      return List.of();
    }
    Object parsed;
    try {
      parsed = new Yaml().load(Files.readString(file));
    } catch (IOException e) {
      throw new UncheckedIOException("读取 MCP 配置失败: " + file, e);
    }
    if (!(parsed instanceof Map<?, ?> root) || !(root.get("servers") instanceof List<?> servers)) {
      return List.of();
    }
    List<McpServerConfig> configs = new ArrayList<>(servers.size());
    for (Object item : servers) {
      if (item instanceof Map<?, ?> entry) {
        configs.add(toConfig(entry, file));
      }
    }
    return configs;
  }

  private static McpServerConfig toConfig(Map<?, ?> entry, Path file) {
    String name = text(entry.get("name"));
    if (name == null || name.isBlank()) {
      throw new IllegalStateException("MCP 配置缺少 name: " + file);
    }
    String transport = text(entry.get("transport"));
    if (transport == null || transport.isBlank()) {
      throw new IllegalStateException("MCP 配置缺少 transport: " + name);
    }
    Map<String, String> env = new LinkedHashMap<>();
    if (entry.get("env") instanceof Map<?, ?> declared) {
      declared.forEach((key, value) -> env.put(String.valueOf(key), String.valueOf(value)));
    }
    return new McpServerConfig(name, transport, text(entry.get("command")), env);
  }

  private static String text(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
