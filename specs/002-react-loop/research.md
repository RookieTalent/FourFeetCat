# Phase 0 Research: ReAct 循环（第17节）

**依据**：`spec.md`（含 2026-09-16 三条澄清）+ `docs/class/第17节：ReAct 原理解析、实现与代码讲解.md` + `docs/TechnicalSolution.md` §4/§8.3/§9.2 + 宪法 v2.0.0。

## D1 循环调用模型的接缝：core 端口 `LlmCaller`

- **决策**：core 立端口 `LlmCaller`（签名与第16节 `ProviderService.chat(sessionId, profile, tools, prompt)` 逐字同形），装配处传 `providerService::chat` 方法引用。
- **理由**：模块落位要求 `ReActLoop` 在 `fourfeetcat-core`、`ProviderService` 在 `fourfeetcat-provider`，模块依赖方向只允许 provider → core，故 core 侧必须以端口倒置（与第16节 `LlmCallRecorder` 同一手法）。
- **代价与边界**：**不改**第16节已交付的 `ProviderService`（签名恰好同形，方法引用即可）——它不 `implements` 该端口，因此本节对前序节零改动。
- **备选**：让 `ReActLoop` 落在 provider 模块（违反技术方案 §10 的落位，且 18/26 节的 CLI/Web 也要用循环，放 provider 会让渠道模块被迫依赖 provider 细节）——否。

## D2 工具执行接缝：core 端口 `ToolTable` + `ToolExecutionResult`

- **决策**：core 立 `ToolTable`（`descriptors(List<String>)` + `execute(String name, String inputJson)`）与结果 record `ToolExecutionResult(success, content, errorMessage, retryable)`；命名取课件原词"工具表"。
- **理由**：主公裁决——本节自造最小执行端口，第20节立统一工具抽象（`OryxTool` + `ToolResult` + `ToolRegistry`）时合并/替换该端口。与第20节契约逐字对齐（四个字段一致），替换时只需改名与挪位。
- **输入形态取 JSON 字符串而非 JsonNode**：模型给出的调用参数本就是 JSON 字符串（`AssistantMessage.ToolCall.arguments()`），审计列 `input_json` 要的也是字符串——不引 Jackson，不新增依赖。第20节接入 `JsonNode` 时在适配器内解析即可。

## D3 审计接缝：core 端口 `ToolInvocationRecorder`

- **决策**：core 立 `@FunctionalInterface ToolInvocationRecorder`，签名 `record(sessionId, toolName, inputJson, resultJson, success, errorMessage, durationMs)`；`fourfeetcat-storage` 出 `ToolInvocationRecorderImpl`。
- **理由**：宪法原则五（审计 Day One）+ 依赖倒置——`ToolExecutor` 在 core，不得直连 storage；第16节 `LlmCallRecorder` 已验证同款手法，两者口径一致。
- **`result_json` 取值**：成功时落工具返回内容（工具约定返回 JSON 文本，MCP 工具天然如此），失败时为 `null`、原因落 `error_message`。

## D4 会话状态：core `Session`（内存）+ `SessionManager` 接口

- **决策**（主公裁决）：`Session` 持 `id/profileName/channel/userId` + `List<Message>`（Spring AI `Message`：`UserMessage`/`AssistantMessage`/`ToolResponseMessage`）；`SessionManager` 接口本节只有 `save(Session)`。第18节补 `getOrCreate(channel,user,profileName)`/`get(sessionId)` 与 JPA 实现，`session_id` 拼接公式**只在第18节的 SessionManager 内**落地（H4 不变量④）。
- **消息表示**（主公裁决）：复用 Spring AI `Message`——组装 Prompt 近乎透传，工具回填天然带 `toolCallId`；代价是第18节 `messages_json` 需自写消息↔JSON 映射，届时由该节的存储实现承担。

## D5 长期记忆接缝：中性供给函数

- **决策**（Clarifications Q1）：`PromptBuilder` 构造期收 `Function<Profile, String>`，缺省实现返回 `null`/空白 = 未启用（该部分跳过）；第22节传 `memoryService::recallFor` 之类方法引用，`PromptBuilder` 签名不变。
- **不作**：不新造"记忆供给"接口（第22节的 `MemoryService` 才是它的正式形态）。

## D6 建表脚本：本节起双轨

- **决策**（Clarifications Q3）：`V17__tool_invocations.sql` 同时落 `db/migration/sqlite/` 与 `db/migration/postgresql/`（同版本号，方言各自正确：SQLite 布尔落 INTEGER(0/1)、时间戳落 TEXT ISO-8601；PostgreSQL 对应 BOOLEAN/BIGINT/TIMESTAMP）。
- **欠账口径**：第16节已交付的 `V16__llm_calls.sql` 只有 sqlite 一份，本节**不动**（宪法"只增不改"，补齐与否由主公另行决定）。

## D7 沙箱检查位：留调用点 + 24 节接线注记

- **决策**：`ToolExecutor.execute` 内保留唯一的执行前检查位（注释注明"24 节 Sandbox 接线"），本节不引 `Sandbox` 类型、不做白名单实现。
- **理由**：H4 不变量①的口径（Sandbox 未就位的节留调用位）；宪法原则六的实现归第24节，本节提前造接口会与 24 节契约打架。

## D8 历史截断算法

- **决策**：从消息列表末尾向前数，找到第 `N+1` 条用户消息，切点定在**它之前**，丢弃更早部分。
- **理由**：切点永远落在用户消息之前，助手工具调用与其回填消息不会被切断（切断会让模型端收到非法消息序列）；同时满足"只留最近 N 轮"（N = `max_history_turns`，默认 20）。
- **备选**：按消息条数截断（会切断工具调用对）——否。

## D9 运行参数默认值与读取

- **决策**：`max_iterations` 默认 10、`max_history_turns` 默认 20，读自 `Profile.settings()`；缺失、非数字、非正数一律回落到默认值。解析集中在一个包内私有小类（`ProfileSettings`），避免两处各写一遍默认值。
- **依据**：课件"约定"节（最大轮数默认 10、历史截断默认 20 轮）。

## D10 工具失败的语义

- **决策**：`ToolTable.execute` 抛出的运行时异常由 `ToolExecutor` 捕获 → 落审计 `success=false` + 原因 → 返回失败结果（原因进对话）→ 循环继续。
- **依据**：技术方案 §4.2"失败时按可重试策略返回错误信息"；第20节课件 `McpToolAdapter` 同样把失败包成 `ToolResult.failure(..., retryable=true)` 而非抛出。**"异常不吞"的含义是原因进审计与对话，不是上抛**——上抛会让一次工具失败炸掉整个循环，与 §4.1 的"回填后继续"矛盾。

## D11 第三方 API 核实（H3 门禁，本地依赖实测）

`javap` 实测本地 `spring-ai-model-1.1.2.jar`（BOM 锁定版本）：

| 需要的能力 | 实测存在的 API |
|---|---|
| 响应是否含工具调用 | `ChatResponse.hasToolCalls()`、`ChatResponse.getResult().getOutput()` → `AssistantMessage.getToolCalls()` / `hasToolCalls()` |
| 响应文本 | `AssistantMessage.getText()`（继承自 `AbstractMessage`） |
| 工具调用请求 | `AssistantMessage.ToolCall(id, type, name, arguments)`（record，`arguments()` 为 JSON 字符串） |
| 工具结果回填 | `ToolResponseMessage.builder().responses(List<ToolResponse>)`，`ToolResponse(id, name, responseData)` |
| 组装请求 | `Prompt(List<Message>)` / `Prompt(List<Message>, ChatOptions)`（第16节 `ProviderService` 消费 `getInstructions()`） |
| 系统消息 | `SystemMessage.builder().text(String).build()` |

结论：全部存在，无需新增依赖。`fourfeetcat-core` 已直依 `spring-ai-model`（第16节声明），本节沿用。

## D12 装配（Spring Bean）

- **决策**：本节**不做** Spring 装配——`ReActLoop`/`AgentService`/`ToolExecutor`/`PromptBuilder`/`ContextLoader` 均为普通 POJO，由单测直接构造；`ToolInvocationRecorderImpl` 按第16节 `LlmCallRecorderImpl` 同款加 `@Component`（其依赖的 JPA 仓储已被 boot 的 `@EnableJpaRepositories` 覆盖）。真正的装配在 18 节 CLI 入口接入时统一做（那时才有 `SessionManager` 实现与 `ToolTable` 的真实来源）。
- **理由**：现在装配会要求不存在的实现类（SessionManager/ToolTable 的实现分别属 18/20 节），装不上；不装不阻塞 `mvn clean verify`。
