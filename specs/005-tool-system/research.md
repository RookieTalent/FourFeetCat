# Phase 0 Research: Tool 体系（第20节）

**Input**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

本节所有"怎么做"的取舍在此定案。凡是**动手前必须核实的第三方 API**，一律标出实测命令与结论（H3 硬门禁）。

---

## D1 统一抽象与 schema 载体：`getInputSchema()` 返回 JSON Schema 文本 `String`

**Decision**: `CatTool` 四个方法 `getName()` / `getDescription()` / `getInputSchema()` / `execute(JsonNode)`；其中 `getInputSchema()` 返回 **String**（JSON Schema 文本），不是现造的 schema 类型。

**Rationale**:
- 第16节已交付 `ToolDescriptor(String name, String description, String inputSchema)`，`ToolSchemaAdapter` 直接把它喂给 Spring AI 的 `DefaultToolDefinition.inputSchema(String)`；`LlmCaller.chat(..., List<ToolDescriptor>, ...)` 也按此口径。保持 `String` ⇒ **Provider 侧与 ReAct 侧零改动**。
- 课件写的是 `JsonSchema getInputSchema()`（示意），技术方案 §6.1 只写"`getInputSchema`（JSON Schema）"，未规定 Java 类型。选 String 是本仓既有管道的既定口径，不是新决策。

**Alternatives considered**: 自造一个 `JsonSchema` 值对象 ⇒ 要改 `ToolDescriptor`、`ToolSchemaAdapter`、`LlmCaller`（三个前序节交付物），只为更"像课件"，否。

---

## D2 `ToolExecutionResult` 按名合并为 `ToolResult`

**Decision**: 第17节的 `ToolExecutionResult`（`success/content/errorMessage/retryable` 四字段）改名为 `ToolResult`，删旧类，6 处引用同步改（4 处 main + 2 处 test）。

**Rationale**: 第17节该类的 javadoc 原文即"四个字段与第20节的 `ToolResult` 逐字对齐……**届时按名合并**"——这是前序节预先声明的改造点，不是本节自选。字段与工厂方法语义一字不改。

**核实**（`grep -rn ToolExecutionResult`，已实测）：`ToolExecutor`、`ToolTable`、`ReActLoop`、`AgentRuntimeConfiguration`、`ReActLoopTest`、`ToolExecutorTest` 共 6 文件。

---

## D3 `ToolTable` 保留为 core 侧端口，`ToolRegistry` 在 tool 侧实现它

**Decision**: core 的 `ToolTable`（`descriptors(List<String>)` / `execute(String, String)`）**签名一字不改**地保留，作为依赖倒置端口；`fourfeetcat-tool` 的 `ToolRegistry` 实现它，另提供 `register(CatTool)` / `registerAnnotated(Object...)` / `contains(String)` / `all()`。

**Rationale**:
- 技术方案 §10 把 `ToolRegistry` 划给 `fourfeetcat-tool`，而 `ToolExecutor` 在 `fourfeetcat-core`。若 core 直接依赖 `ToolRegistry`，就产生 `core → tool` 反向边，与既有的 `tool → core` 成环——宪法"禁止模块间循环依赖"。
- 第17节 `ToolTable` 的 javadoc 已写明"第20节立统一工具抽象后由它取代，**两个方法的语义不变**"——端口留住、语义不变，实现换成注册表，正是这句话的字面意思。

**Alternatives considered**: 把 `ToolRegistry` 放进 core ⇒ 违反 §10 的模块归属，否。

---

## D4 `@Tool` 注解方法如何变成 `CatTool`：桥接 Spring AI 的 schema 生成，执行权仍唯一

**Decision**: `AnnotatedToolAdapter` 用 `MethodToolCallbackProvider.builder().toolObjects(bean).build().getToolCallbacks()` 拿到 Spring AI 扫描产物，把每个 `ToolCallback` 包成 `CatTool`：`getName/getDescription/getInputSchema` 取自 `getToolDefinition()`，`execute(JsonNode)` 转成 `call(inputJson)`。

**H3 核实**（`javap -classpath spring-ai-model-1.1.2.jar`，已实测）：
- `MethodToolCallbackProvider.builder()` → `Builder.toolObjects(Object...)` → `build()` → `getToolCallbacks(): ToolCallback[]`
- `ToolCallback.getToolDefinition()`（含 name/description/inputSchema）、`ToolCallback.call(String)`

**Rationale**: 宪法原则二明说 Spring AI 在本项目"只做协议转换 + `@Tool` 注解的 JSON Schema 生成"——schema 生成正是这条。执行权仍唯一：`call()` 只可能被 `CatTool.execute()` 调用，而 `CatTool.execute()` 只可能被 `ToolRegistry.execute()` 调用，而那只可能从 `ToolExecutor` 来。**禁用** `ChatClient.prompt().tools().call()` 这类自动执行路径（挂载 `ToolCallback` 到 ChatClient 的动作在本节不存在）。

**Alternatives considered**: 自己反射读 `@Tool` 注解 + 自己生成 schema ⇒ 重造 Spring AI 已有的东西，且 schema 生成极易与 Spring AI 的方言不一致，否。

---

## D5 MCP 客户端依赖选型：`io.modelcontextprotocol.sdk:mcp:0.17.0`（不选 `spring-ai-mcp`）

**Decision**: 新增一条第三方依赖 `io.modelcontextprotocol.sdk:mcp`，版本 **0.17.0** 在父 pom 的 `dependencyManagement` 显式钉死。

**Rationale**:
- `spring-ai-mcp:1.1.2`（BOM 内）传递依赖的 MCP SDK 正是 0.17.0（实测其 pom），所以钉 0.17.0 与锁定 BOM **同版本、不冲突**。
- 不选 `spring-ai-mcp` 的理由：它额外拖进 `mcp-spring-webflux` / `mcp-spring-webmvc` 两个传输层，而本工程用 MVC 且**禁用** Spring AI 自动工具执行——它的 `SyncMcpToolCallback` 桥接正好是为那条路服务的，用不上。多带的东西是净负担。
- 依赖链已实测：`mcp` → `mcp-core` + `mcp-json-jackson2`；`reactor-core` 是 `mcp-core` 的 compile 依赖，但**本工程 classpath 上早已有** `io.projectreactor:reactor-core:3.7.19` 与 `spring-webflux:6.2.19`（`mvn dependency:tree -pl fourfeetcat-boot` 实测，由 Spring AI 传递而来），所以引 MCP SDK **不新增异步栈**——宪法原则七禁的是我们自己的代码用它，我们的代码只用同步的 `McpSyncClient`。

**H3 核实**（`javap -classpath mcp-core-0.17.0.jar`，已实测）：
- `McpClient.sync(McpClientTransport)` → `McpClient$SyncSpec.build()`
- `McpSyncClient.listTools(): McpSchema$ListToolsResult`（`.tools(): List<McpSchema$Tool>`）
- `McpSyncClient.callTool(McpSchema$CallToolRequest): McpSchema$CallToolResult`（`.content(): List<McpSchema$Content>`、`.isError()`）
- `StdioClientTransport(ServerParameters, McpJsonMapper)`；`ServerParameters.builder(String command).args(List).env(Map)`
- `McpSchema$Tool.name()/description()/inputSchema()`；`McpSchema$TextContent.text()`
- `io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper(ObjectMapper)`

---

## D6 外部服务配置：只放行 stdio 传输；`command` 按空白拆分为可执行文件 + 参数

**Decision**: `.fourfeetcat/mcp_servers.yaml` 的一项只承载 `name` / `transport` / `command` / `env` 四项（技术方案 §6.4 与课件一致）。核心阶段**只放行 `stdio`**：`transport` 取其他值 → 记 WARN 跳过该 server；`command` 按空白拆成"可执行文件 + 参数数组"喂给 `ServerParameters.builder(cmd).args(...)`。

**Rationale**: 配置形态里没有 `args` 字段，而 stdio server 基本都需要参数（`npx -y xxx`）。按空白拆分是**不新增配置键**的唯一办法——新增 `args` 或 `url` 属交付物清单外的配置键（软门禁 #1）。SSE 传输还额外需要地址键，同样不引入。

---

## D7 外部服务失联隔离：逐个 try/catch，只记 WARN

**Decision**: `McpClientService.connectAll()` 对每个 server 独立 try/catch，失败只 WARN 并跳过它的工具，绝不向上抛、绝不阻断启动。

**Rationale**: 课件 harness 里最值钱的守点之一（"某个 MCP server 失联不能拖垮启动和其他工具"）。外部依赖的可用性不是自己的可用性。

---

## D8 通知渠道解析落点（主公裁决）：core 出接口 + 通用返回值，storage 实现，tool 做解析

**Decision**:

```java
// fourfeetcat-core：只出接口；返回值是通用对象，不新增值对象
public interface NotifyChannelSource {
  /** 全部渠道记录，每条的键为 name / type / url / description；表中无记录 → 空列表。 */
  List<Map<String, String>> all();
}
```

storage 出 `JpaNotifyChannelSource`（读写第19节已交付的 `notify_channels` 表与 `NotifyChannelRepository`）；**解析与投影都在 `fourfeetcat-tool` 的 `NotifyTools` 里**（空表 → 报"未配置渠道"；名缺省 → 取第一条；名给了查不到 → 报错；投影成 `NotifyTarget`）。

**Rationale**:
- `NotifyTarget` 留在 `fourfeetcat-tool`（主公裁决），所以 **core 端口的返回值不能是 `NotifyTarget`**；用通用 `Map` 承载，就不必在 core 里再造一份与 `NotifyTarget` 同形状的值对象。
- `fourfeetcat-tool` 只依赖 core，**看不到也**不该看到 `fourfeetcat-storage` 的仓储（tool 不碰 JPA，第19节已立此口径）；所以"取数"必须经 core 端口由 storage 实现。
- 表只有几行（渠道是运营方手配的），`all()` 一次取全足够；不为此再切一个"按名查"的方法。

**Alternatives considered**:
- core 端口返回自造记录类型 ⇒ 与 `NotifyTarget` 重复表达同一件事，否。
- 端口开在 tool、boot 装配实现 ⇒ 也可行，但主公已定"接口放 core"，且 core 端口与既有的 `LlmCallRecorder`/`ToolInvocationRecorder`/`SessionManager` 同族，口径更统一。

---

## D9 Sandbox 前向接口五件 + `PermissiveSandbox` 临时装配（安全口径要写明白）

**Decision**: `fourfeetcat-tool` 的 `sandbox` 包交付五件：`Sandbox`（`void enforce(SandboxAction)`）、`SandboxAction`（`ActionType type` + `String target`）、`ActionType`（四值 `FILE_READ`/`FILE_WRITE`/`SHELL_COMMAND`/`HTTP_REQUEST`）、`SandboxViolationException`、`PermissiveSandbox`（**不做任何校验**的临时装配）。白名单规则的实现体（`WhitelistSandbox`）归沙箱节。

**Rationale**: 课件"本节交付物"明列"Sandbox 前向接口五件（含 PermissiveSandbox 临时装配，实现本体 24 节）"，技术方案 §6.7 同口径（接口先行 + 核心阶段挂一档实现）。

**⚠️ 安全口径（必须三处标注）**: `PermissiveSandbox` 让所有涉外动作直接通过，是**有意为之的临时状态**——它存在的唯一理由是让沙箱节之前已注册的工具能端到端跑通。它的类注释、tasks.md 的交付物说明、节级验收报告三处都要写明"由沙箱节替换"。它**不是**可用状态，不得据此跑不可信代码。

**接口中立性**: `Sandbox.enforce` 的签名里不出现"白名单""容器""VM"这类某一档实现特有的词——用最重的 microVM 实现反向套这个签名也应能干净套入（技术方案 §6.7 的校验办法）。

---

## D10 命令执行工具：白名单精确放行 + argv 直传 + 超时 + 输出上限

**Decision**: `shell` 工具收 `command`（可执行文件）与 `args`（数组），以 `ProcessBuilder` argv 直传、**不经 shell 解释**；超时 **30 秒**，超时即 `destroyForcibly()` 并返回失败；合并后的输出超过 **8000 字符**即截断并在尾部注明。

**Rationale**: 技术方案 §6.2 与宪法原则六要求"白名单精确放行 + argv 直传 + 超时"；课件补"输出过大要截断并注明"。30 秒与 8000 字符是课件未给数时的工程默认值，落在常量里便于调整。

---

## D11 审计：零新增，注册表接入后自动生效

**Decision**: 不写任何新的审计代码。`ToolRegistry` 实现 core 的 `ToolTable` 之后，`ToolExecutor` 既有的"执行前检查位 → 执行 → 成败都写 `tool_invocations`"路径原样作用到所有新工具（内置、注解、MCP），包括 `SandboxViolationException` 抛出时的失败留痕。

**Rationale**: 技术方案 §6.2 与 §6.7 都写明"复用 `ToolExecutor` 已有的失败审计路径，不需要为 Sandbox 单独新增审计逻辑"。这正好是本节要证明的"加工具成本恒定"。

**注意**: `ToolExecutor` 目前把检查位留成注释（"第24节 Sandbox 接线"）。**本节不把校验搬进 `ToolExecutor`**——校验写在每个工具自己的第一行（技术方案 §6.2/§6.7：`FileTools`/`ShellTools`/`HttpTools` 在各自 execute 开头调 `sandbox.enforce`），`ToolExecutor` 那一行注释保留。

---

## D12 `fourfeetcat tool list` 换成重命令（借容器取 Bean），命令字面量不动

**Decision**: `ToolListCommand` 加 `@ParentCommand FourFeetCatCli root`，经 `root.engine(WebApplicationType.NONE)` 取容器里的 `ToolRegistry` Bean 打印工具名与描述；命令名、分组、描述一字不改。`fourfeetcat-cli` 新增对 `fourfeetcat-tool` 的依赖（无环：`cli → tool → core`）。

**Rationale**: 第18节 `ToolListCommand` 的注释原文即"第20节交付后**只换数据源，命令本身不改**"——这是前序节预声明的改造点。工具实例需要 Sandbox、`RestClient` 与渠道数据源，脱离容器自己拼一套等于把 boot 的装配逻辑抄第二份。

---

## D13 MCP 配置加载器落 tool 模块，用 SnakeYAML

**Decision**: `fourfeetcat-tool` 内新增 `McpServerConfig`（record：name/transport/command/env）与一个 YAML 加载器，读 boot 传入的工作区路径下的 `mcp_servers.yaml`；tool 的 pom 加 `org.yaml:snakeyaml`（BOM 托管，与 `fourfeetcat-cli` 同款声明）。配置文件不存在或为空 → 返回空列表（零配置可启动）。

**Rationale**: 技术方案 §10 把 MCP 相关全部划给 `fourfeetcat-tool`，配置加载属于它自家的事；路径来自 boot（`FOURFEETCAT_ROOT` 的口径只在 boot 里有一处）。仓库里 `.fourfeetcat/mcp_servers.yaml` 与 init 模板都已有 `servers: []` 的空壳，本节把它读起来。

---

## D14 工具返回值原样交给模型：自带一档结果转换器（实施中发现，经主公裁决）

**Decision**: 新增 `fourfeetcat-tool` 的 `PlainTextResultConverter implements ToolCallResultConverter`（`convert` 直接返回
`String.valueOf(result)`），并在每个 `@Tool` 注解上挂 `resultConverter = PlainTextResultConverter.class`。

**Rationale**（实测得出，不是推测）: Spring AI 的默认结果转换器会把 `@Tool` 方法的**返回值 JSON 化**——实测 `read_file` 读
`abc` 时，`ToolCallback.call()` 返回的是 `"abc"`（带引号）；`write_file` 返回 `"已写入 /tmp/…"`。后果是每个内置工具的输出交给
模型时都多一层引号、**换行被转义成字面的 `\n`**：`list_dir` 的多行输出挤成一行、`read_file` 读长文件时整篇被转义。文件内容本身
没写错（已用诊断验证），错的是"递给模型的那份文本"。

Spring AI 只带一个转换器（`DefaultToolCallResultConverter`，实测），没有现成的"原样返回"可挂，所以必须自带一档。

**Alternatives considered**:
- 接受默认的 JSON 化 ⇒ 工具输出可用性实打实下降（多行变一行），否。
- 让工具方法改返回结构化对象 ⇒ 输出形态变了，且 `read_file`/`shell` 这类纯文本工具反而别扭，否。
- 不走 `ToolCallback.call()`、自己反射调目标方法 ⇒ 等于重造参数绑定，否。

**多出项声明**: `PlainTextResultConverter` 不在课件"本节交付物"清单内，属清单外对外概念——已记入 tasks.md T039 的多出项并
经主公裁决（问：修不修？答：修，加一档"原样返回"转换器）。

---

## 依赖核实汇总（H3 硬门禁执行记录）

| 待核实项 | 命令 | 结论 |
|---|---|---|
| MCP SDK 可下载且版本与 BOM 一致 | `mvn dependency:get -Dartifact=org.springframework.ai:spring-ai-mcp:1.1.2` + 读其 pom | ✅ 其依赖 `io.modelcontextprotocol.sdk:mcp:0.17.0`；`mvn dependency:get -Dartifact=io.modelcontextprotocol.sdk:mcp-core:0.17.0` 成功 |
| MCP 客户端类与方法 | `javap -classpath mcp-core-0.17.0.jar ...` | ✅ 见 D5 |
| `@Tool` 扫描与 schema 生成 | `javap -classpath spring-ai-model-1.1.2.jar ...` | ✅ 见 D4 |
| 既有 classpath 是否已有 Reactor/WebFlux | `mvn dependency:tree -pl fourfeetcat-boot -Dincludes=io.projectreactor*,org.springframework:spring-webflux` | ✅ 已有 `reactor-core 3.7.19` 与 `spring-webflux 6.2.19`（Spring AI 传递），本节不新增异步栈 |
| 前序节交付物是否存在 | 对 16/17/18/19 节交付物逐项 grep/Glob | ✅ 全部在位（`ToolTable`/`ToolDescriptor`/`ToolExecutionResult` 为本节改造点） |
