package org.fourfeetcat.channel.cli;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
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
  private final Charset inputCharset;

  public CliChannel(AgentService agentService, SessionManager sessionManager) {
    this(agentService, sessionManager, System.in, System.out);
  }

  /** 流可注入：退出与空行这两条分支要有机器守卫，不能只靠手敲。 */
  public CliChannel(
      AgentService agentService, SessionManager sessionManager, InputStream in, PrintStream out) {
    this(agentService, sessionManager, in, out, stdinCharset());
  }

  /** 编码可注入：让测试钉死一种编码独立验证，不随跑测试的机器变。 */
  CliChannel(
      AgentService agentService,
      SessionManager sessionManager,
      InputStream in,
      PrintStream out,
      Charset inputCharset) {
    this.agentService = agentService;
    this.sessionManager = sessionManager;
    this.in = in;
    this.out = out;
    this.inputCharset = inputCharset;
  }

  /**
   * 进入交互。会话身份只提供"渠道 + 用户 + Agent"三元组，标识怎么拼是会话层的事。
   *
   * <p>输入到底（EOF）也收场：管道喂进来的用法不该崩在 {@code nextLine} 上。
   */
  public void run(String profileName) {
    Session session = sessionManager.getOrCreate(CHANNEL, currentUser(), profileName);
    try (Scanner scanner = new Scanner(in, inputCharset)) {
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

  /**
   * 控制台输入的编码。
   *
   * <p><b>不能硬编 UTF-8</b>：中文 Windows 的控制台按本地编码（GBK）把字节送进来，拿 UTF-8 去解只会得到替换字符，
   * 而这些字符会被原样交给模型（实测踩过：会话历史里存下的就是乱码）。平台本地编码取 Java 18+ 的公开属性 {@code native.encoding}。
   *
   * <p>若显式设了 {@code stdin.encoding} 系统属性则以它为准：重定向（管道）时上游用什么编码 JVM 猜不到，用户可用 {@code
   * -Dstdin.encoding=UTF-8} 自己定。
   */
  static Charset stdinCharset() {
    String configured = System.getProperty("stdin.encoding");
    if (configured != null && !configured.isBlank()) {
      return Charset.forName(configured);
    }
    return Charset.forName(System.getProperty("native.encoding", Charset.defaultCharset().name()));
  }

  /** 终端场景没有别的身份来源，取当前系统用户。 */
  private static String currentUser() {
    return System.getProperty("user.name", "unknown");
  }
}
