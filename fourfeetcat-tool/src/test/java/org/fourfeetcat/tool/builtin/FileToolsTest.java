package org.fourfeetcat.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.fourfeetcat.core.react.ToolExecutor;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.core.tool.ToolInvocationRecorder;
import org.fourfeetcat.core.tool.ToolResult;
import org.fourfeetcat.tool.registry.ToolRegistry;
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
import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * 内置文件工具（课件 harness 一类）：每条都跑"正常能跑通 + 越界会被拦"两条。
 *
 * <p>越界那条刻意断言**文件一个字节都没被改动**——只看抛异常看不出"拦截发生在动手之前"，看副作用才看得出来。
 */
class FileToolsTest {

  /** 放行一切的沙箱替身：本类测的是工具本身，校验语义归沙箱节。 */
  private static final Sandbox ALLOW_ALL = action -> {};

  @TempDir Path tempDir;

  /** 白名单之外的目录：真白名单的用例要能指出"文件不在名单内"这件事。 */
  @TempDir Path outsideDir;

  @Test
  @DisplayName("白名单内的路径_读_写_列目录都跑得通")
  void allowedPath_readWriteAndListWork() throws IOException {
    FileTools tools = new FileTools(ALLOW_ALL);
    Path target = tempDir.resolve("note.txt");

    assertThat(
            BuiltinToolTestSupport.tool(tools, "write_file")
                .execute(BuiltinToolTestSupport.json("path", target.toString(), "content", "第一行")))
        .extracting(result -> result.success())
        .isEqualTo(true);

    assertThat(
            BuiltinToolTestSupport.tool(tools, "read_file")
                .execute(BuiltinToolTestSupport.json("path", target.toString()))
                .content())
        .isEqualTo("第一行");

    assertThat(
            BuiltinToolTestSupport.tool(tools, "list_dir")
                .execute(BuiltinToolTestSupport.json("path", tempDir.toString()))
                .content())
        .contains("note.txt");
  }

  @Test
  @DisplayName("越界会被拦_且目标文件一个字节都没被改动")
  void blockedWrite_leavesTargetUntouched() throws IOException {
    Path target = tempDir.resolve("protected.txt");
    Files.writeString(target, "原始内容");

    Sandbox sandbox = mock(Sandbox.class);
    doThrow(new SandboxViolationException("路径不在白名单内")).when(sandbox).enforce(any());
    CatTool writeFile = BuiltinToolTestSupport.tool(new FileTools(sandbox), "write_file");

    assertThatThrownBy(
            () ->
                writeFile.execute(
                    BuiltinToolTestSupport.json("path", target.toString(), "content", "改掉")))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("白名单");

    // 拦在动手之前：文件内容原封不动，才是真的拦住了
    assertThat(Files.readString(target)).isEqualTo("原始内容");
    verify(sandbox)
        .enforce(
            argThat(
                action ->
                    action.type() == ActionType.FILE_WRITE
                        && target.toString().equals(action.target())));
  }

  @Test
  @DisplayName("越界读_同样在动手之前被拦下")
  void blockedRead_isRejectedBeforeReading() {
    Sandbox sandbox = mock(Sandbox.class);
    doThrow(new SandboxViolationException("路径不在白名单内")).when(sandbox).enforce(any());
    CatTool readFile = BuiltinToolTestSupport.tool(new FileTools(sandbox), "read_file");

    assertThatThrownBy(
            () ->
                readFile.execute(
                    BuiltinToolTestSupport.json("path", tempDir.resolve("x.txt").toString())))
        .isInstanceOf(SandboxViolationException.class);

    verify(sandbox).enforce(argThat(action -> action.type() == ActionType.FILE_READ));
  }

  @Test
  @DisplayName("真白名单下越界写_目标文件一个字节都没被改动")
  void blockedWrite_withRealWhitelist_leavesTargetUntouched() throws IOException {
    // 白名单根只有 tempDir；待改的文件在 outsideDir（白名单之外，且真实存在）
    Path target = outsideDir.resolve("protected.txt");
    Files.writeString(target, "原始内容");

    WhitelistSandbox sandbox =
        new WhitelistSandbox(
            new FileSandboxProperties(List.of(tempDir.toString())),
            new ShellSandboxProperties(List.of()),
            new HttpSandboxProperties(List.of()));
    CatTool writeFile = BuiltinToolTestSupport.tool(new FileTools(sandbox), "write_file");

    assertThatThrownBy(
            () ->
                writeFile.execute(
                    BuiltinToolTestSupport.json("path", target.toString(), "content", "改掉")))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("路径不在白名单内");

    // 真校验走一遍：内容原封不动，才是真的没写成
    assertThat(Files.readString(target)).isEqualTo("原始内容");
  }

  @Test
  @DisplayName("真白名单拦下的动作_走既有审计路径落一条失败记录_原因人能读懂")
  void blockedAction_landsInAuditAsFailureWithReadableReason() {
    WhitelistSandbox sandbox =
        new WhitelistSandbox(
            new FileSandboxProperties(List.of(tempDir.toString())),
            new ShellSandboxProperties(List.of()),
            new HttpSandboxProperties(List.of()));
    ToolRegistry registry = new ToolRegistry();
    registry.registerAnnotated(new FileTools(sandbox));
    ToolInvocationRecorder audit = mock(ToolInvocationRecorder.class);

    ToolResult result =
        new ToolExecutor(registry, audit)
            .execute(
                "cli:wang:default",
                new AssistantMessage.ToolCall(
                    "call-1",
                    "function",
                    "read_file",
                    BuiltinToolTestSupport.json("path", outsideDir.resolve("secret.txt").toString())
                        .toString()));

    // 沙箱拒绝就是一次普通的工具失败：不新开审计通路，原因是可读的越界说明
    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("路径不在白名单内");
    verify(audit)
        .record(
            eq("cli:wang:default"),
            eq("read_file"),
            anyString(),
            isNull(), // 失败路径没有结果内容——审计列里要的是原因，不是空内容
            eq(false),
            contains("路径不在白名单内"),
            anyLong());
  }
}
