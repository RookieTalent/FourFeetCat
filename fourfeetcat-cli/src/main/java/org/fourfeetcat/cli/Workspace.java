package org.fourfeetcat.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 工作区路径与模板读取（包内私有）。
 *
 * <p>轻命令全程不碰容器，路径只能自己算；口径与 application.yaml 的数据源、boot 的装配保持一致—— {@code FOURFEETCAT_ROOT} 可整体搬移工作区。
 *
 * <p>模板正文放在 {@code resources/templates/}（技术方案 §8.1「创建目录、写默认模板、生成默认 Profile」）：模板是**内容**
 * 不是逻辑，YAML/Markdown 该在资源里带语法高亮与校验，改模板也不必碰 Java。
 */
final class Workspace {

  private static final String ROOT_ENV = "FOURFEETCAT_ROOT";
  private static final String DEFAULT_ROOT = ".fourfeetcat";
  private static final String TEMPLATE_DIR = "/templates/";
  private static final String PROFILE_TEMPLATE = "profile.yaml";

  /** Agent 配置目录：本阶段 Profile 的定义源（"一个目录 = 一个 Agent"的形态归后续节）。 */
  static final String PROFILES_DIR = "profiles";

  static final String DEFAULT_PROFILE_FILE = PROFILES_DIR + "/default.yaml";
  static final String MEMORY_DIR = "memory";
  static final String MEMORY_FILE = MEMORY_DIR + "/MEMORY.md";
  static final String AGENTS_FILE = "AGENTS.md";
  static final String SOUL_FILE = "SOUL.md";
  static final String USER_FILE = "USER.md";
  static final String MCP_SERVERS_FILE = "mcp_servers.yaml";
  static final String LOGS_DIR = "logs";

  private static final String NAME_PLACEHOLDER = "__NAME__";
  private static final String DESCRIPTION_PLACEHOLDER = "__DESCRIPTION__";

  private Workspace() {}

  static Path root() {
    return Path.of(System.getenv().getOrDefault(ROOT_ENV, DEFAULT_ROOT));
  }

  static Path profilesDir() {
    return root().resolve(PROFILES_DIR);
  }

  static Path profileFile(String name) {
    return profilesDir().resolve(name + ".yaml");
  }

  /**
   * 新建的 Agent 配置：名字进 name 字段，其余用默认模板。
   *
   * <p>用占位符替换而不是 {@code String.format}：Agent 名是用户输入，含 {@code %} 时格式串会直接抛异常（模板里也没有 需要格式化的东西）。
   */
  static String profileTemplate(String name) {
    return template(PROFILE_TEMPLATE)
        .replace(NAME_PLACEHOLDER, name)
        .replace(DESCRIPTION_PLACEHOLDER, "default".equals(name) ? "默认 Agent" : name);
  }

  /** 读打包内的模板正文；模板是 jar 的一部分，缺了就是打包错了，必须响亮报错而不是回落到空内容。 */
  static String template(String fileName) {
    try (InputStream stream = Workspace.class.getResourceAsStream(TEMPLATE_DIR + fileName)) {
      if (stream == null) {
        throw new IllegalStateException("打包内找不到模板 " + TEMPLATE_DIR + fileName);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("读取模板失败: " + fileName + "（" + e.getMessage() + "）", e);
    }
  }

  /** 已存在就不动：init 要幂等、profile create 不许覆盖既有 Agent。返回是否真的写了。 */
  static boolean createIfAbsent(Path file, String content) throws IOException {
    if (Files.exists(file)) {
      return false;
    }
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return true;
  }
}
