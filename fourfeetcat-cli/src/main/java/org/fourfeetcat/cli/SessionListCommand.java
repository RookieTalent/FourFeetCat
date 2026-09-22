package org.fourfeetcat.cli;

import java.util.List;
import org.fourfeetcat.storage.SessionEntity;
import org.fourfeetcat.storage.SessionRepository;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/** {@code fourfeetcat session list}：列会话（最近聊过的排前面）。重命令——数据只能从库里取，不另建一份。 */
@SuppressWarnings({"PMD.SystemPrintln", "PMD.CloseResource"})
@Command(name = "list", description = "列出会话（按最后活跃时间倒序）", mixinStandardHelpOptions = true)
class SessionListCommand implements Runnable {

  @ParentCommand private SessionCommand parent;

  @Override
  public void run() {
    // 容器生命周期归进程，命令只借来读一次库
    ConfigurableApplicationContext context = parent.engine();
    List<SessionEntity> sessions =
        context.getBean(SessionRepository.class).findAllByOrderByLastActiveAtDesc();
    if (sessions.isEmpty()) {
      System.out.println("无");
      return;
    }
    for (SessionEntity session : sessions) {
      System.out.println(
          session.getSessionId()
              + "  ["
              + session.getChannel()
              + "/"
              + session.getUserId()
              + "/"
              + session.getProfileName()
              + "]  最后活跃 "
              + session.getLastActiveAt());
    }
  }
}
