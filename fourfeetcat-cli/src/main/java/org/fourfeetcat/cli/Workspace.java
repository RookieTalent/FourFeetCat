package org.fourfeetcat.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 工作区路径与模板（包内私有）。
 *
 * <p>轻命令全程不碰容器，路径与模板只能自己算；口径与 application.yaml 的数据源、boot 的装配保持一致—— {@code FOURFEETCAT_ROOT}
 * 可整体搬移工作区。
 */
final class Workspace {

  private static final String ROOT_ENV = "FOURFEETCAT_ROOT";
  private static final String DEFAULT_ROOT = ".fourfeetcat";

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

  /** 默认 Agent：provider.name 必须是全局层已声明的名字，否则加载时会被判非法（课件第16节的校验）。 */
  private static final String PROFILE_TEMPLATE =
      """
      # Agent 配置：改完下一轮立即生效（每次组装 prompt 都现读，无缓存）
      name: __NAME__
      description: __DESCRIPTION__
      identity:
        agent_name: 四脚猫
        prompt: 你是一个乐于助人的助手，回答简洁、直接。
      provider:
        name: deepseek
        model: deepseek-v4-flash
      tools: []
      bootstrap:
        - AGENTS.md
        - SOUL.md
        - USER.md
      settings:
        max_iterations: 10
        max_history_turns: 20
      """;

  private static final String AGENTS_TEMPLATE =
      """
      # 项目说明

      在这里写这个 Agent 所在的业务背景、术语、约定。每轮对话都会全量注入。
      """;

  private static final String SOUL_TEMPLATE =
      """
      # 人格

      在这里定义 Agent 的语气与性格。每轮对话都会全量注入。
      """;

  private static final String USER_TEMPLATE =
      """
      # 用户偏好

      用户手写的初始设定，FourFeetCat 只读不写（Agent 的成长记录写 MEMORY.md）。
      """;

  private static final String MEMORY_TEMPLATE =
      """
      # 长期记忆

      Agent 通过 save_memory 工具写入，不要手动改。
      """;

  private static final String MCP_SERVERS_TEMPLATE =
      """
      # MCP server 配置（第20节起按 Profile 的 mcp_servers 名单连接）
      servers: []
      """;

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
   * <p>用占位符替换而不是 {@code String.format}：Agent 名是用户输入，含 {@code %} 时格式串会直接抛异常（YAML 模板里也 没有需要格式化的东西）。
   */
  static String profileTemplate(String name) {
    return PROFILE_TEMPLATE
        .replace(NAME_PLACEHOLDER, name)
        .replace(DESCRIPTION_PLACEHOLDER, "default".equals(name) ? "默认 Agent" : name);
  }

  static String agentsTemplate() {
    return AGENTS_TEMPLATE;
  }

  static String soulTemplate() {
    return SOUL_TEMPLATE;
  }

  static String userTemplate() {
    return USER_TEMPLATE;
  }

  static String memoryTemplate() {
    return MEMORY_TEMPLATE;
  }

  static String mcpServersTemplate() {
    return MCP_SERVERS_TEMPLATE;
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
