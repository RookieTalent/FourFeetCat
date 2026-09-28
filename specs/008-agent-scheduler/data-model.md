# Phase 1 Data Model：定时任务——第三种触发源（第25节）

**本节零持久化**：不新增实体、不新增表、不新增迁移脚本。定时触发那一轮的模型调用与工具调用由既有审计路径落库（`llm_calls` / `tool_invocations`），本模块自己没有存储。任务状态与执行历史的持久化是第28节的事。下面描述的是**内存里的值对象、配置模型与执行权模型**。

---

## 一、值对象：一条定时任务（`ScheduleConfig`，新建）

| 组件 | 类型 | 来源 | 约束 | 缺省 |
|------|------|------|------|------|
| `id` | `String` | 条目里显式写的标识 | 去空白后非空；**进程内全体已注册任务范围内唯一**（唯一性由注册期查重保证，见第三节） | 缺省派生 `profileName#序号`（序号 = 该 Agent 的 schedules 列表下标，从 0 起） |
| `cron` | `String` | 条目的触发规则 | **必填**；调度框架原生 **6 段**（秒 分 时 日 月 周），如 `0 0 9 * * *` | 无（缺失即该条非法） |
| `zone` | `String` | 条目的时区 | 可空；非空时须是合法时区标识（如 `Asia/Shanghai`） | 空 → 按服务器系统时区运行 |
| `message` | `String` | 到点发给 Agent 的话 | **必填**且去空白后非空 | 无（缺失即该条非法） |

**从原始条目到值对象的解析规则**（原始条目 = `Profile.schedules` 里的一项，即 YAML 里的一条映射）：

| 情况 | 处理 |
|------|------|
| 缺 `cron` / `cron` 为空白 / `message` 缺或空白 | 该条**非法**：记错误日志（点名 Agent 与条目的定位信息）后跳过，不影响其他条目 |
| `cron` 或 `zone` 的取值语法不对 | 该条**非法**：同上（语法的最终判定交给调度框架：`CronTrigger` 抛 `IllegalArgumentException`、时区非法抛 `ZoneRulesException`，注册期捕获） |
| 有 `id` | 用显式 `id`（去空白） |
| 无 `id` | 用 `profileName#序号` |
| `id` 与已注册的任一条重名 | 该条**非法**：记错误日志、跳过后来者并点名两条来源，不静默取第一条 |
| 该条合法 | 注册进调度器：`任务 → 触发规则(cron + 时区)` |

> 本节**不做**强类型化 `Profile.schedules`：它保持第16节建好的原样（原始条目列表），解析发生在注册期。理由见 research D6（既不动前序接口，又让坏条目只跳自己）。

## 二、配置模型：从 Agent 配置到调度器

```text
Profile（既有，零改动）
└── schedules: List<Map<String,Object>>       ← 第16节已建，本节首次有消费方
        │
        │  注册期逐条解析 + 校验（D6）
        ▼
ScheduleConfig(id, cron, zone, message)       ← 本节新建
        │
        │  new CronTrigger(cron, ZoneId.of(zone))   // zone 为空则用系统时区
        ▼
TaskScheduler.schedule(runnable, trigger)     ← 框架设施，boot 装配
```

**注册顺序**：按 `ProfileRegistry` 的遍历顺序（加载顺序 = 配置文件名升序）逐 Agent、按条目下标逐条注册。这个顺序决定了重名裁决里"谁先谁算数"，故 `ProfileRegistry` 内部由 `HashMap` 改为 `LinkedHashMap`——让注册顺序等于加载顺序，重名裁决可复现（不改任何既有成员语义）。

**一处刻意的"不做"**：不为"这条任务归哪个 Agent"额外记一份映射。任务句柄（触发规则）里已经绑定了它要用的 `Profile`——`runOnce` 的入参就是它。少一份状态，少一处不一致。

## 三、标识与唯一性

| 项 | 规则 |
|----|------|
| 标识的形态 | 自由字符串；缺省派生为 `profileName#序号` |
| 唯一性的范围 | **进程内全体已注册任务**（不是"各自 Agent 内"）——执行权按标识分配，跨 Agent 重名会让两条毫不相干的任务互相挡掉下一次触发（spec FR-004） |
| 重名的处置 | 注册期发现重复：记错误日志、跳过后来者、点名两条来源；不静默取第一条 |
| 派生标识为何天然不撞 | 派生形式自带 Agent 名前缀，不会与其他 Agent 的派生标识相同；只有显式乱写才会撞 |

## 四、执行权模型（`AgentScheduler` 的私有状态）

| 状态 | 类型 | 生命周期 | 用途 |
|------|------|---------|------|
| `taskLocks` | `ConcurrentMap<String, Lock>` | **按需懒建**（`computeIfAbsent`，`lockFor` 与 `runOnce` 走同一句） | 每个任务标识一把锁：拿不到即说明该任务上一次还在跑 |

**为什么懒建而不是注册期建**：注册失败的条目不该在表里留下一把永远不会被用的锁；`lockFor` 与 `runOnce` 共用 `computeIfAbsent` 保证两者命中同一把锁（这是 harness 用例成立的前提）。

**一次触发的执行权生命周期**：

```text
触发被交付执行
   ▼
lock.tryLock()  ── 拿不到 ──►  记"上一次仍在跑"日志，本次结束（不排队、不堆积）
   │ 拿到
   ▼
会话 = sessionManager.getOrCreate("scheduler", "scheduler", profileName)   ← 历次触发复用同一条
   ▼
agentService.process(会话, message)                                        ← 与人推完全相同的入口
   ▼
异常？── 是 ──► 记错误日志（不外抛、不影响别的任务）  ──┐
   │ 否                                                │
   ▼                                                   ▼
finally: lock.unlock()   ← 成功失败都必须走到这里（"二进宫"断言守的就是这一步）
```

**状态迁移**：无（除锁的占用/释放）。注册关系与锁表都是**启动期建、运行期只读/瞬态**的结构：同一任务换一条消息不改任何状态，重启即由配置重建。这一点决定了本节天然无状态——实例可随时重启，与"无状态实例、状态外置"的架构约束一致。

**线程安全**：`ConcurrentMap` + 每任务独立的 `ReentrantLock`；不同任务并发触发互不相干，同一任务被并发触发时后者直接跳过。触发执行发生在框架调度线程上，`runOnce` 自身不创建线程。

## 五、被触发那一轮的留痕（复用既有模型，不新增）

| 记录 | 内容 | 来源 |
|------|------|------|
| `llm_calls` | provider / model / token 数 / duration | 既有 `LlmCallRecorder`（`ReActLoop` 调模型时写） |
| `tool_invocations` | 工具名 / 入参 / 结果 / 成败 / duration | 既有 `ToolInvocationRecorder`（`ToolExecutor` 执行工具时写） |
| `sessions` | 钟推会话（渠道与用户为定时专用身份、Agent 名为任务所属 Agent） | 既有 `SessionManager`（`getOrCreate` + `save`） |

**审计路径零改动**：定时只是第三个入口，记账口径与人推完全一致（spec FR-009）。本节**不新增**"任务执行结果"这类表——那是第28节的 `task_executions`。
