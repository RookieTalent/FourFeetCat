# Feature Specification: CLI 入口层与会话持久化（第18节）

**Feature Branch**: `018-lesson18-cli`

**Created**: 2026-09-21

**Status**: Draft

**Input**: User description: "第18节需求：CLI 入口层——FourFeetCat 的命令行门面，把"会调模型"的 Provider 与"会思考"的 ReAct 引擎变成用户敲得动的东西，并交付会话持久化地基（sessions 表 + 会话管理器）。……"

## Clarifications

### Session 2026-09-21

- Q: `fourfeetcat init` 要建出什么样的工作区结构？（课件本节只写"初始化一个工程"未给结构；技术方案 §8.1 给的是 025~029 的最终形态，含 `agents/` 与 `skills/`，而本阶段实际消费的是 `profiles/`） → A: 只建本阶段真正消费的部分——`.fourfeetcat/` 下 `profiles/`（含 `default.yaml` 默认 Agent 配置）、`memory/MEMORY.md`、`logs/`、`mcp_servers.yaml`、`AGENTS.md`/`SOUL.md`/`USER.md` 三个启动信息模板；`agents/`、`skills/` 留给 025~029 节随加载口径一起切换，本节不建空目录制造双定义源。
- Q: 会话的持久化实体（归 `fourfeetcat-storage`）与既有内存态会话类（归 `fourfeetcat-core`）同名，新类名取什么？ → A: `SessionEntity`——JPA 实体加 `Entity` 后缀与持久化层语义区分明确；改动只落本节的实体 / 仓储 / 会话层实现 / 测试四处，前序节零改动（把内存态会话搬进 storage 会违反依赖倒置，否决）。
- Q: `fourfeetcat tool list` 本节怎么处理（它依赖的工具注册能力归第20节）？ → A: 接空实现、输出"当前无可用工具"——命令在位、可跑、`--help` 正常，满足"12 个子命令都能跑"的验收；第20节交付工具注册后只换数据源，命令本身不改。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 在终端里跟自己搭的 Agent 说上话 (Priority: P1)

开发者刚把 Provider 与 ReAct 循环搭好，但还没有任何"亲手用起来"的方式。敲一条 chat 进入交互后，终端出现提示符，输入一句话，系统交给引擎跑完整一轮处理，把最终答复打到屏幕上，然后接着读下一句；输入 `/quit` 干净退出。可以指定某个 Agent 对话（`chat --profile weather`），不指定则用默认 Agent。这是 Demo 一的对话版体验，也是 FourFeetCat 第一个"看得见摸得着"的东西。

**Why this priority**: 这是本节存在的全部理由——没有它，前两节的成果只能靠写测试代码来验证，用户无法真正操作自己的 Agent。其余命令与分流策略都挂在这条主链上。

**Independent Test**: 进入交互后连发两轮消息再 `/quit`，验证两轮都拿到答复、退出码正常、进程不残留。

**Acceptance Scenarios**:

1. **Given** 工作区已就绪且默认 Agent 配置合法，**When** 敲 chat 并输入一句话，**Then** 该句话被交给引擎处理，最终答复被打印到屏幕。
2. **Given** 已进入交互，**When** 输入 `/quit`，**Then** 交互结束、命令返回，不再读取下一行输入。
3. **Given** 显式指定 `--profile weather`，**When** 输入消息，**Then** 处理用的是该 Agent 的配置，而不是默认 Agent 的。
4. **Given** 已进入交互，**When** 连发多轮消息，**Then** 每一轮都能看到答复，中途不退出、不报错。

---

### User Story 2 - 反复对话能接上上文，换渠道/换人/换 Agent 互不串台 (Priority: P1)

用户昨天跟某个 Agent 聊过一段，今天重新进入对话继续问，模型仍记得昨天说过的事。同时，同一个人在不同 Agent 下、或从不同接入渠道进来，必须是各自独立的一份历史，不能互相看见。这套"同一人同一 Agent 同一渠道 = 同一条历史"的判定规则只在一处实现——多个入口各自拼一遍身份、格式差一个分隔符，同一个人就会出现两条互不相认的历史。

**Why this priority**: 会话身份是整个底座所有入口共用的地基，出口径问题最难查（后续节的人推/钟推接缝全压在它上面），必须在第一次交付时就钉死。

**Independent Test**: 同一身份连续两次取会话拿到同一条（同 id）；三个身份分量任一改变即拿到不同会话；新建上下文重查，历史仍在。

**Acceptance Scenarios**:

1. **Given** 同一渠道 + 同一用户 + 同一 Agent，**When** 连续两次请求会话，**Then** 两次拿到的是同一条会话（同一标识）。
2. **Given** 三条身份分量中任一项不同（渠道不同、用户不同、或 Agent 不同），**When** 请求会话，**Then** 得到的是不同的会话。
3. **Given** 已有会话且累积了对话历史，**When** 进程重启后重新取该会话，**Then** 历史完整还在、可继续追加。
4. **Given** 任意外部入口（终端、后续的 HTTP、后续的定时），**When** 需要会话，**Then** 只提供"渠道 + 用户 + Agent"三元组，标识拼接只发生在会话层内部一处。

---

### User Story 3 - 三条命令按轻重分流，看一眼就退的命令秒回 (Priority: P2)

`profile list` 这种"看一眼就退"的命令，用户期望敲完就出结果；而打开依赖注入容器要等 2~4 秒。所以命令分两类：不需要跑引擎/调模型的命令直接用标准文件 API 读写、不打开容器；需要跑引擎的命令（chat、serve、gateway）才打开容器。判断标准只有一条：这个命令要不要调模型/跑引擎。

**Why this priority**: 分流策略是一开始就要定的架构决策——定晚了要么全都慢，要么事后改起来伤筋动骨。

**Independent Test**: 计时对比轻命令与重命令的启动耗时；轻命令的进程不出现容器启动相关日志。

**Acceptance Scenarios**:

1. **Given** 一个不调模型、不跑引擎的命令，**When** 执行它，**Then** 不打开依赖注入容器，结果即刻输出。
2. **Given** chat / serve / gateway 任一，**When** 执行它，**Then** 容器被打开，引擎可正常工作。
3. **Given** 需要查运行态数据的命令（如列出会话），**When** 执行它，**Then** 数据取自真实的持久化存储而不是另建一份。

---

### User Story 4 - 重命令打开容器时，跨模块的数据访问部件必须被扫到 (Priority: P2)

CLI 模块与存放会话/审计数据的模块分属不同 Java 包。重命令打开容器时，普通组件扫描的范围声明不会带动数据访问层的仓储扫描与实体扫描跟着跨模块生效——这两者默认只按主类所在包扫描，是两套独立逻辑。不显式声明就会在启动时报"找到 0 个仓储接口"，审计与会话写不进去、直接报错退出。

**Why this priority**: 这是照着"轻重命令分流 + 重命令才启动容器"这条路走几乎绕不开的真实坑，踩了就是启动即失败，必须提前钉死。

**Independent Test**: 看 chat 的启动日志里仓储扫描到的接口数量大于 0；跑完一轮对话后库里有审计与会话记录。

**Acceptance Scenarios**:

1. **Given** 执行任一重命令，**When** 容器启动完成，**Then** 启动日志中仓储扫描到的接口数量大于 0。
2. **Given** 完成一轮对话，**When** 查数据存储，**Then** 会话记录与本次的模型/工具审计记录都已落库。

---

### User Story 5 - 其余命令把"看情况"和"起项目"补齐 (Priority: P3)

敲 `init` 在一个新目录里初始化出可用的工作区；敲 `profile list/create/show/delete` 管理工作区里的 Agent 配置；敲 `status` / `provider list` / `tool list` / `session list` 看当前状态。每个命令一个命令类，参数解析、帮助信息、报错提示由命令行框架统一提供，不自己撸参数解析。`serve` / `gateway` 本节只负责从命令行侧接起来（Web Service 与 IM 通道的内部实现归后续节）。

**Why this priority**: 这些是"门面完整性"需求——单看每条都很浅，但缺了就不像一个能用的命令行工具；其中依赖后续节的命令只需接起来、不阻塞主链。

**Independent Test**: 12 个子命令逐个执行，`--help` 全部正常；`init` 后工作区结构完整、`profile list` 能列出刚生成的默认 Agent。

**Acceptance Scenarios**:

1. **Given** 一个空目录，**When** 执行 init，**Then** 工作区被创建出来，含 Agent 配置目录、记忆文件、日志目录与默认 Agent 配置。
2. **Given** 工作区已初始化，**When** 执行 profile list，**Then** 列出工作区内的 Agent 配置。
3. **Given** 任一个子命令，**When** 加 `--help`，**Then** 输出该命令的用法说明。
4. **Given** 一条需要长期运行的模式命令（serve / gateway），**When** 执行它，**Then** 进程持续运行而不是立刻退出。

---

### Edge Cases

- 交互中输入空行：不交给引擎处理，直接读下一行（不产生空消息、不报错）。
- 交互中 `/quit` 前后带空白：按退出处理。
- 工作区不存在时执行 chat：给出清晰报错并提示先执行 init，不抛栈。
- Agent 配置非法（坏 YAML、引用了未声明的 provider）：该 Agent 不被注册，记错误日志、不阻断启动；chat 指定一个不存在的 Agent 时给出清晰报错。
- 会话的对话历史为空：取会话正常返回，不报错。
- 一条会话的历史累积到很长：按既有的最近 N 轮截断兜底，不在会话层另设限制。
- 两轮对话之间进程重启：历史仍在（不是只活在内存里）。
- 同一身份并发进入两次：不产生两条会话记录（幂等依赖主键唯一约束兜底）。
- 状态类命令查询的存储为空：输出"无"而不是报错。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 提供单一命令行主入口，所有操作经子命令完成；核心阶段子命令共 12 个：init；status；chat；serve；gateway；profile list / create / show / delete；provider list；tool list；session list。
- **FR-002**: chat MUST 读标准输入、写标准输出，维护当前会话，每收到一行就交给统一处理入口并打印最终答复，收到 `/quit` 时干净退出。chat MUST NOT 含任何 Agent 智能（不自己想、不自己调模型、不自己执行工具）。
- **FR-003**: 系统 MUST 提供三种运行模式——终端交互、HTTP 服务、多通道守护——三者 MUST 共享同一份 Agent 配置与同一套会话存储，差异只在接入层。本节只负责从命令行侧接起后两者。
- **FR-004**: 命令 MUST 按"是否调模型/跑引擎"分流：不需要的命令 MUST NOT 打开依赖注入容器（直接用标准文件 API 读写），需要的命令才打开容器。
- **FR-005**: 命令行参数解析、子命令分发、帮助信息与报错提示 MUST 由命令行框架提供，MUST NOT 手写参数解析。
- **FR-006**: 会话 MUST 持久化：实体含会话标识（主键）、所属 Agent、接入渠道、用户标识、序列化的对话历史、状态（活跃/归档）、创建时间、最后活跃时间、归档时间（可空）。对话历史 MUST 整体序列化为一列存储，核心阶段 MUST NOT 按条拆表。
- **FR-007**: 会话相关表结构 MUST 由手工建表脚本管理，MUST NOT 依赖持久化框架自动建表（`ddl-auto` 保持关闭）。
- **FR-008**: 会话层 MUST 对外提供三个能力：按三元组取或建会话、按标识取会话、保存会话。
- **FR-009**: 会话标识 MUST 由"渠道 + 用户 + Agent"三元组联合生成，且拼接 MUST 只发生在会话层内部这一处；外部入口只提供三元组。同一三元组历次取会话 MUST 返回同一条（幂等）；三元组任一不同 MUST 视为不同会话。
- **FR-010**: 会话的累积与落盘 MUST 走既有处理入口的收尾路径（一次处理结束把累积完的会话交给会话层落盘），MUST NOT 在入口层另建一条落盘旁路。
- **FR-011**: 重命令打开容器时，数据访问层的仓储扫描范围与实体扫描范围 MUST 显式声明为覆盖全部业务包，MUST NOT 依赖单一组件扫描声明连带生效。
- **FR-012**: 状态类命令（status / provider list / tool list / session list）MUST 输出真实运行态数据；`tool list` 在工具注册能力（第20节）尚未交付前 MUST 接空实现、输出"当前无可用工具"，MUST NOT 报错退出，也 MUST NOT 引入任何工具实现。
- **FR-013**: init MUST 创建一个可用的工作区：`profiles/` 目录（含默认 Agent 配置 `default.yaml`）、`memory/MEMORY.md`、`logs/`、`mcp_servers.yaml`，以及 `AGENTS.md`/`SOUL.md`/`USER.md` 三个启动信息模板文件；MUST NOT 创建 `agents/`、`skills/` 空目录（归 025~029 节随加载口径切换）。重复执行 init MUST 幂等——已存在的文件 MUST NOT 被覆盖。
- **FR-014**: Agent 配置的管理命令（list / create / show / delete）MUST 直接操作工作区内的配置目录，MUST NOT 打开容器。
- **FR-015**: 非法 Agent 配置 MUST 记错误日志并跳过，MUST NOT 阻断启动；入口处引用到不存在的 Agent 时 MUST 给出清晰报错。

### Key Entities *(include if feature involves data)*

- **会话（持久化记录）**：一次对话的元数据与历史——会话标识（渠道+用户+Agent 联合生成，主键）、所属 Agent、接入渠道、用户标识、序列化的对话历史、状态、创建时间、最后活跃时间、归档时间。与既有内存态会话是同一概念的两个层次：内存态负责一轮处理中按序累积，持久化记录负责跨重启留存。本体类名定为 `SessionEntity`（归 `fourfeetcat-storage`，映射 `sessions` 表），与 `fourfeetcat-core` 的内存态会话类区分。
- **会话层**：会话的唯一出入口——按三元组取或建、按标识取、保存。会话标识公式的唯一实现处（接口在 `fourfeetcat-core`，实现在 `fourfeetcat-storage`）。
- **命令行主入口与 12 个子命令**：程序入口与各子命令的行为单元。
- **工作区目录结构**：init 创建的可操作目录及其模板文件（Agent 配置、记忆文件、日志目录、启动信息）。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 同一三元组连续两次取会话返回的标识 100% 相同（0 次重复建会话）；三个分量任一改变时标识 100% 不同。
- **SC-002**: 会话历史写入后读回，消息条数与顺序 100% 保真；模拟重启（新建上下文重查）后历史仍在。
- **SC-003**: 手工建表脚本建出的会话表 100% 能被读写（不依赖框架自动建表）。
- **SC-004**: 重命令启动日志中仓储扫描到的接口数量 > 0。
- **SC-005**: 12 个子命令全部可执行、`--help` 全部正常；不调模型的命令不打开容器。
- **SC-006**: 可自动化验收由课件"验收 harness"的两个测试套件承载（会话管理器、会话仓储），另加一个交互壳守卫测试（终端交互的退出/空行分支，见 plan Complexity Tracking）——套件全绿即实现完成；命令分流与 `--help` 属进程级行为，成本大于收益，留人工清单。
- **SC-007**: 切换运行模式后 Agent 配置与对话历史不丢（同一份存储）。

## Assumptions

- 命名口径：课件示例的 OryxOS 命名统一映射到仓库 fourfeetcat 命名（命令行程序名为 `fourfeetcat`，工作区目录为 `.fourfeetcat/`，模块与包以 `fourfeetcat-*` 表达）；主入口类名按技术方案 §8.7 采用 `FourFeetCatCli`（课件称 `OryxOsCli`）。
- 依赖第16、17节交付物：Agent 配置的加载与注册、处理入口（一次处理 = 收句 → 引擎跑完 → 返回答复）、内存态会话值对象、会话层的既有保存出口（本节从"只有保存"扩容为三个能力）、两张审计表与记录器端口。
- **本阶段 Agent 配置的定义源**：工作区 `profiles/` 目录下的 YAML（与既有加载器口径一致）。技术方案 §8.2 描述的"扫 `agents/` 下 `AGENT.md` 的 frontmatter、deriveProfile"是后续节（025~029）的目标形态，本节不改加载口径，届时再切换（对外行为不变）。
- 持久化：SQLite 默认零配置（工作区内的库文件）；表结构走 Flyway 双轨迁移脚本（`db/migration/{sqlite,postgresql}/`，同版本号只增不改），与第16/17节的 V16/V17 序列衔接。
- 会话历史的序列化在持久化层完成：内存态的对话消息（Spring AI 消息类型）与一列 JSON 之间做双向映射，读写两侧同源、往返保真。
- 工具注册与内置工具归后续节（第20节）：本节 chat 需要的最小接线 MUST NOT 引入任何工具实现；`tool list` 在工具注册能力交付前输出空清单。
- 交互的用户标识取当前系统用户（终端场景没有别的身份来源）；后续接入渠道（HTTP / 定时）各自提供自己的用户标识，会话层只收三元组、不解释来源。
- **已排期的收敛项（主公已裁）**：宪法原则四规定的"一个 Agent = `agents/<name>/` 目录 + `AGENT.md` + `deriveProfile`"形态归 025~029 节落地；本节（及前序 16/17 节）沿用 `profiles/` 下的 YAML 作为 Agent 配置定义源，**本节不改加载口径**，届时切换对外行为不变。
- 范围边界（课件"CLI 不管"）：Agent 怎么想（归 ReAct 循环）、工具怎么执行（归工具执行器）、HTTP 接入（归 Web Service）——本节不碰；不做认证、不做 SSE 流式、不做限流。
