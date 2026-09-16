package org.fourfeetcat.core.profile;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * 启动时扫 {@code .fourfeetcat/profiles/} 下所有 YAML 解析成 Profile。坏文件记错误日志跳过、 不阻断启动；本节的校验规则只有一条——provider
 * 名能在全局层找到（后续节各自补自己的校验）。
 */
public class ProfileLoader {

  private static final Logger log = LoggerFactory.getLogger(ProfileLoader.class);
  private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");

  private final Function<String, String> envResolver;
  private final Set<String> knownProviders;

  public ProfileLoader(Set<String> knownProviders) {
    this(System::getenv, knownProviders);
  }

  /** env 读取以函数注入，测试可替身（不依赖真实环境变量）。 */
  public ProfileLoader(Function<String, String> envResolver, Set<String> knownProviders) {
    this.envResolver = envResolver;
    this.knownProviders = Set.copyOf(knownProviders);
  }

  public List<Profile> load(Path profilesDir) {
    List<Path> files;
    try (Stream<Path> stream = Files.list(profilesDir)) {
      files = stream.filter(f -> f.toString().endsWith(".yaml")).sorted().toList();
    } catch (IOException e) {
      if (log.isErrorEnabled()) {
        log.error("无法读取 Profile 目录 {}: {}", profilesDir, e.getMessage());
      }
      return List.of();
    }
    List<Profile> profiles = new ArrayList<>();
    for (Path file : files) {
      Profile profile = loadOne(file);
      if (profile != null) {
        profiles.add(profile);
      }
    }
    return List.copyOf(profiles);
  }

  private Profile loadOne(Path file) {
    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      Object raw = new Yaml().load(reader);
      if (!(raw instanceof Map)) {
        throw new IllegalArgumentException("YAML 根节点必须是映射结构");
      }
      Profile profile = toProfile(file, resolveEnvPlaceholders((Map<String, Object>) raw));
      if (!knownProviders.contains(profile.provider().name())) {
        throw new IllegalArgumentException(
            "provider 名 '" + profile.provider().name() + "' 未在全局配置 fourfeetcat.providers 里声明");
      }
      return profile;
    } catch (IOException | RuntimeException e) {
      // 坏 Profile 记错误日志、不阻断启动（课件口径）；任何一种解析/校验失败都收敛到跳过
      if (log.isErrorEnabled()) {
        log.error("解析 Profile 失败，跳过 {}: {}", file.getFileName(), e.getMessage());
      }
      return null;
    }
  }

  /** ${ENV} 占位解析：递归替换标量值；占位对应变量缺失视为校验问题（不静默当空串跑过去）。 */
  private Map<String, Object> resolveEnvPlaceholders(Map<String, Object> map) {
    Map<String, Object> resolved = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      resolved.put(entry.getKey(), resolveValue(entry.getValue()));
    }
    return resolved;
  }

  private Object resolveValue(Object value) {
    if (value instanceof Map) {
      return resolveEnvPlaceholders((Map<String, Object>) value);
    }
    if (value instanceof List) {
      List<Object> resolved = new ArrayList<>();
      for (Object item : (List<?>) value) {
        resolved.add(resolveValue(item));
      }
      return resolved;
    }
    if (value instanceof String text) {
      return resolvePlaceholdersIn(text);
    }
    return value;
  }

  private String resolvePlaceholdersIn(String text) {
    Matcher matcher = ENV_PLACEHOLDER.matcher(text);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String name = matcher.group(1);
      String value = envResolver.apply(name);
      if (value == null) {
        throw new IllegalArgumentException("环境变量未设置: " + name);
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private Profile toProfile(Path file, Map<String, Object> map) {
    String name = requireString(map, "name", file);
    Map<String, Object> providerMap = asMap(map.get("provider"));
    if (providerMap == null || providerMap.get("name") == null) {
      throw new IllegalArgumentException("Profile 缺少 provider.name");
    }
    Double temperature =
        providerMap.get("temperature") == null
            ? null
            : ((Number) providerMap.get("temperature")).doubleValue();
    Map<String, Object> identityMap = asMap(map.get("identity"));
    return new Profile(
        name,
        asNullableString(map.get("description")),
        new Profile.Identity(
            asNullableString(identityMap.get("agent_name")),
            asNullableString(identityMap.get("prompt"))),
        new Profile.ProviderConfig(
            String.valueOf(providerMap.get("name")),
            asNullableString(providerMap.get("model")),
            temperature),
        asStringList(map.get("tools")),
        asStringList(map.get("skills")),
        asStringList(map.get("mcp_servers")),
        asStringList(map.get("channels")),
        asStringList(map.get("notify_channels")),
        asScheduleList(map.get("schedules")),
        asStringList(map.get("bootstrap")),
        asMap(map.get("settings")));
  }

  private String requireString(Map<String, Object> map, String key, Path file) {
    Object value = map.get(key);
    if (value == null || String.valueOf(value).isBlank()) {
      throw new IllegalArgumentException("Profile 缺少必填字段 " + key + ": " + file.getFileName());
    }
    return String.valueOf(value);
  }

  private static String asNullableString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static Map<String, Object> asMap(Object value) {
    if (value == null) {
      return Map.of();
    }
    if (value instanceof Map) {
      return (Map<String, Object>) value;
    }
    throw new IllegalArgumentException("YAML 结构错误：期望映射，实为 " + value.getClass().getSimpleName());
  }

  private static List<String> asStringList(Object value) {
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> list) {
      return list.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }
    throw new IllegalArgumentException("YAML 结构错误：期望列表，实为 " + value.getClass().getSimpleName());
  }

  private static List<Map<String, Object>> asScheduleList(Object value) {
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> list) {
      List<Map<String, Object>> schedules = new ArrayList<>();
      for (Object item : list) {
        Map<String, Object> schedule = asMap(item);
        if (!schedule.isEmpty()) {
          schedules.add(schedule);
        }
      }
      return List.copyOf(schedules);
    }
    throw new IllegalArgumentException("YAML 结构错误：期望列表，实为 " + value.getClass().getSimpleName());
  }
}
