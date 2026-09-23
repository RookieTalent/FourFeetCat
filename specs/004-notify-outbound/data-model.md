# Phase 1 Data Model: Notify 出站通知（第19节）

**Input**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

---

## 1. `notify_channels` 表（本节唯一新增的表）

字段与约束**逐字摘自** `docs/class/schema.sql`（课程参考建表脚本）的 `notify_channels` 段；双轨脚本同版本号、同列名同约束、方言各自正确（本表无布尔与时间戳列，两轨差异仅在类型书写习惯）。

| 列 | sqlite 轨 | postgresql 轨 | 约束 | 说明 |
|---|---|---|---|---|
| `name` | `VARCHAR(64)` | `VARCHAR(64)` | PK | 全局注册名；Agent 正文/配置里引用的就是它 |
| `type` | `VARCHAR(32)` | `VARCHAR(32)` | NOT NULL | 渠道类型（webhook/feishu/wecom/dingtalk/email），决定由哪档实现处理 |
| `url` | `TEXT` | `TEXT` | 可空 | HTTP 类渠道的地址（含凭证，见 §3） |
| `description` | `TEXT` | `TEXT` | 可空 | 说明，不参与运行逻辑 |
| `config` | `TEXT` | `TEXT` | 可空 | 类型相关多字段（JSON 文本），如 email 的 host/port/from/to |

无索引：主键即查询入口（按名解析），核心阶段无其他查询维度。

---

## 2. 实体（`NotifyChannel`）

| 字段 | Java 类型 | 列 | 备注 |
|---|---|---|---|
| `name` | `String` | `name` | `@Id`（**不用** `@GeneratedValue`：名字由运营方指定） |
| `type` | `String` | `type` | |
| `url` | `String` | `url` | 可空 |
| `description` | `String` | `description` | 可空 |
| `config` | `String` | `config` | 可空；JSON 文本，核心阶段不做解析（没有消费方） |

`@Entity @Table(name = "notify_channels")`，`ddl-auto: none`——表只由 Flyway 脚本建。

---

## 3. 凭证与地址的处理口径

- 地址里的 token **本身就是凭证**（拿到 URL 就能往群里发消息）。
- 因此：库里的 `url` 应存 `${ENV_VAR}` 形式或由运营方通过管理台注入后的值；**明文地址不进日志、不进 git、不进对话上下文**（FR-007）。
- 本节不做渠道写入入口（管理台/端点归扩展阶段），故"写入时如何解析占位符"的落地随写入端一起在后续节定；本节只保证**读出来的值不会被打印或塞进模型上下文**（本节没有任何日志/上下文注入点，天然满足）。

---

## 4. 通知目标（`NotifyTarget`）——不是持久化数据

| 字段 | 类型 | 说明 |
|---|---|---|
| `channelType` | `String` | 渠道类型；实现类据此判断自己能不能处理 |
| `config` | `Map<String, String>` | 一份配置；webhook 档取 `url`，其他档取自己需要的键 |

**它是一次调用的入参，不落库。** 由"按名解析渠道"的动作（归第24节，见 research.md D8）从注册表记录**投影**出来：`name` 不进入目标（目标不需要知道自己的名字），`type` → `channelType`，`url`/`config` 进 `config` map。

---

## 5. 注册表与 Agent 的关系

```text
Agent（Profile）
  └── notify_channels: ["team-lark", "oncall"]     ← 第16节交付的名字清单，本节不改
                │  按名解析（第24节接线）
                ▼
        notify_channels 表（本节交付）
          name="team-lark"  type="webhook"  url="https://.../hook/xxx"
          name="oncall"     type="webhook"  url="https://.../hook/yyy"
                │  投影
                ▼
        NotifyTarget(channelType, {url: ...})   → 交给 NotifyChannelAdapter.send
```

**要点**：渠道是**实例级共享**的配置（两个 Agent 引用同一个名字，改一处两边生效）；地址**不进 Agent 配置正文、不进对话**。这与"一个目录 = 一个 Agent"（宪法原则四）不冲突——业务 Agent 只声明"我可以用哪些渠道"，渠道实体本身是底座级资源。

---

## 6. 状态迁移

本节无状态列、无迁移：渠道是"存在即有效"的登记项；启用/停用、归档等治理能力归扩展阶段的管理台。
