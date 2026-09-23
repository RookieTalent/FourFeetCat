# Phase 0 Research: Notify 出站通知（第19节）

**Input**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

本文件记 8 条设计决策。"已核实"字样表示在**本地依赖**里 `javap` / 实测对过，不是凭记忆。

---

## D1 落位：三个 notify 类进 `fourfeetcat-tool` 的 `notify` 子包

**Decision**: 出站契约、目标值对象、通用 webhook 实现落 `org.fourfeetcat.tool.notify`（`fourfeetcat-tool` 模块，本节起该模块有实质内容）；渠道持久化落 `fourfeetcat-storage`。

**Rationale**:
- 技术方案 §10 把"内置 Tool、MCP Client、Sandbox"三合一放在 `fourfeetcat-tool`；§6.8 的 `NotifyTools` 明确"归 `fourfeetcat-tool`"，出站 Adapter 与它在同一条线上。
- 20 节的 ToolRegistry、24 节的 Sandbox 也落在同一模块——本节先占住 `notify` 子包，后续节各占自己的子包，不打挤。
- `tool` 模块目前只依赖 core（空模块），本节加的三条依赖都是框架内、BOM 托管的库，不破"模块只依赖 core"的骨架。

**Alternatives considered**: 把 notify 单开一个模块——与技术方案 §6.8「`NotifyTools`（内置 Tool，归 `fourfeetcat-tool`）」直接冲突，否。

---

## D2 HTTP 客户端用 `RestClient`（构造注入），依赖三条

**Decision**: `WebhookNotifyAdapter` 收 `RestClient`（**保课件签名**）；`fourfeetcat-tool` 的 pom 新增三条声明：

| 依赖 | 作用 | 版本来源 |
|---|---|---|
| `org.springframework:spring-web` | `RestClient`（同步 HTTP 客户端） | Boot BOM 托管 |
| `org.springframework.boot:spring-boot` | `@Component` 所需的 spring-context（与 `fourfeetcat-cli` 同款声明） | Boot BOM 托管 |
| `org.springframework.boot:spring-boot-starter-test`（test） | JUnit 5 + AssertJ | Boot BOM 托管 |

**Rationale**:
- 课件示例就是 `RestClient` + 构造注入；`RestClient` 是 Spring 6.1+ 的同步客户端，与宪法原则七（同步执行）天然契合。
- **已核实**：`spring-web` 的 `RestClient` 接口存在，`post()` → `RequestBodyUriSpec`、`retrieve()` → `ResponseSpec` 调用链完整（`javap` 用本地仓的 6.2.2；编译与测试实跑的是依赖树解析出的 **6.2.19**，同 minor 线）。
- 三条依赖全在 Boot BOM 里管着版本，**不写 version**（与仓库既有风格一致）。

**Alternatives considered**: `WebClient`（响应式，违宪法原则七）；`HttpURLConnection`（手写样板，无必要）；`RestTemplate`（Spring 已进入维护期，`RestClient` 是它的替代品）。

---

## D3 假接收端用 JDK 内置 `HttpServer`，不引 `MockWebServer`

**Decision**: `WebhookNotifyAdapterTest` 用 `com.sun.net.httpserver.HttpServer` 在本地起假接收端（写成测试类的**私有静态嵌套类**，不新增文件、不进对外概念面）。

**Rationale**:
- 实测：本地仓 `~/.m2/repository/com/squareup/okhttp3/` 下**没有** `mockwebserver`，且 Spring Boot BOM **不管理** okhttp3 → 引入它等于新增第三方依赖 + 需要联网拉取 + 版本要手工锁。
- JDK 的 `HttpServer` **已核实**可用（`com.sun.net.httpserver.HttpServer.create(...)`），断言能力完全一致：能收到 POST、能读 body、能返回 5xx、能记录请求路径与头。
- 主公已裁决走此路（spec Clarifications 第 2 条）。

**Alternatives considered**: 按课件引 `MockWebServer`（见上，收益只是"与课件字面一致"）。

---

## D4 `notify_channels` 表：逐字摘自课程建表脚本

**Decision**: 双轨脚本 `V19__notify_channels.sql`，字段与约束逐字摘自 `docs/class/schema.sql` 的 `notify_channels` 段：

| 列 | 类型 | 约束 | 含义 |
|---|---|---|---|
| `name` | VARCHAR(64) | PK | 全局注册名（Agent 正文里引用的名字） |
| `type` | VARCHAR(32) | NOT NULL | 渠道类型（webhook/feishu/wecom/dingtalk/email） |
| `url` | TEXT | 可空 | HTTP 类渠道的地址 |
| `description` | TEXT | 可空 | 说明 |
| `config` | TEXT | 可空 | 类型相关多字段（JSON），如 email 的 host/port/from/to |

**Rationale**: 课程建表脚本是"16~31 节全部会建的表的完整参考"，其注释明写「19→notify_channels」；第16/17/18 节都按"逐字摘自该脚本"落地，本节沿用同一口径（表名、列名、长度、可空性一律照抄，SQLite 布尔/时间戳口径本节不涉及）。

**Alternatives considered**: 自己设计字段（如加 `enabled`、`created_at`）——超出课程脚本口径，且核心阶段没有写入端，加了也没人用。

---

## D5 实体与仓储落 `fourfeetcat-storage`，`tool` 模块不碰 JPA

**Decision**: `NotifyChannel`（JPA 实体，`@Table(name = "notify_channels")`，主键 `String name`，**不用** `@GeneratedValue`）+ `NotifyChannelRepository`（`JpaRepository<NotifyChannel, String>`）落 `fourfeetcat-storage`。

**Rationale**: 与第16/17/18 节同款依赖倒置手法——能力域模块（tool）只依赖 core，持久化归 storage。`tool` 模块若引 JPA，会把"工具能力"与"数据库"绑死，也破坏本仓既定的模块依赖方向。

**Alternatives considered**: 实体放 tool 模块（违依赖方向）；渠道配置放文件（如 `channels.yaml`）——但 Agent 配置已经走文件、渠道是**实例级共享配置**，技术方案 §6.8 明确要求"持久化在 SQLite 的 `notify_channels` 表"，且课程脚本也建了这张表。

---

## D6 涉外校验的归属：**调用链上游（工具层）**，适配器只做发送

**Decision**: `WebhookNotifyAdapter` **不内嵌**沙箱校验；在其类注释里写明"白名单校验由调用方（工具层）在发送前执行，第24节接线"。本节因此不新增任何校验位。

**Rationale（三条依据一致指向同一结论）**:
1. **课件的顺序断言是硬约束**：第二批 harness 的 `NotifyToolsTest` 用 `InOrder inOrder = inOrder(sandbox, adapter)` 断言「`enforce` 先于 `send` 被调用」。若校验藏进适配器内部，mock 的适配器不会真的去调 mock 的 sandbox——**这条断言就永远测不到东西**，即课件钦定的守护点失效。
2. **技术方案 §6.6/§6.7 的一致模式**：`FileTools`/`ShellTools`/`HttpTools` 都是"在各自 `execute` 方法开头调用 `sandbox.enforce(...)`"——校验由**工具（调用点）**发起，不由 IO 实现发起。
3. **技术方案 §6.8 那句的重点不是调用点**：原文是「发送前一样要过 `Sandbox.enforce(...)` 域名白名单校验，**跟 `http_post` 共享同一份 `http.allowed_domains` 配置，不新增 Sandbox 逻辑**」——它强调的是"共享配置、不另造一套"，与"由工具层调用"并不冲突（`http_post` 的校验也在 `HttpTools.execute` 里，不在 HTTP 客户端实现里）。

**H4 不变量①的满足方式**：本节的唯一涉外 IO 是 `WebhookNotifyAdapter.send`，它的校验位由**调用链上游**在 24 节接线时提供（与 `http_post` 完全同构）；本节在类注释里显式记录这一归属，不留"沉默的绕过路径"（适配器在本节没有任何容器内调用方，直到 24 节 `NotifyTools` 落地才被调用）。

**Alternatives considered**: 校验位塞进适配器（表面更"自保"）——代价是课件那条 `InOrder` 断言失效、且与 `http_post` 的既有模式不一致，否。

---

## D7 失败不静默：`RestClient` 默认行为已满足，无需额外代码

**Decision**: 不做额外异常包装——`retrieve()` 默认状态处理器对 4xx/5xx 抛异常。

**Rationale**: **已核实** `spring-web` 里 `HttpServerErrorException`、`HttpClientErrorException`、`RestClientResponseException` 三个类都在；`RestClient` 的默认行为是"非 2xx 抛 `RestClientResponseException` 家族"。测试只需断言"抛异常"，不必自己写异常映射（写了反而多一层要维护的代码）。该行为已由本节测试用**真实 503 响应**验证（`send_serverError_isNotSwallowed`）。

**Alternatives considered**: 捕获后包装成业务异常——本节没有业务层语义可加，包一层只是把栈加长，否。

---

## D8 渠道解析与"当前 Agent"的接线：本节不做，归 24 节

**Decision**: 本节**不新建**"按名解析渠道"的端口，也不给 `ProfileContext` 加方法；`Profile.notifyChannels` 字段（第16节交付的名字清单）**一字不改**。

**Rationale**:
- 课件的示例调 `profileContext.resolveNotifyChannel(channel)`，但 17 节交付的 `ProfileContext` 只有 `set/current/clear` ——课件把它当成已有能力了。**本节不做工具，故不需要这个方法**，正好避免为此改前序节交付的公共接口（软门禁 #4）。
- 若按全局注册表形态解析，解析动作要读库（storage），而 `ProfileContext` 在 core——给它加"读库"能力会破坏依赖倒置；24 节接工具时再定落点（core 定端口 + storage 实现）。
- 本节唯一的消费点（`NotifyTools`）本身就在 24 节之后，提前定端口等于为没有调用方的接口做设计。

**Alternatives considered**: 现在就在 core 加 `resolveNotifyChannel`/端口——没有调用方、且要动前序接口，否。
