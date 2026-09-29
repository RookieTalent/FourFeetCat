package org.fourfeetcat.core.profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * 插件化 Agent 的加载器（第29节，宪法四）：一个目录 = 一个 Agent。扫描 {@code .fourfeetcat/agents/<name>/} 下每个目录， 把
 * `AGENT.md` 的 frontmatter 派生成底座认识的 {@link Profile}（复用 {@link ProfileLoader#fromYamlMap} 的字段映射与
 * provider 名可解析校验，保证"目录派生 Agent 与手写 Profile 走同一套校验、同一异常同一消息"）。
 *
 * <p><b>渐进披露</b>：本类只负责把 frontmatter 派生成配置、并认出脚本/子指令/参考等资源（{@link #detectResources}），
 * <b>不预载</b>任何正文/脚本/参考正文。Agent 正文在 {@code ContextLoader} 组装 prompt 时注入；脚本/子指令/参考由模型经 底座既有的
 * read_file / shell 按需取用——资源加载天然被限制在自己目录内。
 *
 * <p><b>扫描 vs 单独派生</b>：{@link #deriveProfile(Path)} 是严格派生——缺 name/provider、frontmatter 未闭合等任何问题
 * <b>抛异常点名</b>（harness 断言）；{@link #scan(Path)} 逐个调用并在坏目录上记 error 日志**跳过、不阻断启动**（装配层入口）。
 */
public class AgentLoader {

  private static final Logger log = LoggerFactory.getLogger(AgentLoader.class);

  private static final String AGENT_FILE = "AGENT.md";
  private static final String FRONTMATTER_RESOURCE_REFERENCE = "REFERENCE.md";
  private static final String RESOURCE_SCRIPTS = "scripts";
  private static final String RESOURCE_SKILLS = "skills";

  /** 复用 ProfileLoader 的字段映射 + provider 名校验（同异常、同消息）。 */
  private final ProfileLoader profileLoader;

  public AgentLoader(Set<String> knownProviders) {
    this(System::getenv, knownProviders);
  }

  /** env 读取以函数注入，测试可替身（不依赖真实环境变量）。 */
  public AgentLoader(Function<String, String> envResolver, Set<String> knownProviders) {
    this.profileLoader = new ProfileLoader(envResolver, knownProviders);
  }

  /**
   * 扫 {@code agentsDir} 下每个子目录派生 Profile。坏目录（缺 AGENT.md / frontmatter 未闭合 / 缺必填 / provider 不可解析） 记
   * error 日志点名、跳过，不阻断启动；目录缺失或为空 → 返回空列表。顺序 = 子目录名排序（注册顺序可复现）。
   */
  public List<Profile> scan(Path agentsDir) {
    List<Profile> profiles = new ArrayList<>();
    for (Path dir : listAgentDirs(agentsDir)) {
      try {
        profiles.add(deriveProfile(dir));
      } catch (RuntimeException e) {
        if (log.isErrorEnabled()) {
          log.error("Agent 目录 {} 加载失败，跳过: {}", dir.getFileName(), e.getMessage());
        }
      }
    }
    return List.copyOf(profiles);
  }

  /**
   * 严格派生一个 Agent 目录 → Profile。缺 name / provider、frontmatter 未闭合等**抛异常并点名**（harness 直接
   * 断言这条路径）；{@link #scan} 在此之上做坏目录跳过。
   */
  public Profile deriveProfile(Path agentDir) {
    Path agentFile = agentDir.resolve(AGENT_FILE);
    if (!Files.isRegularFile(agentFile)) {
      throw new IllegalStateException("Agent 目录缺少 " + AGENT_FILE + ": " + agentDir);
    }
    Map<String, Object> frontmatter = frontmatterOf(agentFile);
    return profileLoader.fromYamlMap(agentFile, frontmatter);
  }

  /**
   * 认出该 Agent 目录的附属资源（可选的参考/子指令/脚本）。渐进披露下这些资源**不预载**，只在此告知存在， 由模型经 read_file / shell 按需取用；不存在即为
   * null。
   */
  public AgentResources detectResources(Path agentDir) {
    Path reference = agentDir.resolve(FRONTMATTER_RESOURCE_REFERENCE);
    Path scripts = agentDir.resolve(RESOURCE_SCRIPTS);
    Path skills = agentDir.resolve(RESOURCE_SKILLS);
    return new AgentResources(
        Files.isRegularFile(reference) ? reference : null,
        Files.isDirectory(scripts) ? scripts : null,
        Files.isDirectory(skills) ? skills : null);
  }

  /**
   * 取出 Agent 目录 `AGENT.md` 的**正文**（去掉 frontmatter），供 ContextLoader 注入 system prompt。 没有前导 {@code
   * ---} 或 frontmatter 未闭合时整文件当正文兜底（正文解读优于报错——这是 prompt 源，读不了才报）。
   */
  public static String bodyOf(Path agentFile) {
    try {
      String content = Files.readString(agentFile, StandardCharsets.UTF_8);
      if (!content.startsWith("---")) {
        return content;
      }
      int end = content.indexOf("\n---", 3);
      if (end < 0) {
        return content;
      }
      int bodyStart = end + "\n---".length();
      return bodyStart >= content.length() ? "" : content.substring(bodyStart).stripLeading();
    } catch (IOException e) {
      throw new IllegalStateException("读取 Agent 正文失败: " + agentFile, e);
    }
  }

  private static Map<String, Object> frontmatterOf(Path agentFile) {
    String content;
    try {
      content = Files.readString(agentFile, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("读取 Agent 目录主文件失败: " + agentFile, e);
    }
    if (!content.startsWith("---")) {
      throw new IllegalStateException("AGENT.md 应以 --- 开头: " + agentFile);
    }
    int end = content.indexOf("\n---", 3);
    if (end < 0) {
      throw new IllegalStateException("AGENT.md frontmatter 未闭合（缺少结束的 ---）: " + agentFile);
    }
    Object parsed = new Yaml().load(content.substring(content.indexOf('\n') + 1, end + 1));
    if (!(parsed instanceof Map)) {
      throw new IllegalStateException("AGENT.md frontmatter 须是键值对: " + agentFile);
    }
    return (Map<String, Object>) parsed;
  }

  private static List<Path> listAgentDirs(Path agentsDir) {
    try (Stream<Path> stream = Files.list(agentsDir)) {
      return stream.filter(Files::isDirectory).sorted().toList();
    } catch (IOException e) {
      if (log.isErrorEnabled()) {
        log.error("无法读取 Agent 目录 {}: {}", agentsDir, e.getMessage());
      }
      return List.of();
    }
  }

  /** 一个 Agent 目录里可选附属资源的发现结果（referent only；渐进披露下各资源不预载）。 */
  public record AgentResources(Path referenceFile, Path scriptsDir, Path skillsDir) {}
}
