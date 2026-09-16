# Feature Specification: Agent Provider（第16节）

**Feature Branch**: `016-lesson16-agent-provider`

**Created**: 2026-09-14

**Status**: Draft

**Input**: User description: "第16节需求：Agent Provider——Agent 与大模型之间的统一前台，按配置挑对模型并完成一次 LLM 调用，屏蔽各家厂商接口差异。……"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 按 Profile 声明的名字路由到对应模型 (Priority: P1)

企业实例同时接了两家模型（如 DeepSeek 和 Kimi）。两个 Agent 各自在 Profile 里声明用哪一家。上层循环把要说的话和 Profile 递给 Provider 前台，前台按 Profile 里写的 provider 名字挑出对应模型、用对方听得懂的格式发出、把回话拿回来。换模型时只改该 Agent 自己的 Profile 配置，上层代码一行不动。

**Why this priority**: 这是 Provider 的存在理由——没有按名路由，其余一切（审计、工具翻译）都无从挂载。

**Independent Test**: 双 provider 场景下，各用一个 Agent 名字发起调用，验证各自只命中声明的 provider、互不串台；引用了配置里不存在的 provider 名时得到清晰报错。

**Acceptance Scenarios**:

1. **Given** 全局配置声明了 provider A 和 B，**When** 某 Profile 声明使用 B 并发起一次调用，**Then** 调用发往 B 对应的模型，A 完全未被触碰（"不串台"的直接证据）。
2. **Given** 全局配置声明了 provider A 和 B，**When** 某 Profile 引用了未声明的 provider 名，**Then** 直接报错，不悄悄用错模型、不留空跑过去。
3. **Given** 两个 Profile 分别用 A 和 B，**When** 各发起一次调用，**Then** 两次调用分别命中各自声明的 provider。

---

### User Story 2 - 调用成败都留审计痕迹 (Priority: P2)

某次 LLM 调用超时/限流/模型报错。运维事后想在系统里追查这次事故：审计记录里能找到这次失败的调用，成败标记为失败、带着失败原因。调用成功时同样记账：用了哪家 provider、哪个 model、token 用量、耗时多久。

**Why this priority**: "可审计"是本产品的核心差异化能力，审计地基必须 day one 落库，纯靠日志后期要返工。

**Independent Test**: 调用失败时验证异常上抛且审计先落了"失败+原因"一条；调用成功时验证"成功+用量"一条；两条记录都含 provider、model、耗时。

**Acceptance Scenarios**:

1. **Given** 一次调用因超时失败，**When** 调用返回，**Then** 异常继续抛给上层，且审计记录新增一条 success=false、error_message 含失败原因。
2. **Given** 一次调用成功，**When** 调用返回，**Then** 审计记录新增一条 success=true，含 provider、model、token 用量、耗时。
3. **Given** 某会话连续多次调用，**When** 查审计记录，**Then** 每条记录都能关联到发起调用的会话。

---

### User Story 3 - 工具只翻译、不执行 (Priority: P3)

Agent 有可用工具（如查询类、写入类）。调用模型时把"你手上有哪些工具、每个要传什么参数"翻译成模型可读的说明随请求发出；模型回话可能说"我想调某工具"，这个"想调"的请求原样交回上层循环，由后续的工具执行器真正执行。Provider 全程不存在任何自动执行工具的路径。

**Why this priority**: 工具执行权必须攥在自研循环手里——若这一层出现自动执行，工具会被调两次且绕过沙箱检查。

**Independent Test**: 带工具发起调用，验证请求里携带了翻译过的工具说明且自动执行开关为关；验证工具说明的翻译产物字段一一对齐、不含任何执行逻辑。

**Acceptance Scenarios**:

1. **Given** 本次调用可用工具列表非空，**When** 发起调用，**Then** 请求里带上翻译过的工具说明（名称、描述、参数说明一一对齐）。
2. **Given** 模型在响应中表示想调用某工具，**When** 响应返回上层，**Then** 该工具没有被 Provider 执行过（全程无自动执行路径）。
3. **Given** 工具列表为空，**When** 发起调用，**Then** 调用正常完成，不因空列表报错。

---

### User Story 4 - Profile 从 YAML 加载并按名查找 (Priority: P2)

运维在工作区放若干 Agent 的 Profile YAML 文件。启动时逐个解析成内存对象、建索引、按名字查找消费。单个文件坏了（格式错、引用的 provider 不存在）记错误日志跳过，不阻断其余 Profile 和整个启动。

**Why this priority**: Profile 是所有下游模块消费的核心配置契约，本节是第一个消费它的模块，加载这回事归本节。

**Independent Test**: 临时目录放一好一坏两个 Profile 文件，验证好的正常加载、坏的记日志跳过、启动不中断；验证 `${ENV}` 占位从环境变量解析。

**Acceptance Scenarios**:

1. **Given** 目录下有合法与非法两个 Profile 文件，**When** 启动加载，**Then** 合法的进入索引，非法的记错误日志，加载不中断。
2. **Given** Profile 里凭证写成 `${DEEPSEEK_API_KEY}` 形式的占位，**When** 加载，**Then** 值从环境变量解析，代码与配置文件中无明文 key。
3. **Given** Profile 引用的 provider 名不在全局声明的 provider 列表里，**When** 加载校验，**Then** 该 Profile 记错误日志被拒，报错信息指明引用的名字。

### Edge Cases

- LLM 调用超时、限流、模型报错：失败审计落库后异常原样上抛，不做重试、不切换备用（见"明确不做"）。
- Profile 文件解析失败（YAML 语法错、缺必填字段）：记错误日志跳过该文件，不阻断其余加载。
- `${ENV}` 占位对应的环境变量缺失：作为该 Profile 的校验问题记日志，不静默当空串跑过去。
- 工具列表为空：正常调用，不带工具说明。
- 全局 provider 列表为空 / Profile 目录为空：正常启动，调用时报"未找到 provider"。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统在多 provider 并存时，MUST 按 Profile 声明的 provider 名字路由到对应模型，不串台。
- **FR-002**: Profile 引用了全局配置里不存在的 provider 名时，系统 MUST 直接报错（含所引用的名字），不得悄悄用错或留空跑过去。
- **FR-003**: 工具 MUST 只被翻译成模型可读的说明随请求传入；系统全程 MUST NOT 存在自动执行工具的路径。
- **FR-004**: 每次 LLM 调用成败 MUST 都落审计记录：provider、model、token 用量、耗时；失败记 success=false、error_message 记原因，异常继续上抛。
- **FR-005**: 审计记录 MUST 能关联到发起调用的会话。
- **FR-006**: 系统 MUST 在启动时扫描工作区 Profile 目录，把每个 YAML 解析成内存对象并建按名索引。
- **FR-007**: 单个 Profile 文件解析或校验失败时 MUST 记错误日志跳过，不阻断其余 Profile 加载。
- **FR-008**: Profile 的 provider 名 MUST 在全局声明的 provider 列表里找到同名项，找不到时该 Profile 校验失败。
- **FR-009**: 凭证（API key）MUST 经 `${ENV}` 占位从环境变量解析；代码与配置文件 MUST NOT 出现明文 key。
- **FR-010**: 全局配置层 MUST 能声明本实例接了哪些 provider、各家凭证从哪个环境变量读；Profile 层声明该 Agent 用哪个 provider、哪个 model、什么温度。

### Key Entities

- **Profile**：一个 Agent 的运行配置，字段全集：name、description、identity（呈现身份）、provider（name/model/temperature）、tools、skills、mcp_servers、channels、notify_channels、schedules、bootstrap、settings。本模块只消费 provider 段，类本身一次建全，后续各节按需取用。
- **Provider 全局声明**：实例级配置项，声明接入了哪些 provider（唯一 name）及各家凭证来源（环境变量名）。
- **LLM 调用审计记录**：一次模型调用的留痕——会话、provider、model、token 用量、成败、原因、耗时、时间。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 双 provider 路由场景在验收测试套件中 0 次串台（未声明的 provider 被调用 0 次）；未知名 provider 100% 抛出异常。
- **SC-002**: 成功与失败两类调用在审计存储中各留 1 条记录，失败记录 100% 带原因；抽查任一记录均含 provider、model、耗时、会话关联。
- **SC-003**: 带工具调用时，验收测试断言请求携带翻译后的工具说明、且自动执行开关为关——该断言成为永久回归点。
- **SC-004**: 坏 Profile 文件不阻断启动：多文件混合目录下，合法文件 100% 加载成功。
- **SC-005**: 代码与配置文件中明文 key 检索结果为 0。
- **SC-006**: 可自动化验收全部由课件"验收 harness"测试套件承载，套件全绿即实现完成；集成冒烟（真 key 真调一次）为人工项。

## Assumptions

- 命名口径：课件示例的 OryxOS 命名统一映射到仓库 fourfeetcat 命名（全局配置键为 `fourfeetcat.providers`，工作区 `.fourfeetcat/profiles/`），映射关系在 plan 中记录。
- Profile 字段按第16节课件建全（含 notify_channels），后续节各自补自己字段的校验规则。
- 范围边界（课件"有几样先别做"）：fallback（一家挂了换另一家）、hedge racing（同时发几家抢最快）、熔断——不做，故障直接抛给上层；成本看板不做，只落 LLM 调用审计表。
- 本节为第一节代码课，无前序节交付物依赖；外部依赖（Spring AI、YAML 解析、SQLite）须在项目锁定的依赖清单内可解析，动手前核实。
- Provider 自己没有独立入口，验收以"能撑住上层循环的一次模型调用"为标准。
