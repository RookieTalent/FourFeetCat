package org.fourfeetcat.core.react;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.fourfeetcat.core.profile.AgentLoader;
import org.fourfeetcat.core.profile.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * 启动上下文供给（技术方案 §8.3）：按 Profile 注入 Bootstrap 文件、Agent 正文与 Skill 元数据。
 *
 * <p>两条铁律：每次组装都重新读文件、绝不缓存（用户改完下一轮立即生效）；显式引用的东西缺失必须报错，启动信息文件缺失至少告警 ——静默跳过会造成"人格悄悄丢了"这种最难查的软故障。
 *
 * <p><b>第29节起按来源分派</b>：
 *
 * <ul>
 *   <li><b>Agent 目录来源</b>（{@code agents/<name>/} 存在）：注入 Agent 正文（`AGENT.md` 去 frontmatter），并按宪法四
 *       软连接视图注入公共 Skill 元数据——枚举 {@code agents/<name>/skills/} 下<b>相对软连接</b>，{@code toRealPath()} 校验
 *       目标位于公共 {@code skills/} 根；dangling / escaped / invalid-target 记 warn、跳过该技能。SKILL 正文不预载，模型经
 *       read_file 按需读。非软连接文件（如 Agent 内部子指令 report-format.md）跳过——它是模型用 read_file 按需读的子指令，不进 prompt
 *       元数据。
 *   <li><b>手写 Profile 来源</b>（无 Agent 目录）：维持现状——注入 bootstrap、按 {@code profile.skills()} 直读公共 Skill
 *       元数据。
 * </ul>
 */
public class ContextLoader {

  private static final Logger log = LoggerFactory.getLogger(ContextLoader.class);
  private static final String SKILL_DIR = "skills";
  private static final String SKILL_FILE = "SKILL.md";
  private static final String AGENT_DIR = "agents";
  private static final String AGENT_FILE = "AGENT.md";

  private final Path workspaceRoot;

  public ContextLoader(Path workspaceRoot) {
    this.workspaceRoot = workspaceRoot;
  }

  public String load(Profile profile) {
    StringBuilder text = new StringBuilder();
    for (String name : profile.bootstrap()) {
      Path file = workspaceRoot.resolve(name);
      if (!Files.isRegularFile(file)) {
        log.warn("启动信息文件缺失，已跳过: {}", file);
        continue;
      }
      text.append(read(file)).append('\n');
    }
    Path agentDir = workspaceRoot.resolve(AGENT_DIR).resolve(profile.name());
    if (Files.isRegularFile(agentDir.resolve(AGENT_FILE))) {
      // Agent 目录来源：正文常驻 prompt + 软连接技能元数据
      String body = AgentLoader.bodyOf(agentDir.resolve(AGENT_FILE));
      if (!body.isBlank()) {
        text.append(body).append('\n');
      }
      for (String skillLine : boundSkillLines(agentDir)) {
        if (!skillLine.isBlank()) {
          text.append(skillLine).append('\n');
        }
      }
    } else {
      // 手写 Profile 来源：维持第 16/17 节直读公共库
      for (String name : profile.skills()) {
        text.append(skillLine(name)).append('\n');
      }
    }
    return text.toString().trim();
  }

  /**
   * 枚举 Agent 目录 {@code skills/} 下的<b>相对软连接</b>，逐个按宪法四/原则六校验并产出元数据行 （`- name: desc（读取路径:
   * <abs>）`）。越界/dangling/invalid-target 记 warn、跳过该技能——坏技能不注入， 也不阻断 Agent 本体。非软连接文件跳过（它是 Agent
   * 内部子指令，模型 read_file 按需读）。
   */
  private List<String> boundSkillLines(Path agentDir) {
    Path agentSkills = agentDir.resolve(SKILL_DIR);
    if (!Files.isDirectory(agentSkills)) {
      return List.of();
    }
    List<Path> entries;
    try (Stream<Path> stream = Files.list(agentSkills)) {
      entries = stream.sorted().toList();
    } catch (IOException e) {
      if (log.isWarnEnabled()) {
        log.warn("无法读取 Agent 技能目录 {}: {}", agentSkills, e.getMessage());
      }
      return List.of();
    }
    return entries.stream().filter(Files::isSymbolicLink).map(this::metadataFromSymlink).toList();
  }

  /** 校验一个指向公共 Skill 的软连接并产出元数据行；不过校验记 warn、返回空串（跳过该技能）。 */
  private String metadataFromSymlink(Path link) {
    Path real;
    try {
      real = link.toRealPath();
    } catch (IOException e) {
      if (log.isWarnEnabled()) {
        log.warn("Skill 软连接目标不可达，跳过 {}: {}", link, e.getMessage());
      }
      return "";
    }
    Path skillRoot;
    try {
      skillRoot = workspaceRoot.resolve(SKILL_DIR).toRealPath();
    } catch (IOException e) {
      skillRoot = workspaceRoot.resolve(SKILL_DIR).toAbsolutePath().normalize();
    }
    if (!real.startsWith(skillRoot)) {
      if (log.isWarnEnabled()) {
        log.warn("Skill 软连接越界，拒绝 {} -> {}", link, real);
      }
      return "";
    }
    Path meta = real.resolve(SKILL_FILE);
    if (!Files.isRegularFile(meta)) {
      if (log.isWarnEnabled()) {
        log.warn("Skill 缺 {}: {}", SKILL_FILE, real);
      }
      return "";
    }
    String skillName;
    Path realName = real.getFileName();
    Path linkName = link.getFileName();
    skillName =
        realName != null ? realName.toString() : (linkName != null ? linkName.toString() : "skill");
    Map<String, Object> frontmatter;
    try {
      frontmatter = frontmatterOf(meta, skillName);
    } catch (RuntimeException e) {
      if (log.isWarnEnabled()) {
        log.warn("Skill 元数据不可用，跳过 {}: {}", skillName, e.getMessage());
      }
      return "";
    }
    String name = value(frontmatter, "name", skillName);
    String description = value(frontmatter, "description", "");
    return "- " + name + ": " + description + "（读取路径: " + real.toAbsolutePath() + "）";
  }

  private String skillLine(String skillName) {
    Path skillFile = workspaceRoot.resolve(SKILL_DIR).resolve(skillName).resolve(SKILL_FILE);
    if (!Files.isRegularFile(skillFile)) {
      throw new IllegalStateException(
          "Profile 引用的 Skill 不存在: " + skillName + "（期望文件 " + skillFile + "）");
    }
    Map<String, Object> frontmatter = frontmatterOf(skillFile, skillName);
    String name = value(frontmatter, "name", skillName);
    String description = value(frontmatter, "description", "");
    return "- " + name + ": " + description + "（读取路径: " + skillFile.toAbsolutePath() + "）";
  }

  private Map<String, Object> frontmatterOf(Path skillFile, String skillName) {
    String content = read(skillFile);
    if (!content.startsWith("---")) {
      throw new IllegalStateException("Skill 元数据缺失（应以 --- 开头）: " + skillName);
    }
    int end = content.indexOf("\n---", 3);
    if (end < 0) {
      throw new IllegalStateException("Skill 元数据未闭合（缺少结束的 ---）: " + skillName);
    }
    try {
      Object parsed = new Yaml().load(content.substring(content.indexOf('\n') + 1, end + 1));
      if (parsed instanceof Map) {
        return (Map<String, Object>) parsed;
      }
      throw new IllegalStateException("Skill 元数据须是键值对: " + skillName);
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "Skill 元数据不可解析: " + skillName + "（" + e.getMessage() + "）", e);
    }
  }

  private static String value(Map<String, Object> frontmatter, String key, String fallback) {
    Object raw = frontmatter.get(key);
    return raw == null || String.valueOf(raw).isBlank() ? fallback : String.valueOf(raw);
  }

  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("读取失败: " + file, e);
    }
  }
}
