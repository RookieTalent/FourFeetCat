package org.fourfeetcat.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import picocli.CommandLine.Command;

/** {@code fourfeetcat profile list}：看有哪些 Agent。轻命令，列个目录不值得等容器启动 2~4 秒。 */
@SuppressWarnings("PMD.SystemPrintln")
@Command(name = "list", description = "列出工作区里有哪些 Agent", mixinStandardHelpOptions = true)
class ProfileListCommand implements Runnable {

  private static final String SUFFIX = ".yaml";

  @Override
  public void run() {
    List<String> names = names();
    if (names.isEmpty()) {
      System.out.println("工作区里还没有 Agent（可先执行 fourfeetcat init）");
      return;
    }
    names.forEach(System.out::println);
  }

  private static List<String> names() {
    Path dir = Workspace.profilesDir();
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir)) {
      // 用 relativize 取名字而不是 getFileName()：对目录项永远非空，省掉一次可空解引用
      return files
          .filter(Files::isRegularFile)
          .map(dir::relativize)
          .map(Path::toString)
          .filter(name -> name.endsWith(SUFFIX))
          .map(name -> name.substring(0, name.length() - SUFFIX.length()))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("读取 Agent 配置目录失败: " + dir + "（" + e.getMessage() + "）", e);
    }
  }
}
