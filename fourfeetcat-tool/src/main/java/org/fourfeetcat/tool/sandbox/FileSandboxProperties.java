package org.fourfeetcat.tool.sandbox;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件白名单配置（第24节课件）：{@code file.allowed_paths}——允许 Agent 读 / 写 / 列目录的路径根。
 *
 * <p>两条口径写在这里，是因为它们决定了 {@link WhitelistSandbox} 的行为：
 *
 * <p>① <b>留空 = 什么都不允许</b>，不是"不校验"。安全配置的默认姿态是关着门。 ② <b>每一项必须是绝对路径</b>：相对路径按当前工作目录解析，而同一份配置在
 * CLI、守护进程、容器三处的工作目录各不相同—— 安全白名单的语义不得随运行环境变形，故非绝对路径由实现类在构造期直接拒绝启动并点名该项。
 */
@ConfigurationProperties(prefix = "file")
public record FileSandboxProperties(List<String> allowedPaths) {

  /** 键缺省时绑定可能给出 null，兜成空列表——"没配"与"配成空"同义：全拒。 */
  public FileSandboxProperties {
    allowedPaths = allowedPaths == null ? List.of() : List.copyOf(allowedPaths);
  }
}
