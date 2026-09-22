package org.fourfeetcat.cli;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.function.Function;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * 命令行根命令（第18节）：12 个子命令挂在这底下，所有操作都经子命令完成。
 *
 * <p>轻重命令分流的实现方式：引擎工厂由 boot 注入、**用到才启动**——轻命令一次都不会调到 {@link #engine}，所以从结构上
 * 就不可能为容器启动付代价，不需要维护一张"哪些命令算重命令"的名单。
 *
 * <p>容器由 boot 的应用配置类起动（那里有 {@code @EnableJpaRepositories} / {@code @EntityScan} 的显式声明，正是课件
 * "坑四"要钉的地方）：命令树不自己持有应用配置类，依赖方向才成立。
 */
@Command(
    name = "fourfeetcat",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    description = "FourFeetCat 命令行入口：跟 Agent 对话、把服务跑起来、查配置和状态",
    subcommands = {
      InitCommand.class,
      StatusCommand.class,
      ChatCommand.class,
      ServeCommand.class,
      GatewayCommand.class,
      ProfileCommand.class,
      ProviderCommand.class,
      ToolCommand.class,
      SessionCommand.class
    })
public class FourFeetCatCli implements Runnable {

  private final Function<WebApplicationType, ConfigurableApplicationContext> engineFactory;
  private ConfigurableApplicationContext startedEngine;

  public FourFeetCatCli(
      Function<WebApplicationType, ConfigurableApplicationContext> engineFactory) {
    this.engineFactory = engineFactory;
  }

  /**
   * 装配好的命令行：报错出口与输出编码一次性收在这里，调用方拿到就能 execute。
   *
   * <p>输出编码必须显式绑到平台流自己的编码上：Picocli 内部的 writer 用的是 {@code file.encoding}（本机是 UTF-8），而 {@code
   * System.out/err} 用的是平台编码（中文 Windows 是 GBK）——不绑就会出现"正常输出正常、报错与 --help 花屏"这种一半好一半坏的现象。
   */
  public CommandLine commandLine() {
    return new CommandLine(this)
        .setOut(new PrintWriter(new OutputStreamWriter(System.out, System.out.charset()), true))
        .setErr(new PrintWriter(new OutputStreamWriter(System.err, System.err.charset()), true))
        .setExecutionExceptionHandler(new CommandErrors());
  }

  /** 取引擎（包内可见：重命令同包；分包会让它被迫升为对外概念）。懒启动、同进程只启一次。 */
  ConfigurableApplicationContext engine(WebApplicationType type) {
    if (startedEngine == null) {
      startedEngine = engineFactory.apply(type);
    }
    return startedEngine;
  }

  @Override
  public void run() {
    // 不带子命令：打印用法，别静默退 0
    CommandLine.usage(this, System.out);
  }
}
