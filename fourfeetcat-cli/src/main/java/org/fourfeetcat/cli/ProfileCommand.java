package org.fourfeetcat.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** {@code profile} 分组节点：两段命令名（{@code profile list}）在 Picocli 必须经一层分组，自身无行为。 */
@Command(
    name = "profile",
    description = "管理 Agent 配置",
    mixinStandardHelpOptions = true,
    subcommands = {
      ProfileListCommand.class,
      ProfileCreateCommand.class,
      ProfileShowCommand.class,
      ProfileDeleteCommand.class
    })
class ProfileCommand implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }
}
