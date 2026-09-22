package org.fourfeetcat.cli;

import picocli.CommandLine.Command;

/**
 * {@code fourfeetcat tool list}：列可用工具。
 *
 * <p>工具注册（统一工具抽象 + 工具注册表）归第20节，本节接空实现占位——命令在位、可跑、{@code --help} 正常；第20节交付后 只换数据源，命令本身不改。
 */
@SuppressWarnings("PMD.SystemPrintln")
@Command(name = "list", description = "列出当前可用的工具", mixinStandardHelpOptions = true)
class ToolListCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("当前无可用工具（工具注册归第 20 节）");
  }
}
