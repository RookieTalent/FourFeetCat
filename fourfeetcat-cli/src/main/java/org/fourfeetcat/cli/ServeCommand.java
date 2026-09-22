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
  }
}
