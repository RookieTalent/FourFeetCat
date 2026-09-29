# Feature Specification: 插件化 Agent——一个目录定义一个会自己跑的 Agent

**Feature Branch**: `029-lesson29-plugin-agent`

**Created**: 2026-09-29

**Status**: Draft

**Input**: User description: 第29节 fourfeetcat-core（Agent 目录插件化）——给底座添一条"定义一个 Agent"的标准来源：把 `.fourfeetcat/agents/<name>/` 一个目录定义成一个会自己跑的 Agent。

## Clarifications

### Session 2026-09-29

- Q: Agent 目录里的 `skills/` 在本节应如何处理？ → A: 按宪法四落实公共 Skill 库的软连接绑定视图（Opution A）：`ContextLoader` 枚举 `agents/<name>/skills/` 下的相对软连接 → 真实路径校验（须指向 `.fourfeetcat/skills/` 根的公共 Skill 实体）→ 只注入该 Skill 的 name + description + 本地绝对读取路径；`AGENT.md` frontmatter 不声明 `skills:`（软连接是唯一绑定真相源）；技能正文不预载、模型经 `read_file` 按需读。加载/启动扫描时检测 dangling / escaped / invalid-target 软连接，坏的技能不注入并记告警、不阻断 Agent 本体注册；完整 CRUD 检测（上传时）随第 30 节管理端补。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 往目录丢一个 Agent，到点自己跑 (Priority: P1)

运维把 `daily-reconcile` 目录放进 `.fourfeetcat/agents/`（其中 `AGENT.md` frontmatter 声明了 `schedules` 与所需的系统基础能力）。系统启动后这个 Agent 出现在 Agent 列表（CLI `profile list` / `GET /api/v1/profiles`），到它自己声明的时间点，底座自动跑完整条"拿数据处理→判断→回答→推送"链路、审计留账，全程没写一行 Java、没动一行底座。

**Why this priority**: 这是本节的终点——"Agent OS"名号成立的最小闭环，是优先级之核。

**Independent Test**: 扫一个放置了 N 个 Agent 目录的目录，注册表出现 N 个 Agent；带 `schedules` 的都进了定时器并留了句柄。

**Acceptance Scenarios**:

1. **Given** `.fourfeetcat/agents/` 下有 2 个完整 Agent 目录（各带 `AGENT.md`），**When** 系统启动扫描，**Then** 注册表同时出现这 2 个 Agent，且带 `schedules` 的那个已注册进定时器。
2. **Given** 其中一个 Agent 目录缺 `name` 或 `provider`，**When** 扫描，**Then** 该目录被拒绝并在错误中点名缺哪个字段，但不阻断其余 Agent 注册。

---

### User Story 2 - 运行时也能注册 Agent，校验与启动同规矩 (Priority: P2)

在第 30 节"一句话生成 Agent / 上传即上线"落地前，先立好运行时注册的底座：新扫入的 Agent 立即可见（`register` 后立即能 `get` 到），走与启动扫描完全相同的派生与校验（同一异常、同一消息）。这是"API 建 Agent 与手工丢目录 Agent 行为一模一样"的提前量。

**Why this priority**: ProfileRegistry 从不可变改为可变并发、AgentScheduler 抽出可单独注册的入口，是第 30 节运行时增删的基石。

**Independent Test**: `register` 一个合法位置后立即 `find` 可见；`register` 一个非法配置时抛错消息与启动路径完全一致。

**Acceptance Scenarios**:

1. **Given** ProfileRegistry 初始为空，**When** `register` 一个合法 Profile，**Then** 立即 `find` 返回该 Profile，且定时器为它的 `schedules` 留下句柄。
2. **Given** `register` 一个转载了未注册系统能力的 Profile，**When** 注册校验，**Then** 抛错（类型与消息同启动路径），注册失败。

---

### User Story 3 - 正文即时生效、资源按需加载 (Priority: P3)

改 Agent 目录里的指令正文，下一次触发就用新说明，不重启。

**Why this priority**: "正文改了即时生效"由第 17 节 ContextLoader 无缓存回归兜底；本节把"正文来自 Agent 目录主文件"接进这条链路。

**Independent Test**: 派生出的 Agent，其正文进 system prompt；参考/脚本不预载，按需经底座既有 read_file / shell 取用。

**Acceptance Scenarios**:

1. **Given** 一个 Agent 目录含 `AGENT.md` 正文、`scripts/*.py`、`skills/*.md`、`REFERENCE.md`，**When** 组装 system prompt，**Then** 正文被注入，而参考/脚本/skill 正文不预载。
2. **Given** 模型需要在运行时取脚本/参考，**When** 用底座 `shell` / `read_file`，**Then** 从该 Agent 自己的目录读，加载天然被限制在自己的目录内。

---

### Edge Cases

- Agent 目录缺 `AGENT.md` → 该目录被跳过且记错误日志点名，不阻断启动。
- `AGENT.md` lack frontmatter 闭合（无结束 `---`）→ 解析失败，该目录跳过、记错误日志，不阻断启动。
- frontmatter 缺 `name` / `provider` → 报错点名（deriveProfile 抛错带字段名与目录名）。
- 扫描目标目录不存在或为空 → 不报错、注册 0 个 Agent。
- `schedules` 里含非法 cron / 重复标识 → 单条跳过并记错误日志，不拖垮其余任务（沿用第 25/28 节语义）。
- `agents/<name>/skills/` 存在但软连接越界（绝对链接 / 指向公共 Skill 根之外）/ dangling（目标不存在）/ 非法目标 → 该技能不注入、记告警，Agent 本体仍注册并可用。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 支持把一个 `.fourfeetcat/agents/<name>/` 目录定义成一个 Agent：读取目录主文件 `AGENT.md`，拆出 frontmatter（运行配置）与正文（任务指令），并认出 `scripts/`、`skills/`、`REFERENCE.md` 等附属资源。
- **FR-002**: 系统 MUST 能把 Agent 目录的 frontmatter 派生为一个底座认识的 Profile 值对象，字段一一对应（name / description / identity / provider / tools / mcp_servers / channels / notify_channels / schedules / bootstrap / settings）；`schedules` 原样带进派生的 Profile（定时来自 Agent 的直接证据）。
- **FR-003**: 系累 MUST 在派生与注册时校验 agent 目录声明的能力可解析（如 provider 名已声明、tools 已注册）；校验失败（含缺 `name`/`provider` 等必填）报错并点名，但坏目录不阻断启动、记错误日志后跳过。
- **FR-004**: 系统 MUST 在启动时扫描 `.fourfeetcat/agents/` 下每个目录，派生并注册进 ProfileRegistry；带 `schedules` 的交给定时器注册（与手写 Profile 来源走同一注册入口）。
- **FR-005**: ProfileRegistry 从"只有启动一次性加载"改为运行时可变并发容器，MUST 提供 register / remove / exists，register 后立即能被 find 到；运行时注册与启动扫描走同一段派生注册与同一套校验（同一异常、同一消息）。
- **FR-006**: 定时器 MUST 提供可单独注册单个 Agent（registerProfile）的公开入口，并为每个已注册任务保留句柄（供后续注销/更新），cron 与时区来自该 Agent 的 Profile.schedules。
- **FR-007**: system prompt 组装 MUST 注入该 Agent 的正文（从 agents/<name>/AGENT.md 主文件读、去掉 frontmatter）；参考 / 子指令 / 脚本不预载，靠底座既有 read_file / shell 按需取。
- **FR-008**: 系统 MUST 按宪法四落实公共 Skill 库的软连接绑定视图：Agent 可见的公共 Skill 只由 `agents/<name>/skills/` 下指向 `.fourfeetcat/skills/` 公共实体的相对软连接表达（软连接集合是唯一绑定真相源，`AGENT.md` frontmatter 不声明 `skills:`）；ContextLoader 组装 prompt 时枚举这些软连接，校验真实路径位于公共 Skill 根、拒绝 dangling/escaped/invalid-target（越界或非法的软连接不注入并记告警、不阻断 Agent 本体注册），只注入该 Skill 的 name + description + 本地绝对读取路径；技能正文不预载、模型经 `read_file` 按需读。

### Key Entities *(include if feature involves data)*

- **Agent 目录**（`.fourfeetcat/agents/<name>/`）：一个自足业务 Agent 的载体，含 `AGENT.md`（frontmatter+正文）、可选 `skills/`、`scripts/`、`REFERENCE.md`。
- **Profile**：（既有序）Agent 派生后的运行配置值对象，是底座一切能力的消费入口。
- **schedules**：Agent frontmatter 声明的定时定义，随派生 Profile 原样带进并注册进定时器。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 往 `.fourfeetcat/agents/` 放一个合法 Agent 目录，不写一行 Java，CLI 列表与 REST profile 列表即出现该 Agent。
- **SC-002**: 带 `schedules` 的 Agent 目录到点自动触发，触发走与 CLI / Web 人推完全同一的处理链路；审计（llm_calls / tool_invocations）留账。
- **SC-003**: 运行时 register 的 Agent 立即可见、定时任务留句柄，为第 30 节的注销 / 更新铺路。
- **SC-004**: 坏 Agent 目录（缺必填 / frontmatter 未闭合 / 能力不可解析）不阻断启动，报错点名，`mvn clean verify` 全绿不回退。
- **SC-005**: 绑定的公共 Skill 经软连接注入时名称/描述/读取路径正确、正文不预载；越界 / dangling / invalid-target 软连接不被注入且记告警、不阻断 Agent 本体注册。

## Assumptions

- 复用第 16 节 ProfileLoader 的 frontmatter→Profile 映射与 provider 名可解析校验（同异常、同消息），保证"目录派生 Agent 与手写 Profile 同规矩"。
- Agent 目录的公共 Skill 绑定按宪法四走软连接视图；开发机（Windows 无建软链权限）下软连接类测试本地 skip、CI 真跑，不误删断言（既有记忆约定）。
- Agent 名唯一标识取 frontmatter 的 `name`，缺失报错点名，不做"必须等于目录名"的强制校验（课件 §1.3 示例恰好同名，但未要求强绑定）。
- 扫描目录不存在 / 为空 → 注册 0 个，不报错。
- 示例 Agent 目录 `daily-reconcile/` 四文件（AGENT.md + scripts/reconcile.py + skills/report-format.md + REFERENCE.md）由 spec-kit 按课件 §1.3/1.4 产出，放运行时工作区 `.fourfeetcat/agents/`（gitignore 不随仓库提交），作人工真模型路径的参照物。
- 依赖前序节交付：Profile / ProfileRegistry / ProfileLoader（16）、ContextLoader（17）、AgentScheduler（25/28）、AgentRuntimeConfiguration（装配层）。