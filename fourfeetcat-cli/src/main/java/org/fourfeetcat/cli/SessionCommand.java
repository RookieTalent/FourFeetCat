package org.fourfeetcat.cli;

import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * {@code session} 分组节点：两段命令名在 Picocli 必须经一层分组。
 *
 * <p>它替子命令向上要一次引擎——子命令离根命令隔了一层，让分组节点做这一跳比在每层重复取根命令清楚。
 */
@Command(
    name = "session",
    description = "看会话",
    mixinStandardHelpOptions = true,
    subcommands = {SessionListCommand.class})
class SessionCommand implements Runnable {

  @ParentCommand private FourFeetCatCli root;

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  ConfigurableApplicationContext engine() {
    return root.engine(WebApplicationType.NONE);
  }
}
