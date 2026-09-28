---

description: "Task list for 定时任务——第三种触发源（第25节）implementation"

---

# Tasks: 定时任务——第三种触发源（第25节）

**Input**: Design documents from `/specs/008-agent-scheduler/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/java-contracts.md, quickstart.md

**Tests**: harness 承载验收（spec SC-002~SC-006、SC-009）——测试任务与对应实现**同批落地**（同一实现类被三个故事共用，见"组织说明"）；课件 harness 的关键断言逐条保真，方法名英文 + `@DisplayName` 保留课件中文原文。

**Organization**: 按 User Story 分组（spec：US1 到点自动跑一轮 P1、US2 失败隔离且留痕 P1、US3 上一次没跑完别叠着上 P2）。

**组织说明（一条重要偏离）**：三个 user story 的行为底座是**同一个 `runOnce`**（US1 的"到点交给人推入口"、US2 的"失败不外抛 + 锁必释放"、US3 的"拿不到锁就跳过"落在同一个方法的进入/退出两侧）。因此实现类**一次性落地**（T007），三个故事各自由**自己那批 harness 用例**独立验证与独立验收——按故事拆实现任务只会造成同一文件反复改，不产生任何独立性收益。

**范围说明（主公裁决，开工前四处）**：
- **cron 用框架原生 6 段**（秒 分 时 日 月 周），不做 5 段兼容转换；5 段写法在注册期被硬拒并跳过该条（research D1，spec Clarifications）。
- **`Profile.schedules` 保持原结构**（原始条目列表），强类型解析放注册期，坏条目只跳自己、不牵连整份配置（research D6）。
- **标识可选、缺省派生** `profileName#序号`；唯一性口径取**进程内全体已注册任务**范围（比 spec 初稿的"Agent 内唯一"更严，防跨 Agent 重名静默互挡）（research D7，spec FR-004）。
- **定时注册与运行模式无关**（引擎装配即注册），不新增"要不要调度"的开关（spec Clarifications）。

**第16~24节已交付、本节只消费不改的件**（不得出现在本节 diff 里）：`Profile` 记录与 `ProfileLoader`（含其 `schedules` 字段与既有测试夹具）、`AgentService.process`、`SessionManager` 接口与 `JpaSessionManager`、`ReActLoop` / `ToolExecutor` 与两条审计写入路径、沙箱与四个工具的调用位、全部数据表与迁移脚本。

**同构基准**：第24节任务清单里指向的 OryxOS 参考实现（`/d/oneByDay/ai_code/oryxos`）**本机已不存在**（目录不存在，无 `*Schedul*` 命中）。故本节同构基准只有一处——**课件第 25 节的骨架代码**（`registerAll` / `runOnce` / `taskLocks.computeIfAbsent` / `lockFor` 的形态），其余按本仓既有形态（record 值对象、装配全在 boot）落地。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- 含确切文件路径；路径以 `fourfeetcat-<module>/src/...` 表达

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖与 API 可得性（H3 门禁）、回归基线

- [x] T001 H3 硬门禁复核并固化证据：本节要用的第三方 API 已在动笔前实测（research D1/D2/D10）——`CronTrigger(String, ZoneId)` 存在且**只收 6 段**（5 段抛 `IllegalArgumentException`）、`getExpression()` 可取原始表达式、**无时区读取方法且 `equals` 不比时区**、`nextExecution(TriggerContext)` 可算下一次执行时刻、时区非法抛 `ZoneRulesException`、`TaskScheduler.schedule(Runnable, Trigger)` 与 `ThreadPoolTaskScheduler.setPoolSize(int)` 均在；`spring-context:6.2.19` 已在 `fourfeetcat-core` 编译期 classpath 上（`mvn -o -pl fourfeetcat-core dependency:tree` 可证）。**核不到即停下软报，不得换依赖自行发挥**
- [x] T002 基线核对：`mvn clean test` 确认第16~24节全部测试绿（本节起点的回归基线；含 main 上新提交的 CLI 命令树回归测试）
- [x] T003 [P] 核对课件骨架的同构点（**只读不改**）：`registerAll` 的循环形态、`runOnce(Profile, ScheduleConfig)` 的入参、`taskLocks.computeIfAbsent(...)` + `tryLock` + `finally unlock` 的写法、`sessionManager.getOrCreate("scheduler", "scheduler", profileName)` 的三元组——逐条对照，实现时按本仓命名与包结构落地，不逐字照搬 `@Component` / `@PostConstruct`（research D5）

**Checkpoint**: 依赖与 API 全部可得；基线全绿；同构点清单确认

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 值对象、注册表遍历、依赖声明——US1 的实现建在这三件之上

**⚠️ CRITICAL**: 本阶段完成前，`AgentScheduler` 无配置可读、无遍历入口可用

- [x] T004 [P] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/schedule/ScheduleConfig.java`：record `ScheduleConfig(String id, String cron, String zone, String message)`；javadoc 逐字段写明「谁消费 / 什么口径」：`cron` 是 **6 段**（秒 分 时 日 月 周）、`zone` 为空则按服务器系统时区、`message` 是到点发给 Agent 的话、`id` 是执行权的分配维度（进程内唯一）；附一条 `fromEntry(profileName, index, Map)` 之类的**包内可见**解析入口（缺 `cron` / `message` 即抛可读异常，由调用方记为"该条非法"）
- [x] T005 [P] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/profile/ProfileRegistry.java`：**新增只读方法** `all()`（返回全部已加载 Profile 的不可变列表）；内部容器 `HashMap` → `LinkedHashMap`（注册顺序 = 加载顺序，重名裁决可复现，research D7）。既有 `find(String)` 与构造器签名/语义**一字不改**
- [x] T006 [P] `fourfeetcat-core/pom.xml`：显式声明 `org.springframework:spring-context`（**已在依赖树中**，版本由 Boot BOM 管；照第20节为 `jackson-databind` 立的先例加一条中文注释说明"此前靠 spring-ai-model 传递，即本节起 core 直接 import 它的类型"）（research D10）

**Checkpoint**: `ScheduleConfig` 可造、`ProfileRegistry.all()` 可用、core 能直接编译 `TaskScheduler`/`CronTrigger`

---

## Phase 3: User Story 1 - 到点 Agent 自己跑一轮 (Priority: P1) 🎯 MVP

**Goal**: 启动时扫一遍配置逐条注册，到点拼一条消息交给与 CLI/WEB 完全相同的入口；历次触发复用同一条定时专用会话；坏配置只跳自己

**Independent Test**: `mvn test -pl fourfeetcat-core -Dtest=AgentSchedulerTest -pl fourfeetcat-boot -Dtest=AgentSchedulerWiringTest`

### Tests（harness 先行）

- [x] T007 [US1] `fourfeetcat-core/src/test/java/org/fourfeetcat/core/schedule/AgentSchedulerTest.java`：US1 的四条守点——① `registration_carriesConfiguredCronAndZone`（`@DisplayName("注册时_CronTrigger带上了配置的cron和时区")`）：`ArgumentCaptor<Trigger>` 抓注册参数，`getExpression()` 断言 cron **逐字一致**，`nextExecution(空 TriggerContext)` 断言时区生效（`Asia/Shanghai` 09:00 → 次日 `01:00Z`；`America/New_York` 09:00 → 当日 `13:00Z`；空 `zone` → 系统时区）② `twoTriggers_shareSameSchedulerSession`（`@DisplayName("同一任务两次触发_拿到同一条会话")`）：`verify(sessionManager).getOrCreate("scheduler", "scheduler", "ops-agent")`，且两次拿到同一 `Session` ③ `noSchedules_registersNothing`（无定时配置的 Agent：`schedule` 零调用）④ `badEntries_areSkippedWithoutAffectingNeighbours`（SC-009：同一 Agent 下 5 段 cron / 不存在的时区 / 空 message 各一条 + 一条合法，断言只有合法那条被注册；`@DisplayName` 保留"坏配置被拒且不牵连邻居"语义）
  > 与 T008 同批落地（编译相互依赖，同第22/24节先例）

### Implementation for User Story 1

- [x] T008 [US1] `fourfeetcat-core/src/main/java/org/fourfeetcat/core/schedule/AgentScheduler.java`：构造收 `TaskScheduler`（**接口**，research D3）+ `ProfileRegistry` + `AgentService` + `SessionManager`；`registerAll()` **public**（boot 经 `initMethod` 反射调用）：遍历 `profileRegistry.all()`，逐条解析 `Profile.schedules()`（T004 的解析入口）→ 校验 → 进程内全体范围查重（重名记错误日志、跳过后来者并点名两条来源）→ `taskScheduler.schedule(() -> runOnce(profile, sc), new CronTrigger(cron, ZoneId.of(zone)))`（`zone` 为空则用系统时区）；`runOnce` **包内可见**（harness 直接调）；`lockFor(String)` **包内可见**（同款处理见 contracts 第一节）；异常路径：解析/注册期捕获 `RuntimeException` 记错误日志跳过该条，**不阻断启动**。javadoc 写清四个坑与 D4 的池容量下限，`ponytail:` 注释点明"进程内锁不是分布式锁"这个上限与升级路径
- [x] T009 [US1] 装配：`fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/AgentRuntimeConfiguration.java` 加两个 Bean——`ThreadPoolTaskScheduler taskScheduler()`（`setPoolSize(4)` + 线程名前缀，注释写明"下限是 2，见 research D4"）与 `@Bean(initMethod = "registerAll") AgentScheduler agentScheduler(...)`（依赖四个既有 Bean）；类注释补"第25节再加这两个"
- [x] T010 [US1] `fourfeetcat-boot/src/test/java/org/fourfeetcat/boot/AgentSchedulerWiringTest.java`：装配冒烟——静态初始化在 `${user.dir}/target/scheduler-smoke/profiles/` 写一份带"每秒触发"任务的 Agent 配置，`@SpringBootTest(properties = "FOURFEETCAT_ROOT=...")` 起真容器，用替身 `AgentService`，`Awaitility` 限时等到点，断言处理入口**真的**被调到（这是本节唯一无法由单测覆盖的接线点：装配漏了，单测照样全绿）；类注释点明这条教训
- [x] T011 [US1] 模块跑通：`mvn test -pl fourfeetcat-core -am` 与 `mvn test -pl fourfeetcat-boot -am` 全绿（T007/T008/T010 的验证点 + 既有测试未被打断）

**Checkpoint**: 配置驱动注册成立；到点真的走到同一个人推入口；坏配置只跳自己；会话身份固定为 `scheduler/scheduler/profileName`

---

## Phase 4: User Story 2 - 跑挂一次不塌台，且事后查得到 (Priority: P1)

**Goal**: 单次执行抛异常不外抛、不崩调度器、执行权必被释放；这一轮的模型/工具调用照旧落审计（零新增代码）

**Independent Test**: `mvn test -pl fourfeetcat-core -Dtest=AgentSchedulerTest`

### Tests for User Story 2

- [x] T012 [US2] `AgentSchedulerTest` 追加②：`processThrows_doesNotPropagateAndLockIsReleased`（`@DisplayName("任务抛异常_不外抛且锁必须被释放")`）——`when(agentService.process(any(), any())).thenThrow(...)`，`assertDoesNotThrow(() -> scheduler.runOnce(...))`；**再触发一次**并 `verify(agentService, times(2)).process(...)` 证明锁真的放了（课件原话：光断言"不抛异常"不够，"二进宫"才能抓住 `finally` 漏 `unlock` 的 bug）；另加一条"失败那一轮不影响**另一条**任务的下一次触发"（不同锁互不相干）

### Implementation for User Story 2

- [x] T013 [US2] 失败路径与审计口径核对：`git diff` 证明 `ReActLoop` / `ToolExecutor` / `LlmCallRecorder` / `ToolInvocationRecorder` **零改动**；grep 证明本节**未新增**任何落库调用、数据表、审计通路（宪法原则五、spec FR-009）；确认 `runOnce` 的 catch 只记日志（含任务标识与 Agent 名）且 `finally` 必释放锁

**Checkpoint**: "无人值守时出错会怎样"这条与触发本身一起交付完毕：不塌台、不死锁、账照记

---

## Phase 5: User Story 3 - 上一次没跑完，这一次别叠着上 (Priority: P2)

**Goal**: 执行权被占时本次触发直接跳过（不排队、不堆积）；不同任务互不影响；防重叠是进程内的

**Independent Test**: `mvn test -pl fourfeetcat-core -Dtest=AgentSchedulerTest`

### Tests for User Story 3

- [x] T014 [US3] `AgentSchedulerTest` 追加③：`earlierRunStillInFlight_thisTriggerIsSkipped`（`@DisplayName("上一次还没跑完_本次触发直接跳过")`）——`Lock lock = scheduler.lockFor("task-1"); lock.lock();` 占住执行权后 `scheduler.runOnce(profile, scheduleConfig("task-1"))`，断言 `verify(agentService, never()).process(any(), any())`（课件原文语义逐字保真）；再补一条"**不同**任务不受影响"（占住 task-1 的锁，task-2 的 `runOnce` 照常执行）

### Implementation for User Story 3

- [x] T015 [US3] 并发语义核对：确认 `taskLocks` 用 `ConcurrentMap` + `computeIfAbsent`（`lockFor` 与 `runOnce` **命中同一把锁**——这是 harness 用例成立的前提）；确认装配的池容量 ≥ 2（容量 1 会让不同任务互相排队，research D4）；grep 证明本节**未引入**任何选主 / 分布式锁 / 租约（spec FR-005）

**Checkpoint**: 重叠跳过可独立验证；"跳过判定在触发时点做出"这一语义由用例钉死

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T016 [P] `fourfeetcat-cli/src/main/resources/templates/profile.yaml`：加一段**注释**示例（`schedules` 四项 + 6 段 cron 口径 + 建议显式写 `zone` + 上一次没跑完会跳过 + 历次触发复用同一条定时会话），作为本节**用户可见配置说明的唯一落点**（纯注释，零行为变化）
- [x] T017 全量门禁：`mvn clean verify` 全绿（Spotless / PMD7 / Checkstyle / SpotBugs+FindSecBugs），贴关键输出
- [x] T018 前序节回归：全模块测试绿（跨节契约证据；无定时配置的 Agent 行为与第24节完全一致）
- [x] T019 H4 六条全局不变量逐条自查：①涉外 IO 首行过 `Sandbox.enforce`（本节不动调用位，定时触发的工具调用照旧受约束）②LLM 调用成败都落 `llm_calls`、工具执行成败都落 `tool_invocations`（本节零新增代码即满足）③grep 无明文 key ④`session_id` 只在 `SessionManager` 内拼接（本节只递三元组，grep 证明无第二处）⑤无 Reactor / `CompletableFuture` / 自建线程池 ⑥无 Spring AI 自动工具执行路径
- [x] T020 课件"本节交付物"逐项存在性核对（`ls`/`grep` 输出作证据）
- [x] T021 登记已知文档-代码差（三处，research 末节）：①cron 方言与课件 5 段示例不一致（配置说明已按 6 段写）②"Profile 新增 `schedules` 字段"其实第16节就已建，本节交付的是消费方 ③课件骨架的 `@Component` / `@PostConstruct` 与本仓"core 零 Spring 注解、装配全在 boot"的形态不同（resolve D5）。**不静默、不自行改设计文档**
- [x] T022 验收报告 `specs/008-agent-scheduler/acceptance-report.md`：六项证据 DoD + 课件"做完怎么验"剩余人工项清单（真实到点触发 / 改 cron 不重编译 / 坏配置响亮可辨 / 配置说明核对）+ 变更总结（改动点 / 重点 review 清单 / 如何验证）

---

## 课件"本节交付物"↔ 任务映射（固定停点比对表）

| 课件交付物 | 状态 | 承载任务 |
|-----------|------|---------|
| `AgentScheduler`（`registerAll` + `runOnce` + 按任务 id 的 `ReentrantLock` 表） | **本节交付** | T008（实现）、T009（装配） |
| `ScheduleConfig`（id / cron / zone / message） | **本节交付** | T004 |
| `AgentSchedulerTest` | **本节交付** | T007（US1 四守点）、T012（US2 二进宫）、T014（US3 跳过） |
| 配置：Profile 新增 `schedules` 字段 | **第16节已建**（`List<Map<String,Object>>`，注释即标"第25/28节"）；**本节交付的是消费方** | T004（解析入口）、T005（`all()` 遍历）、T008（注册期消费）；Profile 与 ProfileLoader 零 diff 由 T013 的 `git diff` 作证 |
| 约定：会话身份固定 `("scheduler","scheduler",profileName)` | **本节交付** | T008（实现）、T007 用例② |
| 约定：失败只记日志、不崩调度器 | **本节交付** | T008（catch + finally）、T012 用例 |

**差**：

- **多**（课件清单外，各附理由）：
  - **boot 装配两个 Bean + `AgentSchedulerWiringTest`（T009/T010）**：课件骨架用 `@Component` 自动装配，本仓 core 零 Spring 注解、装配全在 boot（research D5）；装配冒烟是为"接线漏了单测照样全绿"这类历史缺陷补的守点。**不引入新概念**（两个 Bean 都是既有类型）。
  - **`ProfileRegistry.all()` + 内部容器改有序（T005）**：课件骨架的 `profileRegistry.all()` 需要一个遍历入口，该类目前只有 `find`。只读新增，不改既有成员。
  - **`profile.yaml` 模板注释（T016）**：spec 要求"配置说明写明 6 段 cron 口径"，这是它唯一用户可见的落点。
  - **SC-009 的坏配置用例（T007 ④）**：spec 加严项（FR-004 的进程内全局查重、FR-010 的坏条目隔离），课件未展开。
- **缺 / 未做**（有据，课件明写"先别做"）：多实例下的分布式协调（选主 / 分布式锁 / 租约）；任务失败后的自动重试与失败告警；运行时增删改定时任务的接口；任务状态与执行历史的持久化与查询（第28节）。全部在验收报告里登记为边界，**不静默**。

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始
- **Foundational (Phase 2)**: 依赖 Setup；**阻塞** US1（`ScheduleConfig` 与 `all()` 是注册逻辑的前提）
- **US1 (Phase 3)**: 依赖 Phase 2；产出 `AgentScheduler` 本体，是 US2 / US3 用例的前提
- **US2 (Phase 4)**: 依赖 US1（要对同一个 `runOnce` 施压）
- **US3 (Phase 5)**: 依赖 US1（要用 `lockFor` 占锁）
- **Polish (Phase 6)**: 依赖全部

### Within Each User Story

- 测试与实现**同批落地**（T007+T008 编译相互依赖，同第22/24节先例），落地后立刻跑模块测试，红了当场修，不攒到最后
- 三个故事共用同一个实现文件 → **不得并行**，按 P1 → P1 → P2 顺序推进（每个故事完成即可独立验收）
- 新增测试方法名必须是英文（驼峰或 snake_case），课件 harness 里给出的两个中文方法名译成语义等价的英文名，课件原文进 `@DisplayName` 保留对号

### Parallel Opportunities

```bash
# Phase 2 三件互不依赖（三个不同文件）
Task: "T004 ScheduleConfig"
Task: "T005 ProfileRegistry.all()"
Task: "T006 core pom 显式声明 spring-context"

# Phase 6 的文档件可并行
Task: "T016 profile.yaml 模板注释"
```

---

## Implementation Strategy

### MVP First

Phase 1 → Phase 2 → Phase 3（US1）：配置驱动注册 + 到点真的走同一个人推入口 + 会话身份固定 —— 这就是本节的 MVP（也是课件的核心验收）。**STOP and VALIDATE**：`mvn test -pl fourfeetcat-core -Dtest=AgentSchedulerTest` 加 `mvn test -pl fourfeetcat-boot -Dtest=AgentSchedulerWiringTest`。

### Incremental Delivery

1. Phase 2 → 值对象 / 遍历入口 / 依赖声明就位
2. US1 → 调度器本体 + 装配 + 装配冒烟 → 独立验证
3. US2 → 不掉链子的证据（二进宫 + 锁释放 + 审计零新增）
4. US3 → 不叠着跑的证据（跳过 + 任务间互不影响）
5. Phase 6 → 配置说明 + 全量门禁 + 六项证据验收报告

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖
- 本节**不新增**第三方依赖（`spring-context` 已在依赖树中）、**不新增**数据表、**不新增** HTTP 端点或 CLI 子命令、**不新增**全局配置键
- 未全绿不得宣称完成；不许删断言 / `@Disabled` / 放宽阈值；实现错就修实现，认为测试错则停下报告
- 不自动 commit / push / 跑 `package.sh`
