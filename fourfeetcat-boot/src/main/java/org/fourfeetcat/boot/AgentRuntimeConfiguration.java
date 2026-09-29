package org.fourfeetcat.boot;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.fourfeetcat.core.memory.MemoryService;
import org.fourfeetcat.core.notify.NotifyChannelSource;
import org.fourfeetcat.core.profile.AgentLoader;
import org.fourfeetcat.core.profile.ProfileLoader;
import org.fourfeetcat.core.profile.ProfileRegistry;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.react.ContextLoader;
import org.fourfeetcat.core.react.LlmCaller;
import org.fourfeetcat.core.react.PromptBuilder;
import org.fourfeetcat.core.react.ReActLoop;
import org.fourfeetcat.core.react.ToolExecutor;
import org.fourfeetcat.core.schedule.AgentScheduler;
import org.fourfeetcat.core.schedule.ScheduledTaskStore;
import org.fourfeetcat.core.session.SessionManager;
import org.fourfeetcat.core.tool.ToolInvocationRecorder;
import org.fourfeetcat.memory.LongTermMemoryStore;
import org.fourfeetcat.memory.MarkdownMemoryStore;
import org.fourfeetcat.memory.Mem0MemoryStore;
import org.fourfeetcat.memory.MemoryServiceImpl;
import org.fourfeetcat.memory.SqliteMemoryStore;
import org.fourfeetcat.memory.builtin.MemoryTools;
import org.fourfeetcat.provider.ProviderConfiguration;
import org.fourfeetcat.storage.MemoryEntryRepository;
import org.fourfeetcat.tool.builtin.FileTools;
import org.fourfeetcat.tool.builtin.HttpTools;
import org.fourfeetcat.tool.builtin.ShellTools;
import org.fourfeetcat.tool.mcp.McpClientService;
import org.fourfeetcat.tool.mcp.McpServerConfigLoader;
import org.fourfeetcat.tool.notify.NotifyChannelAdapter;
import org.fourfeetcat.tool.notify.NotifyTools;
import org.fourfeetcat.tool.registry.ToolRegistry;
import org.fourfeetcat.tool.sandbox.FileSandboxProperties;
import org.fourfeetcat.tool.sandbox.HttpSandboxProperties;
import org.fourfeetcat.tool.sandbox.Sandbox;
import org.fourfeetcat.tool.sandbox.ShellSandboxProperties;
import org.fourfeetcat.tool.sandbox.WhitelistSandbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

/**
 * 运行期装配（第18节）：把 Profile 加载、上下文组装、循环、编排串起来，让 {@code chat} 能真跑通一轮对话。
 *
 * <p>四个跨模块端口 Bean（{@code LlmCaller} / {@code LlmCallRecorder} / {@code ToolInvocationRecorder} /
 * {@code SessionManager}）全部复用既有实现——provider 模块的 {@code ProviderConfiguration} 与 storage 的两个
 * {@code @Component}。第20节再加四个：{@code Sandbox}（第24节起为白名单实现）、{@code ToolRegistry}、 {@code
 * McpServerConfigLoader} 与 {@code McpClientService}。第22节再加两个：{@code LongTermMemoryStore}（按配置三选一）与
 * {@code MemoryService}（门面）。第25节再加两个：{@code ThreadPoolTaskScheduler} 与 {@code AgentScheduler}（第三种
 * 触发源；注册动作挂在该 Bean 的 {@code initMethod} 上）。
 */
@Configuration
@EnableConfigurationProperties({
  FileSandboxProperties.class,
  ShellSandboxProperties.class,
  HttpSandboxProperties.class
})
public class AgentRuntimeConfiguration {

  /** shell 单条命令的执行上限：课件没给数，取 30 秒这个工程默认值。 */
  private static final Duration SHELL_TIMEOUT = Duration.ofSeconds(30);

  /** shell 输出上限：一次把几十兆日志灌进模型上下文，等于把这一轮对话直接撑爆。 */
  private static final int SHELL_MAX_OUTPUT_CHARS = 8000;

  /** 长期记忆后端的取值字面量（第22节）：与 application.yaml 的 {@code memory.backend} 一一对应，集中一处便于核对。 */
  private static final String BACKEND_MARKDOWN = "markdown";

  private static final String BACKEND_SQLITE = "sqlite";
  private static final String BACKEND_MEM0 = "mem0";

  /** 定时任务的调度线程数（第25节）：下限是 2，理由见 {@link #taskScheduler()}。 */
  private static final int SCHEDULER_POOL_SIZE = 4;

  /**
   * 工作区根：缺省 {@code .fourfeetcat}；FOURFEETCAT_ROOT 环境变量可整体搬移（与 application.yaml 的数据源路径同口径）。
   *
   * <p>第27节新增：`fourfeetcat.root` JVM 系统属性**优先**于环境变量——整机测试（MockAgentE2ETest）据此把工作区指到 临时目录，实现无 key
   * 时全链路仍跑真实路径的 hermetic 隔离，无需污染真实工作区。
   */
  static Path workspaceRoot() {
    return Path.of(
        System.getProperty(
            "fourfeetcat.root", System.getenv().getOrDefault("FOURFEETCAT_ROOT", ".fourfeetcat")));
  }

  /**
   * 两条 Profile 来源汇入同一注册表：手写 {@code <root>/profiles/*.yaml}（第16节）+ 插件式 {@code <root>/agents/<name>/}
   * 目录派生（第29节，一个目录=一个Agent）。有 schedules 的派生 Agent 由 {@code AgentScheduler(initMethod=registerAll)}
   * 自动收敛定时——这里只保证它们都进了 registry。
   */
  @Bean
  public ProfileRegistry profileRegistry(ProviderConfiguration.ProviderProperties providers) {
    Set<String> knownProviders =
        providers.providers().stream()
            .map(ProviderConfiguration.ProviderSpec::name)
            .collect(Collectors.toSet());
    ProfileRegistry registry = new ProfileRegistry();
    new ProfileLoader(knownProviders)
        .load(workspaceRoot().resolve("profiles"))
        .forEach(registry::register);
    new AgentLoader(knownProviders)
        .scan(workspaceRoot().resolve("agents"))
        .forEach(registry::register);
    return registry;
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

  /**
   * 长期记忆后端（第22节）：按 {@code memory.backend} 选一档——这是第21节那道"接口墙"的装配落点，换档只改配置。
   *
   * <p>取值不是这三者时**启动即拒并点名该取值**：把 {@code markdwon} 这类打字错静默当默认档，排查成本会转到几轮对话之后。
   * 外部服务档被选中而地址为空时同样在装配期拒绝——地址没配就该立刻说，而不是等第一次对话才在调用链深处报错。
   *
   * <p>工作区根的口径只有这里一处（{@link #workspaceRoot()}）。
   */
  @Bean
  public LongTermMemoryStore longTermMemoryStore(
      @Value("${memory.backend:markdown}") String backend,
      MemoryEntryRepository memoryEntryRepository,
      RestClient restClient,
      @Value("${memory.mem0.base-url:}") String mem0BaseUrl,
      @Value("${memory.mem0.user-id:fourfeetcat}") String mem0UserId) {
    if (backend.isBlank()) {
      // 空白视为"没配"（与 @Value 的缺省值同口径），它不是"一个未定义的取值"
      return new MarkdownMemoryStore(workspaceRoot());
    }
    switch (backend) {
      case BACKEND_SQLITE:
        return new SqliteMemoryStore(memoryEntryRepository);
      case BACKEND_MEM0:
        if (mem0BaseUrl.isBlank()) {
          throw new IllegalStateException(
              "memory.backend=mem0 但 memory.mem0.base-url 未配置——应指向自托管的记忆服务地址");
        }
        return new Mem0MemoryStore(restClient.mutate().baseUrl(mem0BaseUrl).build(), mem0UserId);
      case BACKEND_MARKDOWN:
        return new MarkdownMemoryStore(workspaceRoot());
      default:
        throw new IllegalStateException(
            "memory.backend 取值非法: " + backend + "（应为 markdown / sqlite / mem0）");
    }
  }

  /** 记忆门面（第22节）：包住被选中的那一档——上层只认它，不认识底下是哪一档。 */
  @Bean
  public MemoryService memoryService(LongTermMemoryStore longTermMemoryStore) {
    return new MemoryServiceImpl(longTermMemoryStore);
  }

  /** 组装器（第17节）：第22节起长期记忆段由门面供给——会话历史段仍由组装器自己负责，两段各注入一次。 */
  @Bean
  public PromptBuilder promptBuilder(ContextLoader contextLoader, MemoryService memoryService) {
    return new PromptBuilder(contextLoader, memoryService);
  }

  /**
   * 沙箱（第20节立接口，第24节挂实现）：核心阶段挂应用层白名单那一档。
   *
   * <p>它替换的是第20节那个"什么都不拦"的临时装配——**接口签名、四个工具的调用位、审计路径一行未改**，只是换了个实现对象。第23节"接口设计对了，
   * 接入成本会小到不成比例"这句话，兑现在这里。
   *
   * <p>三份白名单从配置读（{@code file.allowed_paths} / {@code shell.allowed_commands} / {@code
   * http.allowed_domains}）；留空 = 什么都不允许。文件白名单项必须是绝对路径，否则启动即拒并点名该项。
   */
  @Bean
  public Sandbox sandbox(
      FileSandboxProperties fileProps,
      ShellSandboxProperties shellProps,
      HttpSandboxProperties httpProps) {
    return new WhitelistSandbox(fileProps, shellProps, httpProps);
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
      McpClientService mcpClientService,
      MemoryService memoryService) {
    ToolRegistry registry = new ToolRegistry();
    registry.registerAnnotated(new FileTools(sandbox));
    registry.registerAnnotated(new ShellTools(sandbox, SHELL_TIMEOUT, SHELL_MAX_OUTPUT_CHARS));
    registry.registerAnnotated(new HttpTools(sandbox, restClient));
    // 第19节欠的那笔账在这里还上：推送能力从"契约 + 实现"变成 Agent 在对话里真的能调的工具
    registry.registerAnnotated(new NotifyTools(sandbox, notifyAdapter, notifyChannels));
    // 第20节登记为跨节的两个工具在这里归位（save_memory / recall_memory）：只认门面，对底下是哪一档后端无感
    registry.registerAnnotated(new MemoryTools(memoryService));

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

  /**
   * 定时任务的调度线程池（第25节）。容量 **4**——**下限是 2**。
   *
   * <p>为什么不能是 1：触发型调度里同一条任务是"跑完才排下一次"，所以容量 1 不会让同一任务重叠；但它会让**不同**任务 互相排队——一条长任务（一次 ReAct
   * 循环可能几分钟）会把别的任务到点的触发堵在池队列里，而"到点就跑"正是定时任务的 卖点。不设配置键：并发上界由配置里声明的任务条数决定，没有实测依据之前不引入调优旋钮。
   */
  @Bean
  public ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(SCHEDULER_POOL_SIZE);
    scheduler.setThreadNamePrefix("ffc-sched-");
    return scheduler;
  }

  /**
   * 第三种触发源（第25/28节）：容器起来时扫一遍所有 Agent 的定时配置，逐条登记进 scheduled_tasks 并注册进调度器。
   *
   * <p>注册动作挂在 {@code initMethod} 上而不是给 {@link AgentScheduler} 加 {@code @PostConstruct}：core 自第16节
   * 起零 Spring 注解、装配一律在这里显式做，{@code initMethod} 也免了 core 去依赖注解 API。第28节起注入 {@link
   * org.fourfeetcat.core.schedule.ScheduledTaskStore}——状态与历史落库，重启不丢。
   */
  @Bean(initMethod = "registerAll")
  public AgentScheduler agentScheduler(
      ThreadPoolTaskScheduler taskScheduler,
      ProfileRegistry profileRegistry,
      AgentService agentService,
      SessionManager sessionManager,
      ScheduledTaskStore scheduledTaskStore) {
    return new AgentScheduler(
        taskScheduler, profileRegistry, agentService, sessionManager, scheduledTaskStore);
  }
}
