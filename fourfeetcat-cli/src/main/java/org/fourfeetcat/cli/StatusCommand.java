package org.fourfeetcat.cli;

import java.util.List;
import java.util.Map;
import org.fourfeetcat.storage.SessionRepository;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * {@code fourfeetcat status}：看配置与运行状态。
 *
 * <p>重命令——要读库（会话数）就得进容器；工作区与 provider 那两段是配置，顺路打出来即可。
 */
@SuppressWarnings({"PMD.SystemPrintln", "PMD.CloseResource"})
@Command(name = "status", description = "看当前配置与运行状态", mixinStandardHelpOptions = true)
class StatusCommand implements Runnable {

  @ParentCommand private FourFeetCatCli root;

  @Override
  public void run() {
    // 容器的生命周期归进程（serve/gateway 要它常驻），命令不能替它收摊：这里只借来读几个 Bean
    ConfigurableApplicationContext context = root.engine(WebApplicationType.NONE);

    System.out.println("工作区: " + Workspace.root().toAbsolutePath());
    System.out.println("Agent 配置目录: " + Workspace.profilesDir().toAbsolutePath());
    System.out.println("数据库: " + context.getEnvironment().getProperty("spring.datasource.url"));
    System.out.println("会话数: " + context.getBean(SessionRepository.class).count());

    List<Map<String, Object>> providers = GlobalProviders.declared();
    if (providers.isEmpty()) {
      System.out.println("provider: 未声明");
    } else {
      for (Map<String, Object> provider : providers) {
        System.out.println(
            "provider: "
                + GlobalProviders.value(provider, GlobalProviders.NAME_KEY)
                + "  "
                + GlobalProviders.value(provider, GlobalProviders.BASE_URL_KEY));
      }
    }
  }
}
