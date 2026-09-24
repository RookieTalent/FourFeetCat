package org.fourfeetcat.tool.builtin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.fourfeetcat.tool.registry.PlainTextResultConverter;
import org.fourfeetcat.tool.sandbox.ActionType;
import org.fourfeetcat.tool.sandbox.Sandbox;
import org.fourfeetcat.tool.sandbox.SandboxAction;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 内置命令执行工具（第20节课件）：{@code shell}。
 *
 * <p>三条硬规矩：① 第一行过沙箱校验（命令白名单），不过就拦下；② 可执行文件与参数以 **argv 数组直传**，不经 shell 解释—— 参数里带 {@code ;} 或 {@code
 * |} 不会被当成语法；③ 带超时，到点强杀，不让同步循环无限挂住。
 *
 * <p>输出还有长度上限：一次把几十兆日志灌进模型上下文，等于把这一轮对话直接撑爆。
 */
public class ShellTools {

  private final Sandbox sandbox;
  private final Duration timeout;
  private final int maxOutputChars;

  /**
   * @param timeout 单条命令的执行上限
   * @param maxOutputChars 输出超过这个长度即截断并注明
   */
  public ShellTools(Sandbox sandbox, Duration timeout, int maxOutputChars) {
    this.sandbox = sandbox;
    this.timeout = timeout;
    this.maxOutputChars = maxOutputChars;
  }

  @Tool(
      name = "shell",
      description = "执行一条白名单内的命令；参数按数组直传，不解释 shell 语法",
      resultConverter = PlainTextResultConverter.class)
  public String shell(
      @ToolParam(description = "可执行文件名（须在白名单内）") String command,
      @ToolParam(description = "参数数组，逐项传递", required = false) List<String> args) {
    sandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, command));

    List<String> argv = new ArrayList<>();
    argv.add(command);
    if (args != null) {
      argv.addAll(args);
    }
    Process process;
    try {
      // redirectErrorStream：把 stderr 并进 stdout，模型看到的是一份完整输出，不用猜另一半去哪了
      process = new ProcessBuilder(argv).redirectErrorStream(true).start();
    } catch (IOException e) {
      throw new UncheckedIOException("启动命令失败: " + command, e);
    }

    try {
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        throw new IllegalStateException("命令执行超时（" + timeout.toSeconds() + " 秒），已终止: " + command);
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      return truncate(output);
    } catch (InterruptedException e) {
      // 恢复中断标志：吞掉中断会让停机链路卡死
      Thread.currentThread().interrupt();
      throw new IllegalStateException("命令执行被打断: " + command, e);
    } catch (IOException e) {
      throw new UncheckedIOException("读取命令输出失败: " + command, e);
    }
  }

  private String truncate(String output) {
    if (output.length() <= maxOutputChars) {
      return output;
    }
    return output.substring(0, maxOutputChars)
        + "\n…（输出超过 "
        + maxOutputChars
        + " 字符已截断，共 "
        + output.length()
        + " 字符）";
  }
}
