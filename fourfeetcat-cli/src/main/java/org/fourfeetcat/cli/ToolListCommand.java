package org.fourfeetcat.cli;

import java.util.List;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.tool.registry.ToolRegistry;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * {@code fourfeetcat tool list}：列可用工具。
 *
 * <p>第18节留下的占位在此换数据源（那时它的注释写的就是"第20节交付后只换数据源，命令本身不改"）：命令名、分组、描述一字 未动，只是从"打印一句占位文案"改成"问注册表要清单"。
 *
 * <p>它是**重命令**——注册表里的工具要沙箱、HTTP 客户端与渠道数据源才构造得出来，脱离容器自己拼一套等于把 boot 的装配逻辑 抄第二份。
 */
// 打印工具清单就是这个命令的全部产出（与第18节同款抑制）；容器生命周期归进程，命令只借来取 Bean
@SuppressWarnings({"PMD.SystemPrintln", "PMD.CloseResource"})
@Command(name = "list", description = "列出当前可用的工具", mixinStandardHelpOptions = true)
class ToolListCommand implements Runnable {

  @ParentCommand private FourFeetCatCli root;

  @Override
  public void run() {
    ConfigurableApplicationContext context = root.engine(WebApplicationType.NONE);
    List<CatTool> tools = context.getBean(ToolRegistry.class).all();
    if (tools.isEmpty()) {
      System.out.println("当前无可用工具");
      return;
    }
    for (CatTool tool : tools) {
      System.out.printf("%-20s %s%n", tool.getName(), tool.getDescription());
    }
  }
}
