package org.fourfeetcat.core.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个 Agent 的运行配置（第16节课件：字段本节建全，后续各节用到哪个字段再补各自的校验）。
 *
 * <p>来源是工作区 {@code .fourfeetcat/profiles/<name>.yaml}；本节只消费 provider 段。加载完成后 Profile
 * 是只读契约，紧凑构造器做防御性不可变化（容器字段缺省为空集合）。每个字段都必须有注释，写明「谁消费 / 什么口径」。
 */
public record Profile(
    /** 唯一名：ProfileRegistry 的索引键，也是会话身份的组成部分；重名时后加载覆盖先加载（记 warn）。 */
    String name,
    /** 人类可读描述，用于列出与展示；不参与任何运行逻辑。 */
    String description,
    /** 呈现身份：展示名 + 人格化 prompt 段（写入 system prompt 的身份部分）。 */
    Identity identity,
    /** 本节消费：这个 Agent 用哪家 provider（须能在全局层找到）、哪个 model、什么温度。 */
    ProviderConfig provider,
    /** 可用工具名清单（第20节 ToolRegistry 按名解析；沙箱白名单另算，二者正交）。 */
    List<String> tools,
    /** 绑定的公共 Skill 名清单（第29节按名注入 name/description/读取路径，正文不预载）。 */
    List<String> skills,
    /** 该 Agent 可用的 MCP server 名清单（第20节按名连 mcp_servers.yaml 里的配置）。 */
    List<String> mcpServers,
    /** 接哪些 Channel（CLI/飞书/企微/钉钉等），第18节起按名解析成入站适配器。 */
    List<String> channels,
    /** 可用的通知渠道名清单（第19节按名查 SQLite 全局注册表，正文里只出现名字）。 */
    List<String> notifyChannels,
    /** 定时任务定义（第25/28节：cron + message 等），文件是定义源、表只存运行态。 */
    List<Map<String, Object>> schedules,
    /** 启动上下文文件清单（AGENTS.md/SOUL.md/USER.md），每轮全量注入 system prompt。 */
    List<String> bootstrap,
    /** 运行参数（max_iterations/max_history_turns 等），第17节起按 key 读取。 */
    Map<String, Object> settings) {

  public Profile {
    identity = identity == null ? new Identity(null, null) : identity;
    tools = unmodifiableList(tools);
    skills = unmodifiableList(skills);
    mcpServers = unmodifiableList(mcpServers);
    channels = unmodifiableList(channels);
    notifyChannels = unmodifiableList(notifyChannels);
    schedules = schedules == null ? List.of() : List.copyOf(schedules);
    bootstrap = unmodifiableList(bootstrap);
    settings = unmodifiableMap(settings);
  }

  /** null 容器落空集合（下游无需判空）；值允许 null 的容器走 unmodifiable 包装（保数据不保可变）。 */
  private static Map<String, Object> unmodifiableMap(Map<String, Object> map) {
    return map == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(map));
  }

  private static List<String> unmodifiableList(List<String> list) {
    return list == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(list));
  }

  /** 呈现身份：Agent 的展示名（agent_name）与人格化 system prompt 段（prompt）。 */
  public record Identity(
      /** 展示名（YAML `identity.agent_name`），面向用户出现。 */
      String agentName,
      /** 人格化 prompt 段（YAML `identity.prompt`），拼进 system prompt。 */
      String prompt) {}

  /** Profile 层的调用参数：这个 Agent 用哪家 provider、哪个 model、什么温度。 */
  public record ProviderConfig(
      /** 全局层（`fourfeetcat.providers`）里声明的 provider 名，显式映射表的 key。 */
      String name,
      /** 模型名，如 deepseek-chat；缺省交给 provider 端默认。 */
      String model,
      /** 采样温度；null 表示不干预，用模型默认。 */
      Double temperature) {}
}
