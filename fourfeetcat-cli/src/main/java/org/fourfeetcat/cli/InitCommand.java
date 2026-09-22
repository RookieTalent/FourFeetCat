package org.fourfeetcat.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code fourfeetcat init}：初始化工作区。轻命令——只建目录和模板文件，不进容器（秒回）。
 *
 * <p>重复执行是幂等的：已存在的文件一律不覆盖，避免把用户改过的配置冲掉。不建 {@code agents/}、{@code skills/} 空目录 ——那是"一个目录 = 一个
 * Agent"形态落地时（后续节）的事，现在建只会多出一个空定义源。
 */
@SuppressWarnings("PMD.SystemPrintln")
@Command(name = "init", description = "初始化一个 FourFeetCat 工作区", mixinStandardHelpOptions = true)
class InitCommand implements Runnable {

  @Override
  public void run() {
    Path root = Workspace.root();
    List<String> created = new ArrayList<>();
    List<String> kept = new ArrayList<>();
    try {
      Files.createDirectories(root.resolve(Workspace.LOGS_DIR));
      for (Map.Entry<String, String> file : templates().entrySet()) {
        if (Workspace.createIfAbsent(root.resolve(file.getKey()), file.getValue())) {
          created.add(file.getKey());
        } else {
          kept.add(file.getKey());
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("初始化工作区失败: " + root + "（" + e.getMessage() + "）", e);
    }
    System.out.println("工作区已就绪: " + root.toAbsolutePath());
    System.out.println("新建 " + created.size() + " 项，保留已存在的 " + kept.size() + " 项");
    kept.forEach(item -> System.out.println("  保留: " + item));
  }

  /** 相对工作区根的路径 → 模板正文；顺序即创建顺序（默认 Agent 排头）。 */
  private static Map<String, String> templates() {
    Map<String, String> files = new LinkedHashMap<>();
    files.put(Workspace.DEFAULT_PROFILE_FILE, Workspace.profileTemplate("default"));
    files.put(Workspace.MEMORY_FILE, Workspace.template("MEMORY.md"));
    files.put(Workspace.MCP_SERVERS_FILE, Workspace.template(Workspace.MCP_SERVERS_FILE));
    files.put(Workspace.AGENTS_FILE, Workspace.template(Workspace.AGENTS_FILE));
    files.put(Workspace.SOUL_FILE, Workspace.template(Workspace.SOUL_FILE));
    files.put(Workspace.USER_FILE, Workspace.template(Workspace.USER_FILE));
    return files;
  }
}
