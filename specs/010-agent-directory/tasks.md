---

description: "Task list template for feature implementation"
---

# Tasks: 插件化 Agent——一个目录定义一个会自己跑的 Agent

**Input**: Design documents from `/specs/010-agent-directory/`

**Prerequisites**: plan.md (✓)、spec.md (✓)、research.md、data-model.md、contracts/profile-derivation.md

**Tests**: 本项目实现完成的定义是 `mvn clean verify` 全绿；课件"验收 harness"为硬门禁，故本节测试任务**必须**落地（不是可选）。harness 与对应实现**同工作单元落地、红了当场修**（课件关键回归测试原样保真，方法名译英文、课件原文进 `@DisplayName`）。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Path Conventions

多模块 Maven。本节全部落在 `fourfeetcat-core` 源码/测试 + `fourfeetcat-boot` 装配接线 + 运行时示例目录：

- 生产代码：`fourfeetcat-core/src/main/java/org/fourfeetcat/core/`
- 测试：`fourfeetcat-core/src/test/java/org/fourfeetcat/core/`
- 装配：`fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/`
- 示例 Agent：`.fourfeetcat/agents/daily-reconcile/`（gitignore 不随仓库提交）

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 本 feature 不新建项目/模块、不改 pom；仅确认构建工具链可用（记忆：mvn 不在 PATH、JAVA_HOME 默认 JDK 8，构建前须覆盖）

- [ ] T001 确认四个feetcat-core 模块在当前分支可编译、既有测试基线绿（首次构建前设置 PATH/mvn 与 JAVA_HOME=JDK21）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 供 US1/US2 共用的两个 core 重构——复用 Profile 派生的校验、让 ProfileRegistry 可变。**无这两步，任何 user story 都无法开始。**

- [ ] T002 [P] 在 `fourfeetcat-core/.../core/profile/ProfileLoader.java` 抽出一个包内可见的 `fromYamlMap(Path origin, Map<String,Object>)`（= toProfile + resolveEnvPlaceholders + provider 名可解析校验），把 `loadOne` 改为复用它（可观察行为不变：同异常、同消息、同跳过语义）
- [ ] T003 [P] 把 `fourfeetcat-core/.../core/profile/ProfileRegistry.java` 改为可变并发容器：持 `Collections.synchronizedMap(new LinkedHashMap<String,Profile>())`，新增 `register(Profile)` / `remove(String)` / `exists(String)`；`find`/`all` 保留；`register` 同名覆盖记 warn（顺序=注册顺序，25 节定时依赖）

**Checkpoint**: Foundation ready——ProfileLoader 可复用派生校验、ProfileRegistry 可变，US1/2/3 可并行进入。

---

## Phase 3: User Story 1 - 往目录丢一个 Agent，到点自己跑 (Priority: P1) ★ MVP

**Goal**: 扫 `.fourfeetcat/agents/<name>/` → `AgentLoader` 派生 Profile → 装配层注册进 ProfileRegistry；带 schedules 的由既有 `AgentScheduler.registerAll` 自动收敛定时。

**Independent Test**: `AgentLoaderTest`/`DeriveProfileTest`/`AgentScanRegisterTest` 全绿；手动往工作区丢一个目录，`profile list`/`GET /api/v1/profiles` 出现该 Agent。

### Tests for User Story 1（harness 先行，先红后绿，与实现同批落地）

> 本文件为 `fourfeetcat-core/src/test/java/org/fourfeetcat/core/profile/`

- [ ] T004 [US1] `AgentLoaderTest`：拆 `AGENT.md` frontmatter 与正文、认出 `scripts/` `skills/` `REFERENCE.md`（AgentLoader.detectResources）、缺 `name`/`provider` 报错点名
- [ ] T005 [US1] `DeriveProfileTest`：frontmatter 各字段正确映射到 Profile、`schedules` 原样带进（定时来自 Agent 的直接证据）
- [ ] T006 [US1] `AgentScanRegisterTest`：扫一个放 N 个 Agent 目录的目录 → ProfileRegistry 出现 N 个、带 schedules 的都进了 AgentScheduler（真装配/in-memory 双位）

### Implementation for User Story 1

- [ ] T007 [US1] 新增 `fourfeetcat-core/.../core/profile/AgentLoader.java`：`scan(Path)`/`deriveProfile(Path)`/`detectResources(Path)`（复用 T002 的 `fromYamlMap`），`AgentResources` 小 record 内嵌；坏目录记 error 跳过、单独派生抽错点名
- [ ] T008 [US1] 改 `fourfeetcat-boot/.../AgentRuntimeConfiguration.java` 的 `profileRegistry` Bean：空构造 → `ProfileLoader.load(profiles).forEach(register)` → `AgentLoader.scan(agents).forEach(register)` → 返回（派生 Agent 的 schedules 由 `initMethod=registerAll` 自动收敛）

**Checkpoint**: User Story 1 独立可测——一个目录派生一个 Agent、出现在列表、定时被捡走。

---

## Phase 4: User Story 2 - 运行时也能注册 Agent，校验与启动同规矩 (Priority: P2)

**Goal**: `AgentScheduler` 抽出可单独注册单个 Agent 的入口 + `scheduledTasks` 句柄表（第 30 节注销/更新前提）；`ProfileRegistry` 运行时 `register` 立即可见、非法配置报错与启动路径完全一致。

**Independent Test**: `ProfileRegistryRuntimeTest`/`AgentSchedulerRegisterTest` 全绿。

### Tests for User Story 2

- [ ] T009 [P] [US2] `ProfileRegistryRuntimeTest`（`fourfeetcat-core/.../core/profile/`）：`register` 后立即 `find` 可见；非法配置（provider 不可解析）报错类型与消息 == 启动路径（经 AgentLoader/ProfileLoader 同校验）
- [ ] T010 [P] [US2] `AgentSchedulerRegisterTest`（`fourfeetcat-core/.../core/schedule/`）：`registerProfile` 后 `scheduledTasks` 句柄表有句柄；cron/时区来自 Profile.schedules

### Implementation for User Story 2

- [ ] T011 [US2] 改 `fourfeetcat-core/.../core/schedule/AgentScheduler.java`：私有 `registerProfile(Profile,Set)` 重构为 `private doRegisterProfile(Profile,Set)`，新增 `public int registerProfile(Profile)`（传空 Set）；`registerAll` 改调 `doRegisterProfile`；新增字段 `Map<String,ScheduledFuture<?>> scheduledTasks`，在 `doRegisterProfile` 内以 `config.id()` 存 `taskScheduler.schedule(...)` 返回值（与 `taskLocks` 并存）

**Checkpoint**: User Stories 1+2 独立可测——运行时注册立即可见、定时留句柄。

---

## Phase 5: User Story 3 - 正文即时生效、资源按需加载 (Priority: P3)

**Goal**: `ContextLoader` 注入 Agent 正文（agents/<name>/AGENT.md 去 frontmatter）；公共 Skill 经软连接绑定、只注入 name+desc+路径、正文不预载（宪法四渐进披露）；手写 Profile 来源保持旧 `profile.skills()` 直读（兼容分派）。

**Independent Test**: `ProgressiveDisclosureTest` 全绿；存量 `ContextLoaderTest` 保持不变仍绿。

### Tests for User Story 3

- [ ] T012 [US3] `ProgressiveDisclosureTest`（`fourfeetcat-core/.../core/react/`）：Agent 正文进 system prompt；技能经软连接只注入元数据、Skill 正文不预载；参考/脚本按需经 `read_file`/`shell`（软连接用例在本地 Windows 无建软链权限时 `@Disabled`、CI 真跑，不误删断言）

### Implementation for User Story 3

- [ ] T013 [US3] 改 `fourfeetcat-core/.../core/react/ContextLoader.java`：`load(profile)` 按来源分派——若 `agents/<profile.name()>/` 存在，注入去 frontmatter 的正文并按宪法四软连接视图枚举 `agents/<name>/skills/` 软连接（`toRealPath()` 校验目标位于公共 `skills/` 根，dangling/escaped/invalid-target 记 warn、跳过该技能，非软连接文件跳过由模型 `read_file` 按需读）；否则维持现状

**Checkpoint**: 三个 user story 各自独立可测。

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: 示例交付物 + 全量回归门禁

- [ ] T014 [P] 产出示例 Agent 目录 `.fourfeetcat/agents/daily-reconcile/` 四文件（`AGENT.md` + `scripts/reconcile.py` + `skills/report-format.md` + `REFERENCE.md`），按课件 §1.3/1.4 正文逐字落地，作真模型手动路径参照物
- [ ] T015 跑 `mvn clean verify` 全绿（含 P3C/SpotBugs/PMD），按 quickstart.md 校验软连接/手动扫描链路；输出节级收尾六项证据

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**：无依赖
- **Foundational (Phase 2)**：依赖 Setup —— 阻塞全部 user story
- **User Stories (Phase 3+)**:依赖 Foundational（ProfileLoader 校验复用 + ProfileRegistry 可变）
  - US1 仅依赖 T002/T003；US2 依赖 T003（+可选 T002，报错一致性用）；US3 不依赖 AgentLoader 的可注册路径（自用 ContextLoader），仅依赖 T003 的 `name` 约定（亦可独立）
- **Polish**:依赖全部 user stories 完成（示例目录原则上独立）

### User Story Dependencies

- **US1 (P1)**: 依赖 T002、T003 —— MVP
- **US2 (P2)**: 依赖 T003（+ T002）；独立可测
- **US3 (P3)**: 相对独立（不依赖 AgentLoader 注册路径），可在 US1/US2 之外完成

### Within Each User Story

- Harness 测试与实现同批落地（红了当场修）；测试先行定契约
- 先校验复用（T002）→ 再派生（T007）→ 再装配（T008）
- Story 完成再进入下一优先级

### Parallel Opportunities

- T002 / T003 并行（不同文件）
- T004/T005/T006 为 AgentLoader 的测试（与 T007 相关，建议同单元）；T009/T010 并行
- T014 示例目录独立可并行

---

## Parallel Example: US1 + US2

```bash
# 同批（US1 核心 + US2 测试两部分并行）：
Task: "AgentLoader 实现（deriveProfile/scan/detectResources）"
Task: "ProfileRegistryRuntimeTest 写测试"
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. T001 Setup；2. T002+T003 Foundational；3. T004-T008 US1；4. **STOP 验证** US1 独立可测（AgentLoaderTest/DeriveProfileTest/AgentScanRegisterTest 绿）→ MVP 达成（一个目录派生一个 Agent）

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. + US1 → 测试独立绿 → MVP（一个目录一个会跑的 Agent）
3. + US2 → 运行时注册就位 → 30 节铺路
4. + US3 → 渐进披露/正文生效 → 宪法四软连接视图落地
5. + Polish → 示例目录 + 全量 verify

### 关键顺序约束

- ProfileScore（T003）必须在任何 scan/装配（T008）之前
- ContextLoader 兼容分派（T013）不破坏存量 ContextLoaderTest——改造前先跑存量测试基线绿，改造后存量仍绿 + 新增 ProgressiveDisclosureTest 绿

---

## Notes

- [P] 任务 = 不同文件、无依赖可并行
- Harness 测试方法名必须英文（课件中文名译成语义等价英文，`@DisplayName` 保留课件原文）
- P3C/SpotBugs/PMD 为构建门禁，避开 Java 18+ 语法形态
- 软连接用例：本地 Windows skip、CI 真跑（既有记忆约定，不误删断言）
- 全程不自动 commit/push；同步时机由主公决定