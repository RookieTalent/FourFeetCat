# Phase 1 Contracts: Tool 体系（第20节）

**Input**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Data Model**: [data-model.md](../data-model.md)

本节全部新增签名逐条列出；**动了的前序节签名单独标出**（都是第16/17/18节预先声明的改造点）。

---

## §1 `fourfeetcat-core`：统一抽象与结果值对象

```java
// tool/CatTool.java —— 新增：统一工具抽象。任何来源的工具都是这个样子。
public interface CatTool {
  String getName();
  String getDescription();
  String getInputSchema();            // JSON Schema 文本，与 ToolDescriptor.inputSchema 同型
  ToolResult execute(JsonNode input); // 输入：模型给的参数树
}

// tool/ToolResult.java —— 新增（由 ToolExecutionResult 按名合并）
public record ToolResult(boolean success, String content, String errorMessage, boolean retryable) {
  public static ToolResult success(String content);
  public static ToolResult failure(String errorMessage, boolean retryable);
}

// notify/NotifyChannelSource.java —— 新增：渠道取数端口（依赖倒置）
public interface NotifyChannelSource {
  List<Map<String, String>> all();    // 键：name / type / url / description；空表 → 空列表
}
```

### 被改动的既有签名（**改造点**，两处均由前序节 javadoc 预先声明）

```java
// tool/ToolTable.java —— 端口保留，签名形状与语义一字不改；仅返回类型随改名
public interface ToolTable {
  List<ToolDescriptor> descriptors(List<String> names);   // 不变
  ToolResult execute(String toolName, String inputJson);  // 原 ToolExecutionResult → ToolResult
}

// tool/ToolExecutionResult.java —— 删除（合并进 ToolResult）

// react/ToolExecutor.java、react/ReActLoop.java —— 仅 6 处引用随改名，逻辑零改动
```

**`ToolTable` 为什么活着**：`ToolExecutor` 在 core、`ToolRegistry` 在 tool（技术方案 §10），core 不能反向依赖 tool，故保留 core 侧端口、由 tool 实现（research D3）。第17节该接口的 javadoc 原文即"第20节立统一工具抽象后**由它取代，两个方法的语义不变**"——语义不变、实现换人，正是本契约。

`ToolDescriptor` **不动**：Provider 侧 `ToolSchemaAdapter` 与 `LlmCaller` 因此零改动。

---

## §2 `fourfeetcat-tool`：注册表（唯一执行入口的下游）

```java
// registry/ToolRegistry.java —— 新增：实现 core 的 ToolTable
public class ToolRegistry implements ToolTable {

  /** 注册一个工具；重名 → 抛 IllegalStateException（不静默覆盖，否则"调了哪个"不可复现）。 */
  public void register(CatTool tool);

  /** 扫一遍带 @Tool 注解的方法，逐个包成 CatTool 注册；返回注册个数。 */
  public int registerAnnotated(Object... beansWithToolMethods);

  /** 表里有没有这个名字（内省与 MCP 测试用）。 */
  public boolean contains(String toolName);

  /** 全部已注册工具（`tool list` 与契约守卫遍历用）。 */
  public List<CatTool> all();

  // ToolTable 的两个方法：
  @Override public List<ToolDescriptor> descriptors(List<String> names);  // 按清单取描述，未知名 → 报错
  @Override public ToolResult execute(String toolName, String inputJson); // 查表 → 解析参数 → 执行
}

// registry/AnnotatedToolAdapter.java —— 新增：@Tool 方法 → CatTool
public class AnnotatedToolAdapter {
  /** 用 Spring AI 的 MethodToolCallbackProvider 生成 schema，再把每个回调包成 CatTool。
   *  执行权仍唯一：call() 只可能从 CatTool.execute() 进来（宪法原则二，research D4）。 */
  public static List<CatTool> adapt(Object beanWithToolMethods);
}

// registry/PlainTextResultConverter.java —— 新增：让工具返回值原样交给模型
// 原因：Spring AI 的默认转换器会把 String 返回值 JSON 化（多一层引号、换行转义成字面 \n），
// 实测 read_file 读 abc 返回 "abc"、list_dir 的多行输出挤成一行。research.md D14。
public class PlainTextResultConverter implements ToolCallResultConverter {
  @Override public String convert(Object result, Type returnType);   // String.valueOf(result)
}
```

**`execute` 的两条硬规矩**：① 名字查不到 → 抛错（Profile 声明的工具不存在是配置错误，静默少给会让模型无从下手）；② 工具内部抛出的异常**不吞**——`ToolExecutor` 那一层已负责把它转成失败结果并写审计。

---

## §3 `fourfeetcat-tool`：内置工具（每个的 execute 第一行都过沙箱）

```java
// builtin/FileTools.java —— 新增：read_file / write_file / list_dir
public class FileTools {
  public FileTools(Sandbox sandbox);
  @Tool(name = "read_file",  description = "...") public String readFile(@ToolParam("路径") String path);
  @Tool(name = "write_file", description = "...") public String writeFile(String path, String content);
  @Tool(name = "list_dir",   description = "...") public String listDir(String path);
  // 每个方法体第一行：sandbox.enforce(new SandboxAction(FILE_READ|FILE_WRITE, path))
}

// builtin/ShellTools.java —— 新增：shell
public class ShellTools {
  public ShellTools(Sandbox sandbox, Duration timeout, int maxOutputChars);
  @Tool(name = "shell", description = "...") public String shell(String command, List<String> args);
  // 第一行：sandbox.enforce(new SandboxAction(SHELL_COMMAND, command))
  // 然后：ProcessBuilder argv 直传（不经 shell 解释）→ 超时 destroyForcibly → 输出截断并注明
}

// builtin/HttpTools.java —— 新增：http_get / http_post
public class HttpTools {
  public HttpTools(Sandbox sandbox, RestClient restClient);
  @Tool(name = "http_get",  description = "...") public String httpGet(String url);
  @Tool(name = "http_post", description = "...") public String httpPost(String url, String body);
  // 每个方法体第一行：sandbox.enforce(new SandboxAction(HTTP_REQUEST, url))
}
```

---

## §4 `fourfeetcat-tool`：通知工具（第19节欠的接线）

```java
// notify/NotifyTools.java —— 新增
public class NotifyTools {
  public NotifyTools(Sandbox sandbox, NotifyChannelAdapter adapter, NotifyChannelSource source);

  @Tool(name = "notify", description = "...")
  public String notify(String content, String channel);   // channel 可缺省
}
```

**行为契约**（spec FR-010 与第19节 harness 第二批逐条对齐）：

| 情形 | 行为 |
|---|---|
| 渠道表一条都没有 | 抛异常"未配置通知渠道"——**绝不静默成功** |
| `channel` 缺省/空 | 取第一条 |
| `channel` 给了但查不到 | 抛异常并指出名字 |
| 正常 | 先 `sandbox.enforce(HTTP_REQUEST, url)`，**再** `adapter.send(target, content)` —— **顺序不可反**，反了就是绕过白名单的漏洞（用 `InOrder` 钉死） |
| 发送失败 | 异常上抛，不包装（第19节 `WebhookNotifyAdapter` 的既定行为） |

---

## §5 `fourfeetcat-tool`：MCP 接入

```java
// mcp/McpServerConfig.java —— 新增
public record McpServerConfig(String name, String transport, String command, Map<String, String> env) {}

// mcp/McpServerConfigLoader.java —— 新增：读 .fourfeetcat/mcp_servers.yaml（SnakeYAML）
public class McpServerConfigLoader {
  public McpServerConfigLoader(Path workspaceRoot);
  public List<McpServerConfig> load();     // 文件不存在 / servers 为空 → 空列表
}

// mcp/McpClientService.java —— 新增：启动时连接**全部**声明的 server；持有连接，停机时关闭
public class McpClientService implements AutoCloseable {
  public McpClientService(McpServerConfigLoader loader);

  /** 逐个连、逐个注册；任一失败只 WARN 并跳过它的工具，绝不阻断启动（FR-013）。 */
  public void connectAll(ToolRegistry registry);

  /** 测试缝：把"造一个客户端"这件事抽出来，测试里换成替身，不真起进程。 */
  public void connectAll(ToolRegistry registry, McpClientFactory factory);

  /** 关掉所有已连接的客户端——每个背后是一个子进程，不关就是停机后的孤儿进程。 */
  @Override public void close();
}

// mcp/McpToolAdapter.java —— 新增：MCP 工具 → CatTool
public class McpToolAdapter implements CatTool {
  public McpToolAdapter(McpSyncClient client, McpSchema.Tool spec);
  // getName/getDescription/getInputSchema 直接映射 spec；execute 原样转发参数、结果包成 ToolResult
  // 失败（含 isError()=true）→ ToolResult.failure(..., retryable=true)
}
```

**关键口径**：启动时连**配置里声明的全部**（不按 Agent 逐个连，FR-011）；Agent 侧隔离仍只由"可用工具名清单"承担，不另起一层。

---

## §6 `fourfeetcat-tool`：沙箱前向接口五件

```java
// sandbox/Sandbox.java
public interface Sandbox { void enforce(SandboxAction action); }

// sandbox/SandboxAction.java
public record SandboxAction(ActionType type, String target) {}

// sandbox/ActionType.java
public enum ActionType { FILE_READ, FILE_WRITE, SHELL_COMMAND, HTTP_REQUEST }

// sandbox/SandboxViolationException.java —— 继承 RuntimeException，校验不过即抛
public class SandboxViolationException extends RuntimeException { ... }

// sandbox/PermissiveSandbox.java —— ⚠️ 临时装配：不做任何校验。规则本体归沙箱节，三处标注替换时点。
public class PermissiveSandbox implements Sandbox { /* enforce 直接返回 */ }
```

---

## §7 `fourfeetcat-storage`：取数端口实现

```java
// JpaNotifyChannelSource.java —— 新增；复用第19节的 NotifyChannel 实体与 NotifyChannelRepository
public class JpaNotifyChannelSource implements NotifyChannelSource {
  public JpaNotifyChannelSource(NotifyChannelRepository repository);
  @Override public List<Map<String, String>> all();   // findAll() → 投影成 Map（键 name/type/url/description）
}
```

---

## §8 `fourfeetcat-boot`：装配

```java
// AgentRuntimeConfiguration.java（改）
@Bean Sandbox sandbox();                       // PermissiveSandbox（临时，沙箱节替换）
@Bean NotifyChannelSource notifyChannelSource(NotifyChannelRepository repo);
@Bean ToolRegistry toolRegistry(Sandbox sandbox, NotifyChannelSource source,
                                NotifyChannelAdapter adapter, RestClient restClient);  // 注册内置工具 + MCP connectAll
@Bean ToolExecutor toolExecutor(ToolRegistry registry, ToolInvocationRecorder audit);
// UnregisteredToolTable（第18节占位）——删除
```

---

## §9 模块依赖方向（新增边，全部无环）

```text
core        ← tool      (既有)
core        ← cli        (既有)
core        ← storage   (既有)
storage     ← cli        (既有)
tool        ← cli        (新增：tool list 取 Bean)          cli → tool → core
tool        ← boot      (第19节已加)
storage     ← boot      (既有)
```

**禁止**：`core → tool`（会与 `tool → core` 成环，正是 `ToolTable` 端口存在的理由）。
