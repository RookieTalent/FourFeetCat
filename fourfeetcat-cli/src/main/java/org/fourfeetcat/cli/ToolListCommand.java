package org.fourfeetcat.cli;

import java.util.List;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.tool.registry.ToolRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * {@code fourfeetcat tool list}：列可用工具。
 *
 * <p>第18节留下的占位在此换数据源（那时它的注释写的就是"第20节交付后只换数据源，命令本身不改"）：命令名、分组、描述一字 未动，只是从"打印一句占位文案"改成"问注册表要清单"。
 *
 * <p>它是**重命令**——注册表里的工具要沙箱、HTTP 客户端与渠道数据源才构造得出来，脱离容器自己拼一套等于把 boot 的装配逻辑 抄第二份。
 *
 * <p>父命令字段必须声明**直接父命令**（分组节点 {@link ToolCommand}）而不是根命令：Picocli 构造命令树时就要往这个字段里注入父对象，
 * 类型对不上会直接抛异常、**整棵树都建不起来**（连 {@code --help} 都进不去）。这个坑第20节犯过一次，回归测试见 {@code FourFeetCatCliTest}。
 */
// 打印工具清单就是这个命令的全部产出（与第18节同款抑制）；容器生命周期归进程，命令只借来取 Bean
@SuppressWarnings({"PMD.SystemPrintln", "PMD.CloseResource"})
@Command(name = "list", description = "列出当前可用的工具", mixinStandardHelpOptions = true)
class ToolListCommand implements Runnable {

  @ParentCommand private ToolCommand parent;

  @Override
  public void run() {
    ConfigurableApplicationContext context = parent.engine();
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
