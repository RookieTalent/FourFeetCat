# Implementation Plan: 定时任务——第三种触发源（第25节）

**Branch**: `025-lesson25-scheduler` | **Date**: 2026-09-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-agent-scheduler/spec.md`

## Summary

Provider、ReAct、CLI、Notify、Tool、Memory、Sandbox 讲完，Agent 能被喂话、会想、会动手、会推消息、记得住、有边界。本节补最后一个缺口：**不用人喂话，到点自己干活**。

定时任务不是新能力，是**第三种触发源**：CLI 是人推、Web 是人推、定时是**钟推**——到点由系统自己拼一条消息，喂给跟 CLI / Web 完全相同的入口（`AgentService.process`）。`ReActLoop`、`ToolExecutor`、`ProviderService` 一个字都不用改，审计也不需要为"这是定时触发的"另开一套。

本节交付物很小：**一个调度器（`AgentScheduler`）+ 一个条目值对象（`ScheduleConfig`）+ 一处装配（boot 里两个 Bean）**，核心逻辑就是"读配置 → 按 cron 注册 → 到点拿锁 → 拼消息 → 交给 `AgentService` → 放锁"。难点不在代码量，在四个坑：配置驱动（不用 `@Scheduled` 注解）、重叠防抖（进程内锁，非分布式）、失败隔离（不崩调度器、锁必释放）、时区显式（不让服务器时区替用户做主）。

本节真正的验收线是**两处"只会在第二次触发才现形"的缺陷**：锁忘了释放（`finally` 漏了 `unlock`）、以及触发的那一轮没走同一个人推入口。前者用"二进宫"式断言（抛异常后再触发一次，仍能进处理入口）钉死；后者靠 boot 层的装配冒烟（真容器起一遍、真等到点、断言处理入口被调到）钉死。

## Technical Context

**Language/Version**: Java 21（Maven 多模块，`maven.compiler.release=21`）

**Primary Dependencies**: Spring Boot 3.x 自带的调度设施（`org.springframework.scheduling.TaskScheduler` / `ThreadPoolTaskScheduler` / `CronTrigger`）——它们**已在** `fourfeetcat-core` 的编译期 classpath 上（经 `spring-ai-model` 传递，`mvn -o -pl fourfeetcat-core dependency:tree` 实测可证），本节按第20节为 `jackson-databind` 立的先例**显式声明** `spring-context`（版本由 Boot BOM 管）。**不新增任何第三方依赖**。动手前已实测（见 research D1/D2）：`CronTrigger(String, ZoneId)` 存在且只收 6 段表达式（5 段硬拒）、`getExpression()` 可取原始表达式、**无时区读取方法且 `equals` 不比时区**、非法时区抛 `ZoneRulesException`。

**Storage**: 不适用。本节**不新增表、不新增落库调用、不新增迁移脚本**：定时触发的那一轮，模型调用与工具调用由既有的 `ReActLoop` / `ToolExecutor` 照常写 `llm_calls` / `tool_invocations`（零新增审计代码）。任务状态与执行历史的持久化是第28节的事。

**Testing**: JUnit 5 + AssertJ + Mockito（既有栈）。新增 `AgentSchedulerTest`（core `schedule` 包内；四个坑逐一成对可测）+ `AgentSchedulerWiringTest`（boot 层装配冒烟：真容器 + 每秒钟触发的测试任务 + 替身处理入口 + Awaitility 等到点）。集成类打 `@Tag("integration")`；装配冒烟**不打标签**——它是本节的接线守点，必须进 CI。

**Target Platform**: 服务端（开发机 Windows / 部署 Linux）；调度逻辑平台无关。

**Project Type**: 多模块库（分布式 Agent 底座）——本节只碰 `fourfeetcat-core`、`fourfeetcat-boot`、`fourfeetcat-cli`（仅一个资源模板的注释）三个模块，不新建、不改名模块。

**Constraints**:
- `mvn clean verify` 全绿是完成的定义，含 Spotless(GJF) / PMD7 / Checkstyle(google) / SpotBugs+FindSecBugs。
- 全同步阻塞：`runOnce` 里同步调 `AgentService.process`，**不出现** Reactor / `CompletableFuture` / 自建线程池；调度用的线程池来自框架的调度设施（技术方案 §8.5 的既定选型），不是自建的并发模型。
- `AgentService.process` 是唯一处理入口；定时模块**不得**绕过它自建调用链，也不得为定时新增审计通路。
- 不引入分布式协调（选主 / 分布式锁 / 租约）；防重叠只是进程内一把锁。
- 避开 P3C/ASM 解析不了的 Java 18+ 语法形态；`switch` 若用到须穷尽且不写 `default ->`。

**Scale/Scope**: 新增 2 个源文件（core）、1 个测试类（core）、1 个测试类（boot）；改 3 个既有文件（`ProfileRegistry` +`all()`、core 的 pom 显式声明、boot 装配加两个 Bean）；另改 1 个资源模板的注释（CLI 的 `profile.yaml`，纯注释，零行为变化）。净增代码量在 250 行以内。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|------|------|------|
| 一：自实现 ReAct | 本节不碰循环：`ReActLoop` / `PromptBuilder` / `ToolExecutor` 零改动。定时只负责"拼一条消息交给 `AgentService`"，循环对"消息从哪来"无感知 | ✅ 通过 |
| 二：Spring AI 只用两件事 ⚠️ | 完全不碰 Spring AI：不新增工具、不动 `@Tool` schema、不动 `ChatClient` 路径 | ✅ 通过 |
| 三：Provider 显式映射 | 不碰 provider 路由；provider 只在被触发那一轮里按既有路径解析 | ✅ 通过 |
| 四：一个目录 = 一个 Agent | 不引入 Skill / Agent 目录新概念；`Profile` 既有字段零改动。本节恰好是这条原则的兑现：**给一个 Agent 加条定时任务只改配置、不重新编译**——`schedules` 字段自第16节起就在，本节把它的**消费方**接上 | ✅ 通过 |
| 五：审计 Day One | **零新增审计代码、零新增表**：定时触发的那一轮的 `llm_calls` / `tool_invocations` 由既有路径写入（"钟推"与人推记账口径完全一致，这正是 spec FR-009 的要求）。失败只记日志、不改调度器语义，也不新增 `task_executions` 之类的表（第28节） | ✅ 通过 |
| 六：不使用 SecurityManager；软链校验真实路径 | 不碰沙箱：被触发那一轮里的工具调用照旧走第24节的白名单校验（调用位在工具执行首行，本节不动） | ✅ 通过 |
| 七：同步执行模型 | `runOnce` 全同步：拿锁 → `getOrCreate` 会话 → `agentService.process`（同步阻塞）→ 放锁。**无** Reactor / `CompletableFuture`；唯一的多线程来源是框架的**调度设施**（技术方案 §8.5 明确写的就是 `ThreadPoolTaskScheduler` + `CronTrigger`），它不是编程模型的引入，而是本节的功能本体 | ✅ 通过（需在报告里点明，避免"看见线程池就当异步"的误判） |
| 八：Tool 模块三合一 | 不碰 `fourfeetcat-tool`；定时不是 Tool，不进 `ToolRegistry` | ✅ 通过 |
| 架构约束：依赖倒置 | `AgentScheduler` 落 `fourfeetcat-core`（技术方案 §8.5 指定），依赖 `TaskScheduler`（框架类型）+ `ProfileRegistry` / `AgentService` / `SessionManager`（皆 core 内），**不新增跨模块契约、不新增模块依赖边**；boot → core 是既有边 | ✅ 通过 |
| 架构约束：模块结构 | **不新建、不改名模块**；新增的只是一个包 `org.fourfeetcat.core.schedule`。无需同步 `docs/TechnicalSolution.md` §10 与 CLAUDE.md 模块表 | ✅ 通过 |
| 架构约束：Flyway 双轨 | **不新增迁移脚本**（不落库、不改表） | ✅ 通过 |
| 架构约束：配置与凭证 | 新增的配置是 **Agent 配置里的定时条目**（`schedules` 的四项：标识 / cron / 时区 / 消息），**不是** `application.yaml` 的全局键；**无凭证**、不需要加密存储或环境变量占位 | ✅ 通过 |
| 架构约束：无状态实例、状态外置 | 本节**不落任何任务状态**：注册关系与执行权全在内存，重启即由配置重建（这是技术方案 §8.5 明写的核心阶段口径）；第28节才补持久化与运行控制 | ✅ 通过 |
| 铁律：内容三处同步 | 本节**不改变**对外定位与特性表述：定时任务早已写在 README / 官网 / 技术方案里（§8.5/§8.6，第25节的既定内容），本节是把设计落成代码，无表述漂移。唯一新增的**用户可见说明**是"cron 写 6 段"这条方言口径，落在 Agent 配置模板的注释里（配置自说明）。技术方案 §8.5 未写方言这一处**登记为文档差**，不自行改设计文档 | ✅ 通过（含一处登记） |
| 技术约束：中文注释惯例 | 新增类 / 方法均带中文 javadoc：写清"谁消费、什么口径、为什么这么设计（尤其四个坑与 D4 的池容量下限）" | ✅ 通过 |
| 技术约束：第三方 API 可得性 | 动笔前已 `javap` + 真跑核实（D1/D2）；`spring-context` 已在树中，**无新增依赖**，无 API 可得性风险 | ✅ 通过 |

**Gate 结果：全部通过，无违宪项。** 下面记的是四处**有意的口径选择**（含两处对课件字面的偏离），逐条给出理由与备选被否的原因。

## Project Structure

### Documentation (this feature)

```text
specs/008-agent-scheduler/
├── plan.md                      # 本文件
├── spec.md                      # 需求（含四条裁决）
├── research.md                  # Phase 0：D1~D10 技术裁决
├── data-model.md                # Phase 1：值对象 / 配置模型 / 执行权模型
├── contracts/java-contracts.md  # Phase 1：契约、接线点、配置项契约
├── quickstart.md                # Phase 1：可执行的验证指南
├── checklists/requirements.md   # spec 质量清单（specify 阶段产出）
└── tasks.md                     # Phase 2 产出（/speckit-tasks）
```

### Source Code (repository root)

```text
fourfeetcat-core/src/main/java/org/fourfeetcat/core/schedule/
├── ScheduleConfig.java           # 新增：一条定时任务的值对象（id / cron / zone / message）
└── AgentScheduler.java           # 新增：registerAll + runOnce + 按任务 id 的 ReentrantLock 表

fourfeetcat-core/src/main/java/org/fourfeetcat/core/profile/ProfileRegistry.java
                                  # 改：+all()（只读遍历，课件骨架的 profileRegistry.all()）
                                  #     内部 HashMap → LinkedHashMap（让注册顺序等于加载顺序，重名裁决可复现）

fourfeetcat-core/pom.xml          # 改：显式声明 spring-context（已在树中，第20节 jackson 先例照办）

fourfeetcat-core/src/test/java/org/fourfeetcat/core/schedule/
└── AgentSchedulerTest.java       # 新增：harness 主体（四个坑逐一成对）

fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/AgentRuntimeConfiguration.java
                                  # 改：+2 个 Bean（ThreadPoolTaskScheduler；AgentScheduler，initMethod=registerAll）

fourfeetcat-boot/src/test/java/org/fourfeetcat/boot/
└── AgentSchedulerWiringTest.java # 新增：装配冒烟（真容器 + 每秒触发 + 替身处理入口 + Awaitility）

fourfeetcat-cli/src/main/resources/templates/profile.yaml
                                  # 改：加一段**注释**示例（schedules 四项 + 6 段 cron 口径 + 时区）
```

**Structure Decision**：沿用既有 Maven 多模块布局，**不新建模块**。调度器与条目值对象落 `fourfeetcat-core` 的新包 `org.fourfeetcat.core.schedule`（技术方案 §8.5 明确"归 core"；core 里 package-per-能力域，与 `memory` / `notify` / `profile` / `react` / `session` / `tool` 同构）。装配落 `fourfeetcat-boot` 的 `AgentRuntimeConfiguration`（本仓唯一装配点，第16节起九节不移）。**落位声明**：skill 落位表写"25 `AgentScheduler`/`ScheduleConfig` → core"，本节完全照此执行，无偏离。

## Complexity Tracking

| 项 | 为什么需要 | 为什么不用更简单的做法 |
|---|-----------|----------------------|
| **执行权锁即使在核心阶段也要做** | spec FR-005 与课件"坑二"都要求"同一任务不重叠、且是进程内的"。更关键的是：框架的触发式调度**跑完才重新排期**，所以核心阶段其实叠不出并发——把"不重叠"寄托在这个实现细节上，等于语义是别人的副产品（换调度器即失效、且在单测里测不出来）。做成显式闸门后，它是本模块自己的可测语义，还顺手为第28节的"立即执行一次"留好位置（D4） | 不做锁、依赖框架语义：省十来行，但 FR-005 变成不可测的偶然性质，且第28节一加手工触发就得补回同一个东西 |
| **标识在进程内全体范围内查重**（比 spec 初稿的"Agent 内唯一"更严） | 执行权按标识分配：两个 Agent 各写一条 `daily` 会**静默互相挡**下一次触发——正是这个项目最不能忍的坏症状（无人值守时才现形）。注册期一条查重检查即可杜绝 | 只在 Agent 内查重、跨 Agent 重名不管：少 5 行，把一个静默互挡留在库里。经主公裁决取严（spec FR-004 / D7） |
| **调度线程池容量下限 2（取 4），不设配置键** | 容量为 1 时（Spring 默认口径）一条长任务会把**别的**任务到点的触发堵在池队列里——定时任务的卖点就是"到点就跑"，被别的任务堵住属语义损伤（D4） | ①容量 1：Spring 默认，但对本节的用途是明确有害的；②容量写成配置键：在没有实测依据前不引入调优旋钮（多一个键就多一份要解释、要测的东西） |
| **装配放 boot（`@Bean(initMethod = "registerAll")`），core 不放 `@Component` / `@PostConstruct`**（偏离课件骨架字面） | `fourfeetcat-core` 自第16节起**零 Spring 注解**，Bean 全部由 boot 显式装配；照课件加注解会让 core 首次引入组件扫描契约，还要为 `@PostConstruct` 把 `jakarta.annotation-api` 提为直接编译依赖。`initMethod` 是容器自带能力，零依赖（D5） | 照课件写：多一条扫描契约 + 一个直接依赖，只换来少写一行 `@Bean` |
| **时区的守点用行为断言（`nextExecution`），而非读注册参数里的字段**（偏离课件表格字面） | 实测：`CronTrigger` 没有时区读取方法，且 `equals` **不比时区**——上海与纽约的同一表达式判定相等。想验"时区没被吞掉"，公开面只有"让它算一次下一次执行时刻"这一条路（D2） | 反射读私有字段：脆，Spring 内部结构一变就红。**守点一字未改**（仍是"不让服务器时区替用户做主"），只是换了个更硬的验法 |
| **`Profile.schedules` 保持原始结构，解析放注册期** | 不动前序节的记录签名与加载器（diff 最小），且失败隔离更细：一条坏配置只废自己，不牵连同一 Agent 的其他条目（D6，经主公裁决） | 改成强类型列表由加载器解析：要动 `Profile` 记录 + `ProfileLoader` + 既有测试夹具的断言 |

## Phase 0 摘要

`research.md` 收敛 D1~D10 十项技术裁决，**无 NEEDS CLARIFICATION 残留**。其中 D1 来自主公裁决（cron 方言），D2 / D4 / D10 的关键事实来自解包工程锁定的 jar 实测。核心结论：

- **D1** 触发规则用框架原生 **6 段**（秒 分 时 日 月 周）；5 段写法硬拒（实测 `must consist of 6 fields`），不做兼容转换——"注册参数 = 配置原文"因此是一条可断言的守点。
- **D2** 时区的守点用 `nextExecution(TriggerContext)` 行为断言（上海 09:00 → 次日 01:00Z、纽约 09:00 → 当日 13:00Z）；`TriggerContext` 三方法需内联实现（返回 null 即"从当下起算"）。
- **D3** `AgentScheduler` 依赖 `TaskScheduler` **接口**，实现由 boot 装配。
- **D4** 调度池容量 ≥ 2（取 4）；并记清一条设计事实：框架"跑完才排期"使锁在核心阶段是**显式闸门**而非补丁。
- **D5** 装配全在 boot；core 保持零 Spring 注解。
- **D6** 定时条目保持原始结构，注册期解析、坏条目只跳自己。
- **D7** 标识显式优先、缺省 `profileName#序号`；进程内全体范围查重。
- **D8** 执行权表键 = 裸标识（对齐课件 harness 的 `lockFor("task-1")`）；`lockFor` 包内可见。
- **D9** 会话身份只递三元组 `("scheduler", "scheduler", profileName)`，拼接不越界。
- **D10** core 显式声明 `spring-context`（已在树中，非新增依赖）。

另登记三处**已知文档-代码差**：cron 方言与课件 5 段示例不一致、"Profile 新增 schedules 字段"其实第16节就已建（本节交付的是消费方）、骨架的 `@Component` 写法与本仓装配形态不同。

## Phase 1 摘要

- `data-model.md`：`ScheduleConfig` 四项与解析规则、标识派生与查重、Profile → 任务的配置模型、执行权模型（锁表 + 生命周期），以及"本节零持久化"的说明。
- `contracts/java-contracts.md`：新建类的对外成员清单（含哪些刻意不进对外契约）、四个坑对应的契约条款、`AgentService` / `SessionManager` / `ProfileRegistry` 三处既有契约的**只读/小幅新增**声明、Agent 配置里定时条目的四项契约（键名 / 类型 / 缺省 / 非法值行为）、以及本节不触碰的契约回归清单。
- `quickstart.md`：可复制的验证命令（只跑本节测试 / 全量门禁）+ 四条人工验收路径（真实到点触发、改 cron 不重编译、失败隔离与重叠跳过、审计同口径）。
