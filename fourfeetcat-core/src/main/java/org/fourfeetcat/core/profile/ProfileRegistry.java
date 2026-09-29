package org.fourfeetcat.core.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 派生 Profile 的内存索引，按 name 查找（第29节改造点：由"构造器一次性灌入不可变 List"改为可变并发容器，补运行时
 * register/remove/exists——让"启动扫描"与"运行时新增 Agent（第30节）"走同一段注册校验）。
 *
 * <p>容器用 {@link Collections#synchronizedMap(java.util.Map) synchronizedMap} 包 {@link LinkedHashMap}
 * 而非 {@link java.util.concurrent.ConcurrentHashMap}：第25节定时注册显式依赖"遍历顺序 = 注册顺序、重名裁决可复现"，
 * ConcurrentHashMap 无序会破坏该语义。register/remove 是低频事件（启动 + 30 节动态管理），粗粒度锁足够。
 */
public class ProfileRegistry {

  private static final Logger log = LoggerFactory.getLogger(ProfileRegistry.class);

  private final Map<String, Profile> profiles = Collections.synchronizedMap(new LinkedHashMap<>());

  public ProfileRegistry() {}

  /**
   * 注册一个 Profile（AgentLoader 派生 / 手写 Profile 加载后汇聚到这里）。派生与校验（provider 名可解析、字段必填）由调用方
   * ProfileLoader/AgentLoader 完成，本类只管"按 name 收索引"。同名后注册覆盖先注册并记 warn。
   */
  public void register(Profile profile) {
    Profile previous;
    synchronized (profiles) {
      previous = profiles.put(profile.name(), profile);
    }
    if (previous != null && log.isWarnEnabled()) {
      log.warn("Profile 名重复，后加载覆盖先加载: {}", profile.name());
    }
  }

  public void remove(String name) {
    profiles.remove(name);
  }

  public boolean exists(String name) {
    synchronized (profiles) {
      return profiles.containsKey(name);
    }
  }

  public Optional<Profile> find(String name) {
    synchronized (profiles) {
      return Optional.ofNullable(profiles.get(name));
    }
  }

  /** 全部已加载 Agent 的不可变快照（第25节：定时注册要"扫一遍所有 Profile"）；顺序 = 注册顺序。 */
  public List<Profile> all() {
    synchronized (profiles) {
      return List.copyOf(new ArrayList<>(profiles.values()));
    }
  }
}
