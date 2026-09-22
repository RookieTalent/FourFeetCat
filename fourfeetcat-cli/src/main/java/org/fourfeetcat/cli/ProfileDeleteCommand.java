package org.fourfeetcat.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** {@code fourfeetcat profile delete <name>}：删掉该 Agent 配置。轻命令。 */
@SuppressWarnings("PMD.SystemPrintln")
@Command(name = "delete", description = "删掉某个 Agent 的配置", mixinStandardHelpOptions = true)
class ProfileDeleteCommand implements Runnable {

  @Parameters(index = "0", description = "Agent 名")
  private String name;

  @Override
  public void run() {
    Path file = Workspace.profileFile(name);
    try {
      if (!Files.deleteIfExists(file)) {
        throw new IllegalStateException("Agent 配置不存在: " + file);
      }
    } catch (IOException e) {
      throw new IllegalStateException("删除 Agent 配置失败: " + file + "（" + e.getMessage() + "）", e);
    }
    System.out.println("已删除: " + file);
  }
}
