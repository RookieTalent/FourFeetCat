package org.fourfeetcat.web.system;

import java.util.List;
import org.fourfeetcat.provider.ProviderConfiguration.ProviderProperties;
import org.fourfeetcat.web.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统状态端点（第26节课件）：健康探针与运行信息。
 *
 * <p>两者分工不同：健康探针回答"进程还活着吗"（供容器与探针用，**不碰库、不碰模型、不碰网络**——它一旦依赖外部可用性， 探针就会把外部的抖动报成自己的故障）；运行信息回答"配了哪些
 * Provider、凭证就位没有"，供运营方排查"为什么一问就报错"。
 *
 * <p><b>运行信息报的是配置态，不是连通态</b>：这里不发探活请求。真探测会让本端点的冒烟验证变成网络依赖，也会把"没配 key"与"服务不可达" 混成一种现象——而这两件事的处置完全不同。
 */
@RestController
@RequestMapping("/api/v1")
public class SystemApiController {

  private final ProviderProperties providers;
  private final String applicationName;

  public SystemApiController(
      ProviderProperties providers,
      @Value("${spring.application.name:fourfeetcat}") String applicationName) {
    this.providers = providers;
    this.applicationName = applicationName;
  }

  /** 健康探针：只证明"进程活着、MVC 层可用"。 */
  @GetMapping("/health")
  public ApiResponse<HealthView> health() {
    return ApiResponse.ok(new HealthView("ok"));
  }

  /** 运行信息：应用名与版本 + 各 Provider 的名字、端点与**凭证是否就位**（绝不回显凭证本身）。 */
  @GetMapping("/info")
  public ApiResponse<InfoView> info() {
    List<ProviderView> views =
        providers.providers().stream()
            .map(spec -> new ProviderView(spec.name(), spec.baseUrl(), isConfigured(spec.apiKey())))
            .toList();
    return ApiResponse.ok(new InfoView(applicationName, version(), views));
  }

  /** 凭证是否就位：只看"解析出来是不是非空"，**不返回凭证内容**。 */
  private static boolean isConfigured(String apiKey) {
    return apiKey != null && !apiKey.isBlank();
  }

  /** 版本取自包的实现版本；从 classes 目录直接跑（未打包）时为 null——这是可空字段，不是缺省值的替代品。 */
  private static String version() {
    Package applicationPackage = SystemApiController.class.getPackage();
    return applicationPackage == null ? null : applicationPackage.getImplementationVersion();
  }

  /** 健康视图。 */
  public record HealthView(String status) {}

  /** Provider 的对外视图：名字 + 端点 + 凭证是否就位（不含凭证）。 */
  public record ProviderView(String name, String baseUrl, boolean credentialConfigured) {}

  /** 运行信息视图。 */
  public record InfoView(String application, String version, List<ProviderView> providers) {

    public InfoView {
      // 防御性不可变拷贝：同上
      providers = providers == null ? List.of() : List.copyOf(providers);
    }
  }
}
