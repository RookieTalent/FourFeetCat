package org.fourfeetcat.cli;

import org.fourfeetcat.channel.cli.CliChannel;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.session.SessionManager;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/** {@code fourfeetcat chat}：进终端交互。自身零 Agent 智能，读—转交—打印都在 CliChannel 里。 */
@SuppressWarnings("PMD.CloseResource")
@Command(
    name = "chat",
    description = "在终端里和 Agent 交互式对话（/quit 退出）",
    mixinStandardHelpOptions = true)
class ChatCommand implements Runnable {

  @ParentCommand private FourFeetCatCli root;

  @Option(names = "--profile", defaultValue = "default", description = "和哪个 Agent 对话（默认 default）")
  private String profileName;

  @Override
  public void run() {
    // 容器生命周期归进程，命令只借来取两个 Bean
    ConfigurableApplicationContext context = root.engine(WebApplicationType.NONE);
    new CliChannel(context.getBean(AgentService.class), context.getBean(SessionManager.class))
        .run(profileName);
  }
}
