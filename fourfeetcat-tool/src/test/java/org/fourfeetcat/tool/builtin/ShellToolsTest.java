package org.fourfeetcat.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.tool.sandbox.ActionType;
import org.fourfeetcat.tool.sandbox.Sandbox;
import org.fourfeetcat.tool.sandbox.SandboxViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
