package org.fourfeetcat.tool.sandbox;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 命令白名单配置（第24节课件）：{@code shell.allowed_commands}——允许执行的可执行文件名。
 *
 * <p>口径：按<b>可执行文件名精确比对</b>（大小写敏感，配什么写什么），只取命令的首个 token；参数不参与比对，且以 argv 直传、不经 shell 解释。
 *
 * <p>留空 = 一条命令都不许跑。把解释器（{@code sh} / {@code bash} / {@code cmd}）列进白名单，等于管理员显式授予本机代码执行权限，**不构成隔离**。
 */
@ConfigurationProperties(prefix = "shell")
public record ShellSandboxProperties(List<String> allowedCommands) {

  /** 键缺省时兜成空列表——"没配"与"配成空"同义：全拒。 */
  public ShellSandboxProperties {
    allowedCommands = allowedCommands == null ? List.of() : List.copyOf(allowedCommands);
  }
}
