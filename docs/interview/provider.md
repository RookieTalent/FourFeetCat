# Provider 模块 面试话术

> 模块：`fourfeetcat-provider` · 课件：第 16 节
> 一句话定位：**Agent 与大模型之间的前台**——上层把要说的话递进来，Provider 挑对模型、用对方听得懂的格式发出去、把回话拿回来。

---

## 一、30 秒版（照念）

> Provider 是我做的「Agent 与大模型之间的前台」，也是 FourFeetCat 第一个核心能力。它只干三件事：按 Profile 挑对模型、发起一次调用、把响应原样交回循环。
>
> 有两个关键设计。第一，**工具只翻译不执行**——我把工具说明翻成 Function Calling 的 schema 交给模型，但底层 Spring AI 自带的自动工具执行被我在唯一构建点显式关掉了，执行权收在自己写的 ReActLoop + ToolExecutor 手里。否则工具会被跑两次，更要命的是它绕过了我的沙箱校验——出了事都不知道是谁干的。第二，**多 provider 靠显式映射表路由**，不扫容器里的 Bean 类型，因为 DeepSeek 和 Kimi 在 Spring 容器里类型完全一样，扫出来分不清谁是谁。
>
> 上层是我在 core 里开的 `LlmCaller` 端口，Provider 是它的实现，所以 core 不反向依赖 provider。每次调用成败都落 `llm_calls` 审计表。

---

## 二、展开版（面试官说"细讲"时按这四段推）

### ① 先划边界——这层为什么必须薄

**负责**：挑模型、发起一次调用、翻译 schema、落审计。
**不负责**：循环怎么转（ReActLoop）、工具怎么执行（ToolExecutor）、上下文怎么拼（PromptBuilder）、记忆怎么存（MemoryService）。

一句话收口：**Provider 是"一次 LLM 调用"的封装，不是"一个 Agent 的桥"**。边界不划窄，这层会越写越胖，最后和 ReActLoop 缠死。

### ② 三个坑（信息量最大的一段）

| 坑 | 解法 |
|---|---|
| **多个 provider 怎么区分** | `Map<String, ChatModel>` 显式映射，按全局配置逐条构造；重名直接抛 `IllegalStateException`，启动即拒 |
| **Spring AI 自作主张执行工具** | `ToolCallingChatOptions.builder().internalToolExecutionEnabled(false)`，**收在唯一构建点** |
| **教程里的 provider 名不代表能接** | 动手前 `mvn dependency:tree` 验依赖能下载能解析 |

坑二我做了个动作值得说：工具适配器产出的是**只读 schema 的 `ToolCallback`**，它的 `call()` 方法直接抛异常——**不是靠代码评审保证不执行，是运行到那儿物理上就执行不了**。

（顺带一个取舍：课件说每家模型背后是独立 starter，我实际统一走 **OpenAI 兼容端点**——一个 `OpenAiChatModel` 加各自的 `baseUrl` 就把 DeepSeek/Kimi/Qwen 都接了，省掉一堆 starter 依赖。）

### ③ 结构：两个依赖倒置

```
ReActLoop ──▶ LlmCaller（端口，在 core） ◀── SpringAiProviderServiceImpl（在 provider）
                                            └──▶ LlmCallRecorder（端口，在 core） ◀── storage 出实现
```

`LlmCaller` 和 `LlmCallRecorder` 放 core 的原因：模块依赖只能由 provider、storage 指向 core，反向不行。ReActLoop 注入的是端口类型的 bean，Spring 装配时把实现塞进去。**这是"新加一个 Channel/Tool/provider 都不改 core"的支点。**

### ④ 配置分两层 + 取舍

| 层 | 位置 | 管什么 |
|---|---|---|
| **全局层** | `application.yaml` 的 `fourfeetcat.providers` | 这个实例接了哪些 provider、凭证从哪个 `$ENV` 读 → 解决"连不连得上" |
| **Profile 层** | `.fourfeetcat/profiles/*.yaml` | 这个 Agent 用哪个 model、什么温度 → 解决"这个 Agent 怎么用" |

硬约束：Profile 里的 `provider.name` 在全局层找不到同名项 → 直接抛 `ProviderNotFoundException`，不允许悄悄留空跑过去。

**刻意没做的**：fallback（一家挂了换一家）、hedge racing、熔断、成本看板——都放扩展阶段，现在故障直接把异常抛给上层。核心阶段的目标是"稳定调通一次"。

**验收分层**：mock 掉 `ChatModel` 的单测是主体（不花钱、不依赖网络）；真调模型的那条打 `@Tag("integration")`，**CI 默认跳过——不让外部 API 的可用性变成我流水线的可用性**。

---

## 三、追问预演

| 追问 | 答 |
|---|---|
| 为什么不用 Spring AI 的 `ChatClient` / Agent 抽象？ | 两套执行机制会打架（工具被调两次、绕过沙箱）；而且核心循环我要自己掌握，未来才有定制空间。Spring AI 我只留协议转换和 schema 格式两件事。 |
| Provider 挂了怎么办？ | 诚实答：当前把异常抛给上层；fallback / 熔断在扩展阶段。我先把"能稳定调通一次"做扎实。 |
| `chat` 为什么多带一个 `sessionId`？ | 审计表按 session 关联。`llm_calls` 有 `success` / `error_message` 两列，成功失败都写——只记成功的话，一次事故在库里完全没痕迹。 |
| 怎么保证路由不串台？ | 单测 mock 两个 `ChatModel`：`verify(kimi).call(...)` 加 `verify(deepseek, never()).call(...)`——"不串台"的直接证据。关自动执行也有回归断言。 |
| 新增一家模型要改什么？ | 只在 `application.yaml` 的 providers 列表加一条（name + base-url + `$ENV` 凭证），Java 代码一行不改。 |

---

## 四、证据坐标

| 讲点 | 代码位置 |
|---|---|
| 显式映射表 + 重名即拒 | `fourfeetcat-provider/src/main/java/org/fourfeetcat/provider/ProviderConfiguration.java` |
| 关自动执行 + 成败都落审计 | `.../provider/SpringAiProviderServiceImpl.java`（`internalToolExecutionEnabled(false)`） |
| 只翻译不执行（`call()` 抛异常） | `.../provider/ToolSchemaAdapter.java` |
| core 侧端口 | `fourfeetcat-core/src/main/java/org/fourfeetcat/core/react/LlmCaller.java`、`.../core/LlmCallRecorder.java` |
| 取不到即抛 | `.../provider/ProviderNotFoundException.java` |
