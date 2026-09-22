package org.fourfeetcat.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** {@code fourfeetcat profile show <name>}：打印该 Agent 的配置原文。轻命令。 */
@SuppressWarnings("PMD.SystemPrintln")
@Command(name = "show", description = "打印某个 Agent 的配置", mixinStandardHelpOptions = true)
class ProfileShowCommand implements Runnable {

  @Parameters(index = "0", description = "Agent 名")
  private String name;

  @Override
  public void run() {
    Path file = Workspace.profileFile(name);
    if (!Files.isRegularFile(file)) {
      throw new IllegalStateException("Agent 配置不存在: " + file);
    }
    try {
      System.out.print(Files.readString(file, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException("读取 Agent 配置失败: " + file + "（" + e.getMessage() + "）", e);
    }
  }
}
