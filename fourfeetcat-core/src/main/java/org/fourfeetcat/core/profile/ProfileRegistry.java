package org.fourfeetcat.core.profile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 解析好的 Profile 内存索引，按 name 查找（29 节会给它补运行时 register() 方法，现在只有 启动扫描这一条注册路径）。provider 名可解析性校验在
 * ProfileLoader（拿着文件上下文报错更清晰）。
 */
public class ProfileRegistry {

  private static final Logger log = LoggerFactory.getLogger(ProfileRegistry.class);

  private final Map<String, Profile> profiles = new HashMap<>();

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
}
