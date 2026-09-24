package org.fourfeetcat.boot;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.fourfeetcat.core.notify.NotifyChannelSource;
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
import org.fourfeetcat.core.tool.ToolInvocationRecorder;
import org.fourfeetcat.provider.ProviderConfiguration;
import org.fourfeetcat.tool.builtin.FileTools;
import org.fourfeetcat.tool.builtin.HttpTools;
import org.fourfeetcat.tool.builtin.ShellTools;
import org.fourfeetcat.tool.mcp.McpClientService;
import org.fourfeetcat.tool.mcp.McpServerConfigLoader;
import org.fourfeetcat.tool.notify.NotifyChannelAdapter;
import org.fourfeetcat.tool.notify.NotifyTools;
import org.fourfeetcat.tool.registry.ToolRegistry;
import org.fourfeetcat.tool.sandbox.PermissiveSandbox;
import org.fourfeetcat.tool.sandbox.Sandbox;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 运行期装配（第18节）：把 Profile 加载、上下文组装、循环、编排串起来，让 {@code chat} 能真跑通一轮对话。
 *
 * <p>四个跨模块端口 Bean（{@code LlmCaller} / {@code LlmCallRecorder} / {@code ToolInvocationRecorder} /
 * {@code SessionManager}）全部复用既有实现——provider 模块的 {@code ProviderConfiguration} 与 storage 的两个
 * {@code @Component}。第20节再加四个：{@code Sandbox}（本节为临时装配）、{@code ToolRegistry}、 {@code
 * McpServerConfigLoader} 与 {@code McpClientService}。
 */
@Configuration
public class AgentRuntimeConfiguration {

  /** shell 单条命令的执行上限：课件没给数，取 30 秒这个工程默认值。 */
  private static final Duration SHELL_TIMEOUT = Duration.ofSeconds(30);

  /** shell 输出上限：一次把几十兆日志灌进模型上下文，等于把这一轮对话直接撑爆。 */
  private static final int SHELL_MAX_OUTPUT_CHARS = 8000;

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

  /**
   * 沙箱（第20节）。
   *
   * <p>⚠️ 这里挂的是**临时装配**：它不做任何校验，只为让沙箱节之前已注册的工具能跑通。规则本体归沙箱节，届时把这个 Bean 换成白名单实现即可——接口与调用方一行不改。见
   * {@link PermissiveSandbox} 的类注释。
   */
  @Bean
  public Sandbox sandbox() {
    return new PermissiveSandbox();
  }

  /** MCP 配置加载器（第20节）：读工作区里的 {@code mcp_servers.yaml}——工作区路径的口径只在 boot 里有一处。 */
  @Bean
  public McpServerConfigLoader mcpServerConfigLoader() {
    return new McpServerConfigLoader(workspaceRoot());
  }

  /**
   * 外部工具服务的接入（第20节）。
   *
   * <p>它是 Bean 而不是就地 new 的对象，因为它持有的是子进程连接：停机时得有人调它的 {@code close()} 收尾， {@link AutoCloseable}
   * 就是容器认的销毁方法，不必另配 {@code destroyMethod}。
   */
  @Bean
  public McpClientService mcpClientService(McpServerConfigLoader loader) {
    return new McpClientService(loader);
  }

  /**
   * 工具注册表（第20节）：三种来源的工具都注册进它，{@code ToolExecutor} 只认这一个下游。
   *
   * <p>按 Profile 的工具名清单过滤在该类里完成——注册表全量持有，Agent 各取自己声明的那批。
   *
   * <p>内置工具是在这里"一行挂一个"接进来的：沙箱检查位、{@code tool_invocations} 审计、按清单过滤全在管道的固定位置上， 每加一个工具都不需要动它们。
   */
  @Bean
  public ToolRegistry toolRegistry(
      Sandbox sandbox,
      RestClient restClient,
      NotifyChannelAdapter notifyAdapter,
      NotifyChannelSource notifyChannels,
      McpClientService mcpClientService) {
    ToolRegistry registry = new ToolRegistry();
    registry.registerAnnotated(new FileTools(sandbox));
    registry.registerAnnotated(new ShellTools(sandbox, SHELL_TIMEOUT, SHELL_MAX_OUTPUT_CHARS));
    registry.registerAnnotated(new HttpTools(sandbox, restClient));
    // 第19节欠的那笔账在这里还上：推送能力从"契约 + 实现"变成 Agent 在对话里真的能调的工具
    registry.registerAnnotated(new NotifyTools(sandbox, notifyAdapter, notifyChannels));

    // 外部工具服务（第20节）：启动时连接配置里声明的全部，把它暴露的工具也注册进来。
    // 连不上的只记 WARN 跳过——外部依赖失联不该拖垮底座自己的启动（连接范围与隔离见 McpClientService）。
    mcpClientService.connectAll(registry);
    return registry;
  }

  @Bean
  public ToolExecutor toolExecutor(ToolRegistry toolRegistry, ToolInvocationRecorder audit) {
    return new ToolExecutor(toolRegistry, audit);
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
}
