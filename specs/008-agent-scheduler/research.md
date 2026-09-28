# Phase 0 Research：定时任务——第三种触发源（第25节）

本文件收敛本节全部技术裁决。**无 NEEDS CLARIFICATION 残留**。D1 来自主公裁决（记录在 spec 的 Clarifications），D2 / D4 / D10 的第三方行为来自本机实测（解包工程锁定的 jar 真跑）。

---

## D1 cron 方言：用调度框架原生 6 段，不做 5 段兼容转换

**决策**：配置里的触发规则原样交给框架的 cron 触发器，采用**秒 分 时 日 月 周** 6 段格式（每天 09:00 = `0 0 9 * * *`），配置说明与示例按此口径写。

**实测事实（JDK 21 + 本工程锁定的 spring-context 6.2.19，动笔前跑过）**：

| 输入 | 行为 |
|------|------|
| `0 0 9 * * *`（6 段） | 接受 |
| `0 9 * * *`（5 段，课件与既有夹具里的写法） | 抛 `IllegalArgumentException: Cron expression must consist of 6 fields (found 5 in "0 9 * * *")` |
| `0 0 25 * * *`（时位越界） | 抛 `IllegalArgumentException: Invalid value for HourOfDay (valid values 0 - 23): 25` |
| 时区 `Mars/Olympus` | 抛 `ZoneRulesException: Unknown time-zone ID` |

两点值得记：①5 段写法是**硬拒**而不是被静默按"每小时第 9 分钟"解释——静默误读才是这类方言错最坏的形态；②两种异常都是 `RuntimeException`，注册期捕获后记错误日志跳过该条（FR-010）即可，不需要为它们单开一套错误类型。

**理由**：零转换代码；且"注册时交给调度器的表达式与配置原文**逐字一致**"本身是一条可断言的成功标准，加了补位转换层这条断言就不再成立。经主公裁决。

**备选被否**：配置写 5 段时自动补一个秒位再注册——手感更贴 Unix 习惯，但多一层方言转换、且"注册参数 = 配置原文"失效，用户写 6 段时还要防重复补位。

## D2 时区的"传对了"怎么断言：行为断言，而非读字段

**决策**：harness 的两条断言分两路——cron 用捕获到的触发器的 `getExpression()` 逐字比对；**时区用 `nextExecution(TriggerContext)` 的行为断言**（同一表达式在 `Asia/Shanghai` 下 09:00 → `2026-09-29T01:00Z`，在 `America/New_York` 下 09:00 → `2026-09-28T13:00Z`）。

**实测事实**：`CronTrigger` 的公开面只有 `getExpression()`（返回原始表达式字符串），**没有**时区读取方法；且它的 `equals` **不参与时区比对**——同一表达式的上海触发器与纽约触发器判定相等。两条事实合起来意味着：想验"时区没被吞掉"，唯一可靠的公开手段是让触发器**算一次下一次执行时刻**，看落在哪个瞬间。

`TriggerContext` 是接口且三个方法无 default 实现（`lastScheduledExecution` / `lastCompletion` / `lastActualExecution`），测试里用一个三个方法都返回 null 的内联实现即可——三个都为空即"从当下起算第一次执行"。

**理由**：行为断言比读字段更贴守点本身（坑四要防的是"服务器时区替用户做主"，那正是 nextExecution 落在哪个瞬间）。这是本节唯一一处断言方式偏离课件字面（课件表格只写"ArgumentCaptor 抓注册参数"），偏离原因即上面两条 API 事实——**守点一字未改，只是换了个更硬的验法**。

**备选被否**：反射读私有字段（脆，且 Spring 内部结构一变就红）；只断言 cron 不断言时区（漏掉坑四，等于本节的守点少一条）。

## D3 注入 `TaskScheduler` 接口，不用 `ThreadPoolTaskScheduler` 具体类

**决策**：`AgentScheduler` 的依赖类型是 `org.springframework.scheduling.TaskScheduler`（有 `schedule(Runnable, Trigger)`）；具体的 `ThreadPoolTaskScheduler` 由 boot 装配。

**理由**：接口可 mock（harness 的 ArgumentCaptor 断言不必依赖具体类实现）；与本工程"跨模块只认接口、实现由装配点给"的习惯一致。课件骨架写具体类属示意——它同时写死了 `@Component` + `@PostConstruct`，本工程取显式装配（见 D5）。

**备选被否**：照课件用具体类——harness 仍能 mock（Mockito 可 mock 具体类），但把"用哪个调度器实现"这个装配决定写进了 core 的类型签名里。

## D4 调度线程池容量：下限 2，取 4；不设配置键

**决策**：`ThreadPoolTaskScheduler.setPoolSize(4)`。

**理由（两条）**：

1. **下限是 2**：触发型调度里，同一条任务的"下一次执行"是在**本次执行完成之后**才重新排期的（框架的 rescheduling 语义），所以同一任务不会由调度器自身叠出并发——但**不同任务**会：容量为 1 时，一条长任务（一次 ReAct 循环可能几分钟）会把另一条任务到点的触发堵在池队列里，直到它跑完。这正是 Spring 官方对默认容量 1 的告警场景。
2. **取 4 不设配置键**：并发任务数的上界由配置里声明的定时任务条数决定；在没有实测依据之前不引入一个调优旋钮（多一个配置键就多一份要维护、要解释、要测的东西）。将来有真实压测数据再谈。

**顺带记一条设计事实**：正因为框架"跑完才重新排期"，锁（FR-005）在核心阶段不是防框架自身的重叠，而是**显式闸门**：它把"同一任务不重叠"从"依赖框架实现细节"变成"本模块自己的可测语义"，并为后续的手工立即执行（第28节的立即跑一次）留好位置。

**备选被否**：容量 1（Spring 默认口径，但让不同任务互相排队）；容量写成配置键（无依据的旋钮）；容量取很大（如 20）——定时任务的真实并发只有个位数，大池子只是给"漏了收敛"留余地。

## D5 装配位：在 boot 的 `AgentRuntimeConfiguration` 显式装配，core 不放 Spring 注解

**决策**：`AgentScheduler` 与调度器都是 `fourfeetcat-boot` 里的 `@Bean`；注册动作经 `@Bean(initMethod = "registerAll")` 触发。`fourfeetcat-core` 里**不加** `@Component` / `@PostConstruct`（课件骨架的写法）。

**理由**：`fourfeetcat-core` 自第16节起是**零 Spring 注解**的纯 POJO 模块，全部 Bean 由 boot 的 `@Configuration` 装配（`ReActLoop` / `ToolExecutor` / `ProfileRegistry` / `AgentService` 无一例外）。照课件加注解会让 core 第一次引入组件扫描契约，与九节以来的形态冲突；另外 `@PostConstruct` 需要 `jakarta.annotation-api` 成为 core 的**直接编译依赖**（当前只在测试作用域），而 `initMethod` 是容器自带能力，零依赖。

**备选被否**：照课件 `@Component` + `@PostConstruct`——多一条扫描契约、多一个直接依赖，只换来少写一行 `@Bean`。

## D6 定时配置保持原结构，解析与校验放在注册期

**决策**：`Profile.schedules` 保持既有的原始条目列表（`List<Map<String, Object>>`）一字不改；`ScheduleConfig` 由 `AgentScheduler` 在注册期把每条原始条目解析出来，解析或校验失败即**跳过该条**并记错误日志。

**理由**：①不动前序节的公共接口（`Profile` 记录与 `ProfileLoader` 及其测试夹具全部零改动，是本节 diff 最小的路径）；②失败隔离更好——一条坏配置只废自己，不牵连同一 Agent 的其他条目，而"整份配置被跳过"是更强的连带惩罚；③强类型化的收益（编译期约束）在这里很小，因为条目本来就来自 YAML。经主公裁决。

**备选被否**：`Profile.schedules` 改为强类型列表、由加载器解析——要改前序节的记录签名与加载器，并触碰既有 `ProfileLoaderTest` 的断言。

## D7 标识：显式优先、缺省派生；进程内全体范围内查重

**决策**：

```text
id = 条目里显式写的 id（去空白后非空）
     否则 "profileName#序号"（序号 = 该 Agent 的 schedules 列表下标）

注册期维护一份"已注册标识"集合：
  重复 → 记错误日志，跳过后来者并点名两条来源，不静默取第一条
```

**理由**：派生形式天然带 Agent 名前缀，手写配置最省事（现有 `ProfileLoaderTest` 夹具里的条目根本没写 id，照样成立）；显式写则为后续阶段的稳定键留好路。查重**必须在进程内全体范围**（而不是各自 Agent 内）——执行权按标识分配，两个 Agent 若都写了 `daily`，它们会互相挡掉下一次触发；这种"无人值守时才现形"的坏症状只值一条注册期检查的代价。

**备选被否**：标识必填（给 31 节手写 `AGENT.md` 加负担，且要改前序夹具）；只在 Agent 内查重（跨 Agent 重名静默互相挡）。

## D8 执行权表：裸标识作键；`lockFor` 包内可见

**决策**：`ConcurrentHashMap<String, Lock>`，键即 `ScheduleConfig.id`；`lockFor(String)` 方法**包内可见**（不给外部契约）。

**理由**：键必须是裸标识——课件 harness 里 `lockFor("task-1")` 与 `runOnce(profile, scheduleConfig("task-1"))` 必须命中同一把锁，若给键加 Agent 前缀，harness 的第一条用例就不再成立。`lockFor` 只服务于包内测试（占住锁以构造"上一次还在跑"的场景），与 `FourFeetCatCli.engine()` 同款处理：包内可见，不升为对外概念。

**备选被否**：键加 Agent 前缀（安全但破坏 harness 用例的字面守点——那是对课件契约的改动，不是本节的自由）；把 `lockFor` 做成 public（多一个不该有的对外概念）。

## D9 会话身份：只传三元组，不越界拼接

**决策**：`sessionManager.getOrCreate("scheduler", "scheduler", profile.getName())`；`AgentScheduler` 内**不出现**任何会话标识的拼接。

**理由**：会话标识的拼接在实现内部只有一处（`JpaSessionManager` 的私有方法，第18节定的），两个入口各拼一遍就会让同一个人出现两条互不相认的历史。定时是第三个入口，照旧只递三元组。

## D10 core 的 pom 显式声明 `spring-context`

**决策**：在 `fourfeetcat-core` 的 pom 里显式加 `spring-context` 依赖（版本由 Boot BOM 管，6.2.19）。

**理由**：它**已在 core 的编译期 classpath 上**（经 `spring-ai-model` 传递，实测 `mvn dependency:tree` 可证），所以这不是"新增第三方依赖"（spec FR-011 不破）；但本节起 core 的源码直接 import 它的类型（`TaskScheduler` / `CronTrigger`），按第20节为 `jackson-databind` 立的先例（"此前靠 spring-ai-model 传递，现显式声明"）显式声明，避免"用一个没有被声明过的依赖"这种隐性耦合。

**备选被否**：不声明，靠传递依赖——省一行 pom，但把"core 直接用了 spring-context"这件事藏进传递路径里。

---

## 已知文档-代码差（登记，不静默）

| 差 | 文档怎么说 | 本节怎么做 | 去向 |
|---|-----------|-----------|------|
| 触发规则的写法 | 课件正文与 §8.5 只写"cron 表达式"，课件示例与 `ProfileLoaderTest` 夹具给的是 5 段 `0 9 * * *` | 用框架原生 6 段（D1）。5 段写法在注册期被硬拒、记错误日志跳过 | 配置说明与 31 节两个 Demo 的 `AGENT.md` 按 6 段写；既有夹具里那个 5 段字符串**不改**（它只验"字段能解析出来"，字符串内容无语义） |
| "Profile 新增 `schedules` 字段" | 课件本节交付物列了这一条 | **该字段第16节就已建好**（`List<Map<String,Object>>`，注释即标注"第25/28节"），`ProfileLoader` 也已有对应解析 | 本节实际交付的是**消费方**（调度器把它接上），字段本身零改动；在验收报告里显式说明，避免"交付物少了一项"的误读 |
| 骨架的 `@Component` + `@PostConstruct` | 课件骨架如此写 | 改为 boot 侧 `@Bean(initMethod = "registerAll")`（D5） | 本仓 core 零 Spring 注解是九节以来的既定形态；这是装配风格差异，不是功能缺失 |

> 说明：三处都不是偷工：一处是方言口径经主公裁决、一处是"字段早已存在"的事实澄清、一处是装配风格与本仓一致性。

## 已核实依赖（动手前检查，H0 门禁）

| 依赖 / API | 状态 | 证据 |
|-----------|------|------|
| `Profile` / `ProfileLoader` / `ProfileRegistry`（第16节） | 已在库 | `fourfeetcat-core/src/main/java/org/fourfeetcat/core/profile/` |
| `ProfileRegistry` 的遍历方法（骨架用的 `all()`） | **缺，本节新增**（只读，不动既有成员） | 该类现只有 `find(String)` |
| `AgentService.process(Session, String)`（第17节） | 已在库 | `fourfeetcat-core/react/AgentService.java` |
| `SessionManager.getOrCreate(channel, user, profileName)`（第18节） | 已在库 | `fourfeetcat-core/session/SessionManager.java`；实现 `JpaSessionManager` 的私有拼接是唯一处 |
| 审计写入路径（第16/17节） | 已在库，本节零改动 | `LlmCallRecorder` / `ToolInvocationRecorder` 由 `ReActLoop` / `ToolExecutor` 调用 |
| `TaskScheduler.schedule(Runnable, Trigger)` | 已在库（实测 `javap`） | `spring-context:6.2.19` |
| `ThreadPoolTaskScheduler`（`setPoolSize` 等） | 已在库（实测 `javap`） | 同上 |
| `CronTrigger`（4 个构造器 + `getExpression` + `nextExecution`） | 已在库（实测 `javap` + 真跑） | 同上 |
| `spring-context` 是否已在 core 编译期 classpath | 是（传递） | `mvn -o -pl fourfeetcat-core dependency:tree` |
| 新增第三方依赖 | **无** | 只用已在树的 `spring-context`；显式声明不改变依赖集合 |
