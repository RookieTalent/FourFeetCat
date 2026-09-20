package org.fourfeetcat.core.react;

import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.profile.ProfileRegistry;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.core.session.SessionManager;

/**
 * 一次处理的编排者（第17节课件）：三种触发源（CLI / Web / 定时）最终都调这一个入口。
 *
 * <p>薄薄一层，但有一件非做不可的事——{@link ProfileContext} 的进出配对：工具执行时靠它知道"当前是哪个 Agent"。进的时候
 * set、出的时候（**含异常路径**）clear，漏了就会在并发复用线程时串号。
 */
public class AgentService {

  private final ProfileRegistry profileRegistry;
  private final ReActLoop reActLoop;
  private final SessionManager sessionManager;

  public AgentService(
      ProfileRegistry profileRegistry, ReActLoop reActLoop, SessionManager sessionManager) {
    this.profileRegistry = profileRegistry;
    this.reActLoop = reActLoop;
    this.sessionManager = sessionManager;
  }

  public String process(Session session, String userMessage) {
    Profile profile =
        profileRegistry
            .find(session.getProfileName())
            .orElseThrow(
                () -> new IllegalStateException("会话归属的 Agent 未注册: " + session.getProfileName()));
    ProfileContext.set(profile);
    try {
      String reply = reActLoop.run(session, userMessage, profile);
      // 累积完的历史落盘：下次接着用、事后可查（第18节给出真正的存储实现）
      sessionManager.save(session);
      return reply;
    } finally {
      ProfileContext.clear();
    }
  }
}
