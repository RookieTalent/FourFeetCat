# 节级验收报告：Memory 记忆能力（第22节）

**Branch**: `022-lesson22-memory` | **Date**: 2026-09-26 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Tasks**: [tasks.md](tasks.md)

**结论**：课件"本节交付物"逐项落地，课件验收 harness 逐条对号，`mvn clean verify` 全绿。剩余人工项见 §6。

---

## §1 证据一：硬门禁 `mvn clean verify` 全绿

```
[INFO] BugInstance size is 0
[INFO] Error size is 0
[INFO] No errors/warnings found
[INFO] You have 0 Checkstyle violations.
[INFO] Reactor Summary for FourFeetCat 0.1.0-SNAPSHOT:
[INFO] FourFeetCat ........................................ SUCCESS [  3.197 s]
[INFO] FourFeetCat :: Core ................................ SUCCESS [ 15.043 s]
[INFO] FourFeetCat :: Provider ............................ SUCCESS [  7.347 s]
[INFO] FourFeetCat :: Storage ............................. SUCCESS [ 12.447 s]
[INFO] FourFeetCat :: Memory .............................. SUCCESS [ 10.283 s]
[INFO] FourFeetCat :: Tool ................................ SUCCESS [  9.847 s]
[INFO] FourFeetCat :: Channel CLI ......................... SUCCESS [  5.112 s]
[INFO] FourFeetCat :: Web ................................. SUCCESS [  4.016 s]
[INFO] FourFeetCat :: CLI ................................. SUCCESS [  5.182 s]
[INFO] FourFeetCat :: Boot ................................ SUCCESS [ 13.167 s]
[INFO] BUILD SUCCESS
```

四道静态门禁（Spotless/GJF + PMD7 `bestpractices`+`errorprone` + Checkstyle `google_checks` + SpotBugs `Max/Low` + FindSecBugs）全过，**SpotBugs findings 0、Checkstyle 违规 0、PMD 违规 0**。

单测总计 **152 项全绿**（core 40 / provider 8 / storage 20 / memory 34 / tool 43 / channel-cli 6 / boot 1）：

```
[INFO] Tests run: 40, Failures: 0, Errors: 0, Skipped: 0   (Core)
[INFO] Tests run:  8, Failures: 0, Errors: 0, Skipped: 0   (Provider)
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0   (Storage，含本节新增 4)
[INFO] Tests run: 34, Failures: 0, Errors: 0, Skipped: 0   (Memory，本节新增 34 中的 34 —— 本节前该模块为空)
[INFO] Tests run: 43, Failures: 0, Errors: 0, Skipped: 0   (Tool)
[INFO] Tests run:  6, Failures: 0, Errors: 0, Skipped: 0   (Channel CLI)
[INFO] Tests run:  1, Failures: 0, Errors: 0, Skipped: 0   (Boot，上下文装配)
```

---

## §2 证据二：课件 harness 映射表逐项对号

| 课件点名的测试类 | 落地文件 | 实测 | 守点 |
|---|---|---|---|
| `MemoryStoreContractTest` | `fourfeetcat-memory/src/test/.../MemoryStoreContractTest.java` | **12 项**（4 断言 × 3 档） | ① 截断只裁归档区、核心一字不能少 ② 写入后立刻可读（不许缓存）③ 分区路由正确 ④ 检索只搜归档区 |
| `MarkdownMemoryStoreTest` | 同模块 | 4 | 空文件；**缺区块标题按空处理**；截断边界（4000 恰好不裁 / 4001 才裁）；两区块互不串区 |
| `SqliteMemoryStoreTest` | 同模块 | 4 | 核心区查询**不带条数上限**、归档查询带 `PageRequest.of(0,100)`；归档渲染翻回时间正序；检索走"只在归档内匹配" |
| `Mem0MemoryStoreTest` | 同模块 | 5 | 写入体带内容 / 分区 metadata / 作用域标识；检索转发并解析；**响应体缺期望字段返回空列表不抛异常**；5xx 上抛；**连接被拒上抛不静默返回空** |
| `MemoryToolsTest` | `.../builtin/MemoryToolsTest.java` | 7 | 分区缺省与空串都落归档；显式核心；大小写归一；**非法分区点名报错且不落库**；未命中返回"没有找到相关记忆"；往返可查 |
| `MemoryServiceTest`（harness 表格写作 `MemoryServiceImplTest`） | `.../MemoryServiceImplTest.java` | 2 | 取到的上下文含长期记忆、核心区完整在内；记与查转发到底层（命名以 harness 表格为准） |
| （课件清单未列，对账补上） | `fourfeetcat-storage/src/test/.../MemoryEntryRepositoryTest.java` | 4 | 迁移脚本建出的表能存能读、三列真实存在；核心区全量不受上限影响；归档取最近 N 条且最新在前；库内匹配只命中归档 |

**课件写出代码的两条关键回归原样落地**（方法名英文化、`@DisplayName` 保留课件中文原文）：

- `truncationKeepsCoreIntact` ← 课件 `截断只裁归档区_核心记忆一字不能少`（三条断言逐条保真：核心在、归档最早的被裁、最近的保留）
- `writeIsImmediatelyReadable_noCache` ← 课件 `写入后立刻可读_不允许有缓存`
- 另加课件描述的"核心区关键词检索不到"一条（`recallSearchesArchivalOnly`）

**Mem0 档在契约测试里用内存替身**（课件原话的落地形态）；真实 REST 交互由该档专属测试用进程内假服务验证。

---

## §3 证据三：交付物逐项存在性核对

```
fourfeetcat-core/src/main/java/org/fourfeetcat/core/memory/MemoryService.java        ← 门面接口（跨模块契约）
fourfeetcat-core/src/main/java/org/fourfeetcat/core/memory/MemoryScope.java          ← 分区枚举
fourfeetcat-core/src/main/java/org/fourfeetcat/core/tool/PlainTextResultConverter.java ← 见 §7 说明（由上移而来）
fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/LongTermMemoryStore.java     ← 后端接口
fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/MarkdownMemoryStore.java     ← 档一（默认）
fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/SqliteMemoryStore.java       ← 档二
fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/Mem0MemoryStore.java         ← 档三
fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/InMemoryMemoryStore.java     ← 契约测试替身（交付物外，经主公追认）
fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/MemoryServiceImpl.java       ← 门面实现
fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/MemoryModule.java            ← 模块标记类（交付物外，经主公追认）
fourfeetcat-memory/src/main/java/org/fourfeetcat/memory/builtin/MemoryTools.java     ← save_memory / recall_memory
fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/MemoryEntry.java           ← 实体
fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/MemoryEntryRepository.java ← 仓储
fourfeetcat-storage/src/main/resources/db/migration/sqlite/V20__memory_entries.sql       ← 迁移（双轨）
fourfeetcat-storage/src/main/resources/db/migration/postgresql/V20__memory_entries.sql   ← 迁移（双轨）
```

- **表**：`memory_entries`（+ `idx_memory_entries_scope`），内容取自课程参考建表脚本 `docs/class/schema.sql` 该表一段
- **文件约定**：工作区 `memory/MEMORY.md`，两区块 `## 核心记忆` / `## 归档记忆`，条目行 `- [yyyy-MM-dd] 内容`
- **配置**：`memory.backend`（`markdown`|`sqlite`|`mem0`，缺省 `markdown`）、`memory.mem0.base-url`（`${MEM0_BASE_URL:}`）、`memory.mem0.user-id`（`${MEM0_USER_ID:fourfeetcat}`）
- **集成点**：`PromptBuilder` 记忆段改由门面供给（`buildContext(session)`）
- **测试类**：本节新增 7 个（课件点名 6 + 对账补 1）；前序节 `PromptBuilderTest` 随接缝调整（7 → 9 项）
- **文档三处核对结论**：内置 `@Tool` 方法实测 **9 个**，README / 官网 / 设计文档既有的"内置九个"表述**仍然正确，无需改动**

---

## §4 证据四：前序节回归全绿（跨节契约证据）

`mvn clean verify` 的 10 模块 SUCCESS 即前序节全部测试回归绿（core / provider / storage / cli / channel-cli / web / boot）。其中被本节触碰的两处已单独确认：

- `PromptBuilderTest`（接缝适配）：9 项全绿，含新增的"门面返回空白 ⇒ 记忆段整体跳过"与"记忆段与会话历史各注入一次"两条回归
- tool 模块 43 项全绿（转换器上移后的 import 改动未改变任何行为）
- `FourFeetCatApplicationTests`（boot 上下文）：装配了新的 `LongTermMemoryStore` / `MemoryService` Bean、注册了记忆工具、组装器注入了门面——上下文能起来即装配正确

---

## §5 证据五：H4 全局不变量自查（逐条，含复核命令）

| # | 不变量 | 结果 | 证据 |
|---|---|---|---|
| ① | 涉外 IO 首行过 `Sandbox.enforce` | ✅（含一处已声明的边界判断） | 记忆模块 `grep -c Sandbox` = **0**。本节唯一出网处是外部服务档的后端调用，它是**装配期按配置选定的后端**、不经工具执行路径（工具执行路径才受沙箱管）；该判断已写进 `Mem0MemoryStore` 类注释与 plan 的 Complexity Tracking 两处，避免被误判为漏检 |
| ② | LLM 调用与工具执行成败都落审计表 | ✅ | `grep -c ToolInvocationRecorder`（记忆模块）= **0**：零新增审计代码，记忆工具经既有 `ToolExecutor` → `ToolInvocationRecorder` 路径自动留痕；本节未建任何审计表 |
| ③ | 无明文 key | ✅ | 敏感串全模块唯一命中是注释里"不花 token"一词（非凭证）；配置只写 `${MEM0_BASE_URL:}` 与 `${MEM0_USER_ID:fourfeetcat}` 两个环境变量占位；代码里零硬编码地址与凭证 |
| ④ | `session_id` 只在 `SessionManager` 内拼接 | ✅ | `grep -c sessionId`（记忆模块）= **0**，本节未触碰拼接口径 |
| ⑤ | 无 Reactor / `CompletableFuture` / 自建线程池 | ✅ | 四者 grep 计数均为 **0**；外部服务档走同步 `RestClient`（宪法原则七） |
| ⑥ | 无 Spring AI 自动工具执行路径 | ✅ | `grep -c ChatClient` = **0**；记忆工具经 `registerAnnotated` → `AnnotatedToolAdapter` → `CallbackTool` → `ToolRegistry.execute` → `ToolExecutor` 唯一执行权路径 |
| ⑦ | 只读初始设定文件不被写 | ✅ | `grep -c USER.md`（记忆相关源码）= **0**；可写对象只有长期记忆本体 |

---

## §6 证据六：剩余人工项（harness 判不了，请主公过目）

课件"五、做完怎么验"里机器判不了的部分：

1. **三档切换的体感验证**：`memory.backend` 依次设 `markdown` / `sqlite`，各跑一轮对话，`save_memory` 写入、下一轮上下文带上——同一段对话、同一个体感，只是底下换了后端（文件 vs 表）。这是那道"墙"最直观的人工证据。
2. **外部服务档真连一次（可选）**：部署一个自托管 Mem0，`memory.backend: mem0` + `memory.mem0.base-url` 指向它，验证 `save_memory` 真的进了它、`recall` 能语义召回（本地无实例，测不了）。
3. **真模型完整走一遍**：对话里说一句值得记的话，Agent **主动**调 `save_memory`；开新会话，系统提示里带着核心记忆——"始终在场"在真实链路里的体感。
4. **两条启动报错文案**：① `memory.backend: markdwon`（打字错）→ 启动即拒并点名该取值；② `memory.backend: mem0` 而不配地址 → 装配期点名 `memory.mem0.base-url`。两条各起一次进程看一眼（已由装配代码保证，属进程级行为）。
5. **只读初始设定文件不被写**：code review 确认没有写该文件的代码路径（§5 第⑦条已给 grep 证据）。

---

## §7 实施期的裁决与偏差记录（逐条留痕）

| # | 事项 | 处置 |
|---|---|---|
| 1 | **对账推翻两条加强项**（specify 阶段一度采纳"按 Agent 隔离"与"跨档统一检索大小写"） | 寻获参考实现第22节版后经主公裁决**严格对齐**：本节为工作区全局单份、检索逐字按课件。两条登记为后续节（`tasks.md` T035 未勾选） |
| 2 | **门面接口落 core**（非记忆模块） | 组装器在 core 必须注入门面，接口留在实现模块会成环；同上位手法见 `ProviderService` 上移（第16节） |
| 3 | **配置键用顶层 `memory.*`**（撤回第一轮的 `fourfeetcat.` 前缀方案） | 按参考实现既有形态逐字保留 |
| 4 | **跨节触碰：`PlainTextResultConverter` 上移到 core**（T041） | 记忆工具在 memory 模块拿不到 tool 模块的转换器，而九个内置工具必须同一套返回形态。触发软门禁（改前序节公开类型 FQCN），经主公裁决为"上移到 core"；tool 模块五处 import 同步改，行为零变化 |
| 5 | **PMD 两处门禁修正**（不改语义） | ① `MemoryModule` 的私有构造触发 `MissingStaticMethodInNonInstantiatableClass` → 去掉私有构造（与参考实现一致）；② boot 装配的 `if ("sqlite".equals(...))` 触发 `AvoidLiteralsInIfCondition` → 改为**常量 + 经典 switch**（同时守住"不用增强 switch 的 `default ->`"这条语法禁区） |
| 6 | **Spotless 重排了主公未提交的注释缩进** | `AnnotatedToolAdapter.java` 里主公手写的块注释被 GJF 重新对齐（**内容一字未改**）——不改则 Spotless 门禁不过。特此明示，若主公希望保留原缩进，需改写为行注释或加 Spotless 例外 |
| 7 | **analyze 的六处文档级修正**（不改设计） | ① `FR-017` 由"会话与长期记忆同一门面统一对外"改写为分工口径（原表述与对账结论自相矛盾）② `FR-018` 由与 `FR-014` 重复改写为"留痕完整性"的承载条款（保 ID 不重排）③ `FR-012` 补取值字面量 ④ `SC-010` 收窄为"默认档可启动 + 另两档转人工项"⑤ `T025` 补"缺区块标题"用例 ⑥ `T027` 补"连接被拒上抛 / 响应体缺字段返回空列表"两条用例；另 `T017`/`T018` 补实现注释要求，`T038` 补只读文件 grep 自查项 |
| 8 | **未动的内容**（遵范围边界） | 工作区初始化模板、`Profile` 字段、会话标识拼接、既有四张表及其实体/仓储、工具注册管道本身；README / 官网 / 设计文档三处**零改动**（内置工具数正好凑齐 9） |

**本节全程未 commit / push / 跑 package.sh**，同步时机由主公决定。
