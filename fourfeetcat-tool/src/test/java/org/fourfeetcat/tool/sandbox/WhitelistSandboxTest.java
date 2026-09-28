package org.fourfeetcat.tool.sandbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 白名单沙箱（课件第24节 harness 主体）：三类校验各"允许 + 拒绝"成对，外加绕过与边界场景。
 *
 * <p>安全模块的 harness 有个特殊性：<b>测的重点不是"放行对不对"，是"绕得过绕不过"</b>。所以下面每条拒绝用例都点着一种绕过手法——相对路径穿越、软链外指、形似通配域名。
 */
class WhitelistSandboxTest {

  /** 白名单根（存在，且是真实路径）。 */
  @TempDir Path root;

  /** 白名单之外的目录：挡住它的每一次尝试都要在这里看出痕迹。 */
  @TempDir Path outside;

  private static WhitelistSandbox sandbox(Path root, List<String> commands, List<String> domains) {
    return new WhitelistSandbox(
        new FileSandboxProperties(List.of(root.toString())),
        new ShellSandboxProperties(commands),
        new HttpSandboxProperties(domains));
  }

  // ---------- 一、文件路径 ----------

  @Test
  @DisplayName("白名单内的路径_读_写_列目录都放行")
  void pathInsideRoot_isAllowed() {
    WhitelistSandbox sandbox = sandbox(root, List.of(), List.of());

    assertThatCode(
            () ->
                sandbox.enforce(
                    new SandboxAction(ActionType.FILE_WRITE, root.resolve("note.txt").toString())))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                sandbox.enforce(
                    new SandboxAction(ActionType.FILE_READ, root.resolve("note.txt").toString())))
        .doesNotThrowAnyException();
    assertThatCode(() -> sandbox.enforce(new SandboxAction(ActionType.FILE_READ, root.toString())))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("白名单外的路径_拒绝")
  void pathOutsideRoot_isBlocked() {
    WhitelistSandbox sandbox = sandbox(root, List.of(), List.of());

    assertThatThrownBy(
            () ->
                sandbox.enforce(
                    new SandboxAction(ActionType.FILE_READ, outside.resolve("x.txt").toString())))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("路径不在白名单内");
  }

  @Test
  @DisplayName("相对路径穿越必须被拦")
  void relativeTraversal_isBlocked() {
    WhitelistSandbox sandbox = sandbox(root, List.of(), List.of());
    // 从白名单根出发用 .. 序列爬到白名单之外——字符串层面它"看起来"还在根下面
    String traversed =
        root.resolve("..").resolve("..").resolve("outside").resolve("secret.txt").toString();

    assertThatThrownBy(() -> sandbox.enforce(new SandboxAction(ActionType.FILE_READ, traversed)))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("路径不在白名单内");
  }

  @Test
  @DisplayName("软链指向白名单之外_必须被拦_校验的是真实路径")
  void symlinkEscapingRoot_isBlocked() throws IOException {
    Path secret = outside.resolve("secret.txt");
    Files.writeString(secret, "机密");
    Path link = root.resolve("link-to-secret.txt");
    try {
      Files.createSymbolicLink(link, secret);
    } catch (IOException | UnsupportedOperationException | SecurityException e) {
      // 平台不支持建软链（Windows 未开发者模式 / 无权限）时跳过——是平台能力不足，不是放宽断言
      Assumptions.assumeTrue(false, "本平台建不了软链（" + e.getMessage() + "），这一条留到 Linux/CI 跑");
    }

    WhitelistSandbox sandbox = sandbox(root, List.of(), List.of());

    // 字符串前缀法会在这里放行（link 明明就在 root 下面），严格版不会
    assertThatThrownBy(
            () -> sandbox.enforce(new SandboxAction(ActionType.FILE_READ, link.toString())))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("路径不在白名单内");
  }

  @Test
  @DisplayName("白名单项写成相对路径_构造即拒并点名该项")
  void relativeWhitelistEntry_failsAtConstruction() {
    assertThatThrownBy(
            () ->
                new WhitelistSandbox(
                    new FileSandboxProperties(List.of("relative-workspace")),
                    new ShellSandboxProperties(List.of()),
                    new HttpSandboxProperties(List.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("relative-workspace");
  }

  // ---------- 二、Shell 命令 ----------

  @Test
  @DisplayName("命令白名单内放行_前导空格不算绕过")
  void shellCommand_whitelistedTokenIsAllowed() {
    WhitelistSandbox sandbox = sandbox(root, List.of("ls"), List.of());

    assertThatCode(() -> sandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, "ls")))
        .doesNotThrowAnyException();
    assertThatCode(() -> sandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, "  ls")))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("命令白名单外拒绝_含参数与大小写变体")
  void shellCommand_othersAreBlocked() {
    WhitelistSandbox sandbox = sandbox(root, List.of("ls"), List.of());

    assertThatThrownBy(
            () -> sandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, "rm -rf /")))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("命令不在白名单内: rm");
    // 精确比对：白名单里写 ls 就不认 LS（与域名白名单同一口径：配什么写什么）
    assertThatThrownBy(() -> sandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, "LS")))
        .isInstanceOf(SandboxViolationException.class);
  }

  @Test
  @DisplayName("命令白名单为空_一条都不许跑")
  void shellCommand_emptyWhitelistDeniesAll() {
    WhitelistSandbox sandbox = sandbox(root, List.of(), List.of());

    assertThatThrownBy(() -> sandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, "ls")))
        .isInstanceOf(SandboxViolationException.class);
  }

  // ---------- 三、HTTP 域名 ----------

  @Test
  @DisplayName("通配符域名_不能被形似域名绕过")
  void wildcardDomain_doesNotMatchLookAlike() {
    WhitelistSandbox sandbox = sandbox(root, List.of(), List.of("*.example.com"));

    // 命中子域
    assertThatCode(
            () ->
                sandbox.enforce(
                    new SandboxAction(ActionType.HTTP_REQUEST, "https://api.example.com/x")))
        .doesNotThrowAnyException();
    // evil-example.com 以 "example.com" 结尾，但不是它的子域——少了点号边界就会放它过去
    assertThatThrownBy(
            () ->
                sandbox.enforce(
                    new SandboxAction(ActionType.HTTP_REQUEST, "https://evil-example.com/x")))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("域名不在白名单内: evil-example.com");
    // 裸域不在通配范围内
    assertThatThrownBy(
            () ->
                sandbox.enforce(
                    new SandboxAction(ActionType.HTTP_REQUEST, "https://example.com/x")))
        .isInstanceOf(SandboxViolationException.class);
  }

  @Test
  @DisplayName("域名只比主机名_忽略端口与路径_但大小写敏感")
  void domainComparison_ignoresPortAndPath_butIsCaseSensitive() {
    WhitelistSandbox sandbox = sandbox(root, List.of(), List.of("api.example.com"));

    assertThatCode(
            () ->
                sandbox.enforce(
                    new SandboxAction(
                        ActionType.HTTP_REQUEST, "https://api.example.com:8443/some/path")))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                sandbox.enforce(
                    new SandboxAction(ActionType.HTTP_REQUEST, "https://API.example.com/x")))
        .isInstanceOf(SandboxViolationException.class);
  }

  @Test
  @DisplayName("域名白名单为空_一个都不许访问")
  void domainWhitelist_emptyDeniesAll() {
    WhitelistSandbox sandbox = sandbox(root, List.of(), List.of());

    assertThatThrownBy(
            () ->
                sandbox.enforce(
                    new SandboxAction(ActionType.HTTP_REQUEST, "https://api.example.com/x")))
        .isInstanceOf(SandboxViolationException.class);
  }

  @Test
  @DisplayName("URL 解析不出主机名_按拒绝处理不放行")
  void unparsableUrl_isRejected() {
    WhitelistSandbox sandbox = sandbox(root, List.of(), List.of("example.com"));

    assertThatThrownBy(
            () -> sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, "not a url")))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("URL 无法解析");
    assertThatThrownBy(
            () -> sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, "api.example.com/x")))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("没有主机名");
  }
}
