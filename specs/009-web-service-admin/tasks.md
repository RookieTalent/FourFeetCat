---

description: "Task list for Web Service 与第一版管理平台（第26节）implementation"

---

# Tasks: Web Service 与第一版管理平台（第26节）

**Input**: Design documents from `/specs/009-web-service-admin/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/http-api.md, quickstart.md

**Tests**: harness 承载验收（spec SC-003~SC-007、SC-011、SC-012）——测试任务与对应实现**同批落地**（Controller 与其切片测试互为编译依赖）；课件 harness 的关键断言逐条保真，方法名英文 + `@DisplayName` 保留课件中文原文。

**Organization**: 按 User Story 分组（spec：US1 业务系统用 HTTP 接进流程 P1、US2 出错拿到统一格式 P1、US3 运营方一屏看清底座 P2）。

**组织说明**：三个故事的落点不同层——US1 是**会话与调用的端点**，US2 是**异常出口与超时**（横切所有端点），US3 是**信息查询端点 + 管理台**。因此三批实现文件互不重叠，可按顺序推进，每个故事结束时都有可独立验证的增量：US1 后两条主路径可 curl 通；US2 后六类状态码全可复现；US3 后管理台五页能看。

**范围说明（主公裁决，共五处）**：
- **端点 11 个**：照课件"10 个"加一个只读会话列表 `GET /api/v1/sessions`（课件内部矛盾：管理台提示词要调它、端点表却没有它）。代价是 4 处文档表述同步（T029）。
- **不写课件"决策四"那行排除配置**：本地核实该自动装配类**不在本仓类路径上**（锁定 spring-ai 1.1.2 模型核心件内无任何自动装配条目），那个坑不存在；改用"零凭证可 boot"的断言守住该决策意图（T019）。
- **设计 token 以官网实际为准**：本仓官网现为**明亮白底 + 猫蓝**（非课件写的深色 + 橙，那是旧版官网的值）；课件点名的样式文件在本仓也不存在（真实为 `styles.css`）。
- **60 秒超时落在单次模型调用边界**（客户端读超时 + 单次尝试），不做整轮限时等待线程（与原则七与不变量 ⑤ 冲突）。
- **一次性调用每次独立会话身份**；**归档为软标记 + 拒发**（不静默复活）。

**第16~25节已交付、本节只消费不改的件**（不得出现在本节 diff 里，**唯一例外见 T017**）：`AgentService.process`、`SessionManager` 接口与其存储实现（含会话标识的拼接公式）、`Session` 与 `SessionEntity`、`ProfileRegistry`（含第25节的 `all()`）、`ToolRegistry` 与 `CatTool`、`MemoryService` 门面、`ApiResponse` / `ErrorCode` / `ServiceUnavailableException`（本节只**加一项枚举**与**扩展映射**，不改既有成员）、`ReActLoop` / `ToolExecutor` 与两条审计写入路径、沙箱与工具的调用位、全部数据表与迁移脚本（**本节零新增**）。

**同构基准**：本节同构基准 = **课件第 26 节的骨架代码**——`SessionApiController` 的 `@PostMapping("/{id}/messages")` 三件事形态（32KB 校验 → 会话查找 → 委托处理入口）、`GlobalExceptionHandler` 的四段映射（404 / 503 / 504 / 兜底）、管理台的生成提示词（五页 / 只读 / 三态 / `base:'/admin/'`）。落地时按本仓既有形态（构造注入、记录类型、包结构 `org.fourfeetcat.web.<资源域>`）与既有信封 `ApiResponse` 对齐，**不逐字照搬**：骨架里 `new MessageResponse(reply)` 与 `$.errorCode` 这两处与本仓既有类型不符，按 research D2 改译（守点保真）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- 含确切文件路径；路径以 `fourfeetcat-<module>/src/...` 表达

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖与 API 可得性（H3 门禁）、回归基线、同构点核对

- [x] T001 H3 硬门禁复核并固化证据：本节要用的第三方 API 已在动笔前实测（research D4/D10/D11）——`OpenAiApi.Builder.restClientBuilder(RestClient.Builder)`、`OpenAiChatModel.Builder.retryTemplate(RetryTemplate)`、`RetryTemplate.builder().maxAttempts(int).build()`、`ClientHttpRequestFactorySettings.defaults().withReadTimeout(Duration)` + `ClientHttpRequestFactoryBuilder.detect().build(settings)` **均存在**（`javap` 实测）；`ToolRegistry.all()` 与 `CatTool.getInputSchema()`（返回 **String**）**均存在**；`ProviderProperties` 为该模块的 `@ConfigurationProperties` Bean；依赖树中**没有任何 spring-ai 自动装配件**（`jar tf` 过滤 `AutoConfiguration|imports|spring.factories` 全空）。**核不到即停下软报，不得换依赖自行发挥**
- [x] T002 基线核对：既有 boot 上下文测试 BUILD SUCCESS（本节起点的回归基线，`mvn test -pl fourfeetcat-boot -am -Dtest=AgentSchedulerWiringTest` 已跑通）；交付前另跑一次全量
- [x] T003 [P] 核对课件骨架的同构点（**只读不改**）：`SessionApiController.send` 的三件事形态、`GlobalExceptionHandler` 的四段映射与"兜底不吐 `e.getMessage()`"的分寸、管理台提示词的五页/只读/三态/`base` 约定——逐条对照，实现时按本仓信封与包结构落地；同时登记课件的四处事实差（样式文件名、虚拟线程配置已在、`$.errorCode` 字段名、OryxOS 前缀）

**Checkpoint**: 依赖与 API 全部可得；基线绿；同构点与事实差清单确认

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 错误码与异常类型、web 模块依赖——US1/US2/US3 的实现都建在这两件之上

**⚠️ CRITICAL**: 本阶段完成前，六个 Controller 无法编译（异常类型缺失）、`/tools` 与 `/info` 取不到数据（依赖缺失）

- [x] T004 [P] `fourfeetcat-web/src/main/java/org/fourfeetcat/web/api/ErrorCode.java`：枚举**新增一项** `REQUEST_TIMEOUT(504, ...)`；既有五项（200/400/404/500/503）与其 `getCode/getReason` 一字不改
- [x] T005 [P] `fourfeetcat-web/src/main/java/org/fourfeetcat/web/api/InvalidRequestException.java`：400（消息为空/空白/超 32KB、向已归档会话发消息）；javadoc 写明"这是客户端侧的参数问题，不是服务不可用"
- [x] T006 [P] `fourfeetcat-web/src/main/java/org/fourfeetcat/web/api/SessionNotFoundException.java` 与 `.../ResourceNotFoundException.java`：两个 404 类型（会话标识不存在 / Agent 名未加载等资源不存在）；javadoc 写明二者为何分开（"会话"与"其他资源"的资源域不同，将来可能各自扩展行为）
- [x] T007 [P] **（实施中改写）不新增超时专用异常**：实测（`ProviderReadTimeoutTest`）显示超时以 `ResourceAccessException <- java.net.http.HttpTimeoutException` 浮现，因此 504 由统一出口按原因链映射而来，课件骨架里那个没人抛的 `AgentTimeoutException` 属死代码（先建后删，见 research D3/D10）。原计划的"新建 504 异常类型"**未落地**，这不是漏做而是实测驱动的改写
- [x] T008 `fourfeetcat-web/pom.xml`：增加三条依赖——`fourfeetcat-tool`、`fourfeetcat-provider`（编译期；两条边均无环，research D4）、`spring-boot-starter-test`（test 作用域，本模块此前没有测试依赖）；每条加中文注释写明**为什么**要它（`/tools` 要列全部工具；`/info` 要列全局 Provider 声明）

**Checkpoint**: 异常类型齐备、web 能编译；`/tools` 与 `/info` 所需类型可见

---

## Phase 3: User Story 1 - 业务系统用 HTTP 把 Agent 接进自己的流程 (Priority: P1) 🎯 MVP

**Goal**: 两条主路径可走通——连续对话（建会话 → 发消息 → 查历史 → 归档 → 列会话）与一次性调用；会话历史与审计照常落库

**Independent Test**: `mvn test -pl fourfeetcat-web -am -Dtest=SessionApiControllerTest`

### Tests（harness 先行，与实现同批落地）

- [x] T009 [US1] `fourfeetcat-web/src/test/java/org/fourfeetcat/web/session/SessionApiControllerTest.java`（`@WebMvcTest` 切片，mock 掉处理入口与会话管理器）：US1 的守点——① `sendMessage_callsProcessingEntryExactlyOnce`（`@DisplayName("正常请求_处理入口恰被调用一次")`：`verify(agentService, times(1)).process(...)`，多一次即"端点夹带私货"、0 次即"没接到同一个人推入口"）② `listSessions_ordersByLastActiveDesc`（列表契约）③ `invoke_createsDistinctSessionPerCall`（`@DisplayName("连续两次一次性调用_拿到两条不同会话")`：两次 `getOrCreate` 的"用户"分量不相等——SC-011 的守点；该用例需要无状态调用端点，故切片同时装配 Session 与 Agent 两个 Controller）
  > 课件的同一测试类还含"超 32KB → 400""会话不存在 → 404"以及"已归档拒发 → 400"三条，它们**依赖错误映射落地**（T016），按"红了当场修"的纪律放在 T015

### Implementation for User Story 1

- [x] T010 [US1] `fourfeetcat-web/src/main/java/org/fourfeetcat/web/session/SessionView.java`：会话对外视图记录（data-model §2.1 的八个字段）；javadoc 写明"这是**只读投影**，不是实体"
- [x] T011 [US1] `fourfeetcat-web/src/main/java/org/fourfeetcat/web/session/SessionApiController.java`：五个端点（建 / 发消息 / 查详情 / 归档 / 列表）——建会话走 `getOrCreate` 三元组（渠道固定 `web`，缺省见 contracts 2.1①）；发消息首行校验 `content`（空/空白/超 32KB → 400；已归档 → 400）再委托 `agentService.process`（**与 CLI 同一入口**）；查详情用 `SessionManager.get` 取消息 + 仓储取元数据，只回最近 100 条并带 `truncated` 标记；归档用仓储读-改-存（**幂等、不覆盖首次归档时刻**，research D5）；列表用既有 `findAllByOrderByLastActiveAtDesc`。请求体与详情响应记录**作为嵌套记录**落本文件（少建文件、不扩大对外类型面）；javadoc 写清"端点只做三件事，实际逻辑全在核心层"
- [x] T012 [US1] `fourfeetcat-web/src/main/java/org/fourfeetcat/web/agent/AgentApiController.java`：`POST /agents/{name}/invoke`——首行查 `ProfileRegistry`（未命中 → `ResourceNotFoundException`，research D3 的消歧），每次调用构造唯一用户标识（UUID 去横线）后走同一处理入口；响应只含 `reply`（不返回会话标识）
- [x] T013 [US1] 模块跑通：`mvn test -pl fourfeetcat-web -am` 全绿（T009/T011/T012 的验证点 + 既有测试未被打断）

**Checkpoint**: 两条主路径可 curl 通；处理入口薄度与归档硬边界有断言守着

---

## Phase 4: User Story 2 - 一次请求出错，客户端拿到的是可预期的统一格式 (Priority: P1)

**Goal**: 六类状态码由唯一一处处理器收敛成既有信封；500 不泄漏内幕；504 在真链路里拿得到

**Independent Test**: `mvn test -pl fourfeetcat-web -am -Dtest=GlobalExceptionHandlerTest`

### Tests（harness 先行，与实现同批落地）

- [x] T014 [US2] `fourfeetcat-web/src/test/java/org/fourfeetcat/web/api/GlobalExceptionHandlerTest.java`：`@WebMvcTest` 切片，逐类异常映射 + 统一信封 + **500 不泄漏**。课件那段"门面分寸"回归**逐条保真**、字段名按既有信封改译（research D2 对照表）：`isInternalServerError()` → 同；`$.code` = 500；`$.message` = 既有内部错误话术；`not(containsString(<内部异常 message 片段>))` → 同并加断表名/连接串。方法名英文 + `@DisplayName("内部异常细节_绝不能出现在500响应里")`
- [x] T015 [US2] `fourfeetcat-web/src/test/java/org/fourfeetcat/web/session/SessionApiControllerTest.java`：补课件同一测试类里的另三条守点——`sendMessage_over32Kb_returnsBadRequest`（`@DisplayName("消息超32KB_返回400")`）、`sendMessage_unknownSession_returnsNotFound`（`@DisplayName("会话不存在_返回404")`）、`sendMessage_toArchivedSession_returnsBadRequest`（`@DisplayName("向已归档会话发消息_明确拒绝不静默复活")`）；至此课件 harness 的四条守点逐条对号

### Implementation for User Story 2

- [x] T016 [US2] `fourfeetcat-web/src/main/java/org/fourfeetcat/web/api/GlobalExceptionHandler.java`：**扩展既有类**（不另建），按 research D3 的映射表加分支——400（`InvalidRequestException`）、404（`SessionNotFoundException` / `ResourceNotFoundException`，与既有 `NoResourceFoundException` 并存）、503（既有 `ServiceUnavailableException` + `IllegalStateException`）、504（`AgentTimeoutException`）；既有 400 / 404 / 503 / 兜底四条映射与其口径（**兜底不吐 `e.getMessage()`、内部细节进日志**）一字不改
- [x] T017 [US2] **改造点（本节唯一触碰前序节实现之处）** `fourfeetcat-provider/src/main/java/org/fourfeetcat/provider/ProviderConfiguration.java`：方法体内给 HTTP 客户端设 60 秒读超时（`ClientHttpRequestFactorySettings.defaults().withReadTimeout(...)` + `ClientHttpRequestFactoryBuilder.detect().build(settings)` 装进 `RestClient.Builder`，经 `OpenAiApi.Builder.restClientBuilder(...)` 传入），并把重试压到单次尝试（`OpenAiChatModel.Builder.retryTemplate(RetryTemplate.builder().maxAttempts(1).build())`）——不压重试则一次超时被放大成数倍墙钟，"最长 60 秒"不成立（research D10）。**对外 Bean 契约一字不改**（签名、Bean 名、语义不动）；超时常量集中一处并加中文注释说明口径与局限
- [x] T018 [US2] 模块跑通：`mvn test -pl fourfeetcat-web -am` 与 `mvn test -pl fourfeetcat-provider -am` 全绿（含既有 Provider 测试未被打断——这是 T017 的回归守点）

**Checkpoint**: 六类状态码各有断言；500 的响应体对内幕零泄漏；504 有真超时来源

---

## Phase 5: User Story 3 - 运营方一屏看清底座里有什么、健康不健康 (Priority: P2)

**Goal**: 四个信息查询端点 + 运行状态端点真实可达；管理台五页只读渲染；零凭证可启动有断言

**Independent Test**: `mvn test -pl fourfeetcat-boot -am -Dtest=WebSmokeIT -Dsurefire.failIfNoSpecifiedTests=false`

### Tests（harness 先行，与实现同批落地）

- [x] T019 [US3] `fourfeetcat-boot/src/test/java/org/fourfeetcat/boot/WebSmokeIT.java`（`@SpringBootTest` 真实上下文，**不依赖模型**，`@Tag("integration")`）：四个 GET 端点真实链路可达（`/health`、`/info`、`/profiles`、`/tools`）+ 会话列表走通数据访问层——验证 Bean 装配与数据访问层扫描范围没炸（18 节那个"Found 0 repositories"的坑若复发，这里第一时间红）；并断言**零凭证下上下文起得来**且 `/info` 如实报告"凭证未就位"（把 Provider 声明换成 key 为空的本地替身，使该断言确定而非依赖跑测机器恰好没配 key）。类注释写明"为什么落 boot 而不是 web"（真上下文只有 boot 有，research D14 / plan ④）
  > **实施中补线**：`*IT` 不会被自动执行——父 pom 只在 pluginManagement 里备了 failsafe 配置，不声明插件就不跑（第一轮 `mvn verify` 里这个类一次都没出现，构建却是绿的）。已在 `fourfeetcat-boot/pom.xml` 显式声明 failsafe，复跑确认 3 个用例真跑真过

### Implementation for User Story 3

- [x] T020 [US3] `fourfeetcat-web/src/main/java/org/fourfeetcat/web/profile/ProfileApiController.java`：`GET /profiles`——复用 `ProfileRegistry.all()`（第25节交付），映射为 Agent 视图；**绝不输出凭证字段与系统提示正文**
- [x] T021 [US3] `fourfeetcat-web/src/main/java/org/fourfeetcat/web/tool/ToolApiController.java`：`GET /tools`——读 `ToolRegistry.all()`，映射为工具视图（`name` / `description` / `inputSchema` 文本）
- [x] T022 [US3] `fourfeetcat-web/src/main/java/org/fourfeetcat/web/memory/MemoryApiController.java`：`GET /memory`——调既有门面 `buildContext(null)`（门面当前即"取全文"，`session` 不参与计算）；调用点注释写明口径并登记耦合点（research D9）
- [x] T023 [US3] `fourfeetcat-web/src/main/java/org/fourfeetcat/web/system/SystemApiController.java`：`GET /health`（不依赖模型与网络）与 `GET /info`（应用名 + 版本 + 各 Provider 的**名字/端点/凭证是否就位**，**不发探活、绝不回显凭证内容**，research ⑤）
- [x] T024 [US3] **（实施中改写为 `AdminSpaConfig`）** `fourfeetcat-web/src/main/java/org/fourfeetcat/web/admin/AdminSpaConfig.java`：`/admin/**` 未命中静态资源时回落 `admin/index.html`（前端子路由刷新不 404）；**只覆盖 `/admin/**`**，不碰 `/api/v1/**` 的行为。改写理由：`@GetMapping("/admin/**")` 会把 `/admin/index.html` 自己也匹配进去，"转发到入口页"变成再进一次同一控制器——自转发死循环；资源解析器才能按"是不是静态资源"分流
- [x] T025 [US3] `.claude/skills/four-feet-cat-admin-ui/SKILL.md`：**先立风格 skill、再用它生成管理台**（课件指定顺序，30 节还要加页复用）。内容四块——设计 token（逐项取 `website/.vitepress/theme/styles.css` 的真值：白底 `#FFFFFF`、浅蓝片 `#F2F7FF`、主色 `#3D6FD6`、副色 `#6EA8E6`、描边 `#E3EAF5`、墨字 `#1E293B`、次字 `#5B6B84`、正文中文字体族 + `JetBrains Mono` 等宽）、工程约定（`base:'/admin/'`、产物落 `static/admin`、SPA 回落、只调 `/api/v1` 的 GET 端点、只读不要写按钮）、布局与三态/响应式规范、验收清单。**不引外部 UI skill**（课件已核 `mattpocock/skills` 不适用）
- [x] T026 [US3] `fourfeetcat-web/src/main/frontend/`：Vue 3 + Vite 只读管理台（五页：会话列表 / Agent 列表 / 工具列表 / 长期记忆 / 运行状态），每页调一个对应的 GET 端点；错误态展示统一信封里的 `message`；`vite.config` 设 `base:'/admin/'`、`outDir` 指向 `../resources/static/admin`；入库 `package-lock.json`（`npm ci` 需要）
- [x] T027 [US3] `fourfeetcat-web/pom.xml`：加 `frontend-maven-plugin`（`install-node-and-npm` 固定版本 + `npm ci` + `npm run build`，绑 `generate-resources`）；`.gitignore` 加静态产物目录（产物不入库）
- [x] T028 [US3] 模块跑通：`mvn test -pl fourfeetcat-boot -am -Dtest=WebSmokeIT` 全绿 + `mvn clean package -DskipTests` 产出含管理台的 fat JAR（`static/admin/index.html` 在产物里）

**Checkpoint**: 四端点真实可达；零凭证可 boot 有断言；管理台五页可看且只读

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 文档同步、全量门禁、变更总结

- [x] T029 文档同步（裁决 ① 的既定代价）：`CLAUDE.md` 端点表 + `docs/TechnicalSolution.md` §7.2 标题与正文（会话管理 4 → 5 个）+ 同文件另两处"核心 10 个端点"表述 → 共 4 处改为 11 个端点并补上会话列表；另纠正 `CLAUDE.md` 官网章节"深色首页"这一事实错误（本仓官网现为明亮白底）——**不修改课件**
- [ ] T030 全量门禁与人工项交底：`mvn clean verify` 全绿（含 Spotless / PMD7 / Checkstyle / SpotBugs+FindSecBugs）+ 前序各节测试零失败零跳过；按 quickstart §3 输出**剩余人工项清单**（11 端点真链路与审计、CLI 与会话存储互见、503/504 故障注入、200 并发、管理台五页与三态、接口文档、零凭证启动、视觉同源）
- [ ] T031 变更总结（给 reviewer 的导读）：按 `git status --short` / `git diff --stat` 实测输出三段——改动点（按模块分组，前序节被触碰的文件单独标出：`ProviderConfiguration`、`GlobalExceptionHandler`、`ErrorCode`、`web/pom.xml`）、重点 review 清单（依赖方向、信封字段改译、超时改造点、前端构建插件）、如何验证（命令块 + 预期结果）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**：无前置（T001~T003 已随 plan 阶段完成）
- **Phase 2 Foundational**：依赖 Setup；**阻塞全部三个故事**（异常类型缺失则 Controller 无法编译）
- **Phase 3（US1）→ Phase 4（US2）→ Phase 5（US3）**：均依赖 Foundational。US1 与 US2 的实现文件互不重叠（`session/` `agent/` vs `api/` + provider 装配），理论上可并行；但 T015 要往 US1 的测试文件里追加用例，故按 US1 → US2 → US3 顺序推进
- **Phase 6 Polish**：依赖全部故事完成

### User Story Dependencies

- **US1（P1）**：Foundational 之后即可，独立可测（两条主路径 curl 通）
- **US2（P1）**：Foundational 之后即可；它横切所有端点，但**不依赖 US1 的业务实现**（切片测试用 mock 触发入口即可复现全部六类状态码）。仅 T015 与 US1 的测试文件同文件
- **US3（P2）**：依赖 US2 的 `/info` 异常口径与 US1 的端点风格；管理台消费的是 US1 + US3 自己交付的 GET 端点

### Within Each User Story

- 测试与实现**同批落地**（编译相互依赖），故事结束时模块测试必须全绿
- 视图记录 → 控制器 → 模块跑通

### Parallel Opportunities

- T004 ~ T007：四个异常类型 / 枚举项互不相关，可并行
- T020 ~ T023：四个信息查询 Controller 互不相关，可并行
- T025（风格 skill）必须先于 T026（用它生成管理台）

---

## Parallel Example: Phase 2

```bash
# 四个异常类型 / 枚举项同批落地（不同文件、互不依赖）
Task: "ErrorCode 新增 REQUEST_TIMEOUT(504)"
Task: "新增 InvalidRequestException（400）"
Task: "新增 SessionNotFoundException / ResourceNotFoundException（404）"
Task: "新增 AgentTimeoutException（504）"
```

---

## Implementation Strategy

### MVP First（US1）

1. Phase 1 Setup（依赖可达 + 基线绿）
2. Phase 2 Foundational（异常类型 + 依赖）
3. Phase 3 US1（两条主路径）
4. **STOP and VALIDATE**：`mvn test -pl fourfeetcat-web -am -Dtest=SessionApiControllerTest` 全绿；真起服务 curl 两条主路径

### Incremental Delivery

1. US1 → 业务系统可接入（本节的 MVP）
2. US2 → 错误契约可用（对接方不用逐端点猜错误格式）
3. US3 → 运营方可视（管理台 + 状态端点）
4. Polish → 文档同步 + 全量门禁 + 人工项交底

---

## 本节交付物对账（课件"本节交付物" ↔ 任务清单）

| 课件交付物 | 覆盖任务 | 状态 |
|-----------|---------|------|
| 六个 Controller（Session/Agent/Profile/Memory/Tool/System） | T011、T012、T020、T021、T022、T023 | ✅ 齐（Session 单文件 T011，其断言分两批 T009/T015） |
| `GlobalExceptionHandler`（扩展既有、复用 `ApiResponse` 信封） | T016（扩展）、T004（`ErrorCode` 加 504 项）、T005~T007（异常类型） | ✅ 齐 |
| `springdoc-openapi` 集成 | 依赖已在（工程地基阶段引入），本节只需文档变真 → T019 冒烟 + T030 人工项 | ✅ 齐（零新增依赖，登记为既有件） |
| `SessionApiControllerTest` | T009（US1 四条守点）+ T015（补 32KB→400、会话不存在→404，同一文件） | ✅ 齐（课件四条守点逐条对号） |
| `GlobalExceptionHandlerTest` | T014 | ✅ 齐 |
| `WebSmokeIT` | T019（落 boot，随 `mvn verify` 真跑） | ✅ 齐（含课件点名的"仓储扫描复发即红"守点） |
| 配置：虚拟线程 + 端口 8080 | **已就位于既有 `application.yaml`**（本节零改动，T003 已核对） | ✅ 齐（登记为既有件，不新增配置） |
| 配置：32KB / 100 条限制 | T011（端点内校验与截断 + `truncated` 标记）、T015（32KB 断言） | ✅ 齐 |
| 前端工程（五页）+ 产物落 `static/admin` + `/admin` 托管 + SPA 回落 | T026（工程）、T027（构建串联 + 产物不入库）、T024（回落） | ✅ 齐 |
| 构建串联二选一 | T027（已裁：绑进 Maven） | ✅ 齐 |
| 风格 skill（设计 token + 工程约定 + 三态/响应式 + 验收清单） | T025 | ✅ 齐（名字按本仓口径 `four-feet-cat-admin-ui`） |

**多出项（非课件交付物，均为裁决产物或实测驱动，逐条有据）**：

| 多出项 | 依据 |
|--------|------|
| `GET /api/v1/sessions` | 裁决 ①（课件内部矛盾），T011/T015 |
| `ProviderConfiguration` 的 60 秒超时改造 | 裁决：让 504 在真链路里拿得到，T017 |
| 4 处文档同步 + 1 处官网视觉事实纠正 | 裁决 ① 的既定代价，T029 |
| `.gitignore` 两条（静态产物、前端工具链） | 产物与工具链均不入库，T027 |
| `WebSliceTestConfiguration`（测试锚点） | web 模块没有启动类，`@WebMvcTest` 需要一个配置锚点才能起切片，T009 |
| `ExceptionProbeController`（测试探针） | 让异常映射的测试不穿过业务 Controller；**顶层类**而非嵌套类——嵌套类不会被切片装配，探针全变 404、测试假绿（实测踩过），T014 |
| `MessageRequest` / `ReplyResponse`（共享记录） | 两个控制器对消息的规则完全一致，校验口径必须只此一处，否则会出现两套上限 |
| `ProviderReadTimeoutTest`（provider 模块） | 把"60 秒上限"从声明变成可验事实（本地起服务，不依赖外网），T017 |
| **修 `serve` 启动后不保活**（`ServeCommand` / `GatewayCommand` / `FourFeetCatCli`） | 实施中现场冒烟发现的前序节缺陷：服务起来约 0.1 秒即退出（退出码 0），本节交付的端点与 `/admin` 将无从访问。修法见 plan 登记 ⑧ |
| `fourfeetcat-boot/pom.xml` 声明 failsafe | 不声明则 `*IT` 永不执行（research D14），冒烟等于没有 |
| 冒烟测试增补管理台守点 | 现场冒烟抓到 `/admin` 裸路径 404（plan 登记 ⑨），补断言使其不会再漏 |

**缺口检查**：课件交付物 11 项全部有任务承载，**无缺口**；两处按实测改写（T007 超时异常、T024 回落机制）已在任务条目里写明理由，**无自行发挥**。
