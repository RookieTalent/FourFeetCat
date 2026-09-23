package org.fourfeetcat.boot;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.fourfeetcat.core.ToolDescriptor;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.profile.ProfileLoader;
import org.fourfeetcat.core.profile.ProfileRegistry;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.react.ContextLoader;
import org.fourfeetcat.core.react.LlmCaller;
import org.fourfeetcat.core.react.PromptBuilder;
import org.fourfeetcat.core.react.ReActLoop;
import org.fourfeetcat.core.react.ToolExecutor;
import org.fourfeetcat.core.session.SessionManager;
import org.fourfeetcat.core.tool.ToolExecutionResult;
import org.fourfeetcat.core.tool.ToolInvocationRecorder;
import org.fourfeetcat.core.tool.ToolTable;
import org.fourfeetcat.provider.ProviderConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 运行期装配（第18节）：把 Profile 加载、上下文组装、循环、编排串起来，让 {@code chat} 能真跑通一轮对话。
 *
 * <p>四个跨模块端口 Bean（{@code LlmCaller} / {@code LlmCallRecorder} / {@code ToolInvocationRecorder} /
 * {@code SessionManager}）全部复用既有实现——provider 模块的 {@code ProviderConfiguration} 与 storage 的两个
 * {@code @Component}，本节零改动。
 */
@Configuration
public class AgentRuntimeConfiguration {

  /** 工作区根：FOURFEETCAT_ROOT 可整体搬移（与 application.yaml 的数据源路径同口径）。 */
  static Path workspaceRoot() {
    return Path.of(System.getenv().getOrDefault("FOURFEETCAT_ROOT", ".fourfeetcat"));
  }

  /** Profile 扫 {@code <root>/profiles/}；provider 名可解析性由全局层声明决定（课件第16节）。 */
  @Bean
  public ProfileRegistry profileRegistry(ProviderConfiguration.ProviderProperties providers) {
    Set<String> knownProviders =
        providers.providers().stream()
            .map(ProviderConfiguration.ProviderSpec::name)
            .collect(Collectors.toSet());
    List<Profile> loaded =
        new ProfileLoader(knownProviders).load(workspaceRoot().resolve("profiles"));
    return new ProfileRegistry(loaded);
  }

  /**
   * 出站通知实现所需的同步 HTTP 客户端（第19节）。
   *
   * <p>Boot 自动配置只提供 {@code RestClient.Builder}，而实现类按课件形态收的是 {@code RestClient}——在这里用 Builder 造
   * 出可注入的实例，那个 {@code @Component} 才能被容器实例化（否则启动期报"找不到 RestClient 类型的 Bean"）。
   */
  @Bean
  public RestClient restClient(RestClient.Builder builder) {
    return builder.build();
  }

  /** 启动信息与 Skill 元数据每次现读、无缓存（技术方案 §8.3）。 */
  @Bean
  public ContextLoader contextLoader() {
    return new ContextLoader(workspaceRoot());
  }

  /** 长期记忆未启用：单参构造即"跳过记忆部分"，第22节传方法引用即可，组装器签名不变。 */
  @Bean
  public PromptBuilder promptBuilder(ContextLoader contextLoader) {
    return new PromptBuilder(contextLoader);
  }

  /** 工具注册归第20节；本节只留最小接线，不引入任何工具实现。 */
  @Bean
  public ToolTable toolTable() {
    return new UnregisteredToolTable();
  }

  @Bean
  public ToolExecutor toolExecutor(ToolTable toolTable, ToolInvocationRecorder audit) {
    return new ToolExecutor(toolTable, audit);
  }

  @Bean
  public ReActLoop reActLoop(
      PromptBuilder promptBuilder, LlmCaller llmCaller, ToolExecutor executor) {
    return new ReActLoop(promptBuilder, llmCaller, executor);
  }

  @Bean
  public AgentService agentService(
      ProfileRegistry profileRegistry, ReActLoop reActLoop, SessionManager sessionManager) {
    return new AgentService(profileRegistry, reActLoop, sessionManager);
  }

  /**
   * 本节的最小工具表：一个工具都没有。
   *
   * <p>Profile 声明了工具却拿不到描述时**报错而不是静默少给**——模型会因此无从下手，静默失败最难查。
   */
  private static final class UnregisteredToolTable implements ToolTable {

    private static final String REASON = "（工具注册归第20节）";

    @Override
    public List<ToolDescriptor> descriptors(List<String> names) {
      if (!names.isEmpty()) {
        throw new IllegalStateException("工具未注册: " + names + REASON);
      }
      return List.of();
    }

    @Override
    public ToolExecutionResult execute(String toolName, String inputJson) {
      throw new IllegalStateException("工具未注册: " + toolName + REASON);
    }
  }
}
