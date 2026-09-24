# FourFeetCat 面试话术包

> 用途：面试时介绍 FourFeetCat 的模块设计与技术取舍。每篇文档都是「30 秒版（照念）→ 展开版 → 追问预演 → 证据坐标」四段。
> 事实已对照仓库代码逐条核对（核对日 2026-09-24，分支 `020-lesson20-tool-system`）。代码坐标给的是仓库相对路径，面试前扫一眼即可回忆。

---

## 一、项目一句话（自我介绍的开场）

> FourFeetCat 是用 Java 21 + Spring Boot 3 实现的企业级 Distributed AI Agent OS。它装在企业自己的 K8s 或服务器上，作为统一底座运行多个业务 Agent，共享渠道接入、模型路由、工具调用、记忆系统和沙箱执行能力。数据完全留在企业自己的基础设施，不锁任何云生态。长期目标是走进 Apache 基金会。

**关键数字速记**：14 个 Maven 模块 · 12 个 CLI 子命令 · 9 个内置 Tool · 10 个 REST 端点 · 8 条不可违背原则。

---

## 二、30 秒总述（面试官说"介绍一下你这个项目"时照念）

> 这是一个企业级的 AI Agent 底座，不是单个 Agent，而是"让任意 Agent 可靠运行的环境"。
>
> 技术上两条主线。第一条，**核心自己实现、管道复用现成**——ReAct 循环是我手写的几十行，Spring AI 我只留了两件事：各家大模型的协议转换、工具 schema 的格式生成。它的自动工具执行被我显式关掉了，因为执行权必须只收在我自己的 ToolExecutor 一处，多一条路径就多一份绕过沙箱和审计的可能。
>
> 第二条，**每个模块都是同一套习惯的兑现**：接口先行加依赖倒置（`LlmCaller`、`LlmCallRecorder`、`NotifyChannelSource` 这些端口都定义在 core，实现都在下游模块，所以 core 不反向依赖任何人）；审计从第一天就落库，`llm_calls` 和 `tool_invocations` 两张表成败都写；以及刻意的克制——并行工具调用、上下文压缩、熔断，全推到扩展阶段，先把"能稳定跑通一次"做扎实。

---

## 三、一条河：把散模块串起来讲

面试官最想看到的不只是你会写模块，而是你能把模块串成一个系统。一句话主线：

```
消息进来（CLI，人推）→ 引擎想（ReAct 循环，调 Provider）→ 做（ToolExecutor 执行工具）
                                                              → 结果出去（Notify 主动推送 / 或沿原链路返回发起者）
```

- **入站**：CLI（终端）、Web Service（REST）——都是"人推"，有人发起、有人等响应。
- **引擎**：`AgentService` 编排 → `ReActLoop` 转圈 → `PromptBuilder` 拼上下文 → `Provider` 调模型 → `ToolExecutor` 执行工具。
- **出站**：`Notify`——触发源一旦变成"到点自动"，没人等响应了，Agent 必须自己把结果送出去。这是与入站**对称的另一半**。

讲的时候加一句收尾：**"我不是写了一堆模块，我是在同一个原则下长了四个模块。"**

---

## 四、模块文档索引

| 文档 | 模块 | 一句话定位 |
|---|---|---|
| [provider.md](provider.md) | `fourfeetcat-provider` | Agent 与大模型之间的前台：挑模型、调一次、交回响应，工具只翻译不执行 |
| [react.md](react.md) | `fourfeetcat-core/react` | Agent 的调度内核：想—做—看循环，几十行手写，不用框架黑盒 |
| [cli.md](cli.md) | `fourfeetcat-cli` | 消息进出的门，不是干活的人；12 个子命令，轻重分流 |
| [notify.md](notify.md) | `fourfeetcat-tool/notify` | 出站通知：补上"结果怎么主动送出去"这个出口 |

**待补**（后续课程模块）：memory（21/22 节）、sandbox（23/24 节）、tool 体系（20 节）、定时任务（25 节）、web service（26 节）。

---

## 五、通用话术：跨模块的六个必答题

**Q1「这些轮子为什么不直接用框架现成的？」**
> 分工很清楚：**管道复用，核心自造**。各家大模型协议差异、schema 格式，Spring AI 做得又稳又好，我直接用。但 ReAct 循环是 Agent 最需要自己掌控的地方——什么时候停、工具失败了怎么办、上下文太长了怎么压、哪几步想换个模型，这些用框架黑盒就动不了。所以循环我手写几十行，把控制权攥在手里。

**Q2「你怎么保证改了不回归？」**
> 每个"想清楚"阶段点过名的坑，都配一条对应的回归测试。比如"关掉 Spring AI 自动执行"这条，测试里用 `ArgumentCaptor` 抓住请求断言 `internalToolExecutionEnabled == false`——谁改回自动执行，CI 立刻红。再比如 notify 的"白名单校验必须先于发送"，用 `InOrder` 钉死，顺序反了就是漏洞。

**Q3「审计怎么做的？」**
> `llm_calls` 和 `tool_invocations` 两张表从第一天就写，而且**成败都写**：成功记 token 用量和耗时，失败记 `success=false` 加 `error_message`。最容易漏的就是失败路径——只记成功的话，一次真实事故在数据库里完全没痕迹，"可审计"这个卖点就打了折扣。

**Q4「安全怎么做的？」**
> 三层。第一，**执行权唯一**：工具只能经 ToolExecutor 执行，Spring AI 的自动执行被关掉，notify 的对外 HTTP 也必须走白名单，不能因为是"往外推"就绕过去。第二，**沙箱白名单**：文件走路径白名单（`toRealPath()` 防软连接逃逸）、Shell 走可执行文件精确白名单 + argv 直传不解释 shell 语法、HTTP 走域名白名单。第三，**凭证走环境变量**，`${ENV}` 占位，代码和配置文件里搜不到明文 key。

**Q5「依赖倒置具体怎么落的？」**
> 一句话规则：**跨模块契约（接口 + 值对象）放 core，由下游模块实现**。`LlmCaller`（循环调模型的端口）、`LlmCallRecorder`（审计端口）、`SessionManager`（会话持久化端口）、`NotifyChannelSource`（通知渠道取数端口）都定义在 core，实现分别在 provider、storage、tool 模块。**所以新增一个 Channel 或 Tool 只加新模块，core 一行不用改。**

**Q6「哪些是你刻意没做的？」**
> 并行工具调用、上下文压缩、流式输出、Agent 间互相委托、provider 的 fallback/熔断/成本看板、各 IM 的专用 Adapter——全在扩展阶段。分阶段克制是有意的：治理和分布式基础设施，应该在真实使用数据验证之后再做，而不是先造一座没人住的宫殿。

---

## 六、讲之前自查：最容易露馅的三处

1. **Notify 不是定时任务**。定时任务是另一节课（25 节）；Notify 是出站通知。这条说反了整个模块的定位就崩了，见 [notify.md](notify.md)。
2. **`ProfileContext` 的动机别答成"取 Profile 配置"**。Profile 是一路显式传参的；`ProfileContext` 存在是因为工具接口签名不带 Profile，工具执行时要靠它知道"当前是哪个 Agent"，见 [react.md](react.md)。
3. **别声称 fallback / 熔断 / 工具策略层已实现**。fallback 属 023、工具治理策略属 020，当前分支都还是未落地的。说"在扩展阶段"是安全且真实的答案。
