package org.fourfeetcat.cli;

import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code fourfeetcat provider list}：看本实例声明了哪些 provider。轻命令，不启动容器。
 *
 * <p>**只打印名字与端点，永不打印 key**——key 要么是 {@code ${ENV}} 占位、要么在环境变量里，两者都不该出现在终端回显里。
 */
@SuppressWarnings("PMD.SystemPrintln")
@Command(name = "list", description = "列出本实例声明了哪些 provider", mixinStandardHelpOptions = true)
class ProviderListCommand implements Runnable {

  @Override
  public void run() {
    List<Map<String, Object>> providers = GlobalProviders.declared();
    if (providers.isEmpty()) {
      System.out.println("未声明任何 provider（fourfeetcat.providers 为空）");
      return;
    }
    for (Map<String, Object> provider : providers) {
      System.out.println(
          GlobalProviders.value(provider, GlobalProviders.NAME_KEY)
              + "  "
              + GlobalProviders.value(provider, GlobalProviders.BASE_URL_KEY));
    }
  }
}
