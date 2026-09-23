# Implementation Plan: Notify 出站通知（第19节）

**Branch**: `019-lesson19-notify` | **Date**: 2026-09-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-notify-outbound/spec.md`

## Summary

给底座补上"结果怎么主动送出去"的出口：在 `fourfeetcat-tool` 立出站通知契约（`NotifyChannelAdapter`）、通知目标值对象（`NotifyTarget`，只带渠道类型 + 一份配置）与核心阶段唯一实现（`WebhookNotifyAdapter`，通用 HTTP webhook，POST 一条带内容的 JSON）；在 `fourfeetcat-storage` 立渠道注册表（`notify_channels` 表双轨 V19 + 实体 + 仓储 + 建表测试），Agent 侧只按名引用、地址与凭证不进对话。

**本节范围**（课件 L40 自述 + 主公裁决）：通知工具 `NotifyTools` 的完整接线依赖 20 节的工具注册/工具结果类型与 23/24 节的沙箱校验，其**完成时点在沙箱节之后**——本节只把契约与实现备好，该任务登记为跨节。

## Technical Context

**Language/Version**: Java 21（Maven 多模块，spring-boot-starter-parent 3.5.16）

**Primary Dependencies**: 本节**不新增任何第三方依赖**（全部为框架内、BOM 托管的库）。新增的模块依赖：`fourfeetcat-tool` 引入 `org.springframework:spring-web`（`RestClient`）、`org.springframework.boot:spring-boot`（`@Component` 所需的 spring-context，与 `fourfeetcat-cli` 同款声明）、`spring-boot-starter-test`（test 作用域）；`fourfeetcat-storage` 的测试依赖第16节已就位。假接收端用 **JDK 内置** `com.sun.net.httpserver.HttpServer`（零依赖，已核实可用）。HTTP 客户端 `RestClient`（依赖树实测 `spring-web 6.2.19`）与 `HttpServerErrorException` 家族已核实存在。

**Storage**: `notify_channels` 表（手工建表脚本双轨 `db/migration/{sqlite,postgresql}/V19__notify_channels.sql`，逐字摘自 `docs/class/schema.sql` 的 notify_channels 段）；`ddl-auto: none`；测试建表走手工脚本 + 临时目录文件库。

**Testing**: JUnit 5 + AssertJ。`WebhookNotifyAdapterTest`（`fourfeetcat-tool`，课件 harness 第一批，本节即可跑）；`NotifyChannelRepositoryTest`（`fourfeetcat-storage`，按裁决补的建表守点）。`NotifyToolsTest` 为课件第二批，**跨节、本节只登记不完成**。

**Target Platform**: JVM 服务端（本节的出口供后续定时触发与两个 Demo 使用）

**Project Type**: library（多模块底座；本节第一次让 `fourfeetcat-tool` 有实质内容）

**Performance Goals**: 无专项指标（一次推送 = 一次同步 HTTP 调用；宪法原则七：同步阻塞）

**Constraints**: 宪法原则五（审计不回退：通知的审计走既有工具执行路径，本节不新增审计逻辑）、原则六（沙箱：本节不做工具，故不新增涉外校验位，校验归属调用链上游的第24节）、原则七（同步，无 Reactor/CompletableFuture/自建线程池）；语法禁区——避开 P3C/ASM 解析不了的 Java 18+ 形态；测试方法名英文 + `@DisplayName` 保留课件原文。

**Scale/Scope**: 课件交付物（三个 notify 类 + 两个测试类 + 渠道配置）减去跨节的 NotifyTools，再加按裁决补的渠道表三件套（实体 + 仓储 + 双轨脚本），不加不减。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|---|---|---|
| 一：自实现 ReAct | 本节不碰循环 | ✅ 通过 |
| 二：Spring AI 只用两件事 | 本节不碰模型调用；不用 `ChatClient`、不引任何自动工具执行路径（`@Tool` 注解归第20节） | ✅ 通过 |
| 三：Provider 显式映射 | 本节不碰 | ✅ 通过 |
| 四：一个目录 = 一个 Agent | 本节消费 Profile 已交付的渠道名清单字段，不改其类型与语义；渠道实体是全局注册表、不进 Agent 配置正文（与 §6.8 一致） | ✅ 通过 |
| 五：审计 Day One | 通知推送的审计**走既有工具执行路径**（工具接线在 24 节后落地时自动生效），本节不另建审计表、不新增审计逻辑 | ✅ 通过（口径已记入 spec Assumptions） |
| 六：无 SecurityManager、真实路径校验 | 本节不做工具层，故不新增校验位；`WebhookNotifyAdapter` 只做发送，白名单校验归属调用链上游（第24节接线）——见 research.md D6 | ✅ 通过（归属已记录） |
| 七：同步执行 | 全链路同步阻塞；`RestClient` 是同步客户端 | ✅ 通过 |
| 技术约束：Flyway 双轨 | V19 双轨同版本号落地（sqlite + postgresql） | ✅ 通过 |
| 技术约束：依赖倒置 | 出站契约与实现在 `fourfeetcat-tool`（能力域）；渠道持久化在 `fourfeetcat-storage`；两者都只依赖 core，无环 | ✅ 通过 |
| 技术约束：配置与凭证 | 渠道地址含凭证，走 `${ENV}` 占位解析，明文不入库、不入日志（FR-007） | ✅ 通过 |
| 技术约束：模块结构 | 无新建、无改名模块；`fourfeetcat-tool` 从空模块开始有内容——与该模块既定职责（内置 Tool + MCP + 沙箱三合一）一致，无需同步 §10 | ✅ 通过 |

无违宪项，Complexity Tracking 只记一处口径取舍。

## Project Structure

### Documentation (this feature)

```text
specs/004-notify-outbound/
├── plan.md              # 本文件
├── research.md          # Phase 0：8 条设计决策 + 依赖核实
├── data-model.md        # Phase 1：notify_channels 表（双方言）+ 渠道解析口径
├── quickstart.md        # Phase 1：验证指南（判卷命令块 + 人工项）
├── contracts/           # Phase 1：跨模块 Java 契约
└── tasks.md             # /speckit-tasks 产出（本命令不创建）
```

### Source Code (repository root)

```text
fourfeetcat-tool/src/
├── main/java/org/fourfeetcat/tool/notify/
│   ├── NotifyChannelAdapter.java      # 新增：出站通知契约（一个方法，签名中立）
│   ├── NotifyTarget.java              # 新增：通知目标值对象（record：channelType + config）
│   └── WebhookNotifyAdapter.java      # 新增：核心阶段唯一实现（@Component，RestClient 构造注入）
└── test/java/org/fourfeetcat/tool/notify/
    └── WebhookNotifyAdapterTest.java  # 课件 harness 第一批（假接收端为类内嵌套类，不新增文件）

fourfeetcat-storage/src/
├── main/java/org/fourfeetcat/storage/
│   ├── NotifyChannel.java             # 新增：notify_channels 表实体（列名与 V19 逐字一致）
│   └── NotifyChannelRepository.java   # 新增：JpaRepository<NotifyChannel, String>
├── main/resources/db/migration/sqlite/V19__notify_channels.sql
├── main/resources/db/migration/postgresql/V19__notify_channels.sql
└── test/java/org/fourfeetcat/storage/NotifyChannelRepositoryTest.java

fourfeetcat-boot/
├── pom.xml                            # 改：加 fourfeetcat-tool 依赖（该模块此前无人依赖，不接则新类不进容器与 fat jar）
└── src/main/java/org/fourfeetcat/boot/AgentRuntimeConfiguration.java
                                       # 改：补一个 RestClient Bean（用 Boot 自动配置的 RestClient.Builder 构造）

fourfeetcat-tool/pom.xml               # 改：加 spring-web / spring-boot / spring-boot-starter-test
```

**Structure Decision**: 出站通知的三件套落 `fourfeetcat-tool` 的 `notify` 子包——按技术方案 §6.8/§10，出站适配器属于"能力四"（Tool 体系）那条线，`fourfeetcat-tool` 正是"内置 Tool + MCP + 沙箱三合一"的模块，四节（20/24）的 ToolRegistry 与 Sandbox 也落在同一模块，本节先占住 `notify` 子包。渠道注册表的持久化落 `fourfeetcat-storage`：`tool` 模块**不碰 JPA**（它只依赖 core），实体与仓储在 storage，符合本仓既有的依赖倒置手法（第16/17/18 节同款）。本节不新建模块、不改模块职责边界。

`RestClient` Bean 补在 boot 的装配类：`WebhookNotifyAdapter` 按课件形态收 `RestClient`（构造注入），而 Spring Boot 自动配置只提供 `RestClient.Builder`；装配处用该 Builder 造一个 `RestClient` Bean，是让这个 `@Component` 能落地的必要接线，也让 24 节接 `NotifyTools` 时零改动。

**boot 必须新增对 `fourfeetcat-tool` 的依赖**（`/speckit-analyze` 发现的缺口）：实测该模块此前**没有任何模块依赖它**（只有父 pom 的 `<modules>` 列着），于是 boot 的 classpath 上根本没有 `org.fourfeetcat.tool` ——新类既不会被容器扫到，也进不了 fat jar。加这一条依赖（与既有 web/cli/provider/storage 并列，**无环**：`boot → tool → core`）才让本节的"接口 + 实现"真正接入运行时；`fourfeetcat-tool` 是五大能力之一的宿主模块，接进 boot 是迟早的事，本节接上可让 24 节零改动。

## Complexity Tracking

> 只记一处**口径取舍**，非违宪；列在此处是为了让 reviewer 一眼看到"偏离/多出来的是什么"。

| 事项 | 为什么需要 | 更简单的替代为何被否 |
|---|---|---|
| 假接收端用 JDK 内置 `HttpServer`，而课件写的是 `MockWebServer` | 本地依赖仓无 `mockwebserver`、锁定 BOM 也不管理它——引入就是新增第三方依赖 + 需要联网拉取，只为测试脚手架不值（主公已裁） | 按课件引 `com.squareup.okhttp3:mockwebserver`：多一个测试期第三方依赖且版本要手工锁，收益只是"与课件字面一致" |
