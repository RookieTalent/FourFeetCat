package org.fourfeetcat.channel.cli;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.core.session.SessionManager;

/**
 * CLI Channel（技术方案 §8.4）：{@code fourfeetcat chat} 的实现——读 stdin、写 stdout，维护当前会话，每收到一行就交给
 * 引擎跑完整一轮处理，直到用户输入 {@code /quit}。
 *
 * <p>通篇没有任何"Agent 智能"：它就是个读—转交—打印的壳。怎么想、怎么调模型、怎么执行工具，全在交出去的那一端。
 */
public class CliChannel {

  private static final String CHANNEL = "cli";
  private static final String QUIT = "/quit";
  private static final String PROMPT = "> ";

  private final AgentService agentService;
  private final SessionManager sessionManager;
  private final InputStream in;
  private final PrintStream out;

  public CliChannel(AgentService agentService, SessionManager sessionManager) {
    this(agentService, sessionManager, System.in, System.out);
  }

  /** 流可注入：退出与空行这两条分支要有机器守卫，不能只靠手敲。 */
  public CliChannel(
      AgentService agentService, SessionManager sessionManager, InputStream in, PrintStream out) {
    this.agentService = agentService;
    this.sessionManager = sessionManager;
    this.in = in;
    this.out = out;
  }

  /**
   * 进入交互。会话身份只提供"渠道 + 用户 + Agent"三元组，标识怎么拼是会话层的事。
   *
   * <p>输入到底（EOF）也收场：{@code echo 你好 | fourfeetcat chat} 这种管道用法不该崩在 {@code nextLine} 上。
   */
  public void run(String profileName) {
    Session session = sessionManager.getOrCreate(CHANNEL, currentUser(), profileName);
    try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8)) {
      while (scanner.hasNextLine()) {
        out.print(PROMPT);
        String line = scanner.nextLine();
        if (QUIT.equals(line.trim())) {
          break;
        }
        if (line.isBlank()) {
          continue;
        }
        out.println(agentService.process(session, line));
      }
    }
  }

  /** 终端场景没有别的身份来源，取当前系统用户。 */
  private static String currentUser() {
    return System.getProperty("user.name", "unknown");
  }
}
