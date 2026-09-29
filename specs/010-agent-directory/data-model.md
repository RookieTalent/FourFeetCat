# Data Model: 插件化 Agent

本节无持久化表结构变更（Session 外接、审计表不动）。核心数据形态是**文件系统布局**与**既有序 Profile 值对象**。

## Agent 目录（文件系统，`.fourfeetcat/agents/<name>/`）

| 元素 | 角色 | 进上下文时机 | 底座能力 |
|------|------|-------------|---------|
| `AGENT.md` | frontmatter（运行配置）+ 正文（任务指令） | 触发即常驻 system prompt（去 frontmatter 的正文） | ContextLoader 注入 |
| `scripts/`（可选） | 确定性脚本（如 `reconcile.py`），代码不进上下文 | 用到才跑 | shell / python |
| `skills/`（可选） | ① 指向 `.fourfeetcat/skills/` 公共实体的相对软链接（宪法四绑定真相源）② 普通子指令 md（如 `report-format.md`，模型 read_file 按需读） | ① 每轮注入 name+desc+路径 ② 用到才读 | ContextLoader / read_file |
| `REFERENCE.md`（可选） | 字段字典类参考 | 拿不准才读 | read_file |

## AGENT.md frontmatter → Profile 派生映射

| AGENT.md frontmatter | Profile 字段 | 说明/校验 |
|----------------------|--------------|-----------|
| `name`（必填） | `name` | 缺失报错点名；即 Profile 索引键 |
| `description` | `description` | 展示用 |
| `identity.agent_name` / `identity.prompt` | `identity` | 呈现身份 |
| `provider.name`（必填，须可解析）/`model`/`temperature` | `provider` | provider 名不存在报错（复用 ProfileLoader 校验） |
| `tools` | `tools` | 工具名清单 |
| `skills` | **不派生**（宪法四：frontmatter 不声明 skills，软连接是唯一真相源） | 由 ContextLoader 枚举软连接 |
| `mcp_servers` | `mcpServers` | |
| `channels` | `channels` | |
| `notify_channels` | `notifyChannels` | |
| `schedules` | `schedules`（原样带进） | 定时来自 Agent 的直接证据 |
| `bootstrap` | `bootstrap` | |
| `settings` | `settings` | max_iterations 等 |

## Profile（既有 record，13 字段，不改签名）

派生 Profile 复用该 record；**不新增字段**承载资源路径/目录——资源经"name = 目录名"约定由 ContextLoader 推断，避免 13 处构造调用大爆炸。

## 关键不变量

- `name` 是唯一校验键：缺失报错点名；重名注册后加载覆盖先加载（记 warn）。
- 依赖/metadata 坏 Agent 目录（缺 AGENT.md / frontmatter 未闭合 / 缺必填 / provider 不可解析）→ 记错误日志跳过，不阻断启动；单独 `deriveProfile` 时抛错点名。
- 软连接：只接受指向公共 `skills/` 根的相对链接；dangling/escaped/invalid-target 不注入技能、记告警，Agent 本体仍注册。