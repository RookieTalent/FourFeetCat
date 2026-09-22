# Phase 1 Contracts: CLI 入口层与会话持久化（第18节）

**Input**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Data Model**: [data-model.md](../data-model.md)

跨模块契约（接口 + 值对象）留 `fourfeetcat-core`、由下游模块实现；`core` 不反向依赖任何下游模块。新增/变更的对外签名逐条列出。

---

## §1 `fourfeetcat-core`：`SessionManager` 端口扩容

`fourfeetcat-core/src/main/java/org/fourfeetcat/core/session/SessionManager.java`

```java
public interface SessionManager {

  /** 按"渠道 + 用户 + Agent"三元组取会话；没有就建一条空历史的。同一三元组必须永远返回同一条。 */
  Session getOrCreate(String channel, String user, String profileName);

  /** 按标识取会话；不存在返回空（"还没开始聊"是正常分支，不是错误）。 */
  Optional<Session> get(String sessionId);

  /** 把累积完的会话落盘（第17节既有语义不变）。 */
  void save(Session session);
}
```

**变更点（课件点名的改造点，非软门禁）**：第17节为"单一抽象方法"加的 `@FunctionalInterface` 与其注释一并去掉——注释原文已写明"第18节补 getOrCreate/get 后本注解即去掉"。

**连带确认**：第17节的 `AgentService.process` 里 `sessionManager.save(session)` 一行**不改**；`session_id` 的拼接只出现在实现侧（H4 不变量④）。

---

## §2 `fourfeetcat-storage`：会话持久化的四个新增类

```java
// SessionEntity.java —— JPA 实体，映射 sessions 表（列名与 V18 脚本逐字一致）
@Entity
@Table(name = "sessions")
public class SessionEntity {                 // 字段见 data-model.md §4；getter/setter 齐备
  // @Id private String sessionId;（标识由会话层生成，无 @GeneratedValue）
}

// SessionRepository.java —— 会话表仓储
public interface SessionRepository extends JpaRepository<SessionEntity, String> {
  List<SessionEntity> findAllByOrderByLastActiveAtDesc();   // session list 用；本节只写入+列举，不做审计报表
}

// JpaSessionManager.java —— 端口实现（@Component，与 LlmCallRecorderImpl / ToolInvocationRecorderImpl 同款手法）
@Component
public class JpaSessionManager implements SessionManager {
  public JpaSessionManager(SessionRepository repository);
  // getOrCreate / get / save 三方法；session_id 拼接的唯一实现处
}

// SessionMessagesJson.java —— 包内私有：内存态消息 ↔ 一列 JSON 的往返编解码
final class SessionMessagesJson {
  static String write(List<Message> messages);
  static List<Message> read(String json);
}
```

**依赖方向**：`storage` → `core`（既有）；`storage` 新增直依 `com.fasterxml.jackson.core:jackson-databind`（版本由 Boot 托管）。

---

## §3 `fourfeetcat-channel-cli`：`CliChannel`

```java
package org.fourfeetcat.channel.cli;

/** `fourfeetcat chat` 的实现：读—转交—打印的壳，自身零 Agent 智能。 */
public class CliChannel {

  /** 流可注入：交互壳的退出/空行逻辑要有机器守卫（CliChannelTest）。 */
  public CliChannel(AgentService agentService, SessionManager sessionManager);
  public CliChannel(AgentService agentService, SessionManager sessionManager,
                    InputStream in, PrintStream out);

  /**
   * 进入交互：取/建 (channel="cli", user=当前用户, profile=profileName) 的会话，
   * 循环 读一行 → 空行跳过 → "/quit" 退出 → 其余交给 agentService.process 并打印答复。
   */
  public void run(String profileName);
}
```

**依赖方向**：`channel-cli` → `core`（既有）。

**不做的**：不做 `/help`、不做历史回溯命令、不做多行输入、不做颜色（核心阶段克制）。

---

## §4 `fourfeetcat-cli`：命令树与 12 个子命令

```java
package org.fourfeetcat.cli;

@Command(name = "fourfeetcat", mixinStandardHelpOptions = true, version = "0.1.0",
         description = "FourFeetCat 命令行入口",
         subcommands = {InitCommand.class, StatusCommand.class, ChatCommand.class,
                        ServeCommand.class, GatewayCommand.class,
                        ProfileListCommand.class, ProfileCreateCommand.class,
                        ProfileShowCommand.class, ProfileDeleteCommand.class,
                        ProviderListCommand.class, ToolListCommand.class,
                        SessionListCommand.class})
public class FourFeetCatCli implements Runnable {

  /** 引擎工厂由 boot 注入（本次模块依赖方向只能是 boot → cli）；不新造接口类型。 */
  public FourFeetCatCli(Function<WebApplicationType, ConfigurableApplicationContext> engineFactory);

  /** 懒启动、同进程只启一次；包内可见——重命令同包，不升为对外概念。 */
  ConfigurableApplicationContext engine(WebApplicationType type);

  @Override public void run() { /* 无子命令时打印用法 */ }
}
```

**12 个子命令的类名与命令名（逐字，Picocli `@Command(name=...)`）**：

| 类 | `name` | 类别 | 行为 |
|---|---|---|---|
| `InitCommand` | `init` | 轻 | 建工作区（`profiles/` + `default.yaml`、`memory/MEMORY.md`、`logs/`、`mcp_servers.yaml`、`AGENTS.md`/`SOUL.md`/`USER.md`）；已存在不覆盖（幂等） |
| `StatusCommand` | `status` | 重·无 Web | 工作区根、profile 文件清单、provider 名与 base-url、库路径 |
| `ChatCommand` | `chat` | 重·无 Web | `--profile`（默认 `default`）→ `CliChannel.run` |
| `ServeCommand` | `serve` | 重·Servlet | `--port`（默认 8080）→ 启动 Web 容器（REST 内容归后续节） |
| `GatewayCommand` | `gateway` | 重·无 Web | 启动无 Web 容器（宿主通道），阻塞主线程保活 |
| `ProfileListCommand` | `profile list` | 轻 | 列 `profiles/` 下的配置文件 |
| `ProfileCreateCommand` | `profile create <name>` | 轻 | 写一份模板到 `profiles/<name>.yaml`；已存在则报错不覆盖 |
| `ProfileShowCommand` | `profile show <name>` | 轻 | 打印该配置文件内容 |
| `ProfileDeleteCommand` | `profile delete <name>` | 轻 | 删除该配置文件；不存在则清晰报错 |
| `ProviderListCommand` | `provider list` | 轻 | 读打包内全局层声明，打印 provider 名 + base-url（**永不打印 key**） |
| `ToolListCommand` | `tool list` | 轻 | 输出"当前无可用工具"（工具注册归第20节，届时换数据源） |
| `SessionListCommand` | `session list` | 重·无 Web | 从会话仓储按最后活跃时间倒序列出 |

**命令名与 Picocli 结构**：`profile` / `provider` / `session` / `tool` 是分组命令（`@Command(name="profile", subcommands={...})`），故 12 个子命令共 8 个命令类分组 + 7 个叶子命令类。分组命令自身无行为（`run()` 打印自己的用法）。

**为什么所有 @Command 类同包**：根命令的 `engine(...)` 是包内可见；拆子包会让它被迫升为 `public`，凭空多一个对外概念。

**统一出口**：`CommandErrors`（包内私有）提供"清晰报错、不抛栈"——工作区不存在、Agent 不存在、配置文件缺失等一律走它，退出码非 0。

---

## §5 `fourfeetcat-boot`：装配与主类

```java
// FourFeetCatApplication.java（改）
@SpringBootApplication(scanBasePackages = "org.fourfeetcat")   // 既有，不动
@EnableJpaRepositories(basePackages = "org.fourfeetcat")       // 既有，不动（课件"坑四"的解法）
@EntityScan(basePackages = "org.fourfeetcat")                  // 既有，不动
public class FourFeetCatApplication {
  public static void main(String[] args) {
    // 既有：确保工作区根目录存在（FOURFEETCAT_ROOT 可整体搬移）
    int exitCode = new CommandLine(
            new FourFeetCatCli(
                type -> new SpringApplicationBuilder(FourFeetCatApplication.class).web(type).run()))
        .execute(args);
    System.exit(exitCode);
  }
}

// AgentRuntimeConfiguration.java（新增）—— chat 能真跑通所需的最小装配
@Configuration
class AgentRuntimeConfiguration {
  // ProfileRegistry  ← ProfileLoader(全局层 provider 名).load(workspaceRoot/profiles)
  // ContextLoader(workspaceRoot) → PromptBuilder(contextLoader)
  // ToolTable（包内私有空实现：空名单返回空表，非空名单清晰报错）
  // ToolExecutor(toolTable, ToolInvocationRecorder) → ReActLoop(promptBuilder, LlmCaller, toolExecutor)
  // SessionManager 实现来自 storage 的 @Component
  // AgentService(profileRegistry, reActLoop, sessionManager)
}
```

**装配依赖（均为既有 Bean，本节零改动）**：`LlmCaller`/`LlmCallRecorder` 来自 `ProviderConfiguration`（provider 模块）、`ToolInvocationRecorder` 来自 `ToolInvocationRecorderImpl`、`SessionManager` 来自本节新增的 `JpaSessionManager`。

---

## §6 模块依赖方向（新增边，全部无环）

```text
fourfeetcat-cli        → core（既有）, channel-cli（新）, storage（新）, picocli（新）, spring-boot（新）
fourfeetcat-channel-cli→ core（既有）                        [新增 CliChannel]
fourfeetcat-storage    → core（既有）, jackson-databind（新） [新增四个类 + V18 双轨]
fourfeetcat-core       → （无新增依赖）
fourfeetcat-boot       → cli, channel-cli, provider, storage, web（均既有）[新增装配类]
```

`core` 不新增任何依赖；无模块间循环依赖；无模块新建或改名。
