# Phase 1 Contracts：Web Service 与第一版管理平台（第26节）

本节是**对外 HTTP 契约**（底座第一次对外暴露接口面）。契约面 = ①统一前缀与信封 ②11 个端点逐条契约 ③静态资源与接口文档路径 ④错误码契约 ⑤既有内部契约的接触面清单（只读复用 / 一处改造 / 不得触碰的回归项）。

---

## 一、统一前缀与信封

- 前缀：`/api/v1`（与既有 `PingController` 同前缀；不另起前缀、不做版本协商）。
- 信封：所有端点、成功与失败**同构**——`{ code, message, data, timestamp }`（既有类型，见 data-model §1）。
- 内容类型：`application/json; charset=UTF-8`。
- 认证：**无**（内网假设，见 spec 边界）。因此"谁能调"不在本节契约内，部署方自行用网络层控制。

## 二、端点契约（11 个）

### 2.1 会话管理（5 个）

#### ① `POST /api/v1/sessions` — 创建（或复用）会话

请求体可空：

```json
{ "agent": "ops-agent", "user": "wang" }
```

| 字段 | 必填 | 缺省 | 规则 |
|------|------|------|------|
| `agent` | 否 | 注册表第一个已加载 Agent | 名字必须已加载，否则 404 |
| `user` | 否 | `anonymous` | 任意短标识 |

渠道固定 `web`（不开放给调用方）。**幂等**：同一（渠道，用户，Agent）三元组重复调用返回**同一条**会话（既有契约）。

响应（200）：`data` = 会话视图（data-model §2.1）。

```bash
curl -X POST localhost:8080/api/v1/sessions
curl -X POST localhost:8080/api/v1/sessions -H 'Content-Type: application/json' -d '{"agent":"ops-agent","user":"wang"}'
```

| 状态码 | 条件 |
|-------|------|
| 200 | 成功 |
| 404 | 指定 Agent 未加载；或一个 Agent 都没加载且未指定 `agent` |

#### ② `POST /api/v1/sessions/{id}/messages` — 发消息（触发一次完整处理）

```json
{ "content": "今天北京天气怎么样" }
```

响应（200）：`data` = `{ "reply": "<Agent 回复>" }`。

| 状态码 | 条件 |
|-------|------|
| 200 | 成功（承接 ReAct 循环，可能耗时数十秒） |
| 400 | `content` 缺失/空白/超 32KB；或目标会话已归档 |
| 404 | 会话标识不存在 |
| 503 | Provider 侧故障 |
| 504 | 单次模型调用超过 60 秒 |
| 500 | 其余内部错误（响应体不含内部细节） |

#### ③ `GET /api/v1/sessions/{id}` — 查会话详情与历史

响应（200）：`data` = 会话详情视图（最近 100 条 + 截断标记）。归档会话**照常可查**。

| 状态码 | 条件 |
|-------|------|
| 200 | 成功（含归档会话） |
| 404 | 会话标识不存在 |

#### ④ `DELETE /api/v1/sessions/{id}` — 归档（软标记，幂等）

响应（200）：`data` = 归档后的会话视图（`status=archived`、`archivedAt` 非空）。**重复归档幂等**，不覆盖首次归档时刻。数据一行不删。

| 状态码 | 条件 |
|-------|------|
| 200 | 成功（含重复归档） |
| 404 | 会话标识不存在 |

#### ⑤ `GET /api/v1/sessions` — 列举全部会话（**本节新增**）

响应（200）：`data` = 会话视图**列表**，按最后活跃时间倒序（最近的在最前）；含归档会话，不过滤、不分页。

| 状态码 | 条件 |
|-------|------|
| 200 | 成功（无会话时 `data` 为空列表，不是 404） |

### 2.2 Agent 无状态调用（1 个）

#### ⑥ `POST /api/v1/agents/{name}/invoke` — 无状态调用

请求体同 ②（`content` 规则一致）。响应（200）：`data` = `{ "reply": "..." }`。

- **每次调用独立会话身份**（随机唯一用户标识），上一次调用的历史不会被回放给这一次。
- 调用方**不需要**提供或持有会话标识。

| 状态码 | 条件 |
|-------|------|
| 200 | 成功 |
| 400 | `content` 缺失/空白/超 32KB |
| 404 | Agent 名未加载（含一个 Agent 都没加载） |
| 503 / 504 / 500 | 同 ② |

### 2.3 信息查询（3 个）

#### ⑦ `GET /api/v1/profiles` — 列已加载 Agent

`data` = Agent 视图列表（data-model §2.3）。永不出现凭证字段与系统提示正文。无 Agent 时为空列表。

#### ⑧ `GET /api/v1/memory` — 长期记忆全文

`data` = 记忆文本（字符串）。为空时返回空字符串或空区块文本，**不是错误**。响应形状随记忆后端（文件/库/外部服务）由门面统一后给出，调用方无感。

#### ⑨ `GET /api/v1/tools` — 列已注册工具

`data` = 工具视图列表（data-model §2.4，含参数 JSON Schema 文本）。工具表为空时为空列表。

### 2.4 系统状态（2 个）

#### ⑩ `GET /api/v1/health` — 健康探针

`data` = `{ "status": "ok" }`。**不依赖模型、不依赖网络**（供容器/探针使用，与既有 Actuator 探针并存、不互替）。

#### ⑪ `GET /api/v1/info` — 运行信息

`data` = 应用名 + 版本 + `providers` 列表（名字 / 端点 / **凭证是否就位**）。**不发探活请求**（配置态口径），**绝不回显凭证内容**。

## 三、静态资源与接口文档路径

| 路径 | 内容 | 契约 |
|------|------|------|
| `/admin` | 只读管理台入口页 | 由构建产出的静态资源直接托管；**产物未构建时该路径 404**（构建一次即得） |
| `/admin/**`（未命中静态资源） | 回落 `admin/index.html` | 前端子路由刷新不 404；**回落只覆盖 `/admin/**`**，不影响 `/api/v1/**` 的行为 |
| `/swagger-ui` | 自动生成的接口文档 | 由既有 OpenAPI 依赖自动生成，**不手写任何接口文档文件** |
| `/api/v1/ping` | 工程地基自证端点（既有） | 保留不动，不在本节 11 个端点之列 |

## 四、错误码契约

| 状态码 | 语义 | 触发（合并 D3 的映射表） |
|-------|------|------------------------|
| 400 | 参数错误 | 消息为空/空白/超 32KB；向已归档会话发消息；请求体格式不对；参数非法 |
| 404 | 资源不存在 | 会话标识不存在；Agent 名未加载；静态资源/路径不存在 |
| 500 | 内部错误 | 其余一切；**`message` 一律为统一话术，响应体与响应头不含内部异常消息、堆栈、连接串、表名** |
| 503 | 服务不可用 | Provider 侧故障；处理链内部状态异常（如会话归属的 Agent 在注册表里消失） |
| 504 | 处理超时 | 单次模型调用超过 60 秒（超时以传输层失败浮现，按原因链识别） |

**契约不变式（测试守点）**：
1. 失败响应与成功响应**字段结构完全一致**；
2. 一次正常请求下处理入口（`AgentService.process`）**恰被调用一次**——多一次是"端点夹带私货"，0 次是"没接到同一个人推入口"；
3. 500 响应里**内部细节出现次数为 0**（对连接串、表名、异常消息分别断言）。

## 五、边界说明（登记项）

| 边界 | 说明 |
|------|------|
| 会话标识长度 | `渠道:用户:Agent` 拼出，落 `VARCHAR(64)` 列。一次性调用的随机用户标识为 32 位，故 **Agent 名超过 27 字符**时会越界：SQLite（默认档）不强制，PostgreSQL 部署会拒绝。既有列宽约束，本节不改表 |
| 会话列表不分页 | 一次性调用会持续产生会话行，列表规模随调用量增长——如实呈现（审计优先），本版不做分页与过滤 |
| 无认证 | 端口一旦对不可信网络开放，等于把 Agent 能力全部开放。部署方需自行限制网络可达范围（内网假设） |
| 超时粒度 | 60 秒约束的是**单次模型调用**；一轮多轮工具循环的总时长不受此限（spec Assumptions 已记） |

## 六、既有内部契约的接触面清单

### 6.1 只读复用（签名与语义一字不改）

| 契约 | 位置 | 本节用法 |
|------|------|---------|
| `AgentService.process(Session, String)` | `fourfeetcat-core` | 发消息与无状态调用的**唯一处理入口**（与 CLI 同一个方法） |
| `SessionManager.getOrCreate / get / save` | `fourfeetcat-core` | 取/建/取单条；**会话标识拼接仍只在实现内部那一处**，web 只递三元组 |
| `ProfileRegistry.find / all` | `fourfeetcat-core` | Agent 名存在性校验（消歧 404）与列表 |
| `ToolRegistry.all` | `fourfeetcat-tool` | 工具列表（`CatTool` 三方法） |
| `MemoryService.buildContext` | `fourfeetcat-core` | 长期记忆全文（实现体当前即"取全文"，`session` 参数不参与计算——传 `null` 并登记耦合点，见 D9） |
| `ProviderProperties` | `fourfeetcat-provider` | 全局 Provider 声明（名字 / 端点 / 凭证是否就位） |
| `SessionRepository.findById / findAllByOrderByLastActiveAtDesc / save` | `fourfeetcat-storage` | 会话元数据、列表与归档落库 |
| `Session.getMessages` | `fourfeetcat-core` | 会话历史的只读快照 |
| `ApiResponse` / `ErrorCode` / `ServiceUnavailableException` | `fourfeetcat-web`（第24节） | 信封与错误码，**复用不另建** |

### 6.2 改造点（唯一一处触碰前序节实现）

| 文件 | 改动 | 不变项（回归守点） |
|------|------|------------------|
| `fourfeetcat-provider` 的 `ProviderConfiguration` | 方法体内给 HTTP 客户端设 60 秒读超时、重试压到单次尝试 | **对外 Bean 契约一字不改**：`LlmCaller providerService(ProviderProperties, LlmCallRecorder)` 的签名、Bean 名、语义全不动；Provider 显式映射（原则三）的构造方式不动 |

### 6.3 不得触碰的回归项（跨节契约）

- `ReActLoop` / `PromptBuilder` / `ToolExecutor`：零改动（原则一）。
- 工具执行链上的沙箱校验位与审计写入：零改动（原则五/六）。
- `JpaSessionManager` 的标识拼接公式：零改动（FR：拼接只此一处）。
- `sessions` 表结构与迁移脚本：零新增、零改动。
- 定时任务与既有四个入站渠道的行为：零改动。
