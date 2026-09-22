---
description: "Task list for CLI 入口层与会话持久化 (Lesson 18) implementation"
---

# Tasks: CLI 入口层与会话持久化（第18节）

**Input**: Design documents from `/specs/003-cli-session/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/java-contracts.md, quickstart.md

**Tests**: harness 承载验收（spec SC-006）——测试任务先于/伴随对应实现任务，课件关键回归测试断言逐条保真、方法名英文 + `@DisplayName` 保留课件原文。

**Organization**: 按 User Story 分组（spec：US1 终端交互 P1、US2 会话地基 P1、US3 轻重分流 P2、US4 容器扫描 P2、US5 其余命令 P3）。**Phase 3/4 的顺序说明**：US1 与 US2 同为 P1，但 `chat` 依赖会话层（`getOrCreate`），故按依赖顺序先落 US2。

> **编号修订（2026-09-21，`/speckit-analyze` 后经主公准加）**：补入 4 个 Picocli 分组命令类（`ProfileCommand` / `ProviderCommand` / `ToolCommand` / `SessionCommand`）——两段命令名（`profile list` 等）在 Picocli 必须经分组节点，否则 12 个子命令注册不出来。子命令总数仍是 12；分组节点自身无行为。本文件因此重编号。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- 含确切文件路径；路径以 `fourfeetcat-<module>/src/...` 表达

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖与基线就位（本节零新增第三方依赖，只有模块内既有依赖的声明）

- [x] T001 H3 硬门禁：`mvn -o dependency:tree -pl fourfeetcat-cli` 与 `-pl fourfeetcat-storage` 确认 `info.picocli:picocli:4.7.7` 与 `com.fasterxml.jackson.core:jackson-databind:2.21.4` 在锁定 BOM 内解析成功——核不到立即停下软报，不得换依赖自行发挥
- [x] T002 基线核对：`mvn test` 确认第16/17节全部测试绿（作为本节起点的回归基线）
- [x] T003 [P] `fourfeetcat-cli/pom.xml`：加 `info.picocli:picocli`、`fourfeetcat-channel-cli`、`fourfeetcat-storage`、`org.springframework.boot:spring-boot`（版本全由父 pom 托管，**不写 version**）
- [x] T004 [P] `fourfeetcat-storage/pom.xml`：直依 `com.fasterxml.jackson.core:jackson-databind`（按仓库既有口径"直依声明、不靠传递"）
- [x] T005 [P] `fourfeetcat-channel-cli/pom.xml`：加 `org.springframework.boot:spring-boot-starter-test`（test 作用域，`CliChannelTest` 用）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 会话契约、建表脚本、持久化实现与运行装配（依赖倒置落点）

**⚠️ CRITICAL**: 本阶段完成前任何 User Story 都不具备可跑形态

- [x] T006 [P] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/session/SessionManager.java`：扩容为 `getOrCreate(channel,user,profileName)` / `get(sessionId)`（返回 `Optional<Session>`）/ `save(session)`，**去掉**第17节为单一抽象方法加的 `@FunctionalInterface` 与其注释（课件点名的改造点）
- [x] T007 [P] `fourfeetcat-storage/src/main/resources/db/migration/sqlite/V18__sessions.sql`：`sessions` 表 + `idx_sessions_status`，逐字摘自 `docs/class/schema.sql` 的 sessions 段，时间戳按 sqlite 口径落 TEXT
- [x] T008 [P] `fourfeetcat-storage/src/main/resources/db/migration/postgresql/V18__sessions.sql`：同版本号、同列名同约束，时间戳落原生 `TIMESTAMP`（对齐 V17 双轨写法）
- [x] T009 [P] `fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/SessionEntity.java`：JPA 实体，列名与 V18 逐字一致；`@Id String sessionId`（**不用** `@GeneratedValue`）
- [x] T010 [P] `fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/SessionRepository.java`：`JpaRepository<SessionEntity, String>` + `findAllByOrderByLastActiveAtDesc()`
- [x] T011 [P] `fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/SessionMessagesJson.java`：包内私有，Jackson 树模型实现内存态消息 ↔ 一列 JSON 往返（格式见 data-model.md §2；**未知 type 抛异常，不静默丢弃**）
- [x] T012 `fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/JpaSessionManager.java`：`@Component` 实现 `SessionManager`；`session_id` 拼接的**唯一实现处**（幂等 + 三分量隔离）；`save` 落盘并刷新 `last_active_at`、保留首次 `created_at`
- [x] T013 `fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/AgentRuntimeConfiguration.java`：装配 `ProfileRegistry`（`ProfileLoader` 读工作区 `profiles/`，已知 provider 名取自全局层）→ `ContextLoader(workspaceRoot)` → `PromptBuilder(contextLoader)` → `ToolTable`（**包内私有空实现**：空名单返回空表、非空名单清晰报错）→ `ToolExecutor(toolTable, ToolInvocationRecorder)` → `ReActLoop` → `AgentService`；`LlmCaller`/`LlmCallRecorder`/`ToolInvocationRecorder`/`SessionManager` 四个 Bean 全部复用既有实现

**Checkpoint**: 契约与实现就位，`mvn test -pl fourfeetcat-storage` 可独立跑

---

## Phase 3: User Story 2 - 对话历史跨重启接得上、换渠道/换人/换 Agent 互不串台 (Priority: P1)

**Goal**: 会话落库：同一三元组永远同一条（幂等）、三分量任一不同即不同条、重启后历史还在、消息往返保真

**Independent Test**: `mvn test -pl fourfeetcat-storage`，`SessionManagerTest` + `SessionRepositoryTest` 绿（临时目录文件库 + 手工脚本建表，不碰网络）

### Tests (harness 先行)

- [x] T014 [US2] `fourfeetcat-storage/src/test/java/org/fourfeetcat/storage/SessionManagerTest.java`：课件关键回归测试逐条落地——同三元组两次 `getOrCreate` 同一会话（`getOrCreate_sameTriple_returnsSameSession`，译自课件中文名，原文进 `@DisplayName`）；`channel`/`user`/`profileName` 任一不同即不同会话（`getOrCreate_differentUserOrProfile_isDifferentSession`）；幂等落库恰一行（`getOrCreate_calledTwice_persistsExactlyOneRow`）
- [x] T015 [US2] `fourfeetcat-storage/src/test/java/org/fourfeetcat/storage/SessionRepositoryTest.java`：手工脚本建表后能存能读（`scriptBuiltTable_savesAndReads`）；`messages_json` 往返后消息完整——条数/顺序/toolCall id 与参数逐字保真（`messagesJson_roundTrip_preservesAllMessages`）；模拟重启新建上下文重查历史还在（`reopenedContext_historyStillPresent`）

### Implementation

- [x] T016 [US2] 按 T014/T015 红绿补齐/校准 `SessionEntity`、`SessionRepository`、`SessionMessagesJson`、`JpaSessionManager`（Phase 2 已建，此处只做测试暴露出的修正），跑 `mvn test -pl fourfeetcat-storage` 转绿

**Checkpoint**: US2 独立可测——会话幂等、隔离、持久化、往返保真四件事被测试永久钉死

---

## Phase 4: User Story 1 - 在终端里跟自己搭的 Agent 说上话 (Priority: P1) 🎯 MVP

**Goal**: `fourfeetcat chat` 进入交互，逐行交给引擎、打印答复、`/quit` 退出；`--profile` 可指定 Agent

**Independent Test**: `mvn test -pl fourfeetcat-channel-cli`（`CliChannelTest` 用替身引擎 + 注入流）；进程级交互见 quickstart §5.4 人工项

### Tests (harness 先行)

- [x] T017 [P] [US1] `fourfeetcat-channel-cli/src/test/java/org/fourfeetcat/channel/cli/CliChannelTest.java`：一行输入 → 答复被打印到注入的输出流；`/quit` → 循环结束（引擎不再被调用）；空行 → 跳过、不交给引擎

### Implementation

- [x] T018 [US1] `fourfeetcat-channel-cli/src/main/java/org/fourfeetcat/channel/cli/CliChannel.java`：`run(profileName)` 取/建会话（`channel="cli"`，用户取当前系统用户）→ 循环 读一行 → 空行跳过 → `/quit` 退出 → `agentService.process(session, line)` → 打印答复；流可注入（测试用）。**自身零 Agent 智能**
- [x] T019 [P] [US1] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/Workspace.java`：包内私有——工作区根路径解析（`FOURFEETCAT_ROOT` 优先，缺省 `.fourfeetcat`）+ init 目录/模板常量
- [x] T020 [US1] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/FourFeetCatCli.java`：Picocli 根命令（`mixinStandardHelpOptions`、注册全部子命令）+ 引擎工厂 `Function<WebApplicationType, ConfigurableApplicationContext>`（懒启动、同进程只启一次、**包内可见**）；无子命令时打印用法
- [x] T021 [US1] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/ChatCommand.java`：`--profile`（默认 `default`）→ 取无 Web 容器 → `CliChannel.run(profileName)`
- [x] T022 [US1] `fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/FourFeetCatApplication.java`：`main` 改为"确保工作区根目录存在（既有行为保留）→ 构造 `FourFeetCatCli`（引擎工厂以本类为配置类、按 `WebApplicationType` 起动）→ `new CommandLine(...).execute(args)` → 交回退出码"；三件套注解**不动**

**Checkpoint**: US1 可跑——`chat` 走通一轮问答，答复能打出来

---

## Phase 5: User Story 3 - 三条命令按轻重分流，看一眼就退的命令秒回 (Priority: P2)

**Goal**: 轻命令直接用标准文件 API、不打开容器；重命令才打开容器

**Independent Test**: `time fourfeetcat profile list` 秒回且输出无容器启动日志；`fourfeetcat chat/serve/gateway` 有启动日志（quickstart §5.1）

### Implementation

- [x] T023 [P] [US3] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/CommandErrors.java`：包内私有统一报错出口（工作区不存在、Agent 不存在、配置文件缺失一律清晰报错、非零退出码、不抛栈）
- [x] T024 [P] [US3] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/ProviderListCommand.java`：读**打包内**全局层声明（classpath 上的 `application.yaml` 的 `fourfeetcat.providers`），打印 provider 名与 base-url，**永不打印 key**
- [x] T025 [P] [US3] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/ToolListCommand.java`：输出"当前无可用工具"（工具注册归第20节，届时只换数据源、命令不改）
- [x] T026 [P] [US3] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/ProfileListCommand.java`：列工作区 `profiles/` 下的配置文件（课件骨架同构：`Files.list` 逐个打印文件名）
- [x] T027 [P] [US3] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/ProfileCreateCommand.java`：写模板到 `profiles/<name>.yaml`；已存在则报错**不覆盖**
- [x] T028 [P] [US3] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/ProfileShowCommand.java`：打印该配置文件内容；不存在则清晰报错
- [x] T029 [P] [US3] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/ProfileDeleteCommand.java`：删除该配置文件；不存在则清晰报错
- [x] T030 [US3] 三个分组命令节点 `ProfileCommand` / `ProviderCommand` / `ToolCommand`（`fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/`）：`@Command(name="profile|provider|tool", subcommands={...})`，自身无语义、无子命令时打印用法（Picocli 两段命令名的必需节点）
- [x] T031 [US3] 分流自检：`mvn test` + `grep -n "engine(" fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/{InitCommand,Profile*,ProviderListCommand,ToolListCommand}.java` 应无命中（轻命令路径上不取容器）

**Checkpoint**: 轻命令零容器开销；重命令才付启动成本

---

## Phase 6: User Story 4 - 重命令打开容器时跨模块的数据访问部件必须被扫到 (Priority: P2)

**Goal**: 课件"坑四"被显式钉死：容器以**唯一**应用配置类起动，三件套扫描声明覆盖全部业务包

**Independent Test**: `java -jar fourfeetcat-boot/target/*.jar status 2>&1 | grep -i "repository interfaces"` → 数字 > 0

### Implementation

- [x] T032 [US4] 复核 `fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/FourFeetCatApplication.java` 的三件套（`@SpringBootApplication(scanBasePackages="org.fourfeetcat")` + `@EnableJpaRepositories(basePackages="org.fourfeetcat")` + `@EntityScan(basePackages="org.fourfeetcat")`）**逐字保留**；确认全工程无第二个 `@SpringBootApplication`：`grep -rn "@SpringBootApplication" --include=*.java fourfeetcat-*/src` 应只有一处
- [x] T033 [US4] 端到端确认跨模块 JPA 真生效：跑 `chat` 落一条会话 → 再跑 `session list` 读到它（证明仓储与实体都被扫到、`sessions` 表真的被 Flyway 建出来了）

**Checkpoint**: "启动即失败"类故障被两道证据挡住（启动日志接口数 + 端到端读写）

---

## Phase 7: User Story 5 - 其余命令把"看情况"和"起项目"补齐 (Priority: P3)

**Goal**: `init` / `status` / `session list` / `serve` / `gateway` 到位，12 个子命令齐全

**Independent Test**: `for c in init status chat serve gateway provider session tool profile; do java -jar ... $c --help; done` 全部正常（quickstart §5.2）

### Implementation

- [x] T034 [P] [US5] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/InitCommand.java`：建工作区 `profiles/`（含 `default.yaml` 模板，provider 名用全局层已声明的 `deepseek`）、`memory/MEMORY.md`、`logs/`、`mcp_servers.yaml`、`AGENTS.md`/`SOUL.md`/`USER.md`；**不建** `agents/`、`skills/`；重复执行**幂等**（已存在文件不覆盖）
- [x] T035 [P] [US5] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/StatusCommand.java`：打印工作区根、profile 文件清单、provider 名与 base-url、库路径（走无 Web 容器）
- [x] T036 [P] [US5] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/SessionListCommand.java`：从 `SessionRepository` 按最后活跃时间倒序列出（走无 Web 容器）；库为空时输出"无"，不报错
- [x] T037 [P] [US5] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/SessionCommand.java`：`session` 分组命令节点（`@Command(name="session", subcommands={SessionListCommand.class})`）
- [x] T038 [P] [US5] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/ServeCommand.java`：`--port`（默认 8080）→ 取 Servlet 容器并常驻（REST 端点内容归第26节）
- [x] T039 [P] [US5] `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/GatewayCommand.java`：取无 Web 容器（宿主通道）后阻塞主线程保活（守护进程语义；**不引异步类型、不自建线程池**，宪法原则七）

**Checkpoint**: 12 个子命令全部可跑、`--help` 齐全

---

## Phase 8: Polish & Cross-Cutting Concerns

- [x] T040 `mvn clean verify` 全绿（Spotless/GJF + PMD7 + Checkstyle(google_checks) + SpotBugs+FindSecBugs 四道静态门禁一起过）
- [x] T041 前序节回归：`fourfeetcat-core`/`provider`/`storage` 既有全部测试绿（跨节契约证据）
- [x] T042 H4 六条全局不变量逐条自查（涉外 IO 首行过 `Sandbox.enforce`＜本节不新增对外 IO，只有工作区自身的文件操作＞；审计表写入不回退；无明文 key；`session_id` 只在 `SessionManager` 内拼接；无 Reactor/`CompletableFuture`/自建线程池；无 Spring AI 自动工具执行路径）
- [x] T043 交付物存在性核对与**继承条款对号**：`ls`/`grep` 逐个对号（13 个命令类含 4 个分组节点、`CliChannel`、四个会话类、双轨 V18 脚本、两个 harness 测试类）；FR-015 前半（非法 Profile 记日志跳过、不阻断启动）由既有 `ProfileLoader` + `ProfileLoaderTest` 承担，报告里显式引用；FR-010 / SC-007 走 quickstart §5.5 人工项
- [x] T044 按 quickstart.md 跑判卷命令块，产出验收报告（含剩余人工项清单与已知偏离）与给 reviewer 的变更总结

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始
- **Foundational (Phase 2)**: 依赖 Setup（T003~T005 的 pom 声明）；**阻塞所有 User Story**
- **US2 (Phase 3)**: 依赖 Foundational——US1 的 `getOrCreate` 依赖它
- **US1 (Phase 4)**: 依赖 Phase 2 的 T006（契约）+ T013（装配）；可先用 Phase 3 的会话实现
- **US3 (Phase 5)**: 只依赖 Phase 1（轻命令互不依赖容器）
- **US4 (Phase 6)**: 依赖 US1（T022 的引擎工厂）与 US2（端到端读写）
- **US5 (Phase 7)**: 依赖 Phase 2（`StatusCommand`/`SessionListCommand` 用容器；`InitCommand`/`ServeCommand`/`GatewayCommand` 用 `Workspace`/引擎工厂，故依赖 T019/T020）
- **Polish (Phase 8)**: 依赖全部 User Story

### Within Each Story

- 测试（harness）先写、先红，再补实现转绿
- 模型/契约 → 实现 → 端到端校验
- 一个 story 绿了再进下一个（一环境一动作）

### Parallel Opportunities

- Phase 1：T003/T004/T005 三个 pom 互相独立（[P]）
- Phase 2：T006~T011 六项互相独立（[P]）；T012 依赖 T009/T010/T011；T013 依赖 T006
- Phase 3：T014/T015 两个测试类互相独立（[P]）
- Phase 5：T023~T029 七个文件互相独立（[P]）；T030 依赖它们
- Phase 7：T034~T039 六个文件互相独立（[P]，T037 依赖 T036 的类存在与否不影响并行创建）
- 跨 story：US3 的轻命令与 US1 的 chat 分属不同文件，Phase 4 与 Phase 5 可并行

---

## Parallel Example: Phase 2

```bash
# 六个互相独立的文件可同时落地：
Task: "core SessionManager 扩容三方法（T006）"
Task: "sqlite V18__sessions.sql（T007）"
Task: "postgresql V18__sessions.sql（T008）"
Task: "storage SessionEntity.java（T009）"
Task: "storage SessionRepository.java（T010）"
Task: "storage SessionMessagesJson.java（T011）"
```

## Parallel Example: Phase 7

```bash
Task: "InitCommand（T034）"  Task: "StatusCommand（T035）"   Task: "SessionListCommand（T036）"
Task: "SessionCommand 分组（T037）" Task: "ServeCommand（T038）" Task: "GatewayCommand（T039）"
```

---

## Implementation Strategy

### MVP First（US2 + US1）

1. Phase 1 Setup → 2. Phase 2 Foundational（**阻塞项**）→ 3. Phase 3 US2（harness 绿）→ 4. Phase 4 US1 → **停下验证**：`chat` 走通一轮问答 → 5. 这就是 Demo 一的对话版

### Incremental Delivery

1. Setup + Foundational → 地基就位
2. US2 → 会话地基绿（自动判卷）
3. US1 → `chat` 可交互（Demo 一对话版）
4. US3 → 分流生效（轻命令秒回）
5. US4 → 跨模块 JPA 被两道证据钉死
6. US5 → 12 命令齐全
7. Polish → 四道静态门禁 + 报告

---

## Notes

- [P] = 不同文件、无未完成依赖
- 课件中文测试方法名一律译成语义等价的英文名落地，课件原文进 `@DisplayName` 保留对号
- 交付物**多出项**（须在 T044 报告中声明）：4 个分组命令节点（Picocli 两段命令名的必需节点，主公已准加）、`CliChannelTest`（交互壳守卫）、`SessionMessagesJson`（编解码，包内私有）、`AgentRuntimeConfiguration`（装配，chat 要真跑通必需）、`CommandErrors`/`Workspace`（包内私有辅助）、`SessionRepository.findAllByOrderByLastActiveAtDesc`（`session list` 用）
- 交付物**改造点**（课件点名，非软门禁）：`SessionManager` 接口扩容并去掉 `@FunctionalInterface`
- 前序节遗留、**本节不动**、只记录：第16节 V16 缺 postgresql 双生脚本；实体侧时间戳用 `String` 与 postgres 轨 `TIMESTAMP` 不匹配（research.md D8）
- **已排期的收敛项**（主公已裁）：宪法原则四的 `agents/<name>/` 目录形态 + Skill 软连接归 025~029 节；本节及前序节用 `profiles/` 过渡，不改加载口径
