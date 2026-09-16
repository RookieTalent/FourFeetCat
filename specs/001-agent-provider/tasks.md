---
description: "Task list for Agent Provider (Lesson 16) implementation"
---

# Tasks: Agent Provider（第16节）

**Input**: Design documents from `/specs/001-agent-provider/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/java-contracts.md, quickstart.md

**Tests**: harness 承载验收（spec SC-006），测试任务先于/伴随对应实现任务，课件关键回归测试断言逐条保真、方法名英文 + `@DisplayName` 保留课件原文。

**Organization**: 按 User Story 分组（spec：US1 路由 P1、US2 审计 P2、US3 工具翻译 P3、US4 Profile 加载 P2）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- 含确切文件路径；路径以 `fourfeetcat-<module>/src/...` 表达

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖与全局配置就位

- [x] T001 `fourfeetcat-core/pom.xml` 加 `org.yaml:snakeyaml`（版本走 spring-boot-starter-parent 托管）
- [x] T002 `fourfeetcat-provider/pom.xml` 加 `org.springframework.ai:spring-ai-model` 与 `org.springframework.ai:spring-ai-openai`（版本走 BOM，不写死；理由见 research.md D2）
- [x] T003 H3 硬门禁：`mvn dependency:tree -pl fourfeetcat-provider` 确认两依赖在锁定 BOM 内解析成功——核不到立即停下软报，不得换依赖自行发挥
- [x] T004 [P] `fourfeetcat-boot/src/main/resources/application.yaml` 加 `fourfeetcat.providers` 全局段（deepseek 示例项：name/base-url/api-key `${DEEPSEEK_API_KEY}`，不落明文）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有 Story 共用的契约类型（依赖倒置落点）

- [x] T005 [P] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/profile/Profile.java`：全字段 record（含嵌套 ProviderConfig{name,model,temperature}；字段清单见 data-model.md，含 notify_channels）
- [x] T006 [P] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/ToolDescriptor.java`：record(name, description, inputSchema)
- [x] T007 [P] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/LlmCallRecorder.java`：审计端口接口（签名见 contracts/java-contracts.md §2，Usage 为 Spring AI 类型）

**Checkpoint**: 契约就位，各 Story 可并行

---

## Phase 3: User Story 1 - 按 Profile 名字路由到对应模型 (Priority: P1) 🎯 MVP

**Goal**: 显式 `Map<String, ChatModel>` 按名路由完成一次调用，未知名报错

**Independent Test**: `mvn test -pl fourfeetcat-provider`，路由测试绿

### Tests (harness 先行)

- [x] T008 [P] [US1] `fourfeetcat-provider/src/test/java/org/fourfeetcat/provider/ProviderServiceTest.java`：落课件钉坑测试 `按名路由_两个provider不串台`（译名 `routeByName_twoProvidersNoCrossTalk`，verify kimi 1 次 + verify deepseek never）与未知名抛异常（消息含所引用名）；双 mock ChatModel + mock LlmCallRecorder + mock ToolSchemaAdapter
- [x] T009 [P] [US1] `fourfeetcat-provider/src/test/java/org/fourfeetcat/provider/ProviderSmokeIT.java`：`@Tag("integration")`，缺 `DEEPSEEK_API_KEY` 时 `assumeTrue` 跳过，真调一次断言非空响应

### Implementation

- [x] T010 [P] [US1] `fourfeetcat-provider/src/main/java/org/fourfeetcat/provider/ProviderNotFoundException.java`（消息含所引用 provider 名）
- [x] T011 [P] [US1] `fourfeetcat-provider/src/main/java/org/fourfeetcat/provider/ToolSchemaAdapter.java` 最小实现（chat 编译依赖；`call()` 抛 `IllegalStateException`；完整 harness 在 US3）
- [x] T012 [US1] `fourfeetcat-provider/src/main/java/org/fourfeetcat/provider/ProviderService.java`：`chat(sessionId, profile, tools, prompt)`——映射表取名→未知名抛 T010 异常；options **唯一构建点**：model/temperature 取 Profile、`toolCallbacks` 经适配器、`internalToolExecutionEnabled(false)`；计时；成败都过 `LlmCallRecorder`；异常上抛（依赖 T005~T011）
- [x] T013 [US1] `fourfeetcat-provider/src/main/java/org/fourfeetcat/provider/ProviderConfiguration.java`：按 `fourfeetcat.providers` 逐条构造 ChatModel 显式 put `Map<String,ChatModel>`（坑一：不扫描容器 Bean 类型），装配 ProviderService Bean；确认 boot 主类组件扫描覆盖 `org.fourfeetcat.provider`

**Checkpoint**: US1 独立可测——路由不串台、未知名报错

---

## Phase 4: User Story 2 - 调用成败都留审计痕迹 (Priority: P2)

**Goal**: `llm_calls` 建表、实体、Repository、端口实现，审计两路真落库

**Independent Test**: `mvn test -pl fourfeetcat-storage,fourfeetcat-provider`，审计与仓储测试绿

### Tests (harness 先行)

- [x] T014 [US2] `fourfeetcat-storage/src/main/resources/db/migration/sqlite/V16__llm_calls.sql`：从 `docs/class/schema.sql` L28-41 逐字摘 llm_calls + 索引（不改动一字）
- [x] T015 [P] [US2] `fourfeetcat-storage/src/test/java/org/fourfeetcat/storage/LlmCallRepositoryTest.java`：`jdbc:sqlite:<@TempDir>/test.db` 文件库 + `ScriptUtils.executeSqlScript` 执行 V16 脚本 + `ddl-auto=none`；断言能存能读、`success`/`error_message` 两列真实存在（不用 `:memory:`，理由 research.md D8）
- [x] T016 [P] [US2] `ProviderServiceTest` 补课件钉坑测试 `调用失败_审计必须留下success为false的记录`（译名 `callFailure_auditRecordsFailureWithReason`：`thenThrow` + `assertThrows` + `verify(audit).record(..., eq(false), contains("timeout"), ...)`）与成功审计一条（success=true + usage）

### Implementation

- [x] T017 [P] [US2] `fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/LlmCall.java`：JPA 实体，列名与 V16 脚本逐字一致（boolean↔INTEGER 0/1 手转、createdAt TEXT ISO-8601）
- [x] T018 [P] [US2] `fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/LlmCallRepository.java`：`extends JpaRepository<LlmCall, Long>`
- [x] T019 [US2] `fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/LlmCallRecorderImpl.java`：实现 core 端口（内部用 Repository；boot 装配）

**Checkpoint**: US2 独立可测——成败各留 1 条带原因/用量的记录

---

## Phase 5: User Story 3 - 工具只翻译、不执行 (Priority: P3)

**Goal**: 翻译产物字段一一对齐且不可执行，自动执行关闭钉死

**Independent Test**: `mvn test -pl fourfeetcat-provider`，翻译与自动执行关闭测试绿

### Tests (harness 先行)

- [x] T020 [P] [US3] `fourfeetcat-provider/src/test/java/org/fourfeetcat/provider/ToolSchemaAdapterTest.java`：fixture ToolDescriptor 翻译后 name/description/inputSchema 一一对齐；`assertThrows` 证明 `call()` 不可执行
- [x] T021 [P] [US3] `ProviderServiceTest` 补课件钉坑测试 `带工具schema调用_请求里关闭了自动执行`（译名 `callWithToolSchema_disablesAutoExecution`：captor 取 `Prompt.getOptions()`，`ToolCallingChatOptions.isInternalToolExecutionEnabled(...)` 断 false + `getToolCallbacks()` 非空）

### Implementation

- [x] T022 [US3] 按 T020/T021 红绿修 `ToolSchemaAdapter.java`（DefaultToolDefinition 对齐；只翻译不执行）

**Checkpoint**: US3 独立可测——翻译保真 + 坑二被测试永久钉死

---

## Phase 6: User Story 4 - Profile 从 YAML 加载并按名查找 (Priority: P2)

**Goal**: `.fourfeetcat/profiles/*.yaml` 解析、校验、索引

**Independent Test**: `mvn test -pl fourfeetcat-core`，加载测试绿

### Tests (harness 先行)

- [x] T023 [P] [US4] `fourfeetcat-core/src/test/java/org/fourfeetcat/core/profile/ProfileLoaderTest.java`：`@TempDir` 写 YAML——全字段解析；引用不存在 provider 报错清晰；坏文件不阻断其余加载；`${ENV}` 占位从注入的 env 函数解析（stub `Function<String,String>`）

### Implementation

- [x] T024 [US4] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/profile/ProfileLoader.java`：SnakeYAML `Map` 手工映射；`${NAME}` 正则占位解析（env 读取注入 `Function<String,String>`，默认 `System::getenv`）；单文件失败记 error 跳过；校验 provider 名在全局名单
- [x] T025 [US4] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/profile/ProfileRegistry.java`：`Optional<Profile> find(String name)`；构造期收全局 provider 名单做校验（依赖 T005）——校验实际落 ProfileLoader（拿文件上下文报错更清晰），Registry 为纯索引

**Checkpoint**: US4 独立可测——好文件加载、坏文件跳过、占位解析

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T026 `mvn clean verify` 全绿（Spotless google-java-format / PMD 7 / Checkstyle / SpotBugs+FindSecBugs 逐门过；语法禁区自查：无增强 switch `default ->` 形态）
- [x] T027 节级验收：课件 harness 映射表逐测试对号、"本节交付物"逐项存在性核对、H4 六条不变量自查（②成败落 llm_calls、③grep 无明文 key、⑤无异步、⑥无自动执行路径；①沙箱留 24 节接线注记）、输出剩余人工项清单（真 key 冒烟/依赖 tree 目视/grep 明文 key）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → 无依赖；T003 是 T002 的验证门禁（H3）
- **Foundational (Phase 2)** → 依赖 Phase 1；阻塞全部 Story
- **US1 (Phase 3)** → 依赖 Phase 2
- **US2 (Phase 4)** → 依赖 Phase 2；T016 补测依赖 T012 已有实现（审计代码在 US1 落地，US2 验证并补齐存储侧）
- **US3 (Phase 5)** → 依赖 Phase 3（T021 断言的 options 行为由 T012 实现）
- **US4 (Phase 6)** → 仅依赖 Phase 2（与 US1~US3 无交叉，可并行）
- **Polish (Phase 7)** → 依赖全部

### Parallel Opportunities

- T005/T006/T007（Phase 2 内）；T008/T009（US1 测试）；T014 之后 T015/T016 并行；T017/T018（US2 实体与仓储）；T020/T021（US3 测试）
- US4 整个 phase 可与 US1~US3 并行（不同模块）

---

## Implementation Strategy

### MVP First (User Story 1 Only)

Setup → Foundational → US1 即得"按名路由完成一次调用"的最小闭环；US2 补审计落库、US3 钉死坑二、US4 补 Profile 加载。

### Incremental Delivery

每个 Checkpoint 后跑对应模块 `mvn test`，红了当场修，不攒尾。全部完成后 Phase 7 收口：`mvn clean verify` 全绿 + 六项证据验收报告（不 commit/push，由主公决定）。

---

## Notes

- 课件三个钉坑测试（路由不串台/失败审计/自动执行关闭）分别落 T008/T016/T021，断言逻辑逐条保真，方法名译英文、`@DisplayName` 保留课件原文。
- 反作弊红线：不删断言、不 `@Disabled`、不放宽阈值；实现错修实现，认为测试错停下报告。
