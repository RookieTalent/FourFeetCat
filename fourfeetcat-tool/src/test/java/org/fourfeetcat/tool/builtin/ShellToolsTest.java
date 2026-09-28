package org.fourfeetcat.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.tool.sandbox.ActionType;
import org.fourfeetcat.tool.sandbox.FileSandboxProperties;
import org.fourfeetcat.tool.sandbox.HttpSandboxProperties;
import org.fourfeetcat.tool.sandbox.Sandbox;
import org.fourfeetcat.tool.sandbox.SandboxViolationException;
import org.fourfeetcat.tool.sandbox.ShellSandboxProperties;
import org.fourfeetcat.tool.sandbox.WhitelistSandbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 内置命令执行工具（课件 harness 一类）：正常能跑通 + 越界会被拦，另加超时与输出截断两条边界。
 *
 * <p>用**当前这个 JVM 自己的可执行文件**当被测命令：跨平台、必然存在、必然可执行——比 shell 内建命令可靠得多。
 */
class ShellToolsTest {

  private static final String JAVA = ProcessHandle.current().info().command().orElseThrow();
  private static final Sandbox ALLOW_ALL = action -> {};
  private static final Duration LONG_ENOUGH = Duration.ofSeconds(30);
  private static final int ROOMY = 100_000;

  /** 越界命令那条用例要挑一个"若执行就会留下痕迹"的命令，两家平台的写法不同。 */
  private static final boolean IS_WINDOWS =
      System.getProperty("os.name").toLowerCase().contains("win");

  @TempDir Path tempDir;

  @Test
  @DisplayName("白名单内的命令_正常返回输出")
  void allowedCommand_returnsOutput() {
    CatTool shell =
        BuiltinToolTestSupport.tool(new ShellTools(ALLOW_ALL, LONG_ENOUGH, ROOMY), "shell");

    String output =
        shell
            .execute(BuiltinToolTestSupport.json("command", JAVA, "args", List.of("-version")))
            .content();

    assertThat(output).contains("version");
  }

  @Test
  @DisplayName("越界命令_在启动进程之前就被拦下")
  void blockedCommand_isRejectedBeforeStartingProcess() {
    Sandbox sandbox = mock(Sandbox.class);
    doThrow(new SandboxViolationException("命令不在白名单内")).when(sandbox).enforce(any());
    CatTool shell =
        BuiltinToolTestSupport.tool(new ShellTools(sandbox, LONG_ENOUGH, ROOMY), "shell");

    // 异常从 enforce 抛出，后面的 start() 根本没执行——这就是"校验先于动手"的直接证据
    assertThatThrownBy(
            () ->
                shell.execute(
                    BuiltinToolTestSupport.json("command", JAVA, "args", List.of("-version"))))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("白名单");

    verify(sandbox)
        .enforce(
            argThat(
                action ->
                    action.type() == ActionType.SHELL_COMMAND && JAVA.equals(action.target())));
  }

  @Test
  @DisplayName("命令超时_限时终止并返回失败_不无限挂住循环")
  void timedOutCommand_isKilledAndFails() {
    // 1 毫秒对上 JVM 启动（几十毫秒）必然超时——用当前 JVM 的可执行文件当"慢命令"，跨平台且确定
    CatTool shell =
        BuiltinToolTestSupport.tool(
            new ShellTools(ALLOW_ALL, Duration.ofMillis(1), ROOMY), "shell");

    assertThatThrownBy(
            () ->
                shell.execute(
                    BuiltinToolTestSupport.json("command", JAVA, "args", List.of("-version"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("超时");
  }

  @Test
  @DisplayName("真白名单下越界命令_进程根本没起来")
  void blockedCommand_withRealWhitelist_processNeverStarts() {
    // 白名单只放行当前 JVM 自己；下面这条命令若真跑起来，就会在 tempDir 里留下一个标记文件
    WhitelistSandbox sandbox =
        new WhitelistSandbox(
            new FileSandboxProperties(List.of()),
            new ShellSandboxProperties(List.of(JAVA)),
            new HttpSandboxProperties(List.of()));
    CatTool shell =
        BuiltinToolTestSupport.tool(new ShellTools(sandbox, LONG_ENOUGH, ROOMY), "shell");

    Path marker = tempDir.resolve("marker.txt");
    String shellName = IS_WINDOWS ? "cmd" : "sh";
    List<String> shellArgs =
        IS_WINDOWS ? List.of("/c", "echo x > " + marker) : List.of("-c", "echo x > " + marker);

    assertThatThrownBy(
            () ->
                shell.execute(BuiltinToolTestSupport.json("command", shellName, "args", shellArgs)))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("命令不在白名单内: " + shellName);

    // 看副作用才看得出"拦在动手之前"：进程没起来，标记文件就不该存在
    assertThat(Files.exists(marker)).isFalse();
  }

  @Test
  @DisplayName("输出过长_截断并注明")
  void longOutput_isTruncatedWithNote() {
    String full = runWithLimit(ROOMY);
    // 前提先立住：这条命令的输出确实长过后面设的上限，否则下面的断言全在空转
    assertThat(full.length()).isGreaterThan(10);

    String truncated = runWithLimit(10);

    assertThat(truncated).startsWith(full.substring(0, 10)).contains("已截断");
  }

  private static String runWithLimit(int maxOutputChars) {
    return BuiltinToolTestSupport.tool(
            new ShellTools(ALLOW_ALL, LONG_ENOUGH, maxOutputChars), "shell")
        .execute(BuiltinToolTestSupport.json("command", JAVA, "args", List.of("-version")))
        .content();
  }
}
