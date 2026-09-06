# FourFeetCat 项目宪章（Constitution）

> 本宪章定义 FourFeetCat 开发的非协商原则（non-negotiable principles），提炼自《FourFeetCat 需求文档》第 3 章设计目标与《FourFeetCat 技术方案》第 1.1 节关键技术决策。所有 spec、plan、tasks、implement 及社区贡献代码都必须遵守。
>
> **修订规则**：本宪章写一次定下来，整个主体开发期间不改。如果中途发现某条原则不对，停下来由项目方重新讨论修订；**不允许 AI agent 自行修改本宪章**。

---

## 原则一：JDK 21 + Spring Boot 3.x 单体应用

- Maven 多模块工程，模块清单以技术方案第 10 章为准（14 个模块）
- 骨架先立 9 个基础模块（core / provider / memory / tool / web / storage / cli / boot / channel-cli），persona、knowledge、channel-feishu、channel-wecom、channel-dingtalk 随对应能力演进而增补
- 单二进制 fat JAR 部署，`java -jar` 启动；GraalVM Native Image 放扩展阶段

## 原则二：五大核心能力优先

- 核心阶段交付的是 Agent OS 的**运行时内核**：对接 LLM、ReAct 循环、Memory、Tool 体系、Web Service
- 企业级治理层（多租户、SSO、完整审计、Tool Policy）放扩展阶段，但架构上预留扩展点
- 分阶段克制：每次架构升级用真实使用数据证明其必要性

## 原则三：自实现 ReAct loop + Spring AI 只用一半

- ReAct 核心循环由 FourFeetCat 自己实现（`ReActLoop` + `PromptBuilder` + `ToolExecutor`），不依赖 Spring AI 的 Agent 抽象
- Spring AI 只用三件事：Provider 抽象、协议转换、`@Tool` 注解的 JSON Schema 生成
- **禁用 Spring AI 的自动 tool 执行**——否则 tool 会被调两次；tool 的实际调度完全由 FourFeetCat 自己的 `ReActLoop` + `ToolExecutor` 控制。**这是最容易被写错的一条**
- 多 Provider 并存用 provider name 到 `ChatModel` 的**显式映射**，不靠类型扫描

## 原则四：一个目录 = 一个 Agent，且不是 Tool

- `AGENT.md` 正文由 `ContextLoader` 注入 system prompt（与 Bootstrap 文件同层）；frontmatter 由 `AgentLoader.deriveProfile()` 派生成 `Profile`
- **一个 Agent 目录不是一个可执行 Tool**——它的子资源（Skill 正文、参考、脚本）经底座既有 `read_file` / `shell` 按需取用，不新造机制、不进 `ToolRegistry`
- Skill 公共实体存 `.fourfeetcat/skills/<name>/`；Agent 通过自身 `skills/<name>` 相对软连接选择可见集合，**软连接集合是唯一绑定真相源**，不使用 frontmatter `skills:` 字段
- 渐进式披露：prompt 只注入已绑定 Skill 的 name / description / 本地路径，正文与附属资源按需读取

## 原则五：接口先行

- `Sandbox`、`NotifyChannelAdapter`、`LongTermMemoryStore`、`InboundChannelAdapter`、`ScheduledTaskStore` 等抽象接口不携带任何实现细节（用最重的实现去反向套接口，也应能干净套入）
- 扩展只新增实现类，不改接口、不改调用方
- 契约在 core、实现在外围模块（依赖倒置），如 `fourfeetcat-core/channel/` 与 Channel 适配器模块、`fourfeetcat-core/knowledge/` 与 `fourfeetcat-knowledge`

## 原则六：Plugin Tool 三档接入

1. 零代码：写 Agent 目录（AGENT.md）+ 复用社区现成 MCP server ——**主推**
2. 轻代码：用任何语言自写 MCP server
3. 重代码：Java `@Tool` 注解 Spring Bean

- 选择原则：能用方式一就不用方式二，能用方式二就不用方式三
- 内置 Tool 与 MCP Tool 统一包装成 `CatTool` 注册到 `ToolRegistry`，ReAct 循环不感知 Tool 来源

## 原则七：SQLite + Memory 三档后端 + 审计 day one

- 关系型持久化用 SQLite（Flyway 双轨：SQLite 为默认零配置档，PostgreSQL 为部署选项）；Session、审计、定时任务数据落库
- 长期记忆统一走 `LongTermMemoryStore` 接口墙，三档后端（`MarkdownMemoryStore` 默认 / `SqliteMemoryStore` / `Mem0MemoryStore`）靠 `memory.backend` 一行配置切换；核心/归档分区语义是必选能力
- **审计表 `tool_invocations` 和 `llm_calls` 核心阶段就写入落库**，不是只放日志——可审计的数据地基 day one 就立起来
- 凭证走 `${ENV_VAR}` 占位符从环境变量解析，不明文落地；不用 JDK 17 起已废弃的 `SecurityManager`

## 原则八：无状态实例，状态外置

- 运行实例不持有不可重建的状态，为单机走向分布式留好路
- 单机先行，分布式能力（多副本、高可用、跨节点 Agent 协作）分阶段演进

## 原则九：每个 user story 完成后有可演示 Demo

- 优先级是跑通而非完美
- 各 user story 以人推形态阶段性验证；最终三个验收 Demo（每日天气、每日科技日报、每日 GitHub 日报）以钟推形态完整跑通
