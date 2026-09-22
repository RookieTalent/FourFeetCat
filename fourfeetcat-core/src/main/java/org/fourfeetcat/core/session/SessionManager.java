package org.fourfeetcat.core.session;

import java.util.Optional;

/**
 * 会话持久化出口（依赖倒置）：core 的编排者依赖它，存储侧出实现。
 *
 * <p>三件事：按"渠道 + 用户 + Agent"三元组取或建、按标识取、存。**会话标识的拼接只允许发生在实现内部这一处**—— 所有入口（CLI 传 {@code "cli"}、Web 传
 * {@code "web"}、定时传 {@code "scheduler"}）只提供三元组，两处各拼一遍、格式差 一个分隔符，同一个人就会出现两条互不相认的历史。
 */
public interface SessionManager {

  /** 同一三元组历次调用必须返回同一条会话（幂等）——多轮对话靠它串起来；没有就建一条空历史的。 */
  Session getOrCreate(String channel, String user, String profileName);

  /** 按标识取会话；不存在返回空（"还没开始聊"是正常分支，不是错误）。 */
  Optional<Session> get(String sessionId);

  /** 把累积完的会话落盘（第17节既有语义不变）。 */
  void save(Session session);
}
