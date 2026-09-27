# Implementation Plan: Memory 记忆能力（第22节）

**Branch**: `022-lesson22-memory` | **Date**: 2026-09-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/006-memory-system/spec.md`

## Summary

给 Agent 装上"记忆"。三件事：

1. **焊死那道墙**：`fourfeetcat-core` 新增 `MemoryService`（取上下文 / 记一条 / 查一下三方法）与 `MemoryScope`（核心 / 归档）——上层（组装器、记忆工具）只认这一个门面，门面之上不认识底下是哪一档。接口落 core 不是落记忆模块：组装器在 core、必须注入它，接口留在记忆模块会成环（同第16节"Provider 服务接口上移 core"的既有手法）。
2. **墙之下可插拔**：`fourfeetcat-memory` 新增后端接口 `LongTermMemoryStore` 与三档实现——`MarkdownMemoryStore`（默认，工作区 `memory/MEMORY.md` 一个文件两区块，零依赖）、`SqliteMemoryStore`（按条入 `memory_entries`，截断变查询条数上限）、`Mem0MemoryStore`（自托管外部服务，三个操作翻译成它的 REST 调用）；另加一档 `InMemoryMemoryStore` 作为契约测试里外部服务档的替身。同一套契约断言对三档统一跑——这是"接口不变、实现随便换"的自动化保障。
3. **接通两头**：`MemoryTools`（`save_memory` / `recall_memory`）走第20节已验证的注解注册管道挂进 `ToolRegistry`（补齐第20节登记为跨节的两个工具）；组装器 `PromptBuilder` 的记忆段改由门面供给（会话历史段仍归它自己，不重复注入）。配置项 `memory.backend` 一行切换三档。

交付面**严格对齐参考实现第22节那一版**（见 spec 的 Clarifications 第二轮）：本节长期记忆为工作区全局单份；按 Agent 隔离与跨档统一检索大小写都登记为后续节。

## Technical Context

**Language/Version**: Java 21（Maven 多模块，spring-boot-starter-parent 3.5.16）

**Primary Dependencies**: **零新增第三方依赖**。记忆模块新增的三条都在既有锁定 BOM 内：`fourfeetcat-storage`（仓储 + 实体）、`org.springframework:spring-web`（同步 `RestClient`）、`org.springframework.ai:spring-ai-model`（`@Tool` / `@ToolParam` 注解与 `MethodToolCallbackProvider`——已实测 `1.1.2` 的 jar 内确含 `org/springframework/ai/tool/annotation/Tool.class`，与父 pom 的 `spring-ai-bom 1.1.2` 同版）。boot 模块新增 `fourfeetcat-memory` 依赖（同第19节教训：不接上则新类既进不了容器也进不了打包产物）。

**Storage**: 新增一张表 `memory_entries`（结构化库档用），走既有 Flyway 双轨迁移机制——`fourfeetcat-storage/src/main/resources/db/migration/{sqlite,postgresql}/V20__memory_entries.sql`，同版本号、只增不改，脚本内容取自课程参考建表脚本 `docs/class/schema.sql` 中该表一段（与 V16~V19 同址同口径）。实体 `MemoryEntry` 与仓储 `MemoryEntryRepository` 落 storage（与既有四张表同址）。**不依赖** `hibernate.ddl-auto`（既有配置为 `none`）。

**Testing**: JUnit 5 + Mockito（`spring-boot-starter-test`，各模块已就位）。全部单测、不碰网、不起外部进程——外部服务档用 JDK 内置 `com.sun.net.httpserver.HttpServer` 做进程内假服务（第19/20节已验证的零依赖手法）；结构化库档的契约测试用背靠内存 List 的有状态 mock 仓储（不拉 Spring 容器），真实 SQLite 的建表/LIMIT/LIKE 由 storage 的 `@DataJpaTest` 用例覆盖。集成冒烟打 `@Tag("integration")`，CI 跳过。

**Target Platform**: JVM 服务端

**Project Type**: library（多模块底座；本节是 `fourfeetcat-memory` 从空模块到"能力三全量"的一节）

**Performance Goals**: 无专项指标。每次取上下文/检索都是一次同步读（文件 / 查库 / 调外部服务），"不缓存"是有意为之（宪法原则七的同步模型下，一轮对话读一两次本地文件或一张小表的代价可忽略）。

**Constraints**: 宪法原则二（`@Tool` 只用于 schema 生成，禁用自动执行——记忆工具经既有 `ToolRegistry` 管道，不把 `ToolCallback` 挂到 `ChatClient`）、原则五（审计走既有路径、零新增）、原则七（同步，代码里不出现 Reactor/`CompletableFuture`/自建线程池）、技术约束（Flyway 双轨、依赖倒置、凭证走环境变量）；语法禁区——避开 P3C/ASM 解析不了的 Java 18+ 形态；测试方法名英文 + `@DisplayName` 保留课件中文原文。

**Scale/Scope**: 课件"本节交付物"主干——`MemoryService`（接口 + 实现）/ `MemoryScope` / `LongTermMemoryStore` / 三档实现（文件 / 库 / 外部服务）/ `MemoryTools`（两工具）/ `memory_entries` 表 / 工作区记忆文件约定 / `memory.backend` 配置 / 组装器集成点 + **8 个测试类**（课件点名 6：契约、文件档、库档、外部服务档、记忆工具、门面；对账补 1：仓储；参考实现有、课件未列 1：仓储的 sqlite 档单列）。**交付物外两件经主公追认**：`InMemoryMemoryStore`（契约测试替身 + 轻量测试基建）、`MemoryModule`（模块职责标记类）。**不做**：自动提炼与自动抽取、记忆压缩、语义/向量检索、知识图谱后端、情景记忆、记忆 CRUD、按 Agent 隔离（后续节）、跨档统一检索大小写（后续节）。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|---|---|---|
| 一：自实现 ReAct | 本节不碰循环；`ReActLoop` 零改动（记忆不进循环，只在组装阶段经门面进提示） | ✅ 通过 |
| 二：Spring AI 只用两件事 ⚠️ | 只用它的两件事：①协议转换（不变）②`@Tool` 注解的 schema 生成（`MemoryTools` 走第20节同款管道）。**禁用**自动执行：记忆工具经 `AnnotatedToolAdapter` → `CallbackTool` → `ToolRegistry.execute` → `ToolExecutor` 一条路进来，不把 `ToolCallback` 挂到 `ChatClient` | ✅ 通过 |
| 三：Provider 显式映射 | 本节不碰 provider 路由；`LlmCaller` 与 `ToolDescriptor` 口径不变 | ✅ 通过 |
| 四：一个目录 = 一个 Agent | 不引入 Agent 目录/skills 相关概念；`Profile` 既有字段一律不改（本节不新增 Profile 字段）。记忆作用域本节为工作区全局单份，与"一个目录 = 一个 Agent"不冲突——按 Agent 隔离登记为后续节 | ✅ 通过 |
| 五：审计 Day One | **零新增审计代码**：记忆工具经既有 `ToolExecutor` → `ToolInvocationRecorder` 路径，成败都落 `tool_invocations`；`memory_entries` 是记忆本体表，**不是**审计表 | ✅ 通过 |
| 六：无 SecurityManager、真实路径校验 | 不用 SecurityManager；记忆读写不是涉外 IO（不读白名单外的资源、不跑命令、不出网）——**唯一出网的是外部服务档的一次 HTTP 调用**，它是显式配置的后端而不是工具执行路径，故不过工具的沙箱校验位；这一点在实现注释里写明，避免将来被误判为漏检 | ⚠️ 有条件通过（见 Complexity Tracking） |
| 七：同步执行 | 全同步阻塞；不出现 Reactor/`CompletableFuture`/自建线程池 | ✅ 通过 |
| 八：Tool 模块三合一 | 记忆工具不在 `fourfeetcat-tool`，而在 `fourfeetcat-memory`——这符合第20节的既定分工（第20节交付注册管道，记忆工具本体归第22节），不构成模块拆分 | ✅ 通过 |
| 技术约束：依赖倒置 | 新增跨模块契约 `MemoryService` / `MemoryScope` 在 core；实现在 memory；持久化件在 storage。既有边：memory → {core, storage}、storage → core、boot → {memory, tool, …}，**无环** | ✅ 通过 |
| 技术约束：Flyway 双轨 | 新增 V20 双轨同版本号脚本（sqlite/postgresql），只增不改；脚本内容取自课程参考脚本，`ddl-auto` 保持 `none` | ✅ 通过 |
| 技术约束：配置与凭证 | 外部服务档的地址与作用域标识走 `memory.mem0.base-url` / `memory.mem0.user-id`，由环境变量占位、缺省非凭证值；代码里不出现任何明文 key | ✅ 通过 |
| 技术约束：模块结构 | **不新建、不改名模块**；`fourfeetcat-memory` 的内容扩张与该模块既定职责（能力三）完全一致，无需同步 §10。**落位声明**：skill 落位表写"22 全部→记忆模块"，实际把门面接口与枚举放 core、持久化件放 storage——理由见 Summary 与 research D1/D5，属"细节以 TechnicalSolution §10 与依赖倒置为准"的范围内 | ✅ 通过（已声明） |
| 技术约束：文档同步 | 本节**不改变**对外定位与特性表述：内置工具数在本节后正好凑齐 9 个（`save_memory` / `recall_memory` 归位），README / 官网 / 设计文档的既有"内置九个"表述**仍然正确**，三处均无需改动 | ✅ 通过 |
| 技术约束：中文注释惯例 | 新增类/方法均带中文 javadoc，写明"谁消费 / 什么口径 / 为什么这么设计"（仓库强惯例，非门禁但按惯例执行） | ✅ 通过 |

## Project Structure

### Documentation (this feature)

```text
specs/006-memory-system/
├── plan.md              # 本文件
├── spec.md              # 需求规格（含两轮裁决记录）
├── research.md          # Phase 0：决策与理由
├── data-model.md        # Phase 1：值对象 / 端口 / 表
├── contracts/           # Phase 1：逐模块契约
│   └── java-contracts.md
├── quickstart.md        # Phase 1：验证指南
├── checklists/
│   └── requirements.md  # 规格质量清单
└── tasks.md             # Phase 2：任务清单（/speckit-tasks 产出）
```

### Source Code (repository root)

```text
fourfeetcat-core/
├── src/main/java/org/fourfeetcat/core/
│   ├── package-info.java                 # 改：跨模块契约清单补 memory 一句
│   ├── memory/
│   │   ├── MemoryService.java            # 新：门面接口（跨模块契约）
│   │   └── MemoryScope.java              # 新：分区枚举 CORE / ARCHIVAL
│   └── react/PromptBuilder.java          # 改：记忆段改由门面供给（接缝适配）
└── src/test/java/org/fourfeetcat/core/react/PromptBuilderTest.java   # 改：断言随接缝调整

fourfeetcat-memory/
├── pom.xml                               # 改：+ core、storage、spring-web、spring-ai-model
├── src/main/java/org/fourfeetcat/memory/
│   ├── LongTermMemoryStore.java          # 新：后端接口（对下可插拔）
│   ├── MarkdownMemoryStore.java          # 新：档一（默认）
│   ├── SqliteMemoryStore.java            # 新：档二
│   ├── Mem0MemoryStore.java              # 新：档三（自托管外部服务）
│   ├── InMemoryMemoryStore.java          # 新：契约测试替身（交付物外，经主公追认）
│   ├── MemoryServiceImpl.java            # 新：门面实现（只转发）
│   ├── MemoryModule.java                 # 新：模块职责标记类（交付物外，经主公追认）
│   └── builtin/MemoryTools.java          # 新：save_memory / recall_memory
└── src/test/java/org/fourfeetcat/memory/
    ├── MemoryStoreContractTest.java      # 新：同一套断言对三档
    ├── MarkdownMemoryStoreTest.java      # 新：文件档专属
    ├── SqliteMemoryStoreTest.java        # 新：库档专属
    ├── Mem0MemoryStoreTest.java          # 新：外部服务档专属（进程内假服务）
    ├── MemoryServiceImplTest.java        # 新：门面
    └── builtin/MemoryToolsTest.java      # 新：两个工具

fourfeetcat-storage/
├── src/main/java/org/fourfeetcat/storage/
│   ├── MemoryEntry.java                  # 新：实体
│   └── MemoryEntryRepository.java        # 新：仓储
├── src/main/resources/db/migration/sqlite/V20__memory_entries.sql       # 新
├── src/main/resources/db/migration/postgresql/V20__memory_entries.sql   # 新
└── src/test/java/org/fourfeetcat/storage/MemoryEntryRepositoryTest.java # 新

fourfeetcat-boot/
├── pom.xml                               # 改：+ fourfeetcat-memory 依赖
├── src/main/resources/application.yaml   # 改：+ memory.backend / memory.mem0.*
└── src/main/java/org/fourfeetcat/boot/AgentRuntimeConfiguration.java   # 改：三个 Bean + 工具注册

config/spotbugs/spotbugs-exclude.xml      # 改：注入式协作者的 EI_EXPOSE_REP2 排除（按既有惯例）
```

## Complexity Tracking

| 项 | 为什么需要 | 为什么不用更简单的做法 |
|---|---|---|
| **门面接口放 core（而非记忆模块）** | 组装器 `PromptBuilder` 在 core、必须注入门面；接口若留在记忆模块，core → memory → core 成环。放 core 后，"上层只认门面"在代码上成立且上层不依赖实现模块 | 让 core 只持一个函数式供给口（`Function<Session,String>`）能避免契约上移，但那样"门面"就只是记忆模块内部的类，第21节"把 Memory 焊成一个稳定接口"在这条接缝上落不到代码，将来换后端时接缝的类型也得跟着改。同第16节 `ProviderService` 上移 core 的既有手法选前者 |
| **前序节公共接口适配一处**（`PromptBuilder` 第二构造参数由函数改为门面） | 课件把门面签名定死为 `buildContext(Session)`；原接缝是按 Profile 供给一段文本的函数，类型对不上，"传方法引用"落不了地 | Java 泛型擦除后 `Function<Profile,String>` 与 `Function<Session,String>` 同签名、无法用重载共存；保留原函数再在记忆模块包一层，等于让组装器继续不认门面。故按主公裁决取"按本节门面签名为准"，单参构造保留、未装配路径一字不变 |
| **外部服务档的 HTTP 调用不过工具的沙箱校验位** | 它不属工具执行路径：是装配期按配置选定的后端，其读写在门面内发生，不经 `ToolExecutor` | 挂沙箱校验位就得给"记忆读写"造一类新的动作类型，而沙箱的白名单语义（路径 / 可执行文件 / 域名）对"配置里声明的后端地址"没有增益：地址本就是部署者配的。在实现注释与验收报告里显式标注该判断 |
| **交付物外两件（内存后端 / 标记类）** | 内存后端是课件 harness 明写的"内存假 Mem0 替身"的落地形态，同时是门面与工具测试的基建；标记类说明模块职责 | 替身只在测试里写一份局部匿名类也可行，但门面测试与工具测试都要用同一档替身，散在三处必然重复；两件均已**经主公追认**（触发软门禁一的显式批准） |

## Phase 0 摘要

见 [research.md](research.md)：D1 门面落点、D2 接缝形态、D3 分区枚举、D4 文件档区块解析与写入形态、D5 库档 LIMIT/LIKE 手法、D6 LIKE 不转义、D7 跨档大小写差异（已知状态）、D8 外部服务档端点与"地址缺失即装配期报错"、D9 静态检查排除口径、D10 装配形态、D11 契约测试的替身与假仓储分工、D12 时间戳落库口径、D13 库档专属测试的钉法、D14 记忆段注入形态、D15 前置适配清单。

## Phase 1 摘要

见 [data-model.md](data-model.md)（值对象、端口、表）、[contracts/java-contracts.md](contracts/java-contracts.md)（逐模块签名与行为口径）、[quickstart.md](quickstart.md)（判卷命令与人工项）。
