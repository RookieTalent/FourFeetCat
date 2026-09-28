package org.fourfeetcat.core.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 解析好的 Profile 内存索引，按 name 查找（29 节会给它补运行时 register() 方法，现在只有 启动扫描这一条注册路径）。provider 名可解析性校验在
 * ProfileLoader（拿着文件上下文报错更清晰）。
 *
 * <p>容器用 {@link LinkedHashMap} 而非 HashMap：遍历顺序 = 加载顺序，令"同一批配置每次启动的注册顺序一致"
 * （第25节的定时注册按遍历顺序落条，重名裁决因此可复现）。
 */
public class ProfileRegistry {

  private static final Logger log = LoggerFactory.getLogger(ProfileRegistry.class);

  private final Map<String, Profile> profiles = new LinkedHashMap<>();

  public ProfileRegistry(List<Profile> loaded) {
    for (Profile profile : loaded) {
      Profile previous = profiles.put(profile.name(), profile);
      if (previous != null && log.isWarnEnabled()) {
        log.warn("Profile 名重复，后加载覆盖先加载: {}", profile.name());
      }
    }
  }

  public Optional<Profile> find(String name) {
    return Optional.ofNullable(profiles.get(name));
  }

  /** 全部已加载 Agent 的不可变快照（第25节：定时注册要"扫一遍所有 Profile"）；顺序 = 加载顺序。 */
  public List<Profile> all() {
    return List.copyOf(profiles.values());
  }
}
