# Phase 1 Data Model: Tool 体系（第20节）

**Input**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

**本节不新增持久化数据**：零新表、零新列、零迁移脚本。以下全部是**内存中的值对象与端口形状**（`spec.md` 的 Key Entities 逐条落成 Java）。持久化只被**消费**一次——读第19节已交付的 `notify_channels` 表（见 §6）。

---

## §1 `CatTool` —— 统一工具形状（`fourfeetcat-core`）

任何来源的工具都长这样，执行入口只跟它打交道。

| 方法 | 语义 | 约束 |
|---|---|---|
| `String getName()` | 工具名，模型靠它点名 | 非空；注册表内唯一；重名注册必须有确定行为（拒绝并报错） |
| `String getDescription()` | 干什么用的，给模型看 | 非空（契约守卫会查） |
| `String getInputSchema()` | 参数说明，JSON Schema 文本 | **非空**（契约守卫会查）；口径与既有的 `ToolDescriptor.inputSchema` 同型（research D1） |
| `ToolResult execute(JsonNode input)` | 真正执行 | 输入是模型给的参数树；失败**不抛异常**而是返回失败结果（除沙箱拦截——见 §4） |

**来源三态**（对调用方不可见）：内置工具（`FileTools`/`ShellTools`/`HttpTools`/`NotifyTools`）、注解工具（`@Tool` 方法经 `AnnotatedToolAdapter`）、外部工具（MCP 经 `McpToolAdapter`）。

**不变量**：`execute` 的第一步必须过沙箱校验位（涉外工具），校验不过即抛 `SandboxViolationException`、动作不发生（spec FR-014）。

---

## §2 `ToolResult` —— 一次执行的结果（`fourfeetcat-core`）

`record ToolResult(boolean success, String content, String errorMessage, boolean retryable)`

| 字段 | 语义 | 取值约束 |
|---|---|---|
| `success` | 成没成 | — |
| `content` | 结果内容（给模型读的文本） | 成功时非空 |
| `errorMessage` | 错误原因 | 失败时非空；**失败结果也要回填进对话**让模型换招，不是抛异常炸整轮 |
| `retryable` | 这错值不值得再调一次 | 循环据此决定是否重试 |

工厂：`success(String content)` / `failure(String errorMessage, boolean retryable)`。

**来源**：第17节交付的 `ToolExecutionResult` 按名合并而来，四个字段逐字对齐、语义一字不改（research D2）。改名后 `ToolTable`、`ToolExecutor`、`ReActLoop` 及两个测试类同步改引用。

---

## §3 `ToolDescriptor` —— 递给模型的工具说明（`fourfeetcat-core`，**不动**）

`record ToolDescriptor(String name, String description, String inputSchema)`

从注册表按名字清单导出的**视图**（不是可执行体）。由 `CatTool` 投影而来：`getName/getDescription/getInputSchema` 一一对应。Provider 的 `ToolSchemaAdapter` 与 `LlmCaller` 的签名都按这个口径，本节不碰。

---

## §4 沙箱前向契约（`fourfeetcat-tool` 的 `sandbox` 包）

```text
Sandbox.enforce(SandboxAction action)          # 唯一方法，只表达"在受控环境里执行一个动作"
SandboxAction = { ActionType type, String target }
ActionType    = FILE_READ | FILE_WRITE | SHELL_COMMAND | HTTP_REQUEST
```

| 动作类型 | `target` 是什么 | 谁发起的 |
|---|---|---|
| `FILE_READ` | 文件/目录路径 | `read_file` / `list_dir` |
| `FILE_WRITE` | 文件路径 | `write_file` |
| `SHELL_COMMAND` | 可执行文件名 | `shell` |
| `HTTP_REQUEST` | 完整 URL | `http_get` / `http_post` / `notify` |

**校验失败** → 抛 `SandboxViolationException`，由 `ToolExecutor` 既有的失败路径写入 `tool_invocations`（`success=false` + 原因），不为沙箱新增任何审计逻辑。

**签名中立性硬要求**：接口签名里不出现"白名单""容器镜像""VM 配置"这类某一档实现特有的词——用最重的 microVM 实现反向套这个签名也应能干净套入。

**本节实现状态**：`PermissiveSandbox`（不做校验，**临时装配**，规则本体归沙箱节，三处标注替换时点）。

---

## §5 外部工具服务声明（`fourfeetcat-tool` 的 `mcp` 包 + 工作区 YAML）

`record McpServerConfig(String name, String transport, String command, Map<String, String> env)`

落 `.fourfeetcat/mcp_servers.yaml`（**配置文件，不落库**）：

```yaml
servers:
  - name: github-mcp
    transport: stdio          # 核心阶段只放行 stdio；其他取值记 WARN 跳过（research D6）
    command: npx -y @modelcontextprotocol/server-github   # 按空白拆成可执行文件 + 参数数组
    env:
      GITHUB_TOKEN: ${GITHUB_TOKEN}     # 凭证走环境变量占位，不明文
```

| 字段 | 必填 | 语义 |
|---|---|---|
| `name` | 是 | 声明名，日志与诊断用 |
| `transport` | 是 | 核心阶段仅 `stdio`（**精确匹配**，不做大小写折叠——取值是本仓定的字面量） |
| `command` | 是（stdio） | 启动命令；按空白拆成 argv，不经 shell 解释 |
| `env` | 否 | 子进程环境变量 |

**空配置合法**：文件不存在或 `servers` 为空 → 零注册、正常启动。

---

## §6 渠道取数端口（`fourfeetcat-core`）+ 其实现（`fourfeetcat-storage`）

```java
// fourfeetcat-core
public interface NotifyChannelSource {
  /** 全部渠道记录；每条记录的键为 name / type / url / description。表中无记录 → 空列表。 */
  List<Map<String, String>> all();
}
```

```java
// fourfeetcat-storage —— 读第19节已交付的表，实体与仓储都不改
public class JpaNotifyChannelSource implements NotifyChannelSource { /* findAll() → 投影成 Map */ }
```

**为什么返回通用 `Map` 而不是自造值对象**：`NotifyTarget` 留在 `fourfeetcat-tool`（主公裁决），core 端口的返回值不能是它；用通用对象承载就不必在 core 里再造一份与 `NotifyTarget` 同形状的类型（research D8）。

**解析与投影在消费方**（`fourfeetcat-tool` 的 `NotifyTools`），口径：

| 情形 | 行为 |
|---|---|
| 表中一条都没有 | 抛异常"未配置通知渠道"——**绝不静默成功** |
| 调用时不给渠道名 | 取第一条 |
| 给了渠道名但在表中查不到 | 抛异常并指出名字 | 
| 正常 | 投影成 `NotifyTarget(type, {url: ...})` → 过沙箱 `HTTP_REQUEST` 校验 → 交给出站适配器 |

**落库的 `notify_channels` 表结构不变**（第19节 V19 双轨脚本已交付，本节零迁移）：主键 `name`、`type`、`url`、`description`、`config`。
