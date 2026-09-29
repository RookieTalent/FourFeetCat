# Contract: Agent 目录 → Profile 派生与运行时注册

面向本 feature 的既有/新增接口约定（`fourfeetcat-core` 内部，跨模块/装配层消费）。

## AgentLoader（新增，`org.fourfeetcat.core.profile`）

- `List<Profile> scan(Path agentsDir)`:列出 `agentsDir` 下每个子目录，逐个 `deriveProfile`；坏目录捕获 `RuntimeException` 记 **error 日志点名**、跳过，返回可注册的 Profile 列表。目录缺失/为空 → 返回空列表，不报错。
- `Profile deriveProfile(Path agentDir)`:读 `AGENT.md`，拆 frontmatter/正文，映射为 Profile；缺 `name`/`provider` 或 provider 名不可解析 → 抛 `IllegalArgumentException` **报错点名**（含字段/目录）。
- `AgentResources detectResources(Path agentDir)`:返回 `{referenceFile, scriptsDir, skillsDir}`（存在才非空），支撑"认资源"可测；不承载于 Profile。
- 构造：`AgentLoader(Set<String> knownProviders)` / `AgentLoader(Function<String,String> env, Set<String> knownProviders)`，复用 `ProfileLoader` 内部映射。

## ProfileRegistry（改造，16 节 → 本 feature）

- `void register(Profile)`:以 `name` 为键写入；同名 → 覆盖并 `warn`。调用方（AgentLoader/装配）保证派生校验已完成。
- `void remove(String name)`;`boolean exists(String name)`;`Optional<Profile> find(String)`;`List<Profile> all()`。
- **不变式**:`register` 后 `find` 立即可见；遍历顺序 = 注册顺序（25 节定时注册依赖）；并发安全。

## AgentScheduler（改造，25/28 节 → 本 feature）

- `public int registerProfile(Profile profile)`:为单个 Agent 注册其全部 `schedules`（本profile内 id 查重后逐条 `store.register` + `taskScheduler.schedule`），返回成功条数；每条 `ScheduledFuture` 以 `config.id()` 存入 `scheduledTasks` 句柄表。
- `registerAll()`:启动遍历 registry 调内部 `doRegisterProfile`（保持跨 Agent id 查重）。

## ContextLoader（改造，17 节 → 本 feature，保兼容分派）

- `String load(Profile profile)`:若 `agents/<profile.name()>/` 存在 → 注入 Agent 正文 + 软连接技能元数据；否则 → 维持现状（bootstrap + `profile.skills()` 直读）。
- 软连接规则（宪法四/原则六）：只接受指向公共 `skills/` 根的相对链接，`toRealPath()` 校验；dangling/escaped/invalid-target → 记 warn、跳过该技能，不阻断 Agent。

## 装配（AgentRuntimeConfiguration，改）

- `profileRegistry` Bean:空构造 → `ProfileLoader.load(profiles).forEach(register)` → `AgentLoader.scan(agents).forEach(register)` → 返回；派生 Agent 的 schedules 由 `AgentScheduler(initMethod=registerAll)` 自动收敛。