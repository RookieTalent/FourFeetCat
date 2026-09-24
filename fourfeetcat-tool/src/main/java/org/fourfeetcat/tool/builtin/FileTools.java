package org.fourfeetcat.tool.builtin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.fourfeetcat.tool.registry.PlainTextResultConverter;
import org.fourfeetcat.tool.sandbox.ActionType;
import org.fourfeetcat.tool.sandbox.Sandbox;
import org.fourfeetcat.tool.sandbox.SandboxAction;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 内置文件工具三件（第20节课件）：{@code read_file} / {@code write_file} / {@code list_dir}。
 *
 * <p>每个方法体的**第一行**都是沙箱校验——校验不过直接抛异常，真正的文件 IO 根本没发生。这是硬规矩：把校验写在 IO 之后等于 没写。
 *
 * <p>受检异常在这里就地转成非受检（{@link UncheckedIOException}）：{@code @Tool} 方法的异常会被框架包一层，包了之后审计表里
 * 的失败原因就成了"调用失败"这种废话；带着路径重新抛，事故才查得动。
 */
public class FileTools {

  private final Sandbox sandbox;

  public FileTools(Sandbox sandbox) {
    this.sandbox = sandbox;
  }

  @Tool(
      name = "read_file",
      description = "读取一个文本文件的内容",
      resultConverter = PlainTextResultConverter.class)
  public String readFile(@ToolParam(description = "要读的文件路径") String path) {
    sandbox.enforce(new SandboxAction(ActionType.FILE_READ, path));
    try {
      return Files.readString(Path.of(path));
    } catch (IOException e) {
      throw new UncheckedIOException("读取文件失败: " + path, e);
    }
  }

  @Tool(
      name = "write_file",
      description = "把内容写入文件（整文件覆盖）",
      resultConverter = PlainTextResultConverter.class)
  public String writeFile(
      @ToolParam(description = "要写的文件路径") String path,
      @ToolParam(description = "要写入的完整内容") String content) {
    sandbox.enforce(new SandboxAction(ActionType.FILE_WRITE, path));
    try {
      Files.writeString(Path.of(path), content);
    } catch (IOException e) {
      throw new UncheckedIOException("写入文件失败: " + path, e);
    }
    return "已写入 " + path;
  }

  @Tool(
      name = "list_dir",
      description = "列出一个目录下的条目名",
      resultConverter = PlainTextResultConverter.class)
  public String listDir(@ToolParam(description = "要列的目录路径") String path) {
    sandbox.enforce(new SandboxAction(ActionType.FILE_READ, path));
    try (Stream<Path> entries = Files.list(Path.of(path))) {
      // 取文件名前先滤掉 null：Path.getFileName() 在根路径上是 null，直接 toString 会 NPE
      List<String> names =
          entries
              .map(Path::getFileName)
              .filter(Objects::nonNull)
              .map(Path::toString)
              .sorted()
              .toList();
      return names.isEmpty() ? "（空目录）" : String.join("\n", names);
    } catch (IOException e) {
      throw new UncheckedIOException("列目录失败: " + path, e);
    }
  }
}
