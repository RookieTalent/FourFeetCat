# Implementation Plan: Agent Provider（第16节）

**Branch**: `016-lesson16-agent-provider` | **Date**: 2026-09-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-agent-provider/spec.md`

## Summary

实现 Agent 与大模型之间的统一前台：按 Profile 声明的 provider 名经显式 `Map<String, ChatModel>` 路由完成一次 LLM 调用，工具只翻译 schema 且显式关闭 Spring AI 自动执行，成败都落 `llm_calls` 审计。本节一并交付 Profile 的 YAML 解析加载与内存索引。全程同步阻塞，无 fallback/熔断。

## Technical Context

**Language/Version**: Java 21（Maven 多模块，spring-boot-starter-parent 3.5.16）

**Primary Dependencies**: Spring AI 1.1.2（BOM 已锁）——`spring-ai-model`（ChatModel/Prompt/ToolDefinition）；ChatModel 实现用 `spring-ai-openai`（OpenAI 兼容端点覆盖 DeepSeek/Kimi/Qwen；spring-ai-alibaba-bom 1.1.2.3 不含 dashscope starter，已核实，见 research.md）；SnakeYAML（Profile 解析）；Spring Data JPA + sqlite-jdbc（审计落库）。依赖变更：`fourfeetcat-core` +snakeyaml；`fourfeetcat-provider` +spring-ai-model、spring-ai-openai（版本走 BOM，不写死）；`fourfeetcat-storage` 已备。

**Storage**: SQLite，手工建表脚本 `fourfeetcat-storage/src/main/resources/db/migration/sqlite/V16__llm_calls.sql`（从 `docs/class/schema.sql` 摘 llm_calls 段逐字保真），`ddl-auto: none`，测试用 `jdbc:sqlite:<@TempDir>/test.db` 文件库 + ScriptUtils 执行脚本（不用 `:memory:`）。

**Testing**: JUnit 5 + Mockito（仓库既有门禁：Spotless google-java-format / PMD 7 / Checkstyle / SpotBugs+FindSecBugs）。五个测试类：ProfileLoaderTest、ProviderServiceTest、ToolSchemaAdapterTest、LlmCallRepositoryTest（单测默认跑）、ProviderSmokeIT（`@Tag("integration")` CI 跳过，缺 key `assumeTrue` 跳过）。

**Target Platform**: JVM 服务端（后续 CLI/HTTP 渠道消费）

**Project Type**: library（多模块底座）

**Performance Goals**: 无专项指标（单次调用同步阻塞，Virtual Thread 处理并发）

**Constraints**: 宪法原则七（同步，无 Reactor/CompletableFuture/自建线程池）；语法禁区——避开 P3C/ASM 解析不了的 Java 18+ 形态（增强 switch 的 `default ->` 等）；测试方法名英文 + `@DisplayName` 保留课件原文。

**Scale/Scope**: 第16节交付物清单，不加不减。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|---|---|---|
| 一：自实现 ReAct | 本节不含循环；工具调用请求原样交回上层 | ✅ 通过 |
| 二：Spring AI 只用两件事 | 只用协议转换 + schema 生成；**`internalToolExecutionEnabled(false)` 在 ProviderService 构建 options 的唯一一处显式设置，配回归测试钉死**（勘察证实自动执行内嵌于 ChatModel 实现层且默认开启，"绕开 ChatClient 即安全"不成立） | ✅ 通过 |
| 三：Provider 显式映射 | `ProviderConfiguration` 按 `fourfeetcat.providers` 显式建 `Map<String, ChatModel>`，不扫描容器 Bean 类型 | ✅ 通过 |
| 四：一个目录 = 一个 Agent | 本节按课件交付 Profile YAML 加载（`.fourfeetcat/profiles/`），Agent 目录形态是后续节的改造点，不冲突 | ✅ 通过（口径已记录） |
| 五：审计 Day One | `llm_calls` 成败都写（含 success/error_message），LlmCallRepository 随实体交付 | ✅ 通过 |
| 七：同步执行 | 全链路同步阻塞 | ✅ 通过 |
| 技术约束：Flyway 双轨 | 本节建表脚本落 sqlite 轨（`db/migration/sqlite/`）；postgresql 轨按仓库现状暂无此目录，V16 仅 sqlite 一份（课程 16~31 节口径，见 research.md 决策 D4） | ✅ 通过（记录取舍） |

无违宪项，Complexity Tracking 不需要。

## Project Structure

### Documentation (this feature)

```text
specs/001-agent-provider/
├── plan.md              # 本文件
├── research.md          # Phase 0：依赖核实与关键决策
├── data-model.md        # Phase 1：llm_calls 表 + Profile 结构
├── quickstart.md        # Phase 1：验证指南
├── contracts/           # Phase 1：跨模块 Java 契约
└── tasks.md             # /speckit-tasks 产出（本命令不创建）
```

### Source Code (repository root)

```text
fourfeetcat-core/src/
├── main/java/org/fourfeetcat/core/
│   ├── ToolDescriptor.java            # 工具 schema 最小载体（record）
│   ├── LlmCallRecorder.java           # 审计端口（依赖倒置，storage 实现）
│   └── profile/
│       ├── Profile.java               # 全字段 record（含嵌套 ProviderConfig）
│       ├── ProfileLoader.java         # 扫 .fourfeetcat/profiles/*.yaml，${ENV} 解析
│       └── ProfileRegistry.java       # Map<String,Profile> 按名查找
└── test/java/org/fourfeetcat/core/profile/ProfileLoaderTest.java

fourfeetcat-provider/src/
├── main/java/org/fourfeetcat/provider/
│   ├── ProviderService.java           # chat(sessionId, profile, tools, prompt) → ChatResponse
│   ├── ProviderNotFoundException.java
│   ├── ToolSchemaAdapter.java         # ToolDescriptor → ToolCallback（只翻译）
│   └── ProviderConfiguration.java    # fourfeetcat.providers → Map<String,ChatModel>
└── test/java/org/fourfeetcat/provider/
    ├── ProviderServiceTest.java       # 课件三个钉坑测试
    ├── ToolSchemaAdapterTest.java
    └── ProviderSmokeIT.java           # @Tag("integration")

fourfeetcat-storage/src/
├── main/java/org/fourfeetcat/storage/
│   ├── LlmCall.java                   # JPA 实体
│   ├── LlmCallRepository.java         # JpaRepository
│   └── LlmCallRecorderImpl.java       # 端口实现（boot 装配）
├── main/resources/db/migration/sqlite/V16__llm_calls.sql
└── test/java/org/fourfeetcat/storage/LlmCallRepositoryTest.java

fourfeetcat-boot/src/main/resources/application.yaml   # + fourfeetcat.providers 全局段
```

**Structure Decision**: 跨模块契约（Profile、ToolDescriptor、LlmCallRecorder）放 core，下游模块实现（依赖倒置）；Provider 能力域归 provider；持久化归 storage。与 TechnicalSolution §10 一致（oryxos-* → fourfeetcat-* 映射，已记入 spec Assumptions）。
