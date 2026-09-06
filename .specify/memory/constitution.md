<!--
## Sync Impact Report

- Version change: 1.1.0 (九条原则，文件已被 Spec Kit 初始化重置为空模板) → 2.0.0
- Version bump rationale: MAJOR —— 原则集由九条重构为八条，且编号重排（Spring AI 两件事：旧四→新二；
  审计表 Day One：旧六→新五；"一个目录 = 一个 Agent"保持原则四不变）。编号重排使
  docs/AiProgrammingGuide.md 中两处旧编号交叉引用（§263 "constitution 原则四"、§361
  "constitution 原则六"）失效，属向后不兼容变更。
- 保留不变（从上一版 v1.1.0 及 CLAUDE.md 继承）：原则四编号与语义（TechnicalSolution.md
  多处交叉引用"宪法原则四"= 一个 Agent 目录不是可执行 Tool）；模块结构可按需演进的 v1.1.0 修订精神。
- Added sections: 「技术与架构约束」「开发工作流」（对应模板 SECTION_2/SECTION_3）
- Removed sections: 无（原文件为未填充模板）
- Follow-up TODOs:
  - TODO(DOCS_ALIGNMENT): docs/AiProgrammingGuide.md §263 与 §361 的 constitution 原则编号
    仍指向旧版九条宪法的编号（四→现为二，六→现为五），需单独一次 docs 修订对齐，并按铁律
    同步检查 README/官网是否受影响。
-->

# FourFeetCat（四脚猫）Constitution

本宪法是 FourFeetCat —— 面向企业场景的 Distributed AI Agent OS（Java 21 + Spring Boot 3）
—— 的最高开发准则。所有特性规格（spec）、实施计划（plan）与代码 MUST 遵守本宪法；
与 CLAUDE.md 中「不可违背的原则」同源，冲突时以本宪法为准。

## Core Principles

### 原则一：自实现 ReAct Loop

`ReActLoop` MUST 自行实现，不得使用 Spring AI 的 Agent 抽象（如 `ChatClient.prompt().call()`
的自动工具执行）。核心循环约数十行 Java，完整掌握 Agent 工作机制，保留未来定制循环行为的空间。
理由：底座的核心价值在于对 Agent 工作机制的完全掌控，而非依赖框架黑盒。

### 原则二：Spring AI 只用两件事 ⚠️

Spring AI 在本项目中 MUST 只承担：

1. LLM Provider 协议转换（OpenAI / Anthropic / Gemini 等格式差异由它吸收）；
2. `@Tool` 注解的 JSON Schema 生成。

MUST 禁用 Spring AI 的自动 tool 执行。Tool 的调度和执行完全由 `ReActLoop` +
`ToolExecutor` 控制。违反将导致 tool 被调两次（可测试：同一 tool 调用在
`tool_invocations` 审计表中不得出现重复执行记录）。

### 原则三：Provider 必须显式映射

多 Provider 并存时 MUST 维护 `provider name → ChatModel` 的显式映射表
（`Map<String, ChatModel>`），不得靠扫描 Spring 容器中的 `ChatModel` Bean 类型区分
（Bean 类型相同，无法区分）。可测试：任一 LLM 调用 MUST 能通过映射 key 唯一确定
provider，路由错乱即违宪。

### 原则四：一个目录 = 一个 Agent；Skill 以本地软连接绑定并渐进披露

**编号不可变动**：`docs/TechnicalSolution.md` 多处交叉引用"宪法原则四"。

- 一个 Agent = `.fourfeetcat/agents/<name>/` 一个目录：`AGENT.md`（frontmatter 运行配置 +
  正文任务指令）、可选 `skills/`、`scripts/`、`REFERENCE.md`。`AgentLoader.deriveProfile()`
  把 frontmatter 派生成 `Profile`。
- 公共 Skill 实体统一存放 `.fourfeetcat/skills/<name>/`；Agent 可见的 Skill 只由
  `agents/<agent>/skills/<name>` 下指向公共实体的**相对软连接**表达——软连接集合是唯一
  绑定真相源，`AGENT.md` frontmatter 不声明 `skills:`。
- 加载走三层渐进式披露：每轮 prompt 只注入绑定 Skill 的 `name + description + 本地绝对
  读取路径`；模型命中后用 `read_file` 读 `SKILL.md` 正文；附属资源按需读取或运行。
  不得预载正文、不得新增 `use_skill`、Skill 不进 `ToolRegistry`。
- 一个 Agent 目录**不是可执行 Tool**：`AGENT.md` 解析归 `ContextLoader`（正文注入
  system prompt，子资源经 `read_file`/`shell` 取用）。
- CRUD 与启动恢复 MUST 检测 dangling / escaped / invalid-target / name-mismatch /
  stale-reference；公共 Skill 被引用时默认拒绝删除并返回引用 Agent。

### 原则五：审计表 Day One 写入

`tool_invocations` 和 `llm_calls` 两张审计表 MUST 自核心阶段起写入（不需要查询接口，
但写入不能省）。不得以"日志够了"为由跳过落库——可审计是 FourFeetCat 的核心差异化能力。

工具治理层（020）为沙箱白名单之上独立的减法层：全局/Agent 级工具 allow/deny
（`tool_policy_rules` 表，管理台可编辑热更新）。策略与沙箱正交：策略管「这个 Agent 能不能
用这个工具」，沙箱管「执行时能碰什么资源」；策略放行不豁免沙箱。被策略拒绝的调用照写
`tool_invocations` 且带 `blocked_by='policy'` 标记。

### 原则六：不使用 Java SecurityManager；软连接必须校验真实路径

`SecurityManager` 在 JDK 17 起废弃、JDK 21 已不可用，MUST NOT 使用。Sandbox 通过
`SandboxChecker` 的白名单实现：

- 文件：路径白名单（`file.allowed_paths`）；文件目标存在时 MUST 用 `toRealPath()` 校验
  真实路径仍位于白名单根，新建路径校验最近存在父目录的真实路径。
- Shell：可执行文件精确白名单（`shell.allowed_commands`）；参数 argv 直传，不解释
  Shell 语法。将解释器列入白名单是管理员对本机代码执行权限的显式授予，不构成隔离。
- HTTP：域名通配符白名单（`http.allowed_domains`）。
- SMTP：端点白名单（`smtp.allowed_endpoints`，按 `host:port` 精确放行，端口缺省=任意，
  空=deny-all）。
- Skill 绑定只允许指向 `.fourfeetcat/skills/` 的相对软连接，拒绝绝对链接与越界链接。

### 原则七：同步执行模型

核心阶段全程同步阻塞，配合 Java 21 Virtual Thread 处理并发。MUST NOT 引入 Reactor /
WebFlux / CompletableFuture 等异步编程模型（SSE 流式响应放扩展阶段）。理由：保持核心
简单可控，Virtual Thread 已自动处理 IO 等待。

### 原则八：Tool 模块三合一

内置 Tool、MCP Client 合并在一个 `fourfeetcat-tool` 模块，MUST NOT 拆成多个模块。
`AGENT.md`（及 Agent 目录里的子指令）加载归 `fourfeetcat-core` 的 `ContextLoader`，
不得放进 Tool 模块（否则 Agent 目录被当 Tool 注册，执行时报错）。

## 技术与架构约束

- **语言与运行时**：Java 21（必须，virtual thread 处理并发）+ Spring Boot 3.x；命令行
  用 Picocli，YAML 用 SnakeYAML，日志用 Logback + SLF4J 结构化 JSON，构建为 Maven 多模块。
- **持久化**：SQLite（默认零配置）/ PostgreSQL 14+（url 自动识别）+ Spring Data JPA。
  表结构由 Flyway 管理，迁移脚本在 `fourfeetcat-storage` 的
  `db/migration/{sqlite,postgresql}/` 双轨目录各写一份（同版本号）、只增不改。MUST NOT
  依赖 `hibernate.ddl-auto=update`（保持 `none`）。
- **模块化与依赖倒置**：模块间通过接口解耦，新增 Channel 或 Tool 只加新模块、不改
  `fourfeetcat-core`；跨模块契约（接口 + 值对象）放 `fourfeetcat-core`，由下游模块实现。
  MUST NOT 出现模块间循环依赖。模块结构可按需演进（v1.1.0 修订）：新建/改名模块 MUST 在
  对应特性 plan 里声明理由，并同步更新 CLAUDE.md 模块表与 `docs/TechnicalSolution.md` §10。
- **配置与凭证**：敏感配置（API key、MCP server 凭证）MUST 通过环境变量注入，不得明文
  写在 Profile YAML。落库凭证经主密钥 AES-GCM 加密（`enc:v1:` 前缀），
  `FOURFEETCAT_MASTER_KEY` 优先，缺省 `.fourfeetcat/master.key` 首启自动生成；密钥不
  匹配启动即拒。`ConfigLoader` 启动时做必填项与格式校验，不得静默失败。
- **无状态实例、状态外置**：会话、审计、记忆全部落库/落盘，实例可随时重启与横向扩展
  ——这是走向分布式架构而不需要大改设计的前提。
- **Docker 部署（纯增量形态）**：镜像内不跑 Maven（jar 平台无关，由构建方原生构建一次，
  Dockerfile 只 COPY 胖 jar 进 JRE 基础镜像）；全部状态在 `/data` 卷；镜像非 root
  （uid 1000）+ 内置 healthcheck。改 Dockerfile/entrypoint/.dockerignore 时
  `ci.yml` 的 `docker-build` 门禁自动验证。

## 开发工作流

- **Spec Kit 流程**：特性开发走 `/speckit-specify` → `/speckit-clarify` → `/speckit-plan`
  → `/speckit-tasks` → `/speckit-implement`；本宪法是所有 spec/plan/tasks 的上位约束，
  违宪的特性文档在 analyze 阶段应被指出。
- **实施节奏**：四周节奏（Provider+ReAct → Memory+Tool → Web → 多 Agent 收尾），每个
  能力以对应验收 Demo 收口（`fourfeetcat chat` 查天气穿衣 / 跨对话记偏好 / 零代码 PR
  digest / 10 个 REST 端点联动）。
- **内容三处同步铁律**：`README.md`、官网首页（`website/.vitepress/theme/Home.vue` 双语）、
  `docs/` 设计文档是同一事实的三个呈现。任何定位/特性/架构表述变更 MUST 三处一起改，
  防止漂移。
- **先 grep 再改编号**：修订宪法或文档中带编号的原则前，MUST 先全局 grep 交叉引用
  （如「宪法原则四」），确认编号改动波及面后再动。
- **常见陷阱即红线**：CLAUDE.md「常见陷阱」表（tool 被调两次、Provider 类型扫描、
  审计只写日志、`ddl-auto=update`、ReAct 用异步、MEMORY.md 超长不截断、Tool 模块拆分）
  视为宪法级反面清单，task 与 code review MUST 对照检查。
- **分阶段克制**：先构建最小完整的运行时内核；治理和分布式基础设施在真实使用数据
  验证后再做（核心阶段不做：认证、SSE 流式、WebSocket、限流、RBAC）。

## Governance

- 本宪法是项目最高开发准则，效力高于其他一切实践约定；CLAUDE.md 与本宪法同源维护，
  修订 MUST 双写同步。
- **修订程序**：修订 MUST 经主公（项目所有者）批准；MUST 附带 Sync Impact Report（版本
  变更、原则增删改、迁移事项），涉及原则编号变动的 MUST 同时给出受影响交叉引用的清理
  计划。
- **版本策略**：语义化版本。MAJOR = 原则删除或重定义（含编号重排）；MINOR = 新增原则/
  章节或实质性扩写；PATCH = 措辞与笔误澄清。
- **合规审查**：所有 plan 与 code review MUST 对照本宪法核查（至少覆盖原则二、三、四、
  五、六的可测试条款）；`speckit-analyze` 阶段发现违宪项应阻断而非仅提示。
- 复杂度 MUST 有明确理由；运行期开发指引以 CLAUDE.md 为准。

**Version**: 2.0.0 | **Ratified**: 2026-09-06 | **Last Amended**: 2026-09-06
