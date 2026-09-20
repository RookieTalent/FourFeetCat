package org.fourfeetcat.core.react;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.fourfeetcat.core.profile.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * 启动上下文供给（技术方案 §8.3）：按 Profile 的 bootstrap 读启动信息文件，按 skills 读公共 Skill 库的元数据。
 *
 * <p>两条铁律：每次组装都重新读文件、绝不缓存（用户改完下一轮立即生效）；显式引用的东西缺失必须报错，启动信息 文件缺失至少告警——静默跳过会造成"人格悄悄丢了"这种最难查的软故障。
 *
 * <p>Skill 的取值口径：本节按名单直读 {@code <root>/skills/<name>/SKILL.md}；第29节换成 Agent 目录下的相对软连接
 * 视图（含真实路径校验），对外行为不变。
 */
public class ContextLoader {

  private static final Logger log = LoggerFactory.getLogger(ContextLoader.class);
  private static final String SKILL_DIR = "skills";
  private static final String SKILL_FILE = "SKILL.md";

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
    for (String name : profile.skills()) {
      text.append(skillLine(name)).append('\n');
    }
    return text.toString().trim();
  }

  /** 只注入名称、描述与本地绝对读取路径三样；正文与附属资源由模型按需读（渐进披露，不预载）。 */
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
