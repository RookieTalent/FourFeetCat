# Phase 1 Contracts: Notify 出站通知（第19节）

**Input**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Data Model**: [data-model.md](../data-model.md)

本节全部新增签名逐条列出；跨模块只经 core 既有类型（`Profile` 的渠道名清单字段为消费、不改）。

---

## §1 `fourfeetcat-tool`：出站通知三件套（`org.fourfeetcat.tool.notify`）

```java
// NotifyChannelAdapter.java —— 出站通知契约：只表达意图，不携带任何一档实现特有的词
public interface NotifyChannelAdapter {
  void send(NotifyTarget target, String content);
}

// NotifyTarget.java —— 通知目标值对象
public record NotifyTarget(String channelType, Map<String, String> config) {}

// WebhookNotifyAdapter.java —— 核心阶段唯一实现（通用 HTTP webhook）
@Component
public class WebhookNotifyAdapter implements NotifyChannelAdapter {

  public WebhookNotifyAdapter(RestClient restClient);   // 构造注入，便于替换与测试

  @Override
  public void send(NotifyTarget target, String content);
}
```

**`send` 的行为契约**：

| 情形 | 行为 |
|---|---|
| `config` 里没有 `url` 或为空 | **抛 `IllegalArgumentException`**（地址缺失是配置错误，不发一个空地址的请求）——spec Edge Cases 要求 |
| 正常 | POST 到该地址，`Content-Type: application/json`，body 为 `{"content": "<内容>"}` |
| 接收端 4xx/5xx、超时、不可达 | **异常向上抛**（`RestClient` 默认行为，不做包装——research.md D7）；调用方不会误以为已送达 |
| 沙箱白名单 | **本类不做校验**：由调用方（工具层）在发送前执行，第24节接线（research.md D6）——类注释须写明这一归属 |

**中立性自查（SC-004 的人工项）**：签名里出现的词只有"通知目标""内容"与通用类型；`channelType` 是抽象概念而非某个渠道名。换成任意一档专用实现（某家 IM 的官方 SDK、SMTP 邮件），`send(NotifyTarget, String)` 不需要改。

---

## §2 `fourfeetcat-storage`：渠道注册表两件套

```java
// NotifyChannel.java —— notify_channels 表实体（列名与 V19 脚本逐字一致）
@Entity
@Table(name = "notify_channels")
public class NotifyChannel {                 // 字段见 data-model.md §2；getter/setter 齐备
  // @Id private String name;（名字由运营方指定，无 @GeneratedValue）
}

// NotifyChannelRepository.java —— 渠道表仓储
public interface NotifyChannelRepository extends JpaRepository<NotifyChannel, String> {
  // 本节只需父接口能力（按主键 findById / save）；"按名解析"的封装归第24节
}
```

**依赖方向**：`storage` → `core`（既有）；本节不给 storage 加任何新依赖。

---

## §3 `fourfeetcat-boot`：装配一处（让 `@Component` 能落地）

```java
// AgentRuntimeConfiguration.java（改）
@Bean
public RestClient restClient(RestClient.Builder builder) {   // Builder 由 Boot 自动配置提供
  return builder.build();
}
```

**为什么需要**：`WebhookNotifyAdapter` 按课件形态收 `RestClient`（构造注入），而 Spring Boot 自动配置只提供 `RestClient.Builder`——装配处用该 Builder 造一个 `RestClient` Bean，这个 `@Component` 才能被容器实例化（否则启动期报"找不到 RestClient 类型的 Bean"，连带打断既有上下文测试）。24 节接 `NotifyTools` 时零改动。

---

## §4 模块依赖方向（新增边，全部无环）

```text
fourfeetcat-tool    → core（既有）, spring-web（新）, spring-boot（新）, spring-boot-starter-test（新，test）
fourfeetcat-storage → core（既有）, jackson-databind / spring-ai-model（既有）   [本节只加两个类 + V19 双轨]
fourfeetcat-boot    → 既有依赖不变；只改装配类一行
fourfeetcat-core    → 无新增依赖
```

`tool` 模块**不引 JPA**（实体与仓储在 storage）；`storage` 模块**不引 spring-web**（出站实现不在 storage）。无模块新建或改名。

---

## §5 跨节任务（本节**不交付**，仅登记形状以免后序走样）

```java
// 归第20节（工具注册 + 工具结果类型）+ 第24节（沙箱接线）之后落地，本节不写
@Component
public class NotifyTools {
  public NotifyTools(Sandbox sandbox, NotifyChannelAdapter adapter, /* 渠道解析所需 */);
  public ToolResult notify(String content, String channel);   // 形状以 20 节工具抽象为准
}
```

**本节必须留下的保证**：
1. 通知的审计走**既有工具执行路径**（成功失败都留痕），本节不新增审计逻辑与审计表；
2. 白名单校验由**工具层**在发送前执行（顺序断言即课件 `NotifyToolsTest` 的 `InOrder` 守护点），本节不把校验塞进适配器（research.md D6）；
3. 渠道名缺省时取第一个渠道、"渠道未配置则明确报错"这两条行为，随工具层落地时实现——本节只保证注册表数据能被按名读到。
