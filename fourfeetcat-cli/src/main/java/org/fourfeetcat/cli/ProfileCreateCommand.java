package org.fourfeetcat.cli;

import java.io.IOException;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** {@code fourfeetcat profile create <name>}：新建一份 Agent 配置模板。轻命令，纯文件写。 */
@SuppressWarnings("PMD.SystemPrintln")
@Command(name = "create", description = "新建一个 Agent 配置（已存在则拒绝覆盖）", mixinStandardHelpOptions = true)
class ProfileCreateCommand implements Runnable {

  @Parameters(index = "0", description = "Agent 名")
  private String name;

  @Override
  public void run() {
    if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains(":")) {
      throw new IllegalArgumentException("Agent 名不能为空，也不能含路径分隔符或冒号: " + name);
    }
    Path file = Workspace.profileFile(name);
    boolean created;
    try {
      created = Workspace.createIfAbsent(file, Workspace.profileTemplate(name));
    } catch (IOException e) {
      throw new IllegalStateException("写 Agent 配置失败: " + file + "（" + e.getMessage() + "）", e);
    }
    if (created) {
      System.out.println("已创建: " + file);
    } else {
      throw new IllegalStateException("Agent 配置已存在，未覆盖: " + file);
    }
  }
}
