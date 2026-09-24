# Notify 模块 面试话术

> 模块：`fourfeetcat-tool/notify`（契约与取数端口在 `fourfeetcat-core/notify`）· 课件：第 19 节
> 一句话定位：**出站通知**——入站 Channel 管"消息怎么进来"，Notify 补的是对称的另一半："**结果怎么主动送出去**"。

> ⚠️ **先纠正一个最常见的误解**：Notify **不是定时任务**。定时任务是另一节课（25 节）的模块。Notify 解决的是"结果送不出去"，不是"任务什么时候跑"。

---

## 一、30 秒版（照念，先讲动机再讲设计）

> Notify 是出站通知模块，跟入站 Channel 是对称的：入站管"消息怎么进来"，它管"**结果怎么主动送出去**"。
>
> 为什么需要它：CLI 和 Web 都是"人推"——有人发起调用，Agent 处理完把响应直接返回给发起者，同一根请求-响应链路，不需要额外推送。但触发源一旦变成"到点自动"（每天汇总日报、定时查天气），**这条链路就断了：没有人在另一端等着接响应**，Agent 必须自己决定把结果送到哪、怎么送。没有这个模块，每个业务 Agent 都要在自己 Skill 里手写"调 `http_post` 打这个 webhook 地址"，重复且不统一。
>
> 设计上我坚持**接口先行**：`NotifyChannelAdapter.send(NotifyTarget, String)` 这个签名里**不出现任何一档实现特有的词**——不提"企业微信"、不提"飞书"。渠道类型和它的配置装在 `NotifyTarget(channelType, config)` 里，由实现类自己去解释。核心阶段只填通用 webhook 一档，不去碰各家的签名算法和 AccessToken 刷新——那是认证细节，留扩展阶段按需加。**以后换企业微信官方 SDK，接口签名一个字都不用改。**
>
> 安全上有一条我特意钉死的：**notify 发出去也是一次 HTTP 请求，必须跟 `http_post` 一样过白名单校验**，不能因为它是"往外推"就绕过去。测试里用 `InOrder` 断言校验先于发送——**顺序反了就是漏洞**。
>
> 配置放在全局渠道注册表里，按名引用，不暴露在对话里。大多数场景模型只传 content 就够，channel 是可选的。

---

## 二、展开版

### ① 契约：接口表达意图，不表达实现

```java
public interface NotifyChannelAdapter {
    void send(NotifyTarget target, String content);
}

public record NotifyTarget(String channelType, Map<String, String> config) {}
```

签名里没有任何一档实现特有的词。判断这个接口立得住的办法有个现成的自查题：

> **换成企业微信官方 SDK 的实现，`send(NotifyTarget, String)` 这个签名需要改吗？** 答案是不需要。

核心阶段只挂一档实现（`WebhookNotifyAdapter`），以后加渠道只新增实现类，**不改接口、不改调用方**。业务方换渠道只是改配置里的一行 `type`。

### ② `WebhookNotifyAdapter` 的两个"刻意不做"

| 不做 | 为什么 |
|---|---|
| **不内嵌白名单校验** | 校验由调用方（工具层）在发送前执行，与 `http_post` 等内置 Tool 同一模式，不另造一套；顺序断言也钉在调用方那一侧 |
| **不做失败兜底** | 接收端 4xx/5xx、超时、不可达一律向上抛——**Agent 不能以为"已经发出去了"** |

另外地址缺失也**不静默**：既不向空地址发请求，也不返回成功让调用方误以为送达。这一整条思路贯穿本模块：**宁可响亮地失败，绝不安静地丢消息**。

### ③ `NotifyTools`：三步，顺序不可反

```java
NotifyTarget target = resolve(channel);                                    // ① 渠道名 → 通知目标
sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, url));          // ② 过沙箱校验
adapter.send(target, content);                                             // ③ 交给适配器发送
```

- **②必须在③之前**：反了就等于白名单形同虚设。`NotifyToolsTest` 用 `InOrder` 把这条钉死（"顺序反了就是漏洞"）。
- **三种出错都明确报错，绝不静默**：一个渠道都没配 → 报错；渠道名不存在 → 报错并带上名字；地址缺失 → 报错。调用方不能以为"已经发出去了"。
- `channel` 缺省取第一个渠道——大多数场景 LLM 只传 `content` 就够。
- 工具用 `@Tool` / `@ToolParam` 注解声明（JSON Schema 由 Spring AI 生成，符合"Spring AI 只用两件事"的边界），实际调度仍走自己的 `ToolExecutor`。

### ④ 沙箱现状（被追问时要诚实）

沙箱的**检查点已经接进去了**（`sandbox.enforce` 在发送之前，且顺序被测试钉死），但规则本体（白名单配置与校验算法）归沙箱节交付，当前装配的是 `PermissiveSandbox`——**什么也不拦**，构造时打一条 WARN。

标准答法：*"这是有意为之的临时装配，不是可用状态。它的存在只是让沙箱节之前注册的工具能跑通；规则到位时换一个 Bean 即可，`Sandbox` 接口和所有调用方一行不改。我在类注释里明确写了'不得据此运行不可信代码、不得对外做多租户'。"* —— 这句话体现的是"知道自己在哪一步、并且把临时状态标出来"，比假装已完备强得多。

### ⑤ 演进：比课件更进一步的地方（追问准备）

课件原设计是渠道配在 Profile 的 `notify_channels` 字段、由 `ProfileContext` 解析。实际实现演进成了**全局渠道注册表 + 按名引用**：

```
NotifyTools ──▶ NotifyChannelSource（端口，在 core） ◀── JpaNotifyChannelSource（在 storage，读 notify_channels 表）
```

- **core 只留一个取数端口**，`storage` 出实现，`tool` 消费它——又是同一套依赖倒置。
- **端口返回值是通用 `Map` 而不是自造值对象**，理由：通知目标那个形状的值对象住在 `tool` 模块，`core` 看不见它；用通用对象承载，就不必在 core 里再造一份同形状的类型表达同一件事。"怎么解析成通知目标"归消费方。
- 表里只有运营方手配的几行，一次取全足够，不为此再切一个"按名查"的方法——**少一个方法就少一处口径分叉**。

### ⑥ 对接一个新渠道是配置动作，不含代码（业务方最爱问）

以"把日报推到飞书群"为例：

1. 渠道侧建机器人拿 webhook URL；
2. URL 进环境变量（**URL 里的 token 就是凭证本身——泄漏 URL 等于谁都能往群里发消息**），按 `${ENV}` 占位，不明文进配置、不进日志、不进 git；
3. 在渠道注册表登记一条（`type: webhook` + `url`）；
4. **域名进沙箱白名单**（飞书 `open.feishu.cn`、企业微信 `qyapi.weixin.qq.com`、钉钉 `oapi.dingtalk.com`）——这一步漏了，`enforce` 会先于 `send` 把请求拦下来，这正是顺序断言守住的行为；
5. 对话里发一条测试消息验证。

各家 webhook 的 payload 格式其实并不通用（飞书是 `msg_type` + `content.text`，企微/钉钉是 `msgtype` + `text.content`，Slack 是 `text`，ntfy 是纯文本）——核心阶段发的是通用 `{"content": "..."}`；格式对不上时扩展阶段按渠道类型加一档专用 Adapter，接口和调用方都不用改。要富文本卡片这种深度渠道能力，就别塞进 notify，直接配该渠道的 MCP server（Plugin Tool 三档里的方式二）。

---

## 三、追问预演

| 追问 | 答 |
|---|---|
| 这和定时任务什么关系？ | 没关系，是两个模块。定时任务只负责"到点触发"，触发之后 Agent 跑完循环，结果得靠 Notify 送出去——**没有 Notify，定时任务跑完的结果只能烂在 Session 里没人看到**。 |
| 为什么接口不带渠道特有的字段？ | 接口要表达意图而不是实现。签名一旦出现"企业微信""飞书"这类词，每加一个渠道就要改接口和所有调用方；现在加渠道只新增实现类。 |
| 消息发失败了怎么办？ | 一律向上抛，不静默吞。Agent 不能以为"已经发出去了"——这条比"保证送达"更重要，核心阶段不做重试和降级。 |
| 白名单为什么不在适配器里做？ | 校验是所有对外动作的统一关口，必须收在工具层的调用点，跟 `http_post` 同一个模式；做在各自适配器里就等于每个渠道各造一套，迟早漏一个。 |
| 渠道配置为什么从 Profile 挪到全局注册表？ | 渠道是运营方的基础设施配置（一个企业一套群机器人），不是某个 Agent 的调用参数；改成全局登记、按名引用后，加一个 Agent 不用重抄一遍 webhook。 |
| 端口为什么返回 `Map` 而不是定义值的类型？ | 值的形状住在 tool 模块，core 看不见它。用通用对象承载，就不用为了过一道门在 core 里再复制一份同形状类型。 |

---

## 四、证据坐标

| 讲点 | 代码位置 |
|---|---|
| 契约接口 | `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/notify/NotifyChannelAdapter.java` |
| 值对象 | `.../tool/notify/NotifyTarget.java` |
| 通用 webhook 实现 + 两个"不做" | `.../tool/notify/WebhookNotifyAdapter.java` |
| 工具三步 + 顺序不可反 + 三种明确报错 | `.../tool/notify/NotifyTools.java` |
| 取数端口（依赖倒置，返回通用 Map） | `fourfeetcat-core/src/main/java/org/fourfeetcat/core/notify/NotifyChannelSource.java` |
| 端口实现（读库） | `fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/JpaNotifyChannelSource.java` |
| 沙箱检查点与临时装配 | `.../tool/sandbox/Sandbox.java`、`.../tool/sandbox/PermissiveSandbox.java` |
| 顺序断言（InOrder） | `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/notify/NotifyToolsTest.java`（`enforceHappensBeforeSend`） |
