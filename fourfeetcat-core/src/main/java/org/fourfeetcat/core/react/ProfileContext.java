package org.fourfeetcat.core.react;

import org.fourfeetcat.core.profile.Profile;

/**
 * 本次执行线程可见的当前 Agent（第17节课件：ProfileContext）。
 *
 * <p>为什么需要它：工具执行时需要知道"当前是哪个 Agent"（例如按 Profile 过滤、查它可用的通知渠道），而工具接口 签名不带 Profile。改接口代价太大，于是由编排者在入口放进
 * ThreadLocal、出口清掉。虚拟线程下每个请求独占一个 线程，天然不串号——但**入口出口都在编排者一处**，漏清就会把 A 的配置用到 B 身上。
 */
public final class ProfileContext {

  private static final ThreadLocal<Profile> HOLDER = new ThreadLocal<>();

  private ProfileContext() {}

  public static void set(Profile profile) {
    HOLDER.set(profile);
  }

  /** 未设置时返回 null（不抛异常：工具可能在没有 Agent 上下文的场景下被直接调用）。 */
  public static Profile current() {
    return HOLDER.get();
  }

  public static void clear() {
    HOLDER.remove();
  }
}
