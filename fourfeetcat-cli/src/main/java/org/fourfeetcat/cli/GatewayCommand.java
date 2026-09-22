package org.fourfeetcat.cli;

import java.util.concurrent.CountDownLatch;
import org.springframework.boot.WebApplicationType;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * {@code fourfeetcat gateway}：守护进程模式，同时挂多个入站通道（三种运行模式之一）。
 *
 * <p>本节只从命令行侧接起来：IM 通道模块归后续节，所以这里启动的是"无 Web 容器"的引擎，然后**阻塞主线程保活**——
 * 守护进程自己退出就没有守护可言了。同步阻塞，不引任何异步类型（宪法原则七）。
 */
@SuppressWarnings("PMD.SystemPrintln")
@Command(name = "gateway", description = "守护进程模式：同时挂多个入站通道", mixinStandardHelpOptions = true)
class GatewayCommand implements Runnable {

  @ParentCommand private FourFeetCatCli root;

  @Override
  public void run() {
    root.engine(WebApplicationType.NONE);
    System.out.println("gateway 已启动（宿主入站通道；Ctrl+C 停止）");
    awaitForever();
  }

  private static void awaitForever() {
    try {
      new CountDownLatch(1).await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
