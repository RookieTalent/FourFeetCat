# Research: 插件化 Agent —— 一个目录定义一个会自己跑的 Agent

## 决策总览

本节所有设计决策均已有既定依据（宪法四 / TechnicalSolution §11 / 课件），无遗留 `NEEDS CLARIFICATION`。以下记录"为什么这么设计 + 复用/边界取舍"。

## Decision 1：AgentLoader 落位与 Profile 复用派生

- **Decision**: 新增 `AgentLoader` 于 `org.fourfeetcat.core.profile`（与 `Profile`/`ProfileLoader` 同包）；`deriveProfile(agentDir)` 复用 `ProfileLoader` 的 frontmatter→Profile 映射与 provider 名可解析校验。
- **Rationale**: 宪法四规定 `AgentLoader.deriveProfile()` 把 `AGENT.md` frontmatter 派生成 `Profile`；复用 ProfileLoader 的 `toProfile`（含 `${ENV}` 占位解析、`provider.name` 校验）保证"目录派生 Agent 与手写 Profile 同规矩"（同异常、同消息），是 FR-004/SC-004 的直接保证。
- **Alternatives**: 在 AgentLoader 里重写一套 frontmatter 解析 → 会与 ProfileLoader 双份维护同一个"字段映射 + 校验"逻辑，`ProfileRegistryRuntimeTest` 报错一致性断不了，放弃。
- **Creditors cost**: 将 `ProfileLoader.resolveEnvPlaceholders` / `toProfile` / provider 校验抽到一个包内可见的 `fromYamlMap(Path origin, Map)` 方法（`loadOne` 亦改走它，去重）。属对第 16 节文件的行为无变化改动，第 7 步需标注。

## Decision 2：ProfileRegistry 可变并发化

- **Decision**: `ProfileRegistry` 由"构造器一次性灌入不可变 List"改为可变容器，持有 `Collections.synchronizedMap(new LinkedHashMap<String, Profile>())`，提供 `register / remove / exists / find / all`。
- **Rationale**: FR-004/FR-005 要求运行时 `register` 后立即可见。选 **有序 + 线程安全**（synchronizedMap over LinkedHashMap）而不是 `ConcurrentHashMap`：第 25 节 `AgentScheduler.registerAll` 显式依赖"遍历顺序 = 注册顺序，重名裁决可复现"，ConcurrentHashMap 无序会破坏该语义。
- **Alternatives**: `ConcurrentHashMap`（丢序，违背 25 节注释意图）；`ConcurrentSkipListMap`（按 key 排序而非插入序）。都否。
- **Note**: 运行时 register/remove 是低频事件（启动 + 30 节动态管理），synchronizedMap 的粗粒度锁足够，不为极端吞吐上无锁竞态。

## Decision 3：AgentScheduler 抽出 registerProfile + 句柄表

- **Decision**: `AgentScheduler` 把私有 `registerProfile(Profile, Set)` 重构为 `private doRegisterProfile(Profile, Set)` + 新增 `public int registerProfile(Profile)`（传空集合，无跨 Agent 查重）；新增实例字段 `Map<String, ScheduledFuture<?>> scheduledTasks`，在 `doRegisterProfile` 内把 `taskScheduler.schedule(...)` 返回值以 `config.id()` 为键存入。
- **Rationale**: 课件 2.4 / TechnicalSolution §11.3 明确"新增 scheduledTasks 句柄表（30 节注销/更新用），与 taskLocks 并存"。`registerAll` 改调 `doRegisterProfile`（保留跨 Agent id 查重），实现"启动与运行时注册走同一段代码"。
- **Alternatives**: 单出一套运行时注册入口而不复用 registerAll 循环体 → 双重源，放弃。

## Decision 4：ContextLoader 保兼容分派（关键）

- **Decision**: `ContextLoader.load(profile)` 按来源分派——
  - 若 `agents/<profile.name()>/` 目录存在（Agent 目录派生来源）：**注入正文**（`AGENT.md` 去掉 frontmatter）并**按宪法四软连接视图**注入技能元数据（枚举 `agents/<name>/skills/` 软连接 → `toRealPath()` 校验目标位于公共 `skills/` 根 → 只注入 name+description+本地绝对路径）；非软连接的文件（如内部子指令 `report-format.md`）跳过，由模型经 `read_file` 按需读。
  - 否则（手写 `profiles/*.yaml` 来源）：维持现状——注入 bootstrap、按 `profile.skills()` 直读公共库 Skill 元数据。
- **Rationale**: 本 feature（FR-006/FR-008）要求"Agent 正文进 system prompt + 公共 Skill 软连接绑定 + 渐进披露"，同时宪法八手写 Profile 来源的既有路径（`profile.skills()`）不能断。按"agent 目录是否存在"分派，让**新 Agent 场景走软连接、存量手写 Profile 走直读**——存量 `ContextLoaderTest` 无需改动即保持全绿。
- **Alternatives**: 一刀切改掉 `profile.skills()` 直读路径 → 破坏手写 Profile（16 节来源）与存量测试，放大改造面，否。
- **Note**: 软连接校验按原则六用 `toRealPath()`；软连接本机不可建（Windows 无建软链权限）测试本地 `@Disabled`、CI 真跑。

## Decision 5：装配层扫描

- **Decision**: `AgentRuntimeConfiguration.profileRegistry` Bean 改为：`ProfileRegistry` 空构造 → `ProfileLoader.load(profiles)` 逐个 `register` → `new AgentLoader(knownProviders).scan(agents)` 逐个 `register` → 返回。`AgentScheduler` 依赖该 registry，`initMethod=registerAll` 自动收敛派生 Agent 的 schedules。
- **Rationale**: FR-003 要求启动扫 `agents/` 注册；复用既有 Bean 与 initMethod 链路，scan 与手写 profiles 汇入同一 registry，派生 Agent 的定时被 registerAll 自动捡走，零改动 AgentScheduler 启动路径。
- **Alternatives**: 新建独立 Bean/AutoConfiguration → 过度，Assembly already centralizes.

## Decision 6：无新增第三方依赖

- **Decision**: 不改任何 pom、不新增依赖。`AgentLoader` 用既有 SnakeYAML；`ScheduledFuture` 来自 JDK；软连接校验用 `java.nio.file`.
- **Rationale**: 本 feature 纯 core 内部 + 装配，零新外部依赖符合分阶段克制。故无需 `mvn dependency:tree` 验证新增依赖。

## 边界确认（清晰，不做）

- Role 版本管理、Agent 市场/共享、跨 Agent 能力复用、L3 脚本容器/网络隔离、同名冲突策略、文件监听热加载（30 节）——均按课件"有几样先别做"排除；软连接 **CRUD 检测**（上传时）随 30 节管理端。