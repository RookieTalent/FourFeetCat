# ReAct 模块 面试话术

> 模块：`fourfeetcat-core/react` · 课件：第 17 节
> 一句话定位：**Agent 的调度内核**——想一步、做一步、看结果，转不动或办成了就收尾。

---

## 一、30 秒版（照念）

> ReAct 是 Agent 的调度内核，模式就是 Reason（想）→ Act（做）→ Observe（看）循环。停止条件很关键：**这一轮模型没提出要调工具，就说明它能给最终答复了**，循环返回；万一它反复要调工具停不下来，靠最大轮数兜底（默认 10 轮）强制收尾。
>
> 整个循环只有几十行，因为**它只管调度**——拼上下文交 PromptBuilder、调模型交 Provider、执行工具交 ToolExecutor。循环里塞的东西越少越好读。循环之上还有一个薄薄的编排者 `AgentService`，三种触发源（CLI、Web、定时）最终都汇进它一个入口。
>
> 最需要自己写的理由：循环是 Agent 最需要掌控的地方——什么时候停、工具失败了怎么办、上下文太长了怎么压、哪几步换模型。用框架的现成封装，这些全动不了。所以我自己写这几十行，把控制权攥在手里。

---

## 二、展开版

### ① 职责三拆（这是本模块的骨架）

| 组件 | 只管 | 交给谁 |
|---|---|---|
| `ReActLoop` | 一圈圈转、判断该不该停、把每轮结果累积回 Session | —— |
| `PromptBuilder` | 拼每轮发给模型的内容 | —— |
| `ToolExecutor` | **工具执行的唯一入口**：查表 → 检查 → 执行 → 落审计 | —— |
| `AgentService` | 编排一次处理：取 Profile、进出配对 ProfileContext、落盘 Session | —— |

**执行权只在一个地方发生**——这正是上一节要关掉 Spring AI 自动执行的原因：多一条执行路径，就多一份绕过审计与检查的调用。

### ② 停止条件与两个兜底

```
循环体：
  拼 Prompt → 调模型 → 先把响应存回 Session
  ├─ 模型没提工具 → return 最终答复（这就是停止条件）
  └─ 模型提了工具 → 逐个执行 → 结果回填 → 下一轮
转满 maxIterations（默认 10）→ 返回"达到最大轮数，已停止"
```

注意最后那句返回的是**收尾信号**，不是正常答复——让上层知道这次是被上限截断的。这个区别很重要，别把两者混成一句"返回结果"。

### ③ 三个坑（设计时就要定死，不是写完再补）

| 坑 | 解法 |
|---|---|
| 不设轮数上限 → 模型反复要调工具，死循环 | `maxIterations` 兜底，默认 10 |
| 不管上下文长度 → 每轮带全部历史，越滚越大撑爆 | 历史只留最近 N 轮（默认 20），超了截断 |
| 每轮不累积回 Session → 事后无法审计、下一轮接不上 | 每轮把模型响应和工具结果都 append 回 Session |

**截断有个实现讲究值得讲**：切点必须落在**用户消息**上，而不是简单地数消息条数。因为一条助手消息后面跟着它的工具调用与回填，切在中间会把这条序列切成半截——半截序列对模型端来说是**非法请求**。

### ④ Prompt 的四部分（顺序固定）

1. **system prompt**：角色（`identity.agent_name` + `prompt`）+ ContextLoader 加载的启动信息
2. **长期记忆**：跨会话的，没接入就整段跳过
3. **会话历史**：最近 N 轮（与长期记忆是两码事，别混着说）
4. **工具说明**：不在 Prompt 里——它作为独立参数随 `LlmCaller.chat(..., tools, prompt)` 下发，由 Provider 翻译成 Function Calling 格式

**末尾必须附当前时间**这一行不是随手加的：模型自己不知道今天几号，后面定时场景里的"今天"全靠它。

### ⑤ `AgentService` 与 `ProfileContext`（本节最阴险的一处）

```java
Profile profile = profileRegistry.find(session.getProfileName()).orElseThrow(...);
ProfileContext.set(profile);          // 入口放，工具执行时靠它知道"当前是哪个 Agent"
try {
    String reply = reActLoop.run(session, userMessage, profile);
    sessionManager.save(session);     // 累积完的历史落盘
    return reply;
} finally {
    ProfileContext.clear();           // 异常路径也必须清
}
```

**为什么需要这个 ThreadLocal**：工具接口签名不带 Profile，但有些工具执行时需要知道"当前 Agent 是谁"——改接口签名代价太大，于是在编排者入口放进 ThreadLocal、出口清掉。虚拟线程下每个请求独占一个线程，天然不串号。

**为什么用 finally**：ThreadLocal 泄漏是最阴险的一类 bug——**单请求测试里永远不报错，只在并发复用线程时串号**，把 A 的配置用到 B 身上。所以这条必须在 harness 里显式钉死：注入一个会抛异常的循环，断言 `ProfileContext.current()` 仍然为 null。

### ⑥ 刻意没做的

工具并行调用、Agent 之间互相委托、流式输出、上下文压缩——核心阶段都不做。上下文先用"只留最近 N 轮"这种简单办法顶着，够用就行。

---

## 三、追问预演

| 追问 | 答 |
|---|---|
| 为什么自己写循环，不用 LangChain4j / Spring AI 的 Agent？ | 循环是 Agent 最需要掌控的地方（停止时机、失败处理、上下文压缩、按步换模型），框架黑盒动不了。而且我只需要几十行。 |
| 模型一直要调工具怎么办？ | `maxIterations` 兜底（默认 10），转满强制收尾，返回的是"达到最大轮数"这个收尾信号而非正常答复。 |
| 工具执行失败会不会炸掉整个循环？ | 不会。`ToolExecutor` 捕获异常不吞——原因既进审计表，也回填进对话上下文，模型能据此换招，而不是撞上一堵没有信息的墙。 |
| 多个工具调用是并行的吗？ | 核心阶段明确串行，按顺序逐个执行。并行属于扩展阶段。 |
| 为什么用虚拟线程不加锁？ | 全程同步阻塞（原则：不引 Reactor / CompletableFuture），并发交给 Java 21 虚拟线程——IO 等待时自动让出，代码复杂度不上升。 |
| Profile 是怎么到循环手里的？ | 显式参数一路传：`session.getProfileName()` → `ProfileRegistry.find(...)` → `reActLoop.run(session, msg, profile)`。不走 ThreadLocal。 |
| 上下文太长具体怎么截？ | 只留最近 N 轮（默认 20），且切点必须落在用户消息上——否则会把工具调用序列切成半截，那是非法请求。 |

---

## 四、证据坐标

| 讲点 | 代码位置 |
|---|---|
| 循环本体 + 停止条件 + 收尾信号 | `fourfeetcat-core/src/main/java/org/fourfeetcat/core/react/ReActLoop.java` |
| 编排者 + ProfileContext 进出配对 | `.../core/react/AgentService.java` |
| ThreadLocal 本体与注释 | `.../core/react/ProfileContext.java` |
| 四部分拼装 + 当前时间 + 截断切点 | `.../core/react/PromptBuilder.java` |
| 执行唯一入口 + 异常不吞 + 审计 | `.../core/react/ToolExecutor.java` |
| 轮数 / 历史上限的缺省值 | `.../core/react/ProfileSettings.java` |
| core 侧端口 | `.../core/react/LlmCaller.java`、`.../core/session/SessionManager.java` |
| 回归测试 | `fourfeetcat-core/src/test/java/org/fourfeetcat/core/react/ReActLoopTest.java`、`AgentServiceTest.java` |
