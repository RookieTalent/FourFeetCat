---

description: "Task list for Tool 体系（第20节）implementation"

---

# Tasks: Tool 体系（第20节）

**Input**: Design documents from `/specs/005-tool-system/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/java-contracts.md, quickstart.md

**Tests**: harness 承载验收（spec SC-006）——测试任务先于或伴随对应实现任务，课件关键回归断言逐条保真、方法名英文 + `@DisplayName` 保留课件原文。

**Organization**: 按 User Story 分组（spec：US1 统一抽象与注册表 P1、US2 内置工具动手且踩不出边界 P1、US3 三档接入 P1、US4 通知工具接线 P2）。

**范围说明（主公裁决）**：
- 课件第六部分的五个扩展工具（`edit_file`/`grep`/`glob`/`ask_user`/`web_search`）**本节不实现**——课件归其为"扩展交付"，T035 登记为后续补充，**不勾选**。
- 记忆工具（`save_memory`/`recall_memory`）实现归第22节（`fourfeetcat-memory` 本节时点为空模块），T035 登记为跨节，**不勾选**。
- 沙箱**规则本体**（`WhitelistSandbox` 与四个白名单配置键）归沙箱节；本节只交付前向接口五件与一档**不做校验的临时装配**（T009），其替换时点三处标注。
- 因此本节**不改** README / `docs/TechnicalSolution.md` / `CLAUDE.md` 里"内置九个"的表述——它们仍然正确。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- 含确切文件路径；路径以 `fourfeetcat-<module>/src/...` 表达

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖与基线就位

- [x] T001 H3 硬门禁：`mvn dependency:get -Dartifact=io.modelcontextprotocol.sdk:mcp:0.17.0` 核实可下载，再用 `javap -classpath <mcp-core-0.17.0.jar>` 复核 `McpClient.sync` / `McpSyncClient.listTools()/callTool(McpSchema$CallToolRequest)` / `StdioClientTransport(ServerParameters, McpJsonMapper)` / `ServerParameters.builder(String).args(List).env(Map)` / `McpSchema$Tool.name()/description()/inputSchema()` / `McpSchema$CallToolResult.content()/isError()` / `JacksonMcpJsonMapper(ObjectMapper)` 确实存在（research.md D5 已实测一遍，落地前再核一遍）；核不到立即停下软报，不得换依赖自行发挥
- [x] T002 基线核对：`mvn clean test` 确认第16/17/18/19节全部测试绿（作为本节起点的回归基线；开工前实测：9 模块 BUILD SUCCESS，36s）
- [x] T003 [P] `pom.xml`：`dependencyManagement` 加 `io.modelcontextprotocol.sdk:mcp`（版本用新属性 `mcp-sdk.version` = `0.17.0` 钉死；该 artifact 不在 `spring-ai-bom` 内，必须显式管）
- [x] T004 [P] 两条 BOM 托管依赖声明：`fourfeetcat-core/pom.xml` 加 `com.fasterxml.jackson.core:jackson-databind`（`CatTool.execute(JsonNode)` 需要，此前靠传递、现显式声明）；`fourfeetcat-tool/pom.xml` 加 `io.modelcontextprotocol.sdk:mcp`（不写 version）与 `org.yaml:snakeyaml`（与 `fourfeetcat-cli` 同款声明）

**Checkpoint**: 依赖解析通过，`mvn -o dependency:tree -pl fourfeetcat-tool -am` 能看到 mcp 与 snakeyaml

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 统一抽象、结果值对象、渠道取数端口、沙箱前向接口——**所有** user story 都建在这上面

**⚠️ CRITICAL**: 本阶段完成前，任何工具都无处安放

> **顺序更正（实施期发现）**：T029（`AnnotatedToolAdapter` + `ToolRegistry.registerAnnotated`）与 T025（它的测试）**实际在本阶段末尾落地**，早于 US2——课件明写内置 Tool 与"方式三"是同一套 `@Tool` 注解管道（宪法原则二把 schema 生成交给 Spring AI），因此 US2 的每个内置工具都依赖适配器，T022 的装配没有它就无从注册。任务 ID 保持稳定以免制造大面积重排，执行顺序以本注为准。

- [x] T005 [P] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/tool/CatTool.java`：接口四方法 `getName()` / `getDescription()` / `getInputSchema()`（返回 JSON Schema 文本 String，与既有 `ToolDescriptor.inputSchema` 同型，research D1）/ `execute(JsonNode input)`；类注释写明"来源对调用方不可见"与"涉外工具 execute 第一行过沙箱校验"
- [x] T006 [P] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/tool/ToolResult.java`：`record ToolResult(boolean success, String content, String errorMessage, boolean retryable)` + 工厂 `success(String)` / `failure(String, boolean)`；四字段与 `ToolExecutionResult` 逐字对齐；**同时删除** `tool/ToolExecutionResult.java`
- [x] T007 core 侧 6 处引用随改名（仅类型名，逻辑零改动）：`tool/ToolTable.java`（并更新 javadoc：实现方为 `fourfeetcat-tool` 的 `ToolRegistry`）、`react/ToolExecutor.java`、`react/ReActLoop.java`、`fourfeetcat-boot/.../AgentRuntimeConfiguration.java`、`core/src/test/.../react/ReActLoopTest.java`、`core/src/test/.../react/ToolExecutorTest.java`
- [x] T008 [P] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/notify/NotifyChannelSource.java`：`List<Map<String, String>> all()`；javadoc 写明键为 `name/type/url/description`、空表返回空列表、解析与投影归消费方（research D8）
- [x] T009 [P] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/sandbox/` 五件：`Sandbox`（唯一方法 `void enforce(SandboxAction)`）、`SandboxAction`（record：`ActionType type, String target`）、`ActionType`（四值 `FILE_READ`/`FILE_WRITE`/`SHELL_COMMAND`/`HTTP_REQUEST`）、`SandboxViolationException`（继承 `RuntimeException`）、`PermissiveSandbox`（**⚠️ 临时装配：enforce 直接返回、不做任何校验**——类注释必须原文写明"这是有意为之的临时状态、只为让沙箱节之前已注册的工具能跑通、由沙箱节替换"，且签名里不出现'白名单/容器/VM'等某一档实现特有的词）
- [x] T010 core 回归：`mvn test -pl fourfeetcat-core -am` 全绿（T007 改名的验证点）

**Checkpoint**: 统一抽象与四件前向契约就位，core 既有测试未被打断

---

## Phase 3: User Story 1 - 三种来源的工具收敛成一个样子 (Priority: P1) 🎯 MVP

**Goal**: `ToolRegistry` 汇总三种来源的工具；按 Agent 声明的工具名清单过滤出精确子集；`tool list` 能列出当前注册的工具

**Independent Test**: `mvn test -pl fourfeetcat-tool -am -Dtest='ToolRegistryTest,CatToolContractTest'`

### Tests（harness 先行）

- [x] T011 [US1] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/registry/ToolRegistryTest.java`：① 三种来源的替身工具都以 `CatTool` 身份注册、按名取描述与执行结果都对；② **按声明清单过滤后子集恰好相等**（用集合相等断言，覆盖"多一个"与"少一个"两侧）；③ 名字查不到 → 抛错不静默少给；④ **重名注册 → 明确拒绝**（spec Edge Cases）
- [x] T012 [US1] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/registry/CatToolContractTest.java`：课件 harness 首行逐条落地——`@ParameterizedTest @MethodSource("allRegisteredTools")` 遍历注册表，断言每个工具的 `getName()` / `getDescription()` / `getInputSchema()` 都非空（`@DisplayName` 保留课件原文"每个工具的契约三件套都不能缺"）

### Implementation for User Story 1

- [x] T013 [US1] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/registry/ToolRegistry.java`：`implements ToolTable`；`register(CatTool)`（重名抛 `IllegalStateException`）、`contains(String)`、`all()`、`descriptors(List<String>)`（未知名抛错）、`execute(String, String)`（查表 → 用 Jackson 解析 inputJson 成 `JsonNode` → `CatTool.execute`）；类注释写明"执行权唯一：本类是 `ToolExecutor` 唯一的下游"。**注解注册方法 `registerAnnotated` 不在此建**——它随 `AnnotatedToolAdapter` 一起在 T029 落地（不造没有实现的方法）
- [x] T014 [US1] boot 装配（`fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/AgentRuntimeConfiguration.java`）：**删除**第18节留下的 `UnregisteredToolTable` 内部类；加 `@Bean Sandbox sandbox()`（`PermissiveSandbox`）与 `@Bean ToolRegistry toolRegistry(...)`（本阶段只建空表）；`toolExecutor(...)` 改注入 `ToolRegistry`
- [x] T015 [US1] `fourfeetcat-cli`：`ToolListCommand` 换数据源——加 `@ParentCommand FourFeetCatCli root`，经 `root.engine(WebApplicationType.NONE)` 取 `ToolRegistry` Bean 打印工具名与描述；**命令名/分组/描述一字不改**（第18节预声明的改造点）；`fourfeetcat-cli/pom.xml` 加 `fourfeetcat-tool` 依赖（无环：cli → tool → core）

**Checkpoint**: US1 可独立验收；底座能列工具（本阶段清单为空是预期）

---

## Phase 4: User Story 2 - 内置工具真的能动手，且踩不出边界 (Priority: P1)

**Goal**: `read_file` / `write_file` / `list_dir` / `shell` / `http_get` / `http_post` 六个内置工具落地，每个 execute 的第一行都过沙箱校验位

**Independent Test**: `mvn test -pl fourfeetcat-tool -am -Dtest='FileToolsTest,ShellToolsTest,HttpToolsTest'`

### Tests（harness 先行）

- [x] T016 [P] [US2] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/builtin/FileToolsTest.java`：白名单内目录可读/可写/可列；**沙箱替身抛异常时，目标文件一个字节都没被改动**（越界会被拦 + 副作用不发生的双重断言）；并断言 `sandbox.enforce` **先于**任何文件 IO 被调用（`InOrder` 或替身计数）
- [x] T017 [P] [US2] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/builtin/ShellToolsTest.java`：白名单内命令正常返回；越界命令被拦；超时命令限时终止并返回失败；超长输出被截断并注明；断言 `sandbox.enforce` **先于**进程启动被调用
- [x] T018 [P] [US2] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/builtin/HttpToolsTest.java`：假接收端（`com.sun.net.httpserver.HttpServer`，类内私有静态嵌套类，不新增第三方依赖）验证 GET/POST 取回响应；沙箱替身抛异常时**请求根本没发出去**（顺序断言）

### Implementation for User Story 2

- [x] T019 [P] [US2] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/builtin/FileTools.java`：`read_file` / `write_file` / `list_dir` 三个 `@Tool` 方法；每个方法体第一行 `sandbox.enforce(new SandboxAction(FILE_READ|FILE_WRITE, path))`
- [x] T020 [P] [US2] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/builtin/ShellTools.java`：`shell` 工具；构造签名按契约 §3 取三个参数 `ShellTools(Sandbox sandbox, Duration timeout, int maxOutputChars)`（默认值 30 秒 / 8000 字符在装配处给，research D10，两个旋钮可调而不用改类）；方法体第一行 `sandbox.enforce(SHELL_COMMAND, command)`；`ProcessBuilder` argv 直传（**不经 shell 解释**）、超时 `destroyForcibly()`、合并输出超上限截断并注明
- [x] T021 [P] [US2] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/builtin/HttpTools.java`：`http_get` / `http_post`；每个方法体第一行 `sandbox.enforce(HTTP_REQUEST, url)`；`RestClient` 构造注入
- [x] T022 [US2] boot 装配：`toolRegistry` 里注册三个内置工具实例（Sandbox + `RestClient` 注入；`ShellTools` 在此给 30 秒 / 8000 字符两个默认旋钮）

**Checkpoint**: 六个内置工具可用，`fourfeetcat tool list` 能看到它们

---

## Phase 5: User Story 3 - 业务方按三档接入自己的工具 (Priority: P1)

**Goal**: 外部 MCP server 启动时全量连接并包装注册（失联隔离）；`@Tool` 注解的 Java 方法自动扫描注册

**Independent Test**: `mvn test -pl fourfeetcat-tool -am -Dtest='McpClientServiceTest,McpToolAdapterTest,AnnotatedToolAdapterTest'`

### Tests（harness 先行）

- [x] T023 [P] [US3] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/mcp/McpToolAdapterTest.java`：mock 客户端——`listTools()` 返回的工具被包装成 `CatTool` 且 name/description/inputSchema 一一映射；`execute` **参数原样转发**、结果包成 `ToolResult`；`isError()=true` 时返回 `retryable=true` 的失败
- [x] T024 [P] [US3] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/mcp/McpClientServiceTest.java`：**某个 MCP server 失联不能拖垮启动和其他工具**（课件原文断言）——`when(badClient.listTools()).thenThrow(...)` → `connectAll` **不抛异常**、`registry.contains("good_mcp_tool")` 为真、`registry.contains("bad_mcp_tool")` 为假；`transport` 取非 stdio 值 → 只 WARN 跳过（research D6）
- [x] T025 [US3] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/registry/AnnotatedToolAdapterTest.java`：**多出项**（课件 harness 未点名，经主公裁决补；为三档中的"方式三"加一个契约守点）——带 `@Tool` 注解的样例 Bean 经适配后 name/description/inputSchema 均非空，且 `execute` 走的是**我们的** `CatTool.execute` 路径

### Implementation for User Story 3

- [x] T026 [P] [US3] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/mcp/McpServerConfig.java`（record：`name/transport/command/env`）与 `mcp/McpServerConfigLoader.java`（SnakeYAML 读 `<workspaceRoot>/mcp_servers.yaml` 的 `servers:` 段；文件不存在或为空 → 空列表）
- [x] T027 [P] [US3] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/mcp/McpToolAdapter.java`：`implements CatTool`；getName/getDescription/getInputSchema 直接映射 `McpSchema$Tool`（inputSchema 用 `JacksonMcpJsonMapper.writeValueAsString` 转成文本）；execute 把 JSON 参数转 `Map` 交给 `CallToolRequest`，把 `TextContent` 拼成结果文本
- [x] T028 [US3] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/mcp/McpClientFactory.java` + `mcp/McpClientService.java`：`McpClientFactory` 是"造一个客户端"的测试缝（契约 §5 定名：`McpSyncClient connect(McpServerConfig config)`）；`connectAll()` 逐个 server **独立 try/catch**（失败只 WARN 并跳过，绝不阻断启动，research D7）；`command` 按空白拆成可执行文件 + 参数数组；生产路径用真工厂（`McpClient.sync(new StdioClientTransport(...))`），单测注入替身、不真起进程
- [x] T029 [US3] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/registry/AnnotatedToolAdapter.java`：用 `MethodToolCallbackProvider.builder().toolObjects(bean).build().getToolCallbacks()` 拿 schema（宪法原则二：schema 生成是 Spring AI 的活），把每个 `ToolCallback` 包成 `CatTool`；类注释写明"**禁用自动执行**：`call()` 只可能从 `CatTool.execute()` 进来"；同时在 `ToolRegistry` 补 `registerAnnotated(Object...)` 转发到它
- [x] T030 [US3] boot 装配：`McpServerConfigLoader`（传工作区根）+ `McpClientService` → `toolRegistry` Bean 里调 `connectAll()`

**Checkpoint**: 三档接入全部打通；MCP 失联可复现地被隔离

---

## Phase 6: User Story 4 - 推送成为可被调用的内置工具 (Priority: P2)

**Goal**: `notify` 工具接线落地（第19节登记的跨节任务在此完成）；渠道按名解析落 core 端口 + storage 实现

**Independent Test**: `mvn test -pl fourfeetcat-tool -am -Dtest=NotifyToolsTest`

### Tests（harness 先行）

- [x] T031 [US4] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/notify/NotifyToolsTest.java`：第19节 harness 第二批逐条落地——① 渠道未配置 → **明确报错**（不是静默成功）；② 渠道名缺省 → 取第一个；③ **`enforce` 先于 `send` 被调用**（`InOrder` 钉死，课件原文断言）；④ 渠道名给了但查不到 → 报错并指出名字。sandbox 与 adapter 都用替身；另用一档替身工具走通"注册 → 按声明清单过滤 → 执行 → 审计"四步（spec FR-009，证明记忆工具将来就位时零改动）

### Implementation for User Story 4

- [x] T032 [US4] `fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/JpaNotifyChannelSource.java`：`implements NotifyChannelSource`（`@Component`），`findAll()` 投影成 `Map`（键 `name/type/url/description`）；复用第19节的 `NotifyChannel` 与 `NotifyChannelRepository`，**零迁移脚本、零表结构变更**
- [x] T033 [US4] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/notify/NotifyTools.java`：`notify(content, channel)` 工具——解析（空表报错 / 名缺省取第一条 / 查不到报错）→ 投影成 `NotifyTarget(type, {url})` → **`sandbox.enforce(HTTP_REQUEST, url)` 先** → `adapter.send(target, content)` 后；`NotifyTarget` 与 `NotifyChannelAdapter` 原样不动
- [x] T034 [US4] boot 装配：`@Bean NotifyChannelSource notifyChannelSource(NotifyChannelRepository)` + `toolRegistry` 里注册 `NotifyTools`

**Checkpoint**: 第19节的 `NotifyTools` 跨节任务完成、可勾选

---

## Phase 7: 跨节登记与收尾

**Purpose**: 登记不做的事、核对交付物、跑门禁、出报告

- [ ] T035 **跨节登记（本节不实现、验收时不勾选）**：① 记忆工具 `save_memory` / `recall_memory`（`fourfeetcat-memory`，实现归第22节）——本节只保证注册管道能容纳它（T031 的替身工具已走通四步）；② 课件第六部分五个扩展工具 `edit_file`/`grep`/`glob`/`ask_user`/`web_search`（主公裁决：后续补充）——其中 `edit_file`/`grep`/`glob` 落 `FileTools`、`ask_user` 落 `InteractionTools` + `UserInteraction` 抽象、`web_search` 落 `WebSearchTools` + `SearchProvider` 抽象
- [x] T036 `mvn clean verify` 全绿（Spotless/GJF + PMD7 + Checkstyle(google_checks) + SpotBugs+FindSecBugs 四道门禁一起过，SpotBugs findings 应为 0）
- [x] T037 前序节回归：`fourfeetcat-core` / `provider` / `storage` / `cli` / `channel-cli` / `boot` 既有全部测试绿（跨节契约证据；特别确认改名后的 `ToolExecutorTest` / `ReActLoopTest` 与 boot 上下文测试未被打断）
- [x] T038 H4 六条全局不变量逐条自查：①涉外 IO 首行确过 `Sandbox.enforce`（FileTools/ShellTools/HttpTools/NotifyTools 四处点名核对）②LLM 调用与工具执行成败都落表（本节零新增审计代码，靠 `ToolRegistry implements ToolTable` 自动生效）③grep 无明文 key（MCP 配置的 `env` 只允许 `${ENV}` 占位）④`session_id` 只在 `SessionManager` 内拼接（本节未触碰）⑤无 Reactor/`CompletableFuture`/自建线程池（`reactor-core` 只在第三方库内部，我们的代码只用 `McpSyncClient`）⑥无 Spring AI 自动工具执行路径（grep 确认没有把 `ToolCallback` 挂到 `ChatClient` 的地方）
- [x] T039 交付物存在性核对：`CatTool`/`ToolResult`/`ToolRegistry`/`AnnotatedToolAdapter`/`FileTools`(3)/`ShellTools`/`HttpTools`(2)/`McpClientService`/`McpToolAdapter`/`NotifyTools`/沙箱五件 + `JpaNotifyChannelSource` + **9 个测试类** + `.fourfeetcat/mcp_servers.yaml`（仓库与该文件均已有空模板，**本节不改它**，只核对可读即含）；**多出项声明（均经主公裁决）**：`AnnotatedToolAdapterTest`（T025）、`McpServerConfig`/`McpServerConfigLoader`/`McpClientFactory`（T026/T028）、`NotifyChannelSource` 端口 + `JpaNotifyChannelSource`（T008/T032）、**`PlainTextResultConverter`**（T019~T021 的每个 `@Tool` 上挂——实施中发现 Spring AI 默认把工具返回值 JSON 化，多一层引号、换行转义，经主公裁决自带一档"原样返回"转换器；见 research.md D14）
- [x] T040 节级验收报告 + 变更总结（改动点 / 重点 review 清单 / 如何验证），并列出课件"五、做完怎么验"的**剩余人工项**（真模型、真 MCP server、`@Tool` 示例工具真跑一次、沙箱临时装配替换时点的人工确认）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**：无依赖，先做
- **Phase 2 Foundational**：依赖 Setup；**阻塞全部 user story**
- **Phase 3 US1**：依赖 Phase 2
- **Phase 4 US2**：依赖 US1（工具要注册进注册表）；三个测试类互相 [P]
- **Phase 5 US3**：依赖 US1（`registerAnnotated` 随 T029 落地）
- **Phase 6 US4**：依赖 US1 + US2（校验位语义与 US2 的工具一致）
- **Phase 7 收尾**：依赖全部

### Within Each User Story

- 测试与实现一起落地，跑该模块测试，红了当场修，不攒到最后（任务级 DoD）
- 值对象 → 端口/实现 → 装配

### Parallel Opportunities

- T003 / T004 两条 pom 改动可并行
- T005 / T006 / T008 / T009 四个新文件互不相干，可并行
- T016 / T017 / T018 三个测试类可并行；T019 / T020 / T021 三个实现类可并行
- T023 / T024 / T025 三个测试类可并行；T026 / T027 两个实现类可并行

---

## Implementation Strategy

### MVP First

Phase 1 + 2 + 3（Setup + Foundational + US1）= 统一抽象与注册表跑通，`fourfeetcat tool list` 从占位变成真数据源。

### Incremental Delivery

1. Phase 1~2 → 抽象与前向契约就位
2. Phase 3 → 注册表可列（MVP）
3. Phase 4 → Agent 真的能读写文件、跑命令、发请求（Demo 一"真的去查天气"的动作在此落地）
4. Phase 5 → 业务方能接自己的工具
5. Phase 6 → 第19节欠的债还清
6. Phase 7 → 门禁与报告

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖
- 每个 user story 必须能独立验收；跨 story 的装配在各自 Phase 末尾
- 提交由主公决定，本节全程不自动 commit / push / 跑 package.sh
