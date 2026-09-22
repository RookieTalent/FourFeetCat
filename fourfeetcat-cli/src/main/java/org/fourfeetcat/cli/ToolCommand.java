package org.fourfeetcat.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** {@code tool} 分组节点：两段命令名在 Picocli 必须经一层分组，自身无行为。 */
@Command(
    name = "tool",
    description = "看工具",
    mixinStandardHelpOptions = true,
    subcommands = {ToolListCommand.class})
class ToolCommand implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }
}
