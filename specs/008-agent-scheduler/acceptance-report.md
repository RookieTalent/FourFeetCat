# 节级验收报告：定时任务——第三种触发源（第25节）

**分支**：`025-lesson25-scheduler`　**日期**：2026-09-28　**规格**：[spec.md](./spec.md)　**计划**：[plan.md](./plan.md)

**一句话**：给 `AgentService` 加第三条触发路径（钟推）——到点自己拼一条消息，喂给与 CLI / Web 完全相同的入口。核心引擎一行未改。

---

## §1 证据一：硬门禁 `mvn clean verify` 全绿

```
[INFO] BUILD SUCCESS
[INFO] Tests run: 51  -- fourfeetcat-core      （含本节新增 11）
[INFO] Tests run: 8   -- fourfeetcat-provider
[INFO] Tests run: 20  -- fourfeetcat-storage
[INFO] Tests run: 34  -- fourfeetcat-memory
[INFO] Tests run: 60  -- fourfeetcat-tool      （Skipped: 1，Windows 无建软链权限，按既有约定跳过）
[INFO] Tests run: 6   -- fourfeetcat-channel-cli
[INFO] Tests run: 2   -- fourfeetcat-cli
[INFO] Tests run: 2   -- fourfeetcat-boot      （含本节新增装配冒烟 1）
[INFO] You have 0 Checkstyle violations.     （各模块一致）
[INFO] BugInstance size is 0                  （SpotBugs + FindSecBugs，effort=Max / threshold=Low）
PMD 7.17.0：0 violations（首轮曾报 5 处 `GuardLogStatement`，已按本仓既有形态补 `isXxxEnabled()` 守卫）
Spotless(GJF)：check 通过
```

## §2 证据二：课件 harness 逐项对号

课件"验收 harness"的四个守点 → `AgentSchedulerTest`（`fourfeetcat-core/src/test/java/org/fourfeetcat/core/schedule/`）11 个用例，方法名英文、课件原文进 `@DisplayName`：

| 课件守点 | 用例 | 备注 |
|---------|------|------|
| 注册时 `CronTrigger` 带上了配置的 cron 和时区 | `registration_carriesConfiguredCronAndZone` | cron 用 `getExpression()` **逐字**断言；时区**用行为断言**（见 §7 偏差 1） |
| 锁被占时本次触发直接跳过、不排队 | `earlierRunStillInFlight_thisTriggerIsSkipped` | 课件原文语义逐字保真；占锁必须换线程（见 §7 偏差 2） |
| `runOnce` 内部抛异常：不外抛、**锁在 finally 被释放** | `processThrows_doesNotPropagateAndLockIsReleased` | 含课件点名的"二进宫"式断言（再触发一次仍能进处理入口） |
| 会话三元组固定，两次触发拿到同一 Session | `twoTriggers_shareSameSchedulerSession` | `verify(...).getOrCreate("scheduler","scheduler","ops-agent")` + 同一实例 |
| 课件未点名、spec 加严的守点 | `invalidEntries_areSkippedWithoutAffectingNeighbours`（SC-009）、`duplicateId_isSkipped`（FR-004）、`differentTask_isNotBlockedByAnOccupiedOne`、`oneTaskFailing_doesNotAffectAnotherTask`、`noSchedules_registersNothing`、条目解析两条 | 坏配置响亮可辨 + 不牵连邻居、重名跳过、任务间互不影响 |

课件"别的坑"（`@Scheduled` 静态注解、配置驱动、失败隔离、时区）在用例里都有对应断言，无一条被删改或跳过。

装配冒烟 `AgentSchedulerWiringTest`（boot）：真容器 + 真调度器 + 真会话管理器，只有处理入口是替身；Awaitility 等到点后断言 `process` 至少被调一次。**首轮实测 10 秒内被调 13 次**（每秒触发一次，符合配置）。

## §3 证据三：交付物逐项存在性核对

| 课件"本节交付物" | 落地 |
|-----------------|------|
| `AgentScheduler`（`registerAll` + `runOnce` + 按任务 id 的 `ReentrantLock` 表） | `fourfeetcat-core/.../schedule/AgentScheduler.java` ✓ |
| `ScheduleConfig`（id / cron / zone / message） | `fourfeetcat-core/.../schedule/ScheduleConfig.java` ✓ |
| `AgentSchedulerTest` | `fourfeetcat-core/src/test/.../schedule/AgentSchedulerTest.java` ✓（11 用例） |
| 配置：Profile 新增 `schedules` 字段 | **第16节已建**（`List<Map<String,Object>>`，注释即标"第25/28节"）；本节交付**消费方**（`ScheduleConfig.fromEntry` + `registerAll`），`Profile`/`ProfileLoader`/`ProfileLoaderTest` **零 diff**（`git diff` 已核对） |
| 约定：会话身份固定 `("scheduler","scheduler",profileName)` | `AgentScheduler.runOnce` ✓ + 用例断言 |
| 约定：失败只记日志、不崩调度器 | `runOnce` 的 `catch` + `finally` ✓ + 二进宫用例 |

## §4 证据四：前序节回归全绿（跨节契约证据）

`mvn clean verify` 覆盖全部 14 个模块：共 **183** 个用例全绿，其中本节新增 **12** 个（`AgentSchedulerTest` 11 + 装配冒烟 1），**零断言删改、零用例新增跳过**（唯一 Skipped 是第24节那条软链用例，本机 Windows 无建链权限，按既有约定跳过）。逐文件 `git diff` 核对"前序契约零改动"：

```
ReActLoop.java 未改 | ToolExecutor.java 未改 | Profile.java 未改 | ProfileLoader.java 未改
ProfileLoaderTest.java 未改 | SessionManager.java 未改 | AgentService.java 未改 | 数据表与迁移脚本 未改
```

## §5 证据五：H4 六条全局不变量自查

| # | 不变量 | 本节结论 |
|---|--------|---------|
| ① | 涉外 IO 首行过 `Sandbox.enforce` | 未新增任何涉外 IO；定时触发那一轮的工具调用照旧过第24节白名单（调用位未动） |
| ② | LLM 调用成败都落 `llm_calls`、工具执行成败都落 `tool_invocations` | **零新增审计代码**：钟推那一轮由既有 `ReActLoop`/`ToolExecutor` 记账，与人推同口径（grep 确认 `schedule` 包内无 `Repository`/落库调用） |
| ③ | 无明文 key | grep 确认新文件无 `apiKey`/`api-key`；配置经环境变量占位 |
| ④ | `session_id` 只在 `SessionManager` 内拼接 | grep 确认 `schedule` 包内无任何拼接，只递三元组 |
| ⑤ | 无 Reactor / `CompletableFuture` / 自建线程池 | grep 确认新代码零异步栈；调度线程池来自框架的调度设施（技术方案 §8.5 的既定选型，非自建并发模型） |
| ⑥ | 无 Spring AI 自动工具执行路径 | 未碰 Spring AI |

## §6 证据六：剩余人工项（harness 判不了，请主公过目）

1. **真实到点触发一次**（需真 key）：按 `quickstart.md` §2 给某个 Agent 配一条每分钟的任务、常驻运行，看日志到点自动触发、`llm_calls` 有账、`sessions` 里出现 `channel='scheduler'` 的专用会话。
2. **改 cron 不用重新编译**：只改配置 + 重启即按新时间跑（含改 `zone` 后触发时刻平移）。
3. **坏配置的体感**：真写一条 5 段 cron，看日志点名报错并跳过、同 Agent 的另一条照常跑。
4. **配置说明**：`fourfeetcat init` 生成的模板里那段 `schedules` 注释是否讲清了 6 段方言 / 时区建议 / 跳过语义。

## §7 实施期裁决与偏差记录（逐条留痕）

1. **时区的守点用行为断言，而非读注册参数里的字段**（偏离课件表格字面）：实测 `CronTrigger` **无时区读取方法**，且 `equals` **不参与时区比对**（上海与纽约的同一表达式判定相等）。故 cron 用 `getExpression()` 断言、时区用 `nextExecution(TriggerContext)` 断言（上海 09:00 → `2026-09-28T01:00Z`、纽约 08:30 EDT → `12:30Z`）。**守点一字未改**，只是换了个更硬的验法。
2. **"上一次还在跑"必须换线程构造**（偏离课件骨架的写法）：执行权是 `ReentrantLock`，**对同线程可重入**——在当前线程里自己 `lock()` 再调 `runOnce`，`tryLock()` 会成功，场景根本没构造出来。测试用一条守护线程占锁（真实触发跑在调度线程上，正是这个样子）。首轮照抄骨架时这条用例真红了，是它抓出来的。
3. **`CronTrigger(String, ZoneId)` 不收 null**（实测 `IllegalArgumentException: ZoneId must not be null`）：时区缺省改为**显式**取 `ZoneId.systemDefault()` 再传，"到底用了哪个时区"因此写在调用点上。
4. **`@Component` / `@PostConstruct` 改由 boot 显式装配**（偏离课件骨架）：core 自第16节起零 Spring 注解，`initMethod` 也免了 core 依赖 `jakarta.annotation-api`（research D5）。
5. **`ProfileRegistry` 新增只读 `all()`，内部容器 `HashMap` → `LinkedHashMap`**：注册顺序 = 加载顺序，重名裁决可复现；既有 `find` 与构造器签名未动。
6. **`core/pom.xml` 显式声明 `spring-context`**：该件已在编译期 classpath 上（`dependency:tree` 可证），照第20节 `jackson-databind` 先例显式写出，**依赖集合无变化**（spec FR-011 不破）。
7. **`config/spotbugs/spotbugs-exclude.xml` 新增一条 `CRLF_INJECTION_LOGS` 按类排除**（`AgentScheduler`）：本仓对该规则已有三处同款排除（profile 包 / ContextLoader / McpClientService），理由一致——来源是部署者自管的本地配置、非网络或对话用户可控，sanitize 会削弱"哪条配置没注册上"这条唯一排障线索。已按**类**（不按包）取最窄范围，并写入复核提示（若将来配置值被拿去拼标识/命令/URL，须删除该条）。
8. **装配冒烟不依赖工作区目录**：`AgentRuntimeConfiguration.workspaceRoot()` 读的是 `System.getenv("FOURFEETCAT_ROOT")`，而 `@SpringBootTest(properties=...)` 只设 Spring 属性——**测试环境里两者本就分叉**（既有 boot 测试的数据源走 Spring 属性、工作区走环境变量）。故冒烟改用内存里造的真实注册表，不写工作区文件；它验的是"装配与注册有没有发生"，"文件扫得对不对"归 `ProfileLoader` 的测试管。另：SQLite 只建文件不建目录，测试里补一句 `Files.createDirectories(...)`（生产方式由 `main()` 做）。

## §8 已知文档-代码差（登记，不静默）

| 差 | 文档怎么说 | 本节怎么做 | 去向 |
|---|-----------|-----------|------|
| cron 方言 | 课件正文/`§8.5` 只写"cron 表达式"，课件示例与既有 `ProfileLoaderTest` 夹具给的是 5 段 `0 9 * * *` | 用框架原生 **6 段**（秒 分 时 日 月 周）；5 段会被**硬拒**并跳过该条（实测 `must consist of 6 fields`）。经主公裁决 | 配置说明已按 6 段写进 Agent 配置模板；`docs/TechnicalSolution.md` §8.5 **未记方言**，建议后续 docs 修订补一句（本节不自行改设计文档） |
| "Profile 新增 `schedules` 字段" | 课件本节交付物列了这一条 | 该字段第16节就已建好，本节交付的是**消费方** | 已在本报告 §3 显式说明，避免"交付物少了一项"的误读 |
| 骨架的 `@Component` + `@PostConstruct` | 课件骨架如此 | boot 侧 `@Bean(initMethod = "registerAll")` | 与本仓 core 零 Spring 注解的既定形态一致，见 §7 偏差 4 |

**明确不做（课件"先别做"，非缺失）**：多实例分布式协调（选主 / 分布式锁 / 租约）、失败重试与告警、运行时增删改定时任务接口、任务状态与执行历史持久化（第28节）。
