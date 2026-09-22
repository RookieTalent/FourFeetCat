# Implementation Plan: CLI 入口层与会话持久化（第18节）

**Branch**: `018-lesson18-cli` | **Date**: 2026-09-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-cli-session/spec.md`

## Summary

给 FourFeetCat 装上第一个"人推"入口：`FourFeetCatCli`（Picocli 根命令，12 个子命令）跑在 `fourfeetcat-cli`，`CliChannel`（读 stdin / 写 stdout、维护当前会话、`/quit` 退出）跑在 `fourfeetcat-channel-cli`。命令按"要不要跑引擎"分流：轻命令（`init`、`profile list/create/show/delete`、`provider list`、`tool list`）直接走标准文件 API、不打开容器；重命令（`chat`、`gateway` 走无 Web 容器，`serve` 走 Servlet 容器，`status`、`session list` 走无 Web 容器）才打开容器。

会话持久化地基一并交付：`sessions` 表（手工建表脚本双轨 V18）、`SessionEntity` + `SessionRepository` + `JpaSessionManager`（`fourfeetcat-storage`），`SessionManager` 接口在 `fourfeetcat-core` 从"只有 save"扩容为 `getOrCreate` / `get` / `save`，`session_id` 的拼接**只此一处**。对话历史以 JSON 存 `messages_json` 一列，编解码做在 storage（JDK 之外的 Jackson 已在编译类路径上，无需新增依赖）。

## Technical Context

**Language/Version**: Java 21（Maven 多模块，spring-boot-starter-parent 3.5.16）

**Primary Dependencies**: 本节**不新增任何第三方依赖**。新增的模块内依赖：`fourfeetcat-cli` 引入 `info.picocli:picocli`（4.7.7，父 pom 已 `dependencyManagement` 锁定，本地仓已有）、`fourfeetcat-channel-cli`（CliChannel）、`fourfeetcat-storage`（会话仓储）、`spring-boot`（`WebApplicationType` / `ConfigurableApplicationContext`）；`fourfeetcat-storage` 直依 `com.fasterxml.jackson.core:jackson-databind`（版本由 Boot 托管，随 `spring-ai-model` 已在编译类路径，按仓库既有口径"直依声明、不靠传递"显式声明）。Spring AI 消息类型的往返编解码用 Jackson 树模型 + 三种既有消息的构建器（已 `javap` 逐条核实，见 research.md D3）。

**Storage**: SQLite（默认，工作区 `.fourfeetcat/fourfeetcat.db`）+ PostgreSQL 双轨建表脚本 `fourfeetcat-storage/src/main/resources/db/migration/{sqlite,postgresql}/V18__sessions.sql`（同版本号、方言各自正确，逐字摘自 `docs/class/schema.sql` 的 `sessions` 段）；`ddl-auto: none`；测试建表走手工脚本 + `jdbc:sqlite:<临时目录>/test.db` 文件库（不用 `:memory:`）。

**Testing**: JUnit 5 + Mockito。单测（默认跑）：`SessionManagerTest`、`SessionRepositoryTest`（storage，课件 harness）；另加 `CliChannelTest`（channel-cli，见 Complexity Tracking 的口径说明）。零集成冒烟（本节不碰网络与真模型）；实现完成的定义是 `mvn clean verify` 全绿。

**Target Platform**: JVM 服务端（CLI 是打包后的胖 JAR 入口，`java -jar fourfeetcat-boot/target/*.jar <子命令>`，与 README 既有口径一致）

**Project Type**: cli（多模块底座的入口层 + 持久化层补充）

**Performance Goals**: 轻命令秒回（不打开容器，无 2~4 秒启动开销）；重命令启动开销不设专项指标

**Constraints**: 宪法原则五（审计 Day One 不回退）、原则六（文件操作在白名单内落地，本节只碰工作区自身目录）、原则七（同步，无 Reactor/CompletableFuture/自建线程池）；H4 不变量④（`session_id` 只在 `SessionManager` 内拼接）；语法禁区——避开 P3C/ASM 解析不了的 Java 18+ 形态（增强 switch 的 `default ->` 等）；测试方法名英文 + `@DisplayName` 保留课件中文原文。

**Scale/Scope**: 第18节交付物清单 + 一处运维必需的最小装配（boot 的应用装配类），不加不减；多出的 `CliChannelTest` 为交互壳层的行为守卫测试（见 Complexity Tracking）。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|---|---|---|
| 一：自实现 ReAct | 本节不碰循环；`chat` 经 `AgentService.process` 复用第17节引擎，CLI 层零 Agent 智能 | ✅ 通过 |
| 二：Spring AI 只用两件事 | 本节不新增任何模型调用路径；消息类型只用于会话历史的编解码（值对象消费），不引 `ChatClient`/自动工具执行 | ✅ 通过 |
| 三：Provider 显式映射 | 本节不碰；`provider list` 只读全局层声明的名字与 base-url，从不读 key | ✅ 通过 |
| 四：一个目录 = 一个 Agent | 本节沿用 Profile 目录口径（`profiles/` 下的 YAML），`agents/` + 软连接视图归 025~029 节（spec Clarifications 已记录口径，对外行为不变） | ✅ 通过（口径已记录） |
| 五：审计 Day One | 重命令打开容器即执行 Flyway V18；`chat` 走既有引擎路径，`llm_calls` / `tool_invocations` 写入不回退（`session list` 只读会话表、不改审计路径） | ✅ 通过 |
| 六：无 SecurityManager、真实路径校验 | 本节的文件操作只落工作区自身（`init` 建目录、profile 管理读写 `profiles/` 内文件）；不新增对外 IO 路径，不新增白名单维度 | ✅ 通过 |
| 七：同步执行 | 全链路同步阻塞；`gateway` 用阻塞主线程保活（不引异步类型、不自建线程池） | ✅ 通过 |
| 技术约束：Flyway 双轨 | 本节起 V18 继续双轨（sqlite + postgresql 同版本号）；第16节 V16 的 postgresql 缺档不在本节动（见 research.md D8） | ✅ 通过（已记录） |
| 技术约束：依赖倒置 | `SessionManager` 契约留 core、实现落 storage；core 不反向依赖 storage/cli | ✅ 通过 |
| 技术约束：模块结构 | 无新建、无改名模块；`boot` 的"主类 + 自动配置 + 依赖聚合"三项职责均不变（主类是胖 JAR 入口，装配在 boot） | ✅ 通过，无需同步 §10 |

无违宪项，Complexity Tracking 仅记录两处口径取舍（非违宪）。

## Project Structure

### Documentation (this feature)

```text
specs/003-cli-session/
├── plan.md              # 本文件
├── research.md          # Phase 0：9 条设计决策 + 依赖核实
├── data-model.md        # Phase 1：sessions 表（双方言）+ messages_json 编码格式
├── quickstart.md        # Phase 1：验证指南（判卷命令块 + 人工项）
├── contracts/           # Phase 1：跨模块 Java 契约
└── tasks.md             # /speckit-tasks 产出（本命令不创建）
```

### Source Code (repository root)

```text
fourfeetcat-core/src/main/java/org/fourfeetcat/core/session/
├── Session.java                        # 既有（第17节）：内存态会话，不动
└── SessionManager.java                 # 扩容：getOrCreate / get / save（去掉 @FunctionalInterface）

fourfeetcat-storage/src/
├── main/java/org/fourfeetcat/storage/
│   ├── SessionEntity.java              # 新增：JPA 实体（列名与 V18 逐字一致）
│   ├── SessionRepository.java          # 新增：JpaRepository<SessionEntity, String>
│   ├── JpaSessionManager.java          # 新增：实现 core 的 SessionManager 端口（@Component）
│   └── SessionMessagesJson.java        # 新增：包内私有，内存态消息 ↔ 一列 JSON 往返编解码
├── main/resources/db/migration/sqlite/V18__sessions.sql
├── main/resources/db/migration/postgresql/V18__sessions.sql
└── test/java/org/fourfeetcat/storage/
    ├── SessionManagerTest.java         # 课件 harness
    └── SessionRepositoryTest.java      # 课件 harness

fourfeetcat-channel-cli/src/
├── main/java/org/fourfeetcat/channel/cli/
│   └── CliChannel.java                 # 新增：读—转交—打印的壳 + /quit
└── test/java/org/fourfeetcat/channel/cli/
    └── CliChannelTest.java             # 交互壳层守卫（多出项，见 Complexity Tracking）

fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/
├── FourFeetCatCli.java                 # 新增：Picocli 根命令 + 懒启动容器的引擎工厂
├── InitCommand.java                    # 轻
├── ProfileListCommand.java             # 轻
├── ProfileCreateCommand.java           # 轻
├── ProfileShowCommand.java             # 轻
├── ProfileDeleteCommand.java           # 轻
├── ProfileCommand.java                 # 轻：profile 分组节点（Picocli 两段命令名的必需节点）
├── ProviderListCommand.java            # 轻
├── ProviderCommand.java                # 轻：provider 分组节点
├── ToolListCommand.java                # 轻（空实现占位，第20节换数据源）
├── ToolCommand.java                    # 轻：tool 分组节点
├── SessionCommand.java                 # 重（无 Web）：session 分组节点
├── ChatCommand.java                    # 重（无 Web）
├── StatusCommand.java                  # 重（无 Web）
├── SessionListCommand.java             # 重（无 Web）
├── ServeCommand.java                   # 重（Servlet）
├── GatewayCommand.java                 # 重（无 Web，阻塞保活）
├── CommandErrors.java                  # 新增：包内私有，统一"清晰报错、不抛栈"出口
└── Workspace.java                      # 新增：包内私有，工作区根路径解析 + init 结构模板

fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/
├── FourFeetCatApplication.java         # 改：main 改为"装配引擎工厂 + 交给 Picocli 分发"
└── AgentRuntimeConfiguration.java      # 新增：ProfileLoader/ProfileRegistry/ReActLoop/AgentService 等装配
```

**Structure Decision**: 入口层按技术方案 §8.4/§8.7 一分为二——命令树与轻命令落 `fourfeetcat-cli`，`chat` 的交互实现落 `fourfeetcat-channel-cli`（§8.4 明确 CliChannel 归该模块）。会话契约（`SessionManager`）留 `fourfeetcat-core`，实体/仓储/实现/编解码全落 `fourfeetcat-storage`（依赖倒置，与第16/17节同款手法）。所有 @Command 类**同包**（`org.fourfeetcat.cli`）：根命令对子命令暴露的"取引擎"方法是包内可见，分包会让它被迫升为 public 对外概念。装配（@Bean 与 Profile 加载）落 `fourfeetcat-boot`——它本来就是"主类 + 自动配置 + 依赖聚合"的模块，且 `@EnableJpaRepositories` / `@EntityScan` / `@SpringBootApplication(scanBasePackages)` 三件套已在其中声明，正是课件"坑四"要求显式声明的那处。模块无新建、无改名，无需同步 CLAUDE.md 模块表与 `docs/TechnicalSolution.md` §10。

**命令分级说明**：spec FR-004 的二分（轻 / 重）在本 plan 里被细化为三分——"轻"（不打开容器）、"重·无 Web 容器"（`chat`/`gateway`/`status`/`session list`）、"重·Servlet 容器"（`serve`）。**这不是第三类命令**，只是重命令内部按"要不要对外开端口"再分一次：`chat` 若误走 Servlet 容器会莫名占用 8080，故显式区分。判断标准仍只有一条：这条命令要不要跑引擎/碰容器里的 Bean。

**分组命令节点**：`profile` / `provider` / `session` / `tool` 是 Picocli 的分组节点（`@Command(name="profile", subcommands={...})`），自身无行为、无子命令时打印用法。它们是**两段命令名的必需机制**（Picocli 无法声明名为 `profile list` 的单节点），不计入 §8.7 的"12 个子命令"。

## Complexity Tracking

> 只记三处**口径取舍**，均非违宪；列在此处是为了让 reviewer 一眼看到"多出来/偏离了什么"。

| 事项 | 为什么需要 | 更简单的替代为何被否 |
|---|---|---|
| 多 4 个 Picocli 分组命令节点（`ProfileCommand`/`ProviderCommand`/`ToolCommand`/`SessionCommand`） | 两段命令名（`profile list`、`provider list` 等）是技术方案 §8.7 与 CLAUDE.md 的已定字面量，Picocli 无法用单节点声明两段名，必须经分组节点（主公已准加，`/speckit-analyze` 后补入） | 改成单段命令名（`profile-list`）：破坏已定字面量，否 |
| 多一个 `CliChannelTest`（课件 harness 只点名两个测试类） | 交互壳的 `/quit` 退出与空行跳过是唯一有分支的新逻辑，且属课件"人工项"里的第一条；人工项按纪律留人工，但逻辑不能没有机器守卫（写代码即留一个可跑的检查） | 全推给人工：`/quit` 写错就是"退不出去"的体感级故障，代价远大于 40 行测试 |
| 重命令的容器启动交给 boot 装配的引擎工厂（`chat`/`gateway` 走无 Web 容器、`serve` 走 Servlet 容器），而非命令内部各自 `SpringApplication.run` | 全工程只应有一个应用配置类（`FourFeetCatApplication`，三件套已在其上）：命令各自持有配置类会出现两份 `@SpringBootApplication`，胖 JAR 主类探测与上下文测试口径都会分裂；且 `chat` 若走 Servlet 容器会莫名占用 8080 端口 | 把应用配置类搬进 `fourfeetcat-cli`：`boot` 的"主类"职责与 CLAUDE.md 模块表、`docs/TechnicalSolution.md` §10、README 的 `java -jar fourfeetcat-boot/target/*.jar chat` 都要跟着改（三处同步铁律），本节不该付这个代价 |
