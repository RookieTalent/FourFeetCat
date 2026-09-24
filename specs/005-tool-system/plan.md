# Implementation Plan: Tool 体系（第20节）

**Branch**: `020-lesson20-tool-system` | **Date**: 2026-09-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-tool-system/spec.md`

## Summary

给 Agent 装上"手"。三件事：

1. **立统一抽象**：`fourfeetcat-core` 新增 `CatTool`（名字 / 用途描述 / 参数说明 / 执行四方法）与 `ToolResult`（成功标识 / 内容 / 错误 / 可重试），把"来源"彻底屏蔽；`fourfeetcat-tool` 的 `ToolRegistry` 实现 core 既有的 `ToolTable` 端口，三种来源（内置、`@Tool` 注解、MCP）都包装成 `CatTool` 注册进同一张表，再按 Agent 声明的工具名清单过滤。
2. **内置工具真的能动手**：`FileTools`（read_file/write_file/list_dir）、`ShellTools`（shell，白名单 + argv 直传 + 超时 + 输出截断）、`HttpTools`（http_get/http_post），每个工具的第一行都过沙箱校验位；第19节欠的 `NotifyTools` 在此接线落地。
3. **三档接入打通**：注解 Java 方法自动注册（`AnnotatedToolAdapter`）、外部 MCP server 启动时全量连接并包装注册（`McpClientService` + `McpToolAdapter`，失联隔离）、零代码复用由 29 节的 Agent 目录承接（本节保证工具入口可用）。

沙箱**规则本体**归沙箱节，本节只交付它的前向接口五件与一档不做校验的**临时装配**（见 Complexity Tracking）。

## Technical Context

**Language/Version**: Java 21（Maven 多模块，spring-boot-starter-parent 3.5.16）

**Primary Dependencies**: **新增一条第三方依赖**——MCP 官方 Java SDK `io.modelcontextprotocol.sdk:mcp`（版本 0.17.0 在父 pom 的 `dependencyManagement` 显式钉死）。选型与核实见 research.md D5：它是锁定 BOM 里 `spring-ai-mcp:1.1.2` 所传递的同一版本；不选 `spring-ai-mcp` 本身，因为它多带 webflux/webmvc 传输层，而本工程禁用 Spring AI 自动工具执行，用不上它的桥接。另加两条 **BOM 托管**依赖声明：`fourfeetcat-core` 加 `com.fasterxml.jackson.core:jackson-databind`（`CatTool.execute(JsonNode)` 需要，此前靠传递、现显式声明）、`fourfeetcat-tool` 加 `org.yaml:snakeyaml`（读 MCP 配置，与 `fourfeetcat-cli` 同款）。模块依赖新增：`fourfeetcat-cli` → `fourfeetcat-tool`（`tool list` 取 Bean）。

**Storage**: 本节**不新增表、不新增迁移脚本**。只消费第19节已交付的 `notify_channels`（实体 `NotifyChannel` + `NotifyChannelRepository`），在其上加一层实现 `NotifyChannelSource` 端口的适配类。

**Testing**: JUnit 5 + Mockito + AssertJ（`spring-boot-starter-test`，各模块已就位）。全部单测、不碰网、不花 token——MCP 用 mock 客户端、HTTP 用 JDK 内置 `com.sun.net.httpserver.HttpServer`（第19节已验证的零依赖假接收端手法）、沙箱用替身、进程执行用 JDK 自带可执行文件。集成冒烟打 `@Tag("integration")` CI 跳过。

**Target Platform**: JVM 服务端

**Project Type**: library（多模块底座；本节是 `fourfeetcat-tool` 从"只有 notify"到"能力四全量"的一节）

**Performance Goals**: 无专项指标。一次工具调用 = 一次同步执行（宪法原则七）；MCP 是同步 `McpSyncClient`，不引入流式。

**Constraints**: 宪法原则二（禁用自动工具执行）、原则五（审计走既有路径、零新增）、原则六（不用 SecurityManager，校验位在工具第一行）、原则七（同步，代码里不出现 Reactor/`CompletableFuture`/自建线程池）；语法禁区——避开 P3C/ASM 解析不了的 Java 18+ 形态；测试方法名英文 + `@DisplayName` 保留课件原文。

**Scale/Scope**: 课件"本节交付物"主干——`CatTool`/`ToolResult`/`ToolRegistry`/`AnnotatedToolAdapter`/`FileTools`(3)/`ShellTools`/`HttpTools`(2)/`McpClientService`/`McpToolAdapter`/`NotifyTools`/沙箱前向五件 + **9 个测试类**（课件点名 7：契约、注册表、三个内置工具、两个 MCP；第19节 harness 第二批 1：`NotifyToolsTest`；多出项 1：`AnnotatedToolAdapterTest`）+ MCP 配置。**不做**：课件第六部分五个扩展工具、记忆工具（第22节）、沙箱规则本体（沙箱节）、工具治理策略、按需加载。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|---|---|---|
| 一：自实现 ReAct | 本节不碰循环；`ToolExecutor` 零改动（只在它下游换上真注册表） | ✅ 通过 |
| 二：Spring AI 只用两件事 ⚠️ | 只用它两件事：①协议转换（不变）②`@Tool` 的 schema 生成（`MethodToolCallbackProvider`）。**禁用**自动执行：不把 `ToolCallback` 挂到 `ChatClient`，`call()` 只可能经 `CatTool.execute()` ← `ToolRegistry.execute()` ← `ToolExecutor` 一条路进来。`ToolSchemaAdapter` 里那个"只翻译不执行"的产物原样保留 | ✅ 通过（research D4 记录了唯一执行路径的论证） |
| 三：Provider 显式映射 | 本节不碰 provider 路由；`ToolDescriptor` 口径不变，`LlmCaller` 签名不变 | ✅ 通过 |
| 四：一个目录 = 一个 Agent | Agent 目录/Skill 绑定归 29 节；本节只消费 Profile 既有的 `tools` 名清单字段（类型与语义不改），不引入 `notify_channels` 之类的 frontmatter 字段 | ✅ 通过 |
| 五：审计 Day One | **零新增审计代码**：注册表实现 `ToolTable` 后，`ToolExecutor` 既有的成败双写路径自动覆盖所有新工具（含沙箱拦截的失败） | ✅ 通过（research D11） |
| 六：无 SecurityManager、真实路径校验 | 不用 SecurityManager；校验位立在每个涉外工具的第一行（`Sandbox.enforce`），规则本体归沙箱节。**本节挂的是不做校验的临时装配**——已列为有意为之的临时状态，三处标注替换时点 | ⚠️ 有条件通过（见 Complexity Tracking） |
| 七：同步执行 | 代码里不出现 Reactor/`CompletableFuture`/自建线程池；MCP 走同步 `McpSyncClient`。Reactor 只是第三方库内部实现，且早已在 classpath 上 | ✅ 通过 |
| 技术约束：依赖倒置 | 新增端口 `NotifyChannelSource` 在 core；实现在 storage；消费方在 tool（依赖方向 tool→core、storage→core、boot→{tool,storage,cli}，**无环**） | ✅ 通过 |
| 技术约束：Flyway 双轨 | 本节零表结构变更 ⇒ 零迁移脚本 | ✅ 通过 |
| 技术约束：配置与凭证 | MCP 配置的 `env` 段允许 `${ENV_VAR}` 占位；本节写入的任何配置不留明文凭证 | ✅ 通过 |
| 技术约束：模块结构 | **不新建、不改名模块**；`fourfeetcat-tool` 的内容扩张与该模块既定职责（内置 Tool + MCP + 沙箱三合一）完全一致，无需同步 §10 | ✅ 通过 |
| 技术约束：文档同步 | 本节不扩内置工具数量（五个扩展工具与记忆工具都不做），README/技术方案/CLAUDE.md 的"内置九个"表述**仍然正确**，无需改动 | ✅ 通过 |

## Project Structure

### Documentation (this feature)

```text
specs/005-tool-system/
├── plan.md              # 本文件
├── research.md          # Phase 0：13 条设计决策 + H3 依赖核实记录
├── data-model.md        # Phase 1：工具/结果/描述/校验动作/MCP 声明五个值对象 + 端口形状
├── quickstart.md        # Phase 1：验证指南（判卷命令块 + 人工项）
├── contracts/           # Phase 1：跨模块 Java 契约
└── tasks.md             # /speckit-tasks 产出（本命令不创建）
```

### Source Code (repository root)

```text
fourfeetcat-core/src/main/java/org/fourfeetcat/core/
├── tool/CatTool.java                  # 新增：统一工具抽象（四方法）
├── tool/ToolResult.java               # 新增（由 ToolExecutionResult 改名）：四字段结果值对象
├── tool/ToolExecutionResult.java      # 删除（按名合并进 ToolResult，第17节预声明）
├── tool/ToolTable.java                # 改：返回类型随改名，签名与语义不变；javadoc 指向 ToolRegistry
├── ToolDescriptor.java                # 不动（String inputSchema 口径，Provider 零改动）
├── react/ToolExecutor.java            # 改：仅随 ToolResult 改名，检查位注释保留
├── react/ReActLoop.java               # 改：仅随 ToolResult 改名
└── notify/NotifyChannelSource.java    # 新增：渠道取数端口（返回通用 Map 列表）

fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/
├── registry/ToolRegistry.java         # 新增：实现 core 的 ToolTable；注册 + 按清单过滤 + 内省
├── registry/AnnotatedToolAdapter.java # 新增：@Tool 方法 → CatTool（schema 由 Spring AI 生成）
├── builtin/FileTools.java             # 新增：read_file / write_file / list_dir
├── builtin/ShellTools.java            # 新增：shell（白名单 + argv 直传 + 30s 超时 + 输出截断）
├── builtin/HttpTools.java             # 新增：http_get / http_post
├── notify/NotifyTools.java            # 新增：notify（第19节 harness 第二批的接线落地）
├── mcp/McpServerConfig.java           # 新增：一条外部服务声明（name/transport/command/env）
├── mcp/McpServerConfigLoader.java     # 新增：读 .fourfeetcat/mcp_servers.yaml（SnakeYAML）
├── mcp/McpClientFactory.java          # 新增：造客户端的测试缝（契约 §5；让 connectAll 可在单测里不真起进程）
├── mcp/McpClientService.java          # 新增：启动时连接全部声明的 server，逐个失联隔离
├── mcp/McpToolAdapter.java            # 新增：MCP 工具 → CatTool（原样转发参数）
└── sandbox/{Sandbox,SandboxAction,ActionType,SandboxViolationException,PermissiveSandbox}.java
                                       # 新增：沙箱前向接口五件（规则本体归沙箱节）

fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/
└── JpaNotifyChannelSource.java        # 新增：读 notify_channels 表，实现 core 的取数端口

fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/AgentRuntimeConfiguration.java
                                       # 改：删 UnregisteredToolTable；装配 Sandbox / ToolRegistry /
                                       #     内置工具注册 / NotifyChannelSource / McpClientService

fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/ToolListCommand.java
                                       # 改：换数据源（借容器取 ToolRegistry），命令字面量不动

fourfeetcat-cli/pom.xml                # 改：加 fourfeetcat-tool 依赖
fourfeetcat-core/pom.xml               # 改：显式声明 jackson-databind
fourfeetcat-tool/pom.xml               # 改：加 mcp SDK、snakeyaml
pom.xml                                # 改：dependencyManagement 钉 MCP SDK 版本
```

**Structure Decision**: 沿用技术方案 §10 的既定归属——`CatTool`/`ToolResult`/取数端口在 `fourfeetcat-core`（跨模块契约，依赖倒置）；其余 Tool 体系全部在 `fourfeetcat-tool`（内置 Tool + MCP + 沙箱三合一）；渠道持久化适配在 `fourfeetcat-storage`；装配与 CLI 数据源在 `fourfeetcat-boot` / `fourfeetcat-cli`。**不新建、不改名任何模块。**

`ToolRegistry` 放在 `fourfeetcat-tool` 而不是 core，是为了守住"禁止模块间循环依赖"：`ToolExecutor` 在 core，若 core 直接依赖 `ToolRegistry` 就成环；core 侧保留既有的 `ToolTable` 端口，tool 侧实现它（research D3）。

## Complexity Tracking

> 记两处需要 reviewer 一眼看到的偏差点：一处是**安全上的临时状态**（必须被替换），一处是**前序节交付物的改名**。

| 事项 | 为什么需要 | 更简单的替代为何被否 |
|---|---|---|
| `PermissiveSandbox`：一档**不做任何校验**的临时装配，作为本节默认的 `Sandbox` Bean | 课件"本节交付物"明列，技术方案 §6.7 同口径（接口先行 + 核心阶段挂一档）。没有它，沙箱节之前已注册的工具一个都跑不通，"Agent 真的能动手"这条验收就落不了地 | 直接把白名单实现提前做 ⇒ 等于把沙箱节整体抢跑，且白名单配置键（`file.allowed_paths` 等）属沙箱节的交付面；不挂任何实现 ⇒ 工具全部不可用。<br>**风险与兜底**：这是**有意为之的临时状态**，不是可用状态。类注释、tasks.md 交付物说明、节级验收报告三处标注"由沙箱节替换"；harness 用替身 Sandbox 验证"校验先于动作"的顺序断言，不依赖临时装配的行为 |
| `ToolExecutionResult` → `ToolResult`（删旧类、改 6 处引用）与 `ToolListCommand` 换数据源 | 前者是第17节 javadoc 原文"四个字段与第20节的 `ToolResult` 逐字对齐……**届时按名合并**"；后者是第18节 javadoc 原文"第20节交付后**只换数据源，命令本身不改**"。两处都是前序节**预先声明**的改造点 | 保留旧名 ⇒ 与课件、与技术方案 §6.1 的字面量长期分叉，且"逐字对齐"的那句话失去意义；`tool list` 不接 ⇒ 命令永远打印占位文案 |
