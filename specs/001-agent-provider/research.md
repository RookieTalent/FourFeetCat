# Research: Agent Provider（第16节）

勘察期已完成的核实与决策（方法：~/.m2 仓库实查 + BOM pom 全文 + jar javap，非网络猜测）。

## D1：Spring AI 工具自动执行的真实位置（本节最重要发现）

- **Decision**：在 `ProviderService` 构建 options 的唯一一处显式 `ToolCallingChatOptions.builder().internalToolExecutionEnabled(false)`，并以 captor 断言回归测试钉死。
- **Rationale**：javap（spring-ai 1.1.8，1.1.x 线稳定）证实：自动工具执行循环内嵌于 ChatModel 实现层（如 `OpenAiChatModel` 持有 `ToolCallingManager`），常量 `DEFAULT_TOOL_EXECUTION_ENABLED = true`。"绕开 ChatClient 直调 ChatModel 就不会自动执行"是错误前提——不显式关闭，宪法原则二静默失效，且 mock ChatModel 的单测感知不到。
- **Alternatives**：只在文档里约定"不用 ChatClient"（否决：不可测试，坑没有钉子）；走 `OpenAiChatOptions.setTools` 私有格式（否决：不可移植）。

## D2：ChatModel 实现依赖选型

- **Decision**：`fourfeetcat-provider` 引 `spring-ai-openai`（版本走 Spring AI BOM 1.1.2），各 provider 以 OpenAI 兼容端点接入（base-url + api-key）；`spring-ai-alibaba-bom` 照 import 不消费。
- **Rationale**：`spring-ai-alibaba-bom 1.1.2.3` 全文核实只托管 agentscope/graph/a2a-nacos 等 8 个 artifact，**不含** dashscope starter；DeepSeek/Kimi/Qwen 均有 OpenAI 兼容 API，满足"Spring AI 只做协议转换"的宪法要求。
- **Alternatives**：显式指定版本拉 alibaba dashscope artifact（否决：破坏 BOM 锁定语义，版本不受管）。
- **待 implement 首个周期终验**：`mvn dependency:tree` 确认 `spring-ai-model`/`spring-ai-openai` 在锁定 BOM 内可解析（H3 硬门禁，核不到即软停报告）。

## D3：Spring AI API 事实清单（javap 核实，1.1.8）

- `ChatModel.call(Prompt) → ChatResponse`（`org.springframework.ai.chat.model`，artifact `spring-ai-model`）。
- `ToolCallingChatOptions.builder().model(String).temperature(Double).toolCallbacks(List).internalToolExecutionEnabled(Boolean).build()`；静态助手 `ToolCallingChatOptions.isInternalToolExecutionEnabled(ChatOptions)` 免强转。
- `ChatResponse.getMetadata().getUsage().getPromptTokens()/getCompletionTokens()/getTotalTokens()`。
- 关闭自动执行后，`ChatResponse.getResult().getOutput().getToolCalls()` 携带未执行的工具调用请求（id/type/name/arguments）——正是第17节 ReAct 循环要消费的。
- `ToolCallback.getToolDefinition()`；`ToolDefinition.name()/description()/inputSchema()`（inputSchema 为 JSON 字符串）。
- 注意：1.1.2 jar 本地未下载，以上签名在首个编译周期即最终确认。

## D4：SQLite 建表与 Flyway 双轨取舍

- **Decision**：建表脚本落 `db/migration/sqlite/V16__llm_calls.sql`（从 `docs/class/schema.sql` llm_calls 段逐字摘取）；postgresql 轨本节不写（仓库尚无该目录，课程 16~31 节单 SQLite 口径）。
- **Rationale**：课程参考建表脚本（schema.sql 头注）明确 16~31 节按节摘表、测试里执行脚本建表、不依赖 ddl-auto=update。
- **Alternatives**：双轨各写一份（否决：postgres 轨目录不存在，属清单外新建概念，超出本节交付物）。

## D5：审计依赖方向（依赖倒置）

- **Decision**：core 定义端口 `LlmCallRecorder`，storage 出 `LlmCallRecorderImpl`（内部用 LlmCallRepository），boot 装配。
- **Rationale**：Spec-Kit 执行指导明示"ProviderService 引用 storage 细节"是落位错误；宪法技术约束要求跨模块契约放 core。
- **Alternatives**：provider 直接依赖 storage 的 Repository（否决：违反落位原则与依赖方向）。

## D6：课件 OryxTool 前向引用的消解

- **Decision**：core 定义 `record ToolDescriptor(String name, String description, String inputSchema)` 作为工具 schema 最小载体；第20节交付工具执行接口时提供到 ToolDescriptor 的导出。
- **Rationale**：课件适配器翻译"我们的 OryxTool"，但工具抽象是第20节交付物，本节不能跳节自造完整 Tool 接口；最小 record 有真实消费方（ProviderService 入参、适配器入参、第20节衔接点）。
- **Alternatives**：Test-only 接口（否决：课程交付物须有真实存在意义）。

## D7：命名与字段口径（主公裁决）

- **Decision**：课件 OryxOS 命名统一映射 fourfeetcat（模块/包名/配置键 `fourfeetcat.providers`/工作区 `.fourfeetcat/profiles/`）；Profile 按课件建全字段（含 notify_channels）。
- **Rationale**：整仓一致性优先于课件字面量保真；两处已记入 spec Assumptions。

## D8：SQLite 测试库形态

- **Decision**：`jdbc:sqlite:<@TempDir>/test.db` 文件库 + `ScriptUtils.executeSqlScript` 执行迁移脚本建表 + `hibernate.hbm2ddl.auto=none`。
- **Rationale**：`:memory:` 与 HikariCP 连接池不兼容（每个连接各一个独立库）；课件 L236 明确要求测试走手工脚本，避免"测试绿、生产跑真脚本列名对不上"。
