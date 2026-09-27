---

description: "Task list for Memory 记忆能力（第22节）implementation"

---

# Tasks: Memory 记忆能力（第22节）

**Input**: Design documents from `/specs/006-memory-system/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/java-contracts.md, quickstart.md

**Tests**: harness 承载验收（spec SC-009）——测试任务先于或伴随对应实现任务；课件关键回归断言逐条保真、方法名英文 + `@DisplayName` 保留课件中文原文。

**Organization**: 按 User Story 分组（spec：US1 三档收敛到门面 P1、US2 核心/归档分区语义 P1、US3 记忆工具 P1、US4 记忆进系统提示 P2）。

**范围说明（主公裁决，两轮对账）**：
- **交付面严格对齐参考实现第22节版**：本节长期记忆为**工作区全局单份**（`memory/MEMORY.md`）。**不做**按 Agent 隔离（登记为后续节）与**不做**跨档统一检索大小写（登记为后续修订）——两条均为本轮对账**推翻**的加强项，`T035` 登记不勾选。
- **交付物外两件经主公追认**：`InMemoryMemoryStore`（契约测试替身 + 轻量测试基建，放主源码）、`MemoryModule`（模块职责标记类）——`T019`/`T021` 照常勾选，描述里标注来源。
- **落位与 skill 落位表的差异已在 plan 声明**：门面接口与分区枚举落 `fourfeetcat-core`（依赖倒置，避免 core → memory 成环）；持久化件落 `fourfeetcat-storage`；其余落 `fourfeetcat-memory`。
- **前序节适配一处**（跨节触碰，`T008`/`T032`/`T033`/`T034`）：组装器第二构造参数改为注入门面。
- 本节**不改** README / 官网 / 设计文档：内置工具数在本节后正好凑齐 9 个，既有"内置九个"表述仍然正确（`T039` 登记为核对结论）。
- **参考实现可比对**（实现期逐行对齐语义、按本仓命名改写）：`/d/oneByDay/ai_code/oryxos` 的提交 `aa781d28` 的六处——`oryxos-core/.../memory/`、`oryxos-memory/`、`oryxos-storage/.../MemoryEntry*.java`、`oryxos-storage/src/main/resources/schema.sql`、`oryxos-boot/src/main/resources/application.yml`、`oryxos-cli/.../OryxOsRuntime.java`。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- 含确切文件路径；路径以 `fourfeetcat-<module>/src/...` 表达

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖与基线就位

- [x] T001 H3 硬门禁：核实本节要用的第三方 API 在锁定依赖里可达——`org.springframework.ai:spring-ai-model:1.1.2` 提供 `org.springframework.ai.tool.annotation.Tool` 与 `@ToolParam`（`unzip -l` 查 jar 内容即可确认）、`org.springframework:spring-web` 提供 `RestClient`、`org.springframework.data:spring-data-commons` 提供 `Pageable`/`PageRequest`；核不到立即停下软报，不得换依赖自行发挥
- [x] T002 基线核对：`mvn clean test` 确认第16/17/18/19/20节全部测试绿（作为本节起点的回归基线）
- [x] T003 [P] `fourfeetcat-memory/pom.xml`：依赖加 `fourfeetcat-storage`、`org.springframework:spring-web`、`org.springframework.ai:spring-ai-model`（`@Tool`/`@ToolParam` 的 schema 生成管道，不靠传递）；`fourfeetcat-core` 既有依赖保留；`spring-boot-starter-test`（test）已有则不动。**不新增任何第三方库**
- [x] T004 [P] `fourfeetcat-boot/pom.xml`：加 `fourfeetcat-memory` 依赖——不接上则新类既进不了容器也进不了打包产物（同第19节教训，注释照写）
- [x] T005 [P] `fourfeetcat-boot/src/main/resources/application.yaml`：加 `memory.backend: markdown`、`memory.mem0.base-url: ${MEM0_BASE_URL:}`、`memory.mem0.user-id: ${MEM0_USER_ID:fourfeetcat}` 三键，并加注释说明"markdown 默认 / mem0 仅切到该档时使用、地址与作用域标识走环境变量不落明文"

**Checkpoint**: 依赖解析通过；`mvn -o dependency:tree -pl fourfeetcat-memory` 能看到 storage 与 spring-ai-model

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 门面契约、分区枚举、后端接口、持久化件——**所有** user story 都建在这上面

**⚠️ CRITICAL**: 本阶段完成前，任何一档后端都无处安放

- [x] T006 [P] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/memory/MemoryScope.java`：枚举 `CORE` / `ARCHIVAL`；类注释写明"核心始终在场、归档可截断可检索；分区由调用方显式指定"
- [x] T007 [P] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/memory/MemoryService.java`：接口三方法 `buildContext(Session)` / `remember(String, MemoryScope)` / `recall(String)`；javadoc 写明**两条关键口径**——①"取上下文只返回长期记忆（核心全量 + 归档截断后），会话历史由组装器的历史段独立负责"（课件 harness 表格为准）②"接口落 core 而非记忆模块：组装器在 core 必须注入它，接口留实现模块会成环（同第16节 Provider 服务上移的既有手法）"
- [x] T008 `fourfeetcat-core/src/main/java/org/fourfeetcat/core/package-info.java`：跨模块契约清单补 `memory` 一句（**跨节触碰**：原清单已列 channel / knowledge，补到与实际一致）
- [x] T009 [P] `fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/LongTermMemoryStore.java`：接口三方法 `append` / `load` / `recallByKeyword`；javadoc 逐条列出**四条行为契约**（不缓存 / 核心永不截断 / 分区由调用方指定 / 检索只在归档区）；签名里不出现"文件""表""HTTP""向量"等某一档特有的词
- [x] T010 [P] `fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/MemoryEntry.java`：实体，列名与迁移脚本逐字一致（`id` / `scope` / `content` / `created_at`）；时间列按仓库既有口径声明为 `String`（ISO-8601，research D12 记录沿用跨轨风险的理由）；类注释指向 `db/migration/sqlite/V20__memory_entries.sql`
- [x] T011 [P] `fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/MemoryEntryRepository.java`：`extends JpaRepository<MemoryEntry, Long>`；三方法 `findByScopeOrderByIdAsc(String)`、`findByScopeOrderByIdDesc(String, Pageable)`、`searchArchival(@Param("pattern") String)`（`@Query` 显式限定 `scope = 'ARCHIVAL'` + `content LIKE :pattern` + 按 id 正序）；类注释写明"核心区查询永不带条数上限，上限只出现在归档查询上"
- [x] T012 [P] `fourfeetcat-storage/src/main/resources/db/migration/sqlite/V20__memory_entries.sql`：`memory_entries` 表 + `idx_memory_entries_scope` 索引，内容取自课程参考建表脚本 `docs/class/schema.sql` 的该表一段；头部注释写明来源与"核心区全量不截断、归档只带最近 N 条（查询 LIMIT，非删除）"；**注释里不得出现美元符号紧跟花括号的占位式写法**（V19 记录的 Flyway 实测踩坑）
- [x] T013 [P] `fourfeetcat-storage/src/main/resources/db/migration/postgresql/V20__memory_entries.sql`：同列名同约束，只方言不同（自增改标识列、`TEXT` 时间列改原生 `TIMESTAMP`），照 V17~V19 的跨轨写法
- [x] T014 core 回归：`mvn test -pl fourfeetcat-core -am` 全绿（T006~T008 的编译与既有测试验证点）

**Checkpoint**: 契约（门面 + 枚举）与后端接口、持久化件、双轨迁移就位；core 既有测试未被打断

---

## Phase 3: User Story 1 - 三档长期记忆后端收敛到同一个门面之下 (Priority: P1) 🎯 MVP

**Goal**: `MemoryServiceImpl` 把读写委托给可插拔的后端；三档实现（文件 / 结构化库 / 外部服务）各就各位；`memory.backend` 一行切换，门面之上零改动

**Independent Test**: `mvn test -pl fourfeetcat-memory -am -Dtest='MemoryStoreContractTest,MemoryServiceImplTest'`

### Tests（harness 先行）

- [x] T015 [US1] `fourfeetcat-memory/src/test/java/org/fourfeetcat/memory/MemoryStoreContractTest.java`：`@TestInstance(PER_CLASS)` + `@TempDir`；`allStores()` 三档数据源 = 文件档（真文件）/ 结构化库档（**背靠内存 List 的有状态假仓储**，stub 四个方法）/ 内存替身档（`InMemoryMemoryStore`，代替外部服务档跑契约）；四条断言组——① `truncationKeepsCoreIntact`（课件原文进 `@DisplayName("截断只裁归档区_核心记忆一字不能少")`，断言逐条保真：核心在、归档最早的被裁、最近的保留）② `writeIsImmediatelyReadable_noCache`（`@DisplayName("写入后立刻可读_不允许有缓存")`）③ `scopeRoutesToCorrectSection`（核心区不参与检索、归档区可检索、两者都在上下文里）④ 核心区关键词检索不到
  > 与 T016~T020 同批落地（编译依赖）；假仓储与替身档的分工见 research D11

### Implementation for User Story 1

- [x] T016 [P] [US1] `fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/MarkdownMemoryStore.java`：构造收工作区根，文件 = `<root>/memory/MEMORY.md`；`CORE_HEADER`/`ARCHIVE_HEADER` 字面量、条目行 `- [yyyy-MM-dd] 内容`、归档水位 4000 字符；`read()` 缺文件返回空串、`extractSection` 取标题之后到下一个标题之前并 `strip()`；写入整文件重写（`Files.createDirectories` 建父目录）；`truncateIfNeeded` **只接归档段**（契约二靠物理隔离）；检索按行包含、区分大小写（课件逐字）
- [x] T017 [P] [US1] `fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/SqliteMemoryStore.java`：构造收仓储；`append` 落 `scope.name()` + 原文 + `Instant.now().toString()`；`load` = 核心区全量（升序查询）+ 归档区取最近 100 条后**翻回时间正序**，两侧都带分区标题；`recallByKeyword` 走 `searchArchival("%关键词%")`；渲染条目行 `- 内容`；类注释写明"截断从字符串裁尾变成归档查询的条数上限、LIMIT 只加在归档上（契约二靠 SQL 结构保证）"，并写明"检索关键词按课件原样拼进库内匹配、**不转义通配符**"这一取舍的理由（记忆内容是 Agent 自写的非对抗输入，spec Edge Cases 已录）
- [x] T018 [P] [US1] `fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/Mem0MemoryStore.java`：构造收 `RestClient` + 作用域标识；`append` → `POST /v1/memories/`（体含 `messages[0].{role,content}` / `user_id` / `metadata.scope`）；`load` → 两次 `GET /v1/memories/?user_id=&scope=`（CORE 与 ARCHIVAL 各一次）；`recallByKeyword` → `POST /v1/memories/search/`；响应解析取 `results[].memory`（缺 results 时按数组根），**空或解析不出返回空列表**；类注释写明"端点按该服务社区版约定编写，具体以其部署版本为准；提炼/冲突消解/语义检索都交给它"，另写明**边界判断**——"本次出网是按配置选定的后端调用，不经工具执行的沙箱校验位"及其理由（plan 的 Complexity Tracking 有记录，避免将来被误判为漏检）
- [x] T019 [P] [US1] `fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/InMemoryMemoryStore.java`：**交付物外、经主公追认**；进程内两个 List；归档取尾部 100 条；检索 `contains`（区分大小写，与文件档同口径）；类注释写明两个用途（契约测试里代替外部服务档、门面/工具测试的轻量基建）
- [x] T020 [US1] `fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/MemoryServiceImpl.java`：构造收后端接口；三方法**只转发**（`buildContext` → `store.load()`，不拼会话历史）；类注释写明"换后端只换注入的 store，门面签名与上层调用不变；`session` 参数按本节对账结论当前不参与作用域圈定，保留它是为了签名稳定"
- [x] T021 [P] [US1] `fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/MemoryModule.java`：**交付物外、经主公追认**；模块职责标记类（说明本模块提供门面实现、四档后端与记忆工具）
- [x] T022 [US1] `fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/AgentRuntimeConfiguration.java`：加 `LongTermMemoryStore` Bean（读 `memory.backend`：`sqlite` → 库档、`mem0` → 外部服务档（读地址与作用域标识）、`markdown` 或空 → 文件档；**其余取值抛异常点名该取值**；`mem0` 档地址为空 → **抛异常点名 `memory.mem0.base-url`**）与 `MemoryService` Bean；`ToolRegistry` 参数加门面（本任务只加参数，工具注册见 T031）
- [x] T023 [P] [US1] `config/spotbugs/spotbugs-exclude.xml`：按仓库既有惯例加**带理由**的按类排除项（门面实现与外部服务档构造注入协作者的 `EI_EXPOSE_REP`/`EI_EXPOSE_REP2`），理由对齐既有七处同因排除；**不放宽** threshold/effort、不引入 `spotbugs-annotations`
- [x] T024 [US1] `fourfeetcat-memory/src/test/java/org/fourfeetcat/memory/MemoryServiceImplTest.java`：取到的上下文含长期记忆（核心区完整在内 + 归档截断后的部分也在）；`remember`/`recall` 转发到底层（命中与非命中两侧）；用内存替身档做桩，`@DisplayName` 保留课件原文

**Checkpoint**: `mvn test -pl fourfeetcat-memory -am` 全绿；换一档后端只需改 `memory.backend` 一行

---

## Phase 4: User Story 2 - 核心记忆始终在场，归档记忆可截断、可检索 (Priority: P1)

**Goal**: 分区语义在**每一档**的落地方式都被专项测试钉住——文件档靠字符串裁剪、库档靠查询条数上限、外部服务档靠它自己的作用域机制

**Independent Test**: `mvn test -pl fourfeetcat-memory -pl fourfeetcat-storage -am -Dtest='MarkdownMemoryStoreTest,SqliteMemoryStoreTest,Mem0MemoryStoreTest,MemoryEntryRepositoryTest'`

### Tests（harness 先行）

- [x] T025 [US2] `fourfeetcat-memory/src/test/java/org/fourfeetcat/memory/MarkdownMemoryStoreTest.java`：文件档专属——空记忆文件 `load` 返回两个空区块不报错；**文件存在但缺某一分区标题**时该区按空处理、不报错；归档恰好不超上限不裁、超过才裁最早的；核心与归档写入互不串区（归档检索只命中归档、核心关键词检索不到）
- [x] T026 [US2] `fourfeetcat-memory/src/test/java/org/fourfeetcat/memory/SqliteMemoryStoreTest.java`：库档专属（课件点名、参考实现未单列，本节补上）——用 mock 仓储 + 调用验证钉住三件事：核心区走"全量按写入顺序"的查询、归档区走"最近 N 条"的查询且**条数上限只出现在归档查询上**、检索走"只在归档内匹配"的查询
- [x] T027 [US2] `fourfeetcat-memory/src/test/java/org/fourfeetcat/memory/Mem0MemoryStoreTest.java`：外部服务档专属——用 JDK 内置 `com.sun.net.httpserver.HttpServer` 起进程内假服务（**不碰真 server**）；断言：写入请求体带内容与分区 metadata、作用域标识出现在体里、检索被转发并解析成 memory 文本、服务端 5xx 异常上抛不静默吞、**连接被拒时异常上抛不静默返回空**、**响应体不含期望字段时返回空列表不抛异常**
- [x] T028 [US2] `fourfeetcat-storage/src/test/java/org/fourfeetcat/storage/MemoryEntryRepositoryTest.java`：`@DataJpaTest` + `Replace.NONE` + 静态临时库目录 + `@DynamicPropertySource`（照既有四个仓储测试的模板）；`@BeforeEach` 执行 `db/migration/sqlite/V20__memory_entries.sql` 建表；断言：表能存能读（含时间列非空）、归档取最近 N 条且最新在前、库内匹配**只命中归档**

**Checkpoint**: 两个分区语义在三档上的落地方式与真库 SQL 语义都有机器守卫

---

## Phase 5: User Story 3 - Agent 自己动手记与查，走与内置工具完全相同的管道 (Priority: P1)

**Goal**: `save_memory` / `recall_memory` 以内置工具身份挂进注册表（补齐第20节登记为跨节的两个工具）

**Independent Test**: `mvn test -pl fourfeetcat-memory -am -Dtest='MemoryToolsTest'`

### Tests（harness 先行）

- [x] T029 [US3] `fourfeetcat-memory/src/test/java/org/fourfeetcat/memory/builtin/MemoryToolsTest.java`：分区缺省写归档（给了 `null` 也落归档）；显式核心写核心（核心区不参与归档检索、但出现在上下文里）；**非法分区返回点明该取值的错误且不落库**；检索未命中返回"没有找到相关记忆"**不抛异常**；大小写不一的 `scope` 取值被归一接受

### Implementation for User Story 3

- [x] T030 [US3] `fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/builtin/MemoryTools.java`：`@Tool(name = "save_memory", ...)` / `@Tool(name = "recall_memory", ...)` 两方法 + `@ToolParam` 参数说明；分区取值归一（空/null → 归档，大小写不敏感），**非法取值返回点明该取值的错误文本且不落库**；检索未命中返回"没有找到相关记忆"；只认门面，对后端无感
- [x] T031 [US3] `fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/AgentRuntimeConfiguration.java`（**同文件，须在 T022 之后**）：`toolRegistry(...)` 里 `registry.registerAnnotated(new MemoryTools(memoryService))`——与既有内置工具同一行挂一个，不加新机制、不加新审计表

**Checkpoint**: 记忆工具出现在"按 Agent 工具清单过滤"的结果里；调用后长期记忆本体真被改写、成败都落 `tool_invocations`

---

## Phase 6: User Story 4 - 记忆进得了系统提示，且不重复注入历史 (Priority: P2)

**Goal**: 组装器的记忆段改由门面供给；未装配时组装结果与第17节基线逐字一致

**Independent Test**: `mvn test -pl fourfeetcat-core -am -Dtest='PromptBuilderTest'`

### Tests（harness 先行）

- [x] T032 [US4] `fourfeetcat-core/src/test/java/org/fourfeetcat/core/react/PromptBuilderTest.java`：**跨节触碰**——既有断言随接缝调整（构造参数由函数改为门面替身）；补两条回归：① 门面返回非空 ⇒ 记忆段作为独立 system 消息出现 ② 门面返回空白或未装配 ⇒ **该段整体跳过**，消息序列与第17节基线一致（会话历史只出现一次）

### Implementation for User Story 4

- [x] T033 [US4] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/react/PromptBuilder.java`：**跨节触碰**——第二构造参数与字段由 `Function<Profile, String>` 改为 `MemoryService`；`build` 里 `memoryService != null` 时取 `buildContext(session)`，**非空白才**作为独立 system 消息插入（在系统文本消息之后、历史消息之前）；单参构造保留（内部转调 `null`）；javadoc 写明"未装配门面时该段留空、逐字兼容第17节"
- [x] T034 [US4] `fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/AgentRuntimeConfiguration.java`（**同文件，须在 T022/T031 之后**）：`promptBuilder(...)` 参数加门面并传给组装器；Bean 的 javadoc 由"长期记忆未启用"订正为"记忆段由门面供给"

**Checkpoint**: 一次组装里会话历史只出现一份；记忆段与历史段各注入一次

---

## Phase 7: 跨节登记与收尾

**Purpose**: 登记不做的事、核对交付物、跑门禁、出报告

- [ ] T035 **跨节登记（本节不实现、验收时不勾选）**：① 按 Agent 隔离（一个 Agent 一份、`agents/<name>/MEMORY.md`、作用域键入库）——对账结论：参考实现把它放在后续的"Agent 专属记忆"节；② 跨档统一检索大小写——登记为后续修订；③ 查长期记忆的 Web 端点（归 Web Service 节）；④ 自动提炼/自动抽取、记忆压缩、语义与向量检索、知识图谱后端、情景记忆、记忆 CRUD（课件"有几样先别做"）
- [x] T036 `mvn clean verify` 全绿（Spotless/GJF + PMD7 + Checkstyle(google_checks) + SpotBugs+FindSecBugs 四道门禁一起过，SpotBugs findings 应为 0）
- [x] T037 前序节回归：`fourfeetcat-core` / `provider` / `storage` / `cli` / `channel-cli` / `boot` 既有全部测试绿（跨节契约证据；特别确认接缝适配后的 `PromptBuilderTest` 与 boot 上下文测试未被打断）
- [x] T038 H4 六条全局不变量逐条自查：① 涉外 IO 首行过沙箱校验——**本节唯一出网处是外部服务档的后端调用**，它不经工具执行路径，该判断在实现注释与报告里显式标注（plan 的 Complexity Tracking 有记录）② 记忆工具执行成败都落 `tool_invocations`（零新增审计代码，靠既有 `ToolExecutor` 路径）③ grep 无明文 key（`memory.mem0.*` 只走环境变量占位）④ `session_id` 只在 `SessionManager` 内拼接（本节未触碰）⑤ 无 Reactor/`CompletableFuture`/自建线程池 ⑥ 无 Spring AI 自动工具执行路径（记忆工具经 `registerAnnotated` → 既有注册管道）⑦ **只读初始设定文件被写入的次数为 0**（FR-016 / SC-008）：grep 全节的写入路径，确认没有任何代码路径写 `.fourfeetcat/USER.md`（`USER.md` 只被启动信息加载器读），并把 grep 命令与结果贴进报告
- [x] T039 交付物存在性核对：门面接口 / 分区枚举 / 后端接口 / 四档实现 / 门面实现 / 模块标记类 / 记忆工具 / `MemoryEntry` / `MemoryEntryRepository` / 双轨 V20 迁移 + **7 个本节新增测试类**（前序节 `PromptBuilderTest` 随接缝调整，另计）；多出项声明（均经主公追认）：`InMemoryMemoryStore`、`MemoryModule`、`MemoryEntryRepositoryTest`。**同时核对文档三处无需改动**：内置工具数在本节后正好 9 个（实测 `@Tool` 方法计数 9），README / 官网 / 设计文档的"内置九个"表述仍然正确（登记为核对结论，不是改动项）
- [x] T040 节级验收报告 + 变更总结（改动点 / 重点 review 清单 / 如何验证），并列出课件"五、做完怎么验"的**剩余人工项**（三档切换体感、外部服务档真连、真模型全链路、只读设定文件未被写、两条启动报错文案）
- [x] T041 **（实施期新增，经主公裁决）跨节触碰**：`PlainTextResultConverter` 由 `fourfeetcat-tool` 的 registry 包**上移到 `fourfeetcat-core` 的 tool 包**——记忆工具在 memory 模块，拿不到 tool 模块的这个转换器，而九个内置工具必须同一套返回形态。上移只依赖 Spring AI 的转换接口（core 已有该依赖），两边共用一份；tool 模块内四处 `@Tool` 所在类与一处测试的 import 同步改（机械改动，行为零变化）。触发的是软门禁（改前序节公开类型的 FQCN），处置经主公裁决为"上移到 core"

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**：无依赖，先做
- **Phase 2 Foundational**：依赖 Setup；**阻塞全部 user story**
- **Phase 3 US1**：依赖 Foundational（要用门面与后端接口）
- **Phase 4 US2**：依赖 US1 的四档实现（专项测试对它们下断言）
- **Phase 5 US3**：依赖 US1（工具只认门面）
- **Phase 6 US4**：依赖 Phase 2（门面类型）；与 US3 只共享 boot 的同一个装配文件，按 T022 → T031 → T034 顺序改
- **Phase 7 收尾**：依赖全部完成

### Within Each User Story

- 测试与对应实现**同批落地**（本仓的"harness 先行"落在"测试任务排在前、但与被测类同批编译"）
- 接口 → 实现；单档实现 → 门面实现 → 装配

### Parallel Opportunities

- T003~T005（三个 pom/yaml 文件互不相干）
- T006~T013（契约、枚举、实体、仓储、两个迁移脚本，七个不同文件）
- T016~T019（四档实现，四个不同文件）
- T025~T028（四个测试类）
- T023（排除清单）与其它实现任务互不相干

### 同一文件的多任务（不可并行，须按序）

`fourfeetcat-boot/.../AgentRuntimeConfiguration.java`：T022（store/service Bean）→ T031（工具注册）→ T034（组装器 Bean）。

---

## Implementation Strategy

### MVP First

Phase 1 → Phase 2 → Phase 3（US1）即可独立验收：三档后端装配到同一个门面、`memory.backend` 一行切换、契约测试与门面测试全绿。

### Incremental Delivery

US1（可插拔骨架）→ US2（分区语义专项守卫）→ US3（Agent 能记能查）→ US4（记忆真进系统提示）→ 收尾。

### 逐任务门禁

- **写前**：涉及第三方 API 的任务（T001/T018/T030）先核实方法存在；核不到即软报
- **写中**：只创建交付物点名的对外概念；已定字面量逐字保真（分区标题、配置键、工具名、方法名）；异常不吞（catch 必落日志或上抛）
- **写后**：实现与测试一起落地，跑该模块测试，红了当场修

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖
- 每个 user story 必须能独立验收
- 提交由主公决定，本节全程不自动 commit / push / 跑 package.sh
