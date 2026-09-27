# Phase 0 Research: Memory 记忆能力（第22节）

**Input**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

本节的对账基准是主公提供的**参考实现第22节那一版**（其文件清单与课件"本节交付物"逐条成对；该版把"按 Agent 隔离"留给后续的"Agent 专属记忆"、把"跨档统一检索大小写"留给后续修订）。下面逐条记录本节的技术裁决；标注「已裁决」的条目来自主公本轮问答，其余为工程默认值并写明理由。

---

## D1 门面接口落 core（已裁决）

**Decision**：`MemoryService`（三方法）与 `MemoryScope`（CORE / ARCHIVAL）落 `fourfeetcat-core` 的 `memory` 包；实现在 `fourfeetcat-memory`。

**Rationale**：组装器 `PromptBuilder` 在 core、必须注入门面。若接口留在记忆模块，core → memory → core 成环——而依赖倒置是本仓的技术约束。上移后，"上层只认门面"在代码上成立，且上层**不依赖实现模块**。同第16节把 `ProviderService` 上移 core 的既有手法。

**Alternatives considered**：
- 接口留记忆模块、core 只持 `Function<Session,String>`：能避免契约上移，但"门面"退化成记忆模块的内部类，第21节"把 Memory 焊成一个稳定接口"在这条接缝上落不到代码——将来换后端时接缝类型也得跟着动。
- 在 core 再造一个最小端口、让门面实现它：多一层没有第二个实现者的抽象，且上层仍拿不到门面本身。

---

## D2 接缝形态：组装器直接注入门面（已裁决）

**Decision**：`PromptBuilder` 的第二构造参数由 `Function<Profile, String>` 改为 `MemoryService`；组装时 `memoryService.buildContext(session)`，返回值非空则作为一条独立的 system 消息插入；`memoryService == null` 时该段整体跳过。单参构造保留。

**Rationale**：课件把门面签名定死为 `buildContext(Session)`，与"按 Profile 供给一段文本"的函数类型对不上；Java 泛型擦除后两个函数类型的签名相同，无法用重载共存。直接注入门面是唯一既保住课件签名、又让上层真认门面的形态（也是参考实现的形态）。

**Alternatives considered**：保留原函数再在记忆模块包一层（组装器仍不认门面）；把函数类型换成"按会话供给"（能对上签名，但门面类型不进接缝，第21节的"墙"在这一处落空）。

**Affected prior artifacts**：`fourfeetcat-core/.../react/PromptBuilder.java`、`PromptBuilderTest.java`、`fourfeetcat-boot/.../AgentRuntimeConfiguration.java`（Bean 签名）。这是本节**唯一**触碰前序节公共接口之处。

---

## D3 分区枚举落点

**Decision**：`MemoryScope` 与门面同落 core。

**Rationale**：门面签名 `remember(String, MemoryScope)` 引用它；工具也引用它。枚举留在实现模块会让 core 的契约引用实现模块类型，与 D1 同一理由。

---

## D4 文件档（默认）：区块解析与写入形态

**Decision**：

| 项 | 取值 |
|---|---|
| 路径 | `<FOURFEETCAT_ROOT>/memory/MEMORY.md`（工作区根由环境变量决定，缺省 `.fourfeetcat`） |
| 分区标题 | `## 核心记忆` / `## 归档记忆`（字面量，逐字） |
| 条目行 | `- [yyyy-MM-dd] 内容`（`LocalDate.now()`） |
| 读取 | 文件不存在视作空串；取某区=从该标题之后到下一个标题之前的内容并 `strip()` |
| 写入 | 读全文 → 按分区追加 → **整文件重写**为「三行两区块」形态（父目录不存在则创建） |
| 截断 | 归档区文本长度 > 4000 时取**尾部** 4000 字符（只裁归档段——核心区不在入参里，契约二靠物理隔离保证） |
| 检索 | 按行过滤：非空行且包含关键词；**区分大小写**（逐字按课件） |
| 取上下文 | 返回带两个标题的文本：`## 核心记忆` + 核心区全量 + `## 归档记忆` + 归档区截断后 |

**Rationale**：课件逐字给出了这套形态；"只把归档段传进截断函数"是契约二最省的保证方式（物理上碰不到核心区）。

**已知后果**：写入即重写整文件，工作区初始化时生成的占位说明文字**不会保留**（首次写入后被规范化成两区块形态）。参考实现同款行为，属有意为之，已写进 spec 的 Edge Cases。

**Alternatives considered**：按行插入保留其余正文（解析规则复杂、超出"简单但有效"的口径）；把截断点对齐到条目边界（避免切出半截条目）——记忆压缩在明确不做之列，半截条目是简单截断的已知代价。

---

## D5 结构化库档：截断落成 LIMIT、检索落成库内匹配

**Decision**：

| 操作 | 手法 |
|---|---|
| 写入 | `repository.save(entry)`，`scope` = 枚举名，`content` = 原文，`createdAt` = ISO-8601 字符串（见 D12） |
| 核心区 | `findByScopeOrderByIdAsc("CORE")`——全量、按写入顺序 |
| 归档区 | `findByScopeOrderByIdDesc("ARCHIVAL", PageRequest.of(0, 100))` 取最近 100 条，再 `reversed()` 翻回时间正序——**LIMIT 只加在归档查询上**，契约二靠 SQL 结构保证 |
| 检索 | `@Query` 显式限定 `scope = 'ARCHIVAL'` + `content LIKE :pattern` + 按 id 正序 |
| 渲染 | 条目行 `- 内容`；返回文本同样带两个分区标题 |

**Rationale**：课件明写"截断变成 SQL 的 LIMIT，核心区不受影响——契约二靠 SQL 结构保证"。用派生查询 + `Pageable` 表达 LIMIT，避免写原生 SQL、跨方言（SQLite / PostgreSQL）都成立。

**Alternatives considered**：原生 SQL `LIMIT 100`（SQLite 与 PostgreSQL 语法一致，但派生查询更贴合仓库既有的 Spring Data 风格）；`findTop100By…`（把条数写死在方法名里，调水位就要改接口）。

---

## D6 LIKE 不做通配符转义

**Decision**：检索关键词按课件原样拼进 `LIKE` 模式（`%关键词%`），不转义 `%` 与 `_`。

**Rationale**：记忆正文与检索词都来自 Agent 自己的对话，是非对抗输入；课件明确"核心阶段就是简单的包含匹配，别一上来上正则或分词"。此取舍要写进实现注释，避免后来者以为是漏了转义。

**Alternatives considered**：加 `ESCAPE` 子句转义（超出"别做复杂"的口径，且文件档无法对齐同一语义）。

---

## D7 跨档检索大小写差异是已知状态（已裁决）

**Decision**：本节**不统一**大小写口径——文件档按行包含（区分大小写）、结构化库档的 `LIKE` 对 ASCII 不区分、外部服务档用它自带的检索。契约测试**不对大小写行为下断言**。

**Rationale**：对账结论——参考实现把"跨档统一不区分大小写"放在后续修订，课件本节也未提。跨档统一登记为后续节，本节不抢先。**这一条是本轮对账推翻的加强项之一**。

**Consequence**：契约测试只断言"同一关键词的命中/未命中在两个方向上一致"，不构造仅大小写不同的用例。

---

## D8 外部服务档：端点、容错与"地址缺失即拒"

**Decision**：

| 项 | 取值 |
|---|---|
| 写入 | `POST /v1/memories/`，体含 `messages[0].{role,content}`、`user_id`、`metadata.scope` |
| 读取 | `GET /v1/memories/?user_id={u}&scope={s}` |
| 检索 | `POST /v1/memories/search/`，体含 `query`、`user_id` |
| 响应解析 | 有 `results` 取 `results[].memory`，否则把根当数组；**空响应或解析不出 → 返回空列表**（结构随服务版本而异，不抛异常） |
| 服务端 5xx | `RestClient` 抛响应异常，**上抛不静默吞**（由工具执行路径记成失败结果回填给模型换招） |
| 地址缺失 | `memory.backend=mem0` 而 `memory.mem0.base-url` 为空 → **装配期明确报错并点名该配置键**，不静默回落到别档、也不把空串喂给 HTTP 客户端 |

**Rationale**：端点按该服务的社区版约定编写（实现注释写明"具体路径以部署版本为准"）；地址缺失的处置同时满足两条要求——spec 要求"未配置即明确报错"，且避免用未定义的输入去构造 HTTP 客户端基址。

**Alternatives considered**：调用时才报错（把配置错误推迟到第一次对话，排障链路更长）；回落 `markdown` 档（静默改语义，spec 明确禁止）。

---

## D9 静态检查：走既有排除清单，不放宽全局门禁

**Decision**：门面实现与外部服务档实现会在构造器里持有注入的协作者引用，SpotBugs 的 `EI_EXPOSE_REP2`（低阈值也会报）按仓库既有惯例在 `config/spotbugs/spotbugs-exclude.xml` 加**带理由**的按类排除项。

**Rationale**：仓库已有七处同因排除（`WebhookNotifyAdapter` / `HttpTools` / `McpToolAdapter` / `CliChannel` …），理由统一是"被注入的是共享的框架对象或不可拷贝的协作者，防御性拷贝没有语义"。本节**不引入** `spotbugs-annotations` 依赖，也**不放宽** threshold/effort。

**Alternatives considered**：加 `spotbugs-annotations`（provided）+ `@SuppressFBWarnings`（参考实现的做法）——本仓已有更集中的排除清单惯例，散在代码里的注解会让"为什么排除"离开清单、更难复核。

---

## D10 装配形态

**Decision**：全部落 `fourfeetcat-boot` 的既有 `AgentRuntimeConfiguration`（**不新建**自动配置文件、不新建 `spring.factories`/`AutoConfiguration.imports`——本仓业务模块无自动配置注册）：

1. `LongTermMemoryStore` Bean：读 `memory.backend`，`sqlite` → `SqliteMemoryStore(repository)`；`mem0` → `Mem0MemoryStore(以 base-url 为基址的 HTTP 客户端, user-id)`；`markdown` 或空 → `MarkdownMemoryStore(workspaceRoot())`；**其余取值抛异常点名该取值**（配置打字错不得静默当默认）。
2. `MemoryService` Bean：包住选中的后端。
3. `ToolRegistry` Bean：参数加门面，`registerAnnotated(new MemoryTools(memoryService))`——与既有内置工具同一行挂一个。
4. `PromptBuilder` Bean：参数加门面，传给组装器（D2）。
5. `fourfeetcat-boot/pom.xml` 加 `fourfeetcat-memory`（否则新类进不了容器也进不了打包产物）；`application.yaml` 加 `memory.backend` 与 `memory.mem0.*` 三键。

**Rationale**：`AgentRuntimeConfiguration` 就是本仓的装配落点（第18节起），跨模块端口 Bean 都在这里造；MCP 配置、沙箱临时装配同理。

---

## D11 契约测试：三档怎么凑齐、真库谁去验

**Decision**：

| 档 | 契约测试里用谁 | 真东西谁验 |
|---|---|---|
| 文件档 | `@TempDir` 下的真文件（零依赖，可直接用真的） | 同左 |
| 结构化库档 | **背靠内存 List 的有状态 mock 仓储**（`save`/两个派生查询/`@Query` 四个方法都给 answer） | `MemoryEntryRepositoryTest`（`@DataJpaTest` + 真 SQLite 文件库）——建表、LIMIT、LIKE 都由它验 |
| 外部服务档 | `InMemoryMemoryStore` 替身（进程内 Map，满足四条契约） | `Mem0MemoryStoreTest`（进程内假 HTTP 服务）——真实 REST 交互由它验 |

**Rationale**：课件 harness 原话就是"用一个'内存假 Mem0'替身替换掉真 REST 调用——契约测的是'这一档有没有守规矩'"。结构化库档若在契约测试里拉 Spring 容器，会把本可秒级的单测变成上下文启动测试，且真库的 SQL 语义在仓储测试里已经覆盖。

**Consequence**：契约测试必须用 `PER_CLASS` 生命周期，让参数化数据源工厂能访问实例注入的 `@TempDir`。

---

## D12 时间戳落库口径

**Decision**：`MemoryEntry.createdAt` 声明为 `String`（ISO-8601），由 `SqliteMemoryStore.append` 写入 `Instant.now().toString()`；**不用** JPA 生命周期回调。

**Rationale**：与仓库既有实体完全同口径（既有几张表的时间列都是 `String` + 调用方给 ISO-8601 字符串），且不把"谁来填时间"藏进容器回调里——单测里能直接断言。

**已知风险（沿用，不新增）**：PostgreSQL 轨的时间列是原生 `TIMESTAMP` 而实体声明 `String`，这是第18节就记录在案的跨轨口径风险（"不在本节修"）；本节沿用同一形态，不扩大风险面。

---

## D13 库档专属测试钉什么

**Decision**：`SqliteMemoryStoreTest` 用 mock 仓储 + 调用验证，钉住三件事：①核心区走"全量、按写入顺序"的那条查询；②归档区走"最近 N 条"的那条查询且**条数上限只出现在归档查询上**；③检索走"只在归档区内匹配"的那条查询。"LIKE 真的只命中归档"由真实 SQL 覆盖（D11 的仓储测试）。

**Rationale**：课件把库档专属测试的守点定为"截断/检索的落地方式与文件档不同"，而这些差异**正是查询方法的选择**——用调用验证最能直接钉住，且不需要拉容器。参考实现第22节未单列该类（它的真实 SQL 断言在仓储测试里），课件点名了，故本节补上，两边守点都覆盖。

---

## D14 记忆段的注入形态

**Decision**：记忆作为**独立的 system 消息**注入（在系统文本消息之后、历史消息之前），而不是拼进系统文本字符串。

**Rationale**：第17节的组装器就是这个结构（四部分里"长期记忆"自成一则消息），其既有测试也按消息序列断言。拼进文本会扩大前序节改动面，且丢掉了"这一段可以被单独看见"的可观测性。

**Alternatives considered**：参考实现拼进系统文本（那是它的组装器结构不同所致，不构成本节的取舍依据）。

---

## D15 前序节适配清单（逐条落实，其余一律不动）

| # | 文件 | 改什么 |
|---|---|---|
| 1 | `fourfeetcat-core/.../react/PromptBuilder.java` | 第二构造参数类型与字段；`build` 里改调门面 |
| 2 | `fourfeetcat-core/src/test/.../react/PromptBuilderTest.java` | 断言随接缝调整；补"未装配门面时逐字一致"的回归 |
| 3 | `fourfeetcat-core/.../core/package-info.java` | 跨模块契约清单补 memory 一句（原清单已列 channel / knowledge） |
| 4 | `fourfeetcat-boot/.../AgentRuntimeConfiguration.java` | 三个 Bean + `PromptBuilder` 参数 |
| 5 | `fourfeetcat-boot/pom.xml` | 加 `fourfeetcat-memory` |
| 6 | `fourfeetcat-boot/src/main/resources/application.yaml` | 加 `memory.backend` / `memory.mem0.base-url` / `memory.mem0.user-id` |
| 7 | `config/spotbugs/spotbugs-exclude.xml` | 按类排除 `EI_EXPOSE_REP2`（D9） |

**不在清单内的一律不动**（包括：工作区初始化模板的正文、`Profile` 字段、会话标识拼接、既有四张表与它们的实体/仓储、`ToolRegistry` 的注册管道本身）。

---

## D16 交付物外的两件（已裁决）

**Decision**：`InMemoryMemoryStore` 放**主源码**（不是测试源码），与参考实现一致；`MemoryModule` 为模块职责标记类。两件均经主公追认交付。

**Rationale**：替身要同时被契约测试、门面测试、工具测试三处复用；放测试源码虽也可行，但跨测试类共享需要额外可见性安排，且它是"满足同一套契约的第四档实现"——语义上是实现而非测试脚手架。标记类与仓库既有的 `package-info` 惯例一致，说明模块职责。

---

## D17 内置工具计数核对

**Decision**：本节交付后内置工具为 **9 个**（文件三 + 命令一 + HTTP 二 + 通知一 + 记忆二），与 README / 官网 / 设计文档既有的"内置九个"表述**正好一致**，三处文档**无需改动**。

**Rationale**：第20节交付 7 个，把"记事 / 检索记忆"两个登记为跨节；本节补齐后计数成立。这条要在验收报告里作为"内容三处同步铁律"的核对项写明结论。
