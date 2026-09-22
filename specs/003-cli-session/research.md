# Phase 0 Research: CLI 入口层与会话持久化（第18节）

**Input**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

本文件记 9 条设计决策。"已核实"字样表示在**本地依赖**里 `javap` / 源码逐条对过，不是凭记忆。

---

## D1 主入口落位：胖 JAR 主类在 boot，命令树在 cli

**Decision**: `fourfeetcat-boot` 的 `FourFeetCatApplication.main(args)` 保留为胖 JAR 的唯一主类，它构造 Picocli 命令树并把 `args` 交给它执行；`FourFeetCatCli`（`fourfeetcat-cli`）是命令树的根，不写第二个 `public static void main`。

**Rationale**:
- CLAUDE.md 模块表与 `docs/TechnicalSolution.md` §10 都写 `fourfeetcat-boot` = "主类、自动配置、依赖聚合"，`fourfeetcat-cli` = "命令入口"。把 `main` 放 boot 是**两份文档的现有口径**，不需要任何文档同步。
- README 第 151 行既有用法 `java -jar fourfeetcat-boot/target/*.jar chat` 明确：胖 JAR 由 boot 产出、子命令作为参数传入——即"JVM 主入口在 boot、子命令树在 cli"。这条是硬件级约束（改了要动 README + 官网 + docs 三处）。
- `boot` 已依赖 `fourfeetcat-cli`（既有 pom），方向天然成立，无循环依赖。

**Alternatives considered**:
- 把 `FourFeetCatApplication` 搬进 `fourfeetcat-cli`、boot 退化为纯聚合模块：会同时改 CLAUDE.md 模块表、`docs/TechnicalSolution.md` §10 与 README（三处同步铁律），且 `boot` 的上下文加载测试要跟着搬家。本节不该付这个代价。
- 命令各自 `SpringApplication.run`：见 D2。

---

## D2 重命令的容器启动：boot 装配一个引擎工厂，命令按需懒取

**Decision**: `FourFeetCatCli` 的构造器收一个引擎工厂 `Function<WebApplicationType, ConfigurableApplicationContext>`（JDK 的 `Function` + Spring Boot 的 `WebApplicationType`，**不新造接口类型**）；boot 的 `main` 传入 `type -> new SpringApplicationBuilder(FourFeetCatApplication.class).web(type).run()`。根命令提供包内可见的 `engine(WebApplicationType)`：首次调用才启动、同进程内只启一次。重命名的分类由命令自身表达——`chat` / `gateway` / `status` / `session list` 取 `WebApplicationType.NONE`，`serve` 取 `WebApplicationType.SERVLET`；轻命令一条都不取，因此容器根本不会被启动。

**Rationale**:
- 宪法/课件要求"轻命令不启动 Spring"。让"要不要引擎"成为**命令自己的事**（用了才启动），比维护一张"哪些命令算重命令"的名单更不容易漂——名单会随着后续节加命令而忘记更新，而"没调就没启"结构上不会错。
- `chat` 若是走 Servlet 容器，会顺带把 Tomcat 起到 8080——用户只是想在终端里聊天，却撞上端口占用，这属于"莫名行为"。用 `WebApplicationType.NONE` 明确区分。
- 命令内直接 `SpringApplication.run(FourFeetCatApplication.class)` 会要求命令持有应用配置类，而该类在 boot（cli 不能反向依赖 boot）——所以由 boot 把启动动作作为函数注入，是唯一不破坏依赖方向的做法。

**Alternatives considered**:
- 新造一个 `EngineLauncher` 接口：功能相同但凭空多一个只有一个实现的对外类型，否。
- boot 先 `parseArgs` 判断轻重再决定是否启动：需要维护轻重名单，且 `SpringApplication.run(args)` 会把 `--profile` 当 Spring 属性读，否。

---

## D3 `messages_json` 的编解码：Jackson 树模型 + 三种既有消息的构建器

**Decision**: 在 `fourfeetcat-storage` 加一个**包内私有**的 `SessionMessagesJson`，用 Jackson `ObjectMapper` 的树模型（`ArrayNode` / `ObjectNode`）把会话里的消息列表写成一条 JSON 数组、并能读回来。格式：

```json
[{"type":"user","text":"..."},
 {"type":"assistant","text":"...","toolCalls":[{"id":"..","type":"..","name":"..","arguments":".."}]},
 {"type":"tool","responses":[{"id":"..","name":"..","responseData":".."}]}]
```

读取时按 `type` 分派；**未知 type 直接抛异常**，不静默丢弃（丢消息 = 下一轮模型看到残缺上下文，最难查的一类软故障）。

**Rationale（本地核实）**:
- 会话里只会出现三种消息（`ReActLoop` 只 `appendUserMessage` / `appendAssistantMessage` / `appendToolResponses`；system 消息每轮由 `PromptBuilder` 现拼、不入会话）——正是这三种，不多不少。
- Spring AI 1.1.2 的这三个类**没有 Jackson 反序列化入口**（构造器是 `UserMessage(String)` / 受保护的 `AssistantMessage(String,Map,List,List)` / 受保护的 `ToolResponseMessage(List,Map)`，无 `@JsonCreator`），直接 `readValue` 无法往返。**已 `javap` 核实**可用的重入口：
  - `new UserMessage(String)` 与 `UserMessage.builder().text(..).build()`
  - `AssistantMessage.builder().content(String).toolCalls(List<ToolCall>).build()`
  - `ToolResponseMessage.builder().responses(List<ToolResponse>).build()`
  - `AssistantMessage.ToolCall` 是 `record(id, type, name, arguments)`、`ToolResponseMessage.ToolResponse` 是 `record(id, name, responseData)`（都有规范构造器，可直接重建）
- 不用 Jackson 的 `activateDefaultTyping`（多态类型注入）：那会把类名写进数据、并打开一条反序列化攻击面（FindSecBugs 对 `enableDefaultTyping` 有告警），而这里只需要三个已知形状。

**Alternatives considered**:
- Jackson 多态反序列化 + mixin：仍要处理受保护构造器与 builder，代码更多且更脆。
- 自己手写 JSON 解析：没必要，Jackson 已在编译类路径上（见 D4）。

---

## D4 依赖：本节零新增第三方依赖

**Decision**: 不新增任何第三方依赖。新增的是**模块内既有依赖的声明**：

| 模块 | 新增声明 | 版本来源 | 说明 |
|---|---|---|---|
| `fourfeetcat-cli` | `info.picocli:picocli` | 父 pom `dependencyManagement` 已锁定 `${picocli.version}`=4.7.7 | 本地仓 `~/.m2/.../picocli/4.7.7` 已存在（**已核实**） |
| `fourfeetcat-cli` | `fourfeetcat-channel-cli` | 父 pom 已统一管理 | 用 `CliChannel` |
| `fourfeetcat-cli` | `fourfeetcat-storage` | 同上 | `session list` 用会话仓储 |
| `fourfeetcat-cli` | `org.springframework.boot:spring-boot` | Boot 托管 | `WebApplicationType` / `ConfigurableApplicationContext` |
| `fourfeetcat-storage` | `com.fasterxml.jackson.core:jackson-databind` | Boot 托管 | 按仓库既有口径"直依声明、不靠传递"（与 `spring-ai-model` 的声明口径一致） |

**Rationale**: `jackson-databind` 与 `picocli` 都已在锁定 BOM / 父 pom 里管好版本，且 Jackson 随 `spring-ai-model` 已在 storage 的编译类路径上（`mvn -o dependency:tree -pl fourfeetcat-storage` **实测**：`jackson-databind:2.21.4:compile`、`jackson-core:2.21.4`、`jackson-datatype-jsr310:2.21.4`），**无需联网下载任何新东西**。

**Alternatives considered**: 用 SnakeYAML 顶替 Jackson 做往返（YAML 是 JSON 超集）——能把依赖表缩一行，但"用一个数据格式的库当另一个的解析器"是隐式耦合，读者要在两处之间来回跳，不值。

---

## D5 `Session` 的类名冲突：持久化实体定名 `SessionEntity`

**Decision**: 内存态会话保持 `org.fourfeetcat.core.session.Session` 不动；持久化实体定名 `org.fourfeetcat.storage.SessionEntity`（映射 `sessions` 表），仓储 `SessionRepository`（主键类型 `String`），端口实现 `JpaSessionManager`。

**Rationale**: 主公已于 clarify 阶段裁决（见 spec Clarifications）。改动只落 storage 侧四个新文件，前序节零改动；把内存态会话搬进 storage 会让 core 反向依赖 JPA，违宪。

**Alternatives considered**: `StoredSession`（与仓库既有实体命名风格不齐）；把 `Session` 整体搬进 storage（违宪，已否）。

---

## D6 `SessionManager` 接口扩容签名

**Decision**:

```java
public interface SessionManager {
  Session getOrCreate(String channel, String user, String profileName);
  Optional<Session> get(String sessionId);
  void save(Session session);
}
```

去掉第17节为单一抽象方法加的 `@FunctionalInterface`（该注解旁的注释已写明"第18节补 getOrCreate/get 后本注解即去掉"——这是课件点名的改造点，不属软门禁）。

**Rationale**: `get` 用 `Optional` 与仓库既有取值口径一致（`ProfileRegistry.find` 返回 `Optional`）；返回 `null` 会迫使每个调用点写判空，而"没查到"是正常业务分支（不该抛异常）。`getOrCreate` / `save` 保持第17节既有语义不变，`AgentService.process` 里的 `sessionManager.save(session)` 一行不改。

**Alternatives considered**: `get` 抛异常——"会话不存在"不是错误，是"还没开始聊"，否。

---

## D7 轻重命令的分流名单与判断标准

**Decision**: 判断标准是"这条命令要不要跑引擎 / 碰容器里的 Bean"。据此：

| 类别 | 命令 | 依据 |
|---|---|---|
| 轻（不打开容器） | `init`、`profile list/create/show/delete`、`provider list`、`tool list` | §8.7 点名 `init`、`profile list` 为轻；另三条只读文件/占位输出，无 Bean 可依赖 |
| 重 · 无 Web 容器 | `chat`、`gateway`、`status`、`session list` | 要跑引擎或读会话库 |
| 重 · Servlet 容器 | `serve` | 对外提供 REST（§8.6） |

`provider list` 读的是**打包内的全局层声明**（`application.yaml` 的 `fourfeetcat.providers`），只打印 provider 名与 base-url，**永不打印 key**（key 只有 `${ENV}` 占位或环境变量值）；`tool list` 按主公裁决输出"当前无可用工具"，第20节交付工具注册后只换数据源。

**Rationale**: §8.7 只点名了 `init`/`profile list`（轻）与 `chat`/`serve`/`gateway`（重），其余七条按同一条标准推得，避免"全轻"（`session list` 读不到库）或"全重"（`profile list` 等 4 秒）两个极端。`gateway` 在本节没有 IM 通道可挂（通道模块归后续节），故与 `chat` 同为无 Web 容器并阻塞主线程保活（守护进程语义，宪法原则七：同步阻塞，不引异步）。

**Alternatives considered**: `provider list` 改成重命令去容器里取活 Bean——会把"看一眼配置"变成 4 秒启动，且读不到用户用 `--spring.config.location` 覆盖的值（那属于运行的容器，不是命令行该管的事），否。

---

## D8 建表脚本：V18 双轨；第16节 postgresql 缺档不在本节动

**Decision**: 新增 `db/migration/sqlite/V18__sessions.sql` 与 `db/migration/postgresql/V18__sessions.sql`，同版本号、同列名同约束、方言各自正确。sqlite 逐字摘自 `docs/class/schema.sql` 的 `sessions` 段；postgresql 版把三个时间戳落 `TIMESTAMP`（对齐 V17 的双轨写法）。

**Rationale**: 宪法"技术与架构约束"要求双轨各一份、只增不改；第17节已按此落地 V17。**已知的跨轨口径风险（记录，不在本节修）**：第16节的 V16 只有 sqlite 轨、缺 postgresql 双生脚本；且实体侧时间戳字段用 `String`（V17 的 `ToolInvocation.createdAt` 即如此），在 postgres 轨的 `TIMESTAMP` 列上读写会不匹配。这是前序节遗留，修它属于改前序节交付物，按纪律不静默动手，写进验收报告的交人部分。

**Alternatives considered**: 顺手补 V16 的 postgresql 脚本（前序节交付物，须先请主公裁决）。

---

## D9 装配：@Bean 落 boot，`ToolTable` 用空实现占位

**Decision**: 在 `fourfeetcat-boot` 加 `AgentRuntimeConfiguration`，装配 `ProfileLoader`（读 `.fourfeetcat/profiles/`，已知 provider 名取自全局层）→ `ProfileRegistry` → `ContextLoader(workspaceRoot)` → `PromptBuilder(contextLoader)`（长期记忆未启用，用单参构造）→ `ToolTable`（**包内私有空实现**：`descriptors` 对空名单返回空表、对非空名单给出清晰报错；`execute` 直接报"工具未注册"）→ `ToolExecutor(toolTable, recorder)` → `ReActLoop(promptBuilder, llmCaller, toolExecutor)` → `AgentService(profileRegistry, reActLoop, sessionManager)`。`LlmCaller` / `LlmCallRecorder` / `ToolInvocationRecorder` 三个 Bean 分别由既有 `ProviderConfiguration` 与 storage 的两个 `@Component` 实现提供，本节零改动。

**Rationale**:
- `chat` 要真能跑通（Demo 一对话版），这四个 Bean 必须有；工作区根路径解析与 `FOURFEETCAT_ROOT` 口径已在 `FourFeetCatApplication.main` 与 `application.yaml` 里定死，boot 是天然的落点（"自动配置"职责）。
- `ToolTable` 的真实实现在第20节（统一工具抽象 + 工具注册表）。本节只留最小空实现，**不引入任何工具实现**（spec Assumptions 已声明），且它是包内私有类——不进对外概念面。
- 空实现对非空工具名单**报错而不是静默返回空**：Profile 声明了工具却拿不到描述，模型会无从下手；报错才查得动（对齐 `ToolTable` 接口既有注释的口径）。

**Alternatives considered**: 把装配放 `fourfeetcat-cli`——那会让 cli 依赖 provider 模块（`LlmCaller` 的 Bean 来自 provider），入口层反向耦合业务能力域，否。
