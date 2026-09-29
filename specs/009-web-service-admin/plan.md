# Implementation Plan: Web Service 与第一版管理平台（第26节）

**Branch**: `009-lesson26-web-service` | **Date**: 2026-09-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-web-service-admin/spec.md`

## Summary

到第25节为止，底座的能力全部就位，但**只有 CLI 一个对外出口**——业务系统接不进来。本节补上能力五：把内部能力包成 REST 端点，顺带交付第一版只读管理台。

一句话说清本节的技术形态：**六个 Controller，每个都是"参数校验 + 响应包装 + 错误处理"三件事的壳**，实际逻辑一律委托给跟 CLI 完全相同的入口（`AgentService.process`）。人推的两个入口共用一个引擎——这是架构上反复强调的点，也是写代码时最容易被"顺手在 Controller 里加点逻辑"破坏掉的东西，所以 harness 里有一条专门的断言：一次正常请求下处理入口**恰被调用一次**。

本节真正的工程重心是三处**全局口径**，而不是端点数量：

1. **异常出口只有一个**——六类状态码（400/404/500/503/504 与成功的 200）由唯一一处处理器收敛成既有信封 `ApiResponse`；其中"500 不泄漏内幕"这条最值钱：内部异常细节只进日志，对外只给统一话术。
2. **防呆边界**——单条消息 32KB、历史返回最近 100 条；超限是 400，不是 500。
3. **零凭证可启动**——服务只认一份 Provider 凭证（甚至一份都不配）就能起来，`serve` 不因框架的急切装配索要第二份 key。

管理台是本节第二个交付物，也是"API 是完备的对外通道"这句话的验证：**它自己没有后端**，五个页面全部只调已发布的查询端点，因此端点集但凡缺一块，管理台立刻就露怯。它只读、不做任何写操作（"通过 API 定义 Agent"是 29/30 节的正题），且视觉与官网首页同源。

## Technical Context

**Language/Version**: Java 21（Maven 多模块，`maven.compiler.release=21`）；前端 Vue 3 + Vite（Node 24 / npm 11 本机已就位）。

**Primary Dependencies**:
- Spring MVC（`spring-boot-starter-web`，已在 `fourfeetcat-web` 上）+ Java 21 虚拟线程（配置已就位）。
- `springdoc-openapi-starter-webmvc-ui` 2.9.1——**已在** web 模块的 pom 与根 pom 的 dependencyManagement 里（工程地基阶段引入），本节只是让它的文档变真。
- 新增**构建插件** `frontend-maven-plugin`（绑定 `install-node-and-npm` + `npm ci` + `npm run build`）——它不进运行期依赖树，只进构建期。
- 新增**模块依赖**两条：`fourfeetcat-web → fourfeetcat-tool`（取 `ToolRegistry.all()` 列出全部工具）、`fourfeetcat-web → fourfeetcat-provider`（取 `ProviderProperties` 列出各 Provider 配置态）。两条边均无环（tool→core、provider→core），web 是叶子消费者。见 D4。
- 60 秒读超时所需的 `org.springframework.boot.http.client.ClientHttpRequestFactorySettings` / `ClientHttpRequestFactoryBuilder`（Boot 3.5.16 自带）与 `org.springframework.retry.support.RetryTemplate`（`spring-retry 2.0.13`，经 `spring-ai-retry` 传递）——**动笔前已 `javap` + 依赖树核实**（见 D10）。

**Storage**: 不适用。本节**不新增表、不新增落库调用、不新增迁移脚本**：会话与审计都由既有路径落库（`sessions` / `llm_calls` / `tool_invocations`）。`GET /sessions` 复用既有的"按最后活跃时间倒序列举"仓储方法（`session list` 命令用的就是它）。

**Testing**: JUnit 5 + AssertJ + Mockito + MockMvc（既有栈）。
- `SessionApiControllerTest`（web，`@WebMvcTest` 切片；mock 掉核心处理入口）——超 32KB → 400、会话不存在 → 404、正常请求处理入口恰调一次、归档会话拒发 → 400。
- `GlobalExceptionHandlerTest`（web，切片）——逐类异常映射到约定状态码、响应体都是统一信封、**500 响应绝不含内部异常 message**。
- `WebSmokeIT`（**boot**，`@SpringBootTest` 真实上下文，不依赖模型）——健康/运行信息/Agent 列表/工具列表四端点真实链路可达 + 零凭证可启动。落 boot 的理由见 D14：真上下文装配与数据访问层扫描声明只有 boot 有；它由 Spring Boot 父 pom 配好的 failsafe 在 `verify` 阶段跑（**不受文件名 `*IT` 影响**，CI 会真跑它）。打 `@Tag("integration")` 与既有 `ProviderSmokeIT` 同口径。

**Target Platform**: 服务端（开发机 Windows / 部署 Linux + 容器）；管理台为浏览器端静态资源，无构建平台特殊性。

**Project Type**: 多模块库（分布式 Agent 底座）——本节只碰 `fourfeetcat-web`（主战场）、`fourfeetcat-provider`（一处在装配处加超时）、`fourfeetcat-boot`（一个冒烟测试）；**不新建、不改名模块**。

**Constraints**:
- `mvn clean verify` 全绿是完成的定义，含 Spotless(GJF) / PMD7 / Checkstyle(google) / SpotBugs+FindSecBugs。
- 端点路径与错误码口径**定死**：11 个端点（见 contracts）、400/404/500/503/504；信封字段沿用既有 `ApiResponse`（`code` / `message` / `data` / `timestamp`）。
- 全同步阻塞：不出现 Reactor / `CompletableFuture` / 自建线程池；并发交给虚拟线程。60 秒上限落在**单次模型调用**边界，不引入限时等待线程。
- Controller 不得夹带业务逻辑；不得绕过 `AgentService` 自建处理链；不得为 Web 另开审计通路。
- 会话标识的拼接仍只在既有唯一位置（`JpaSessionManager`），web 层只递三元组。
- 避开 P3C/ASM 解析不了的 Java 18+ 语法形态（`switch` 不写 `default ->`）。
- 管理台构建产物不入库（`.gitignore`），源码入库。

**Scale/Scope**: 新增约 11 个源文件（6 个 Controller + 4 个异常类型 + 1 个 SPA 回落配置）、1 个前端工程（Vue 3 + Vite，AI 生成）、1 个项目内风格 skill、3 个测试类；改 4 个既有文件（web 的 pom、provider 的 `ProviderConfiguration`、boot 的 `application.yaml`（若需）与 4 处文档表述）；另加 1 个 `.gitignore` 条目。净增后端代码量在 600 行以内（含注释与视图记录）。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|------|------|------|
| 一：自实现 ReAct | 本节不碰循环：`ReActLoop` / `PromptBuilder` / `ToolExecutor` 零改动。发消息端点做的事就是"调 `AgentService.process`"——与前 17~25 节完全同一个人推入口 | ✅ 通过 |
| 二：Spring AI 只用两件事 ⚠️ | 不新增工具、不动 `@Tool` schema、不碰 `ChatClient` 路径。**唯一与 Spring AI 的接触面**是 `OpenAiApi.Builder.restClientBuilder`（给 HTTP 客户端加读超时）——这是协议适配层的连接参数，不是自动工具执行路径。**且经核实**：课件"决策四"要排的那个自动装配类在本仓类路径上根本不存在（锁定的 spring-ai 1.1.2 模型核心件内无任何自动装配条目），故不写那行死配置，改用"零凭证可 boot"的断言守住该决策的意图（见 D11） | ✅ 通过（含一处登记） |
| 三：Provider 显式映射 | 不碰路由；`/info` 只把 `fourfeetcat.providers` 的声明**读出来展示**（名字 + 端点 + 凭证是否就位），不做任何按类型扫描容器的动作 | ✅ 通过 |
| 四：一个目录 = 一个 Agent | 不引入新的 Agent/Skill 目录概念。`/profiles` 呈现的是既有 `ProfileRegistry.all()`（第25节为定时加的那次只读遍历，本节复用同一方法，零新增）；管理台不提供任何 Agent 增删改入口 | ✅ 通过 |
| 五：审计 Day One | **零新增审计代码、零新增表**：Web 触发的每一轮，`llm_calls` / `tool_invocations` 由既有路径写入（人推两入口记账口径完全一致）。管理台呈现的记忆/会话也都是既有落库内容的只读投影 | ✅ 通过 |
| 六：不使用 SecurityManager；软链校验真实路径 | 不碰沙箱：Web 触发那一轮的工具调用照旧走第24节白名单校验（调用位在工具执行首行）。本节**不做**认证/鉴权/限流（内网假设，见 spec 边界） | ✅ 通过 |
| 七：同步执行模型 | 端点全同步直进直出，并发交给虚拟线程（配置已在 `application.yaml`）。**无** Reactor / `CompletableFuture` / 自建线程池；60 秒上限落在模型调用的客户端读超时，不引入限时等待线程（见 D10） | ✅ 通过 |
| 八：Tool 模块三合一 | `ToolRegistry` 仍是唯一工具表；`/tools` 只是把它的 `all()` 读出来展示，工具执行链路一行未动 | ✅ 通过 |
| 架构约束：依赖倒置 | 新建的 Controller / 异常处理都在 `fourfeetcat-web`（课件落位表指定）；**web 新增两条出边**（→ tool、→ provider），两条都指向叶子方向、无环（见 D4）。不新增跨模块契约、不在 core 里加任何东西 | ✅ 通过（新增依赖边已声明） |
| 架构约束：模块结构 | **不新建、不改名模块**；无模块边界变化，无需同步 `docs/TechnicalSolution.md` §10 与 CLAUDE.md 的模块表（但端点计数表述需同步，见下） | ✅ 通过 |
| 架构约束：Flyway 双轨 | **不新增迁移脚本**（不落库、不改表） | ✅ 通过 |
| 架构约束：配置与凭证 | 本节**不新增配置键**（虚拟线程、端口都在既有 `application.yaml` 里）；凭证仍走环境变量占位，`/info` 只报"是否就位"、**绝不回显凭证内容** | ✅ 通过 |
| 架构约束：无状态实例、状态外置 | 服务端不持有跨请求状态：会话与审计都在库里，实例可随时重启。（60 秒超时是连接级参数，不是运行状态） | ✅ 通过 |
| 铁律：内容三处同步 | 本节**改变了端点的对外表述**（10 → 11），因此 README / 官网 / 文档必须同步：`CLAUDE.md`、`docs/TechnicalSolution.md`（3 处）共 4 处表述改口径（见 D13）。官网首页与 README 未列端点计数，无需改动 | ✅ 通过（含 4 处同步 + 1 处事实纠正） |
| 技术约束：中文注释惯例 | 新增类 / 方法均带中文 javadoc：写清"谁消费、什么口径、为什么这么设计（尤其错误码口径与 500 不泄漏）" | ✅ 通过 |
| 技术约束：第三方 API 可得性 | 动笔前已 `javap` + 依赖树核实（D10：超时相关三类；D4：`ToolRegistry.all()` 与 `ProviderProperties` 的可见性）；**无未核实的第三方 API** | ✅ 通过 |

**Gate 结果：全部通过，无违宪项。** 下面登记的是七处**有意的口径选择或对课件字面的偏离**，逐条给出理由与被否备选。

## 偏离与裁决登记（写给 reviewer）

| # | 偏离点 | 理由 | 备选被否 |
|---|--------|------|---------|
| ① | **端点 11 个**（照课件"10 个"加一个只读会话列表） | 课件的管理台提示词要求"会话列表"页调 `GET /api/v1/sessions`，而该路径不在其自列的 10 个里——课件内部自相矛盾。加端点保住了"管理台五个页面全由已发布端点供数"这一验证意图 | 严格 10 个、把列表页缩成"按 id 查询"：管理台核心一页残缺，且"API 是完备通道"这句话失去验证力（经主公裁决取加端点） |
| ② | **不写课件"决策四"那行排除配置** | 本地核实：锁定 spring-ai 1.1.2 的模型核心件**不含任何自动装配条目**，依赖树里也没有对应自动装配件——本仓的 ChatModel 从一开始就是显式构造的，那个"启动即索要第二份凭证"的坑**不存在**。写一行指向不存在类的配置会误导后人 | 照课件加上（防御位）：一行死配置，读者会以为该自动装配件在类路径上（经主公裁决取不加） |
| ③ | **设计 token 以本仓官网实际值为准**（明亮白底 + 猫蓝，非课件写的深色 + 橙） | 课件该章意图是"钉死到官网首页、一个字别自创"，但它那份色值描述的是**旧版官网**：本仓 `website/.vitepress/theme/styles.css` 现为白底 + 猫蓝，橙色在首页里根本没出现，课件点名的样式文件名在本仓也不存在。照过期值做会造出与首页**不同源**的界面，违背课件自己的意图与本仓三处同步铁律 | 照课件深色 + 橙：管理台与官网两套气质，"与首页完全一致"在验收时是假话（经主公裁决取官网实际） |
| ④ | **`WebSmokeIT` 落 `fourfeetcat-boot`**，并在该模块**显式声明 failsafe 插件** | 课件只说"`@SpringBootTest` 起真实上下文"，未说落哪个模块。真上下文（运行期装配 + 数据访问层扫描声明 + 全部能力 Bean）**只有 boot 有**；放 web 会退化成再造一套测试装配。**实施中实测纠正**：`*IT` 类不会被自动执行——父 pom 只给了 pluginManagement 配置，不声明插件就不跑（第一轮 verify 里 `WebSmokeIT` 一次都没出现，构建却是绿的）；已在 boot pom 声明 failsafe（详见 research D14） | 放 web 并自建测试配置：测试里复制一份生产装配，装配漂移时测试不红——本节最该守的恰是"装配没错"；改类名成 `*Test`：动课件已定的类名字面量 |
| ⑤ | **`/info` 的 Provider 状态是配置态**（凭证是否就位），不发探活请求 | 真探测会让该端点的冒烟验证变成网络依赖，且本仓"零 key 可 boot"是既有口径——真探测在零 key 部署下必然全红，把"没配 key"与"服务不可达"混成一种现象 | 真网络探测（经主公裁决取配置态） |
| ⑥ | **60 秒上限落在单次模型调用**，不是整轮墙钟 | 真落在整轮要引入限时等待线程（与原则七的同步模型、invariant ⑤ 的"无自建线程"冲突），且超时后工作线程可能仍在写库。落在客户端读超时：真超时、真 504、零线程模型变更 | 整轮墙钟限时（经主公裁决取调用边界）；只做异常映射不做超时（504 在真链路里拿不到） |
| ⑦ | **一次性调用每次独立会话身份**（UUID 去横线）；**归档为软标记 + 拒发** | 前者：若同 Agent 共用一条会话，历次无关调用会互相污染上下文（历史段会回放），"无状态"名不副实。后者：归档只改状态不设约束，则"归档"只剩一个字段值 | 共用固定身份 / 归档仅标记不设约束（均经主公裁决否） |
| ⑧ | **修前序节缺陷：`serve` 启动后不保活**（触碰 `ServeCommand` / `GatewayCommand` / `FourFeetCatCli` 三个第18节文件） | 本节是**第一次真正让 `serve` 常驻**（课件原话），实测暴露：命令方法一返回，`main` 的 `System.exit` 立刻把进程带走——服务在启动后约 0.1 秒优雅停机，**退出码还是 0**。`gateway` 从一开始就有保活位（`CountDownLatch`），`serve` 漏了。修法：把保活位收到根命令一处（谁启引擎、谁负责"启完不要返回"），两条长驻命令共用。**不改任何对外契约**（命令名、参数、行为语义不变，只是让"启动服务"这件事真的成立） | 只报告不改：本节交付的 Web Service 将**没有可用的启动方式**（`/admin`、11 个端点都无从访问），"能力五有了对外出口"这句话不成立 |
| ⑨ | **管理台回落判据要认下挂载点本身**（`/admin` 裸路径） | 实施中现场冒烟抓到：资源解析器收到裸路径时 `resourcePath` 是 `"."`，只按"含点即静态资源"判断会把挂载点自己判成找不到的资源——`/admin` 404 而 `/admin/sessions` 正常。已修（显式认下 `""` 与 `"."`），并把该守点补进冒烟测试（含"缺资源的带扩展名路径必须 404、接口打错必须 404"两条反向断言） | 让人访问 `/admin` 得到 404：管理台等于只有知道子路径的人才进得去 |

**另登记 4 处课件与代码库的既有事实差**（不自行改课件，只在报告里点名）：课件点名的样式文件 `website/.viteppress/theme/custom.css` 在本仓不存在（真实为 `styles.css`）；课件第 75 行的虚拟线程与端口配置**已在** `application.yaml` 里（本节无需新增该配置）；课件 harness 的断言字段 `$.errorCode` 与它自己指定的既有信封字段（`code`）不符（见 D2）；课件写的产品前缀为 OryxOS，本仓已统一为 FourFeetCat（模块名、包名、工作区目录均按本仓口径落地）。

## Project Structure

### Documentation (this feature)

```text
specs/009-web-service-admin/
├── plan.md                      # 本文件
├── spec.md                      # 需求（含五条裁决）
├── research.md                  # Phase 0：D1~D14 技术裁决
├── data-model.md                # Phase 1：对外视图 / 请求体 / 限制与状态规则
├── contracts/http-api.md        # Phase 1：11 个端点的 HTTP 契约 + 既有契约接触面
├── quickstart.md                # Phase 1：可执行的验证指南
├── checklists/requirements.md   # spec 质量清单（specify 阶段产出）
└── tasks.md                     # Phase 2 产出（/speckit-tasks）
```

### Source Code (repository root)

```text
fourfeetcat-web/src/main/java/org/fourfeetcat/web/
├── api/ApiResponse.java                    # 既有，复用（不改）
├── api/ErrorCode.java                      # 改：+REQUEST_TIMEOUT(504)（枚举加一项）
├── api/GlobalExceptionHandler.java         # 改：扩展异常映射（新增 404/400/504 分支，兜底口径不变）
├── api/ServiceUnavailableException.java    # 既有，复用（不改）
├── api/InvalidRequestException.java        # 新增：400（空消息 / 超 32KB / 已归档会话拒发）
├── api/SessionNotFoundException.java       # 新增：404（会话不存在）
├── api/ResourceNotFoundException.java      # 新增：404（Agent 名未加载等资源不存在）
├── api/AgentTimeoutException.java          # 新增：504（模型调用超时）
├── session/SessionApiController.java       # 新增：5 个端点（建/发消息/查历史/归档/列表）
├── session/SessionView.java                # 新增：会话对外视图（列表页与详情页共用）
├── agent/AgentApiController.java           # 新增：1 个端点（无状态 invoke）
├── profile/ProfileApiController.java       # 新增：1 个端点（列 Agent）
├── memory/MemoryApiController.java         # 新增：1 个端点（长期记忆全文）
├── tool/ToolApiController.java             # 新增：1 个端点（列工具）
├── system/SystemApiController.java         # 新增：2 个端点（health / info）
├── admin/AdminSpaController.java           # 新增：/admin/** 未命中回落 index.html（SPA 前端路由）
└── ping/PingController.java                # 既有，保留（工程地基自证端点）

fourfeetcat-web/src/main/frontend/          # 新增：Vue 3 + Vite 只读管理台（AI 生成，五页调五个 GET 端点）
fourfeetcat-web/src/main/resources/static/admin/   # 构建产物（不入库，由前端构建产出）
fourfeetcat-web/pom.xml                     # 改：+tool、+provider 依赖；+test 依赖；+frontend 构建插件

fourfeetcat-web/src/test/java/org/fourfeetcat/web/
├── session/SessionApiControllerTest.java   # 新增：切片单测（32KB / 404 / 恰调一次 / 归档拒发）
└── api/GlobalExceptionHandlerTest.java     # 新增：切片单测（六类映射 + 统一信封 + 500 不泄漏）

fourfeetcat-boot/src/test/java/org/fourfeetcat/boot/
└── WebSmokeIT.java                         # 新增：真上下文冒烟（四端点可达 + 零凭证可 boot）

fourfeetcat-boot/pom.xml                     # 改（实施中补线）：显式声明 maven-failsafe-plugin
                                            #   否则 *IT 类永远不会被执行——父 pom 只提供了 pluginManagement 配置（research D14）

fourfeetcat-provider/src/main/java/org/fourfeetcat/provider/ProviderConfiguration.java
                                            # 改（改造点）：给 HTTP 客户端设 60 秒读超时 + 单次尝试；
                                            #   对外 Bean 契约（LlmCaller providerService(...)）一字不改

.claude/skills/four-feet-cat-admin-ui/SKILL.md   # 新增：管理台设计 token + 工程约定 + 三态/响应式规范 + 验收清单
CLAUDE.md / docs/TechnicalSolution.md            # 改：端点计数 10 → 11（共 4 处）+ 1 处官网视觉事实纠正
.gitignore                                       # 改：+静态产物目录
```

**Structure Decision**：沿用既有 Maven 多模块布局，**不新建、不改名模块**。落位完全照课件落位表（"26 Controller/异常处理/static-admin → fourfeetcat-web"），唯一自主决定的是两个测试类的落位（④ 已说明）与前端源码/产物目录（课件指定在 web 模块内）。新增的两条模块依赖边见 research D4 与本文 Complexity Tracking 第 2 行，属依赖方向声明，不构成模块边界变化。

## Complexity Tracking

| 项 | 为什么需要 | 为什么不用更简单的做法 |
|---|-----------|----------------------|
| **新增第 11 个端点** | 管理台"会话列表"页必须有列表数据源，而课件自列的 10 个端点里没有 | 去掉列表页或改成按 id 查询：管理台缺一页，且"五个页面全由已发布端点供数"这条对端点完备性的验证失效 |
| **web → tool / provider 两条依赖边** | `/tools` 要列**全部**工具（core 的 `ToolTable` 只有"按名单取描述"，没有"列全部"）；`/info` 要列**全局 Provider 声明**（该记录类型住在 provider 模块） | 在 core 新造一个"列全部工具 / 查 Provider 配置"的端口：为两个只读展示新增跨模块契约（新对外概念，且要动 core）；或在 web 里自己读配置/扫容器：违反原则三的显式映射口径 |
| **60 秒超时动到 provider 的装配处** | 超时是连接级参数，只能在客户端构造处设；不设则该状态码在真链路里永远拿不到（只做映射等于写了条死分支） | 只写异常映射：504 成摆设；整轮限时等待线程：与同步模型冲突且超时后工作线程仍在写库 |
| **管理台作为独立 Vue 工程 + 构建插件** | 课件明确要求前端与官网同栈（Vue 3 + Vite）、只读、五页；且"一条构建命令产出完整产物"使全量门禁覆盖前端 | 裸 HTML 单文件：违背课件同栈要求；静态资源不入构建：`mvn verify` 覆盖不到前端，CI 看不见它坏 |
| **用测试（而非排除配置）守"零凭证可 boot"** | 该决策的真实意图是"不因框架急切装配索要第二份凭证"；本仓那个自动装配类根本不在类路径上，配置手段无处施加 | 照抄那行排除：指向不存在类的死配置，误导后人 |

## Phase 0 摘要

`research.md` 收敛 D1~D14 十四项技术裁决，**无 NEEDS CLARIFICATION 残留**。其中 D4 / D10 / D14 的关键事实来自本机实测（依赖树 + `javap` + 真跑一次既有 boot 测试），课件与代码库的四处事实差逐条登记。核心结论：

- **D1** 11 个端点与路径冻结（含新增的只读会话列表）。
- **D2** 信封沿用既有 `ApiResponse`；课件 harness 里的 `$.errorCode` 与既有信封不符，按"复用既有信封"这条正文口径落地，断言**守点**（状态码 + 统一话术 + 不含内幕）逐条保真。
- **D3** 异常映射表：4 个新增类型 + 3 个既有类型复用；并消歧"未注册 Agent"（端点先查注册表 → 404）与 503（Provider 侧故障）。
- **D4** web 新增两条无环依赖边；备选（core 造端口）被否。
- **D5** 会话端点的数据来源与归档落点（直用既有仓储，沿用 CLI 先例；不动 `SessionManager` 契约）。
- **D6** 一次性调用的会话身份（UUID 去横线；会话标识列长边界）。
- **D7** `POST /sessions` 的缺省口径（渠道固定 web、用户缺省 anonymous、Agent 缺省取注册表第一个）。
- **D8** 防呆边界（32KB / 空消息 / 100 条截断 + 截断标记）。
- **D9** 长期记忆全文的取用路径（门面既有口径，传 `null` 并登记耦合点）。
- **D10** 60 秒超时的落地方式（客户端读超时 + 单次尝试）与 API 可得性证据。
- **D11** 零凭证可 boot 的守点用测试断言，不写排除配置。
- **D12** 管理台构建串联（插件 + 产物不入库 + 离线构建注意）与前端工程形态。
- **D13** 设计 token 真值来源与本仓文档同步清单（4 处 + 1 处事实纠正）。
- **D14** `WebSmokeIT` 落 boot 且会被 `mvn verify` 真跑（父 pom 的 failsafe 配置，不受 `*IT` 命名影响）。

## Phase 1 摘要

- `data-model.md`：会话视图 / 消息视图 / Agent 视图 / 工具视图 / Provider 状态视图 / 统一信封，请求体与外键规则，限制与状态规则（32KB、100 条、active/archived、一次性会话身份），以及"本节零持久化、零新表"的说明。
- `contracts/http-api.md`：11 个端点的完整 HTTP 契约（方法 / 路径 / 请求 / 响应示例 / 状态码 / 边界），错误码与信封契约，静态资源与文档路径契约（`/admin`、SPA 回落、`/swagger-ui`），以及本节对既有契约的**接触面清单**（只读复用 / 一处改造 / 不得触碰的回归项）。
- `quickstart.md`：可复制的验证命令（只跑本节测试 / 全量门禁 / 前端构建）+ 六条人工验收路径（真模型链路与审计、CLI 与会话存储互见、503 与 504 故障注入、并发压测、管理台五页与三态、接口文档）。
