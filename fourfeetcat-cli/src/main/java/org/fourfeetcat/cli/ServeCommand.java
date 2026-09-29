package org.fourfeetcat.cli;

import org.springframework.boot.WebApplicationType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * {@code fourfeetcat serve}：启动 HTTP 服务（三种运行模式之一）。
 *
 * <p>重命令且**走 Servlet 容器**——这是它与 chat/gateway 的唯一区别：后两者只要引擎，不要端口。
 *
 * <p>端口经系统属性传入：引擎工厂是个统一入口（命令只告诉它要哪种容器），端口的覆盖走 Spring 的标准配置通道。
 *
 * <p><b>启动完必须保活</b>（{@link FourFeetCatCli#awaitShutdown()}）：第26节第一次真正让人把 {@code serve} 常驻起来，实测暴露了
 * "起了容器就返回"的后果——命令方法一返回，{@code main} 的 {@code System.exit} 立刻把进程带走，服务在启动后约 0.1 秒就优雅停机， 而退出码还是
 * 0（看起来一切正常）。{@code gateway} 从一开始就有这个保活位，{@code serve} 漏了。
 */
@SuppressWarnings("PMD.SystemPrintln")
@Command(name = "serve", description = "启动 HTTP 服务（REST 端点内容归后续节）", mixinStandardHelpOptions = true)
class ServeCommand implements Runnable {

  @ParentCommand private FourFeetCatCli root;

  @Option(names = "--port", defaultValue = "8080", description = "监听端口（默认 8080）")
  private int port;

  @Override
  public void run() {
    System.setProperty("server.port", String.valueOf(port));
    root.engine(WebApplicationType.SERVLET);
    System.out.println("HTTP 服务已启动，端口 " + port + "（Ctrl+C 停止）");
    root.awaitShutdown();
  }
}
