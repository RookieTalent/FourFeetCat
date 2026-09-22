package org.fourfeetcat.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** {@code provider} 分组节点：两段命令名在 Picocli 必须经一层分组，自身无行为。 */
@Command(
    name = "provider",
    description = "看 provider 配置",
    mixinStandardHelpOptions = true,
    subcommands = {ProviderListCommand.class})
class ProviderCommand implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }
}
