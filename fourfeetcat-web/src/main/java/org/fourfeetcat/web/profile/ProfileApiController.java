package org.fourfeetcat.web.profile;

import java.util.List;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.profile.ProfileRegistry;
import org.fourfeetcat.web.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 查询端点（第26节课件）：列出全部已加载 Agent。
 *
 * <p>读的是既有的 Agent 注册表（第25节为"启动时扫一遍所有 Agent"补的那次只读遍历，本节直接复用）——端点自己不扫描、不缓存， 因此"加了一个 Agent
 * 目录"这件事对查询是即时可见的。
 *
 * <p><b>只呈现可读字段</b>：凭证、系统提示正文一概不出现在响应里。这是一个查询清单，不是配置导出——把它当导出用， 等于把每次查询都变成一次潜在的密钥外泄。
 */
@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileApiController {

  private final ProfileRegistry profileRegistry;

  public ProfileApiController(ProfileRegistry profileRegistry) {
    this.profileRegistry = profileRegistry;
  }

  /** 列出全部已加载 Agent；一个都没有时返回空列表，不是 404。 */
  @GetMapping
  public ApiResponse<List<AgentView>> list() {
    return ApiResponse.ok(
        profileRegistry.all().stream().map(ProfileApiController::toView).toList());
  }

  static AgentView toView(Profile profile) {
    Profile.ProviderConfig provider = profile.provider();
    return new AgentView(
        profile.name(),
        profile.description(),
        profile.identity().agentName(),
        provider == null ? null : provider.name(),
        provider == null ? null : provider.model(),
        profile.tools(),
        profile.channels());
  }

  /** Agent 的对外视图：只含展示与路由所需字段，**不含凭证、不含系统提示正文**。 */
  public record AgentView(
      String name,
      String description,
      String agentName,
      String provider,
      String model,
      List<String> tools,
      List<String> channels) {

    public AgentView {
      // 防御性不可变拷贝：记录里直接持有外部传入的可变列表，等于把这个视图的内部结构暴露给调用方
      tools = tools == null ? List.of() : List.copyOf(tools);
      channels = channels == null ? List.of() : List.copyOf(channels);
    }
  }
}
