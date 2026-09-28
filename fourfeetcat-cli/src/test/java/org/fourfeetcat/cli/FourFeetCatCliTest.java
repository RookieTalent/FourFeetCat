package org.fourfeetcat.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * 命令树（守第18/20节的缺陷）：这个类只钉一件事——**整棵树建得起来**。
 *
 * <p>起因是一处真实缺陷：第20节把 {@code tool list} 从轻命令改成重命令时，给它填的 {@code @ParentCommand} 字段类型是根命令，而它的直接父
 * 命令是分组节点 {@code tool}。Picocli 在**构造命令树**时就要往这个字段注入父对象，类型对不上直接抛异常——整棵树都建不起来，连 {@code --help}
 * 都进不去；而当时没有任何测试碰过命令树的构建，所以一路绿灯到了打包产物里。
 *
 * <p>所以这里**必须**从根命令建树（只测单个命令类是抓不住这个坑的），并且给引擎工厂一个"谁调谁错"的替身：轻命令与建树都不该启动容器。
 */
class FourFeetCatCliTest {

  private static FourFeetCatCli cli() {
    return new FourFeetCatCli(
        type -> {
          throw new IllegalStateException("建树与轻命令都不该启动容器");
        });
  }

  @Test
  @DisplayName("命令树能建起来_九个子命令与分组下的二级命令都在")
  void commandTreeBuilds() {
    CommandLine commandLine = cli().commandLine();

    assertThat(commandLine.getSubcommands().keySet())
        .contains(
            "init", "status", "chat", "serve", "gateway", "profile", "provider", "tool", "session");
    // 二级命令也要在：缺陷正是发生在"分组节点 → 子命令"这一层
    assertThat(commandLine.getSubcommands().get("tool").getSubcommands()).containsKey("list");
    assertThat(commandLine.getSubcommands().get("session").getSubcommands()).containsKey("list");
  }

  @Test
  @DisplayName("帮助文本渲染得出来_含子命令清单")
  void usageMessageRenders() {
    assertThat(cli().commandLine().getUsageMessage())
        .contains("fourfeetcat")
        .contains("gateway")
        .contains("tool");
  }
}
