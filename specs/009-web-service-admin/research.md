# Phase 0 Research：Web Service 与第一版管理平台（第26节）

本文件收敛本节全部技术裁决。**无 NEEDS CLARIFICATION 残留**。三条关键裁决（D2 的信封字段、D5 的归档落点、D10 的超时落点）来自主公在 clarify 与 H0 阶段的裁定，记在 spec 的 Clarifications；D4 / D10 / D14 的第三方与框架事实来自本机实测（依赖树 + `javap` + 真跑一次既有 boot 测试）。

---

## D1 端点清单与路径冻结（11 个）

**决策**：统一前缀 `/api/v1`，六个 Controller，共 11 个端点。路径逐条冻结如下（与原设计的 10 个只差第 5 条）：

| # | Controller | 方法与路径 | 说明 |
|---|-----------|-----------|------|
| 1 | `SessionApiController` | `POST /sessions` | 创建（或复用）一条 Web 渠道会话 |
| 2 | `SessionApiController` | `POST /sessions/{id}/messages` | 发消息，触发一次完整处理 |
| 3 | `SessionApiController` | `GET /sessions/{id}` | 查会话详情与历史 |
| 4 | `SessionApiController` | `DELETE /sessions/{id}` | 归档（软标记，幂等） |
| 5 | `SessionApiController` | `GET /sessions` | **本节新增**：只读列举全部会话（最近活跃在前） |
| 6 | `AgentApiController` | `POST /agents/{name}/invoke` | 无状态调用 |
| 7 | `ProfileApiController` | `GET /profiles` | 列全部已加载 Agent |
| 8 | `MemoryApiController` | `GET /memory` | 长期记忆全文 |
| 9 | `ToolApiController` | `GET /tools` | 列全部已注册工具 |
| 10 | `SystemApiController` | `GET /health` | 健康探针 |
| 11 | `SystemApiController` | `GET /info` | 运行信息 + 各 Provider 配置态 |

**理由**：第 5 条是课件内部矛盾（管理台提示词要求调它、端点表却没有它）的裁决结果（经主公裁决取"加端点"）。管理台五个页面各调一个 GET 端点（5→会话列表、7→Agent 列表、9→工具列表、8→记忆、11→运行状态），"管理台无需自有后端"这条验证因此成立。

**备选被否**：严格 10 个、把列表页缩成"按 id 查询"（管理台核心一页残缺）；或让管理台直读数据库（违背"API 是完备通道"的验证意图）。

---

## D2 响应信封沿用既有 `ApiResponse`；课件 harness 的 `errorCode` 字段按其正文口径改译

**决策**：所有端点（成功与失败）统一返回既有信封 `ApiResponse<T>(code, message, data, timestamp)`，**不另建错误体**。课件 harness 那段"门面分寸"回归的**守点逐条保真**，但字段名按既有信封改译：

| 课件 harness 的断言 | 本节落地 | 守点是否等价 |
|--------------------|---------|------------|
| `status().isInternalServerError()` | 同 | ✅ 一字不改 |
| `jsonPath("$.errorCode").value("INTERNAL_ERROR")` | `jsonPath("$.code").value(500)` | ✅ 等价：既有信封的"状态码"字段在 `code`（数值），其取值与 HTTP 状态同值 |
| `jsonPath("$.message").value("内部错误")` | `jsonPath("$.message").value(<既有内部错误话术常量>)` | ✅ 等价：话术取自既有 `ErrorCode.INTERNAL_ERROR.getReason()`，不新造字符串 |
| `content().string(not(containsString("jdbc:sqlite")))` | 同（并加断异常 message 的其他片段） | ✅ 一字不改 |

**理由**：课件同一节明确写"统一信封闭用既有的 `ApiResponse`（`code` / `message` / `data` / `timestamp`）——本节复用、不另建 `ErrorBody`"，而它的 harness 片段却断言 `$.errorCode`——**课件内部不一致**。两条互相矛盾的指令里，正文那条是设计指令（且与第24节已落地的代码一致），harness 片段是笔误的残留（旧信封字段名）。取正文口径落地，并把 harness 的**守点**（状态码正确、话术统一、内幕一个字不漏）逐条保真——这三条才是这条测试值钱的地方。

**备选被否**：给 `ApiResponse` 加 `errorCode` 字符串字段以照抄断言（改第24节交付的既有信封 = 动已定字面量，且同一节又要求"不另建"）；把 harness 整条测试删掉（反作弊红线）。

---

## D3 异常类型与状态码映射表（含"未注册 Agent"的消歧）

**决策**：新增 4 个异常类型，复用 3 个既有类型，映射表如下。全部收敛在既有的 `GlobalExceptionHandler`（扩展，不另建）。

| 状态码 | 异常类型 | 新增/既有 | 触发场景 |
|-------|---------|----------|---------|
| 400 | `InvalidRequestException` | 新增 | 消息为空/空白、超过 32KB、向已归档会话发消息 |
| 400 | `MethodArgumentNotValidException`、`HttpMessageNotReadableException` | 既有 | 请求体格式不对 |
| 400 | `IllegalArgumentException` | **本节新增**（课件骨架把这条写成"既有、保留不动"，实际代码里没有——本节补上） | 参数非法 |
| 404 | `SessionNotFoundException` | 新增 | 会话标识不存在 |
| 404 | `ResourceNotFoundException` | 新增 | Agent 名未加载等资源不存在 |
| 404 | `NoResourceFoundException` | 既有（保留不动） | 静态资源/路径不存在 |
| 503 | `ServiceUnavailableException` | 既有 | Provider 侧故障（**即课件所指 `ProviderUnavailableException`**——本仓第24节已建的同类，不重复造同义类） |
| 503 | `IllegalStateException` | 既有（课件指定映射） | 处理链内部状态异常（如会话归属的 Agent 在注册表里消失） |
| 503 / 504 | `ResourceAccessException` | 既有（Spring 的传输层失败包装） | 模型调用的连接失败（→503）与**超时**（→504）。实测两者都以此类浮现：`ResourceAccessException <- java.net.http.HttpTimeoutException`（见 D10 与 `ProviderReadTimeoutTest`），故按**原因链里认不认得出超时**分流 |
| — | ~~`AgentTimeoutException`~~ | **未采用** | 课件骨架里那个"超时专用异常"没人抛：504 由真实的传输层超时映射而来。多一个没人抛的异常类只是死代码，故删除（已落地） |
| 500 | 兜底 `Exception` | 既有（口径不变） | 其余一切；**内部细节只进日志，对外只给统一话术** |

**消歧（本节最容易踩的一处）**：`AgentService.process` 在"会话归属的 Agent 未注册"时抛 `IllegalStateException`，按上表会落 503；但 spec 的边界要求"调用一个未加载的 Agent 名 → 404（名字写错是客户端的问题，不是服务不可用）"。**解法**：与 Agent 名相关的两个端点**先查 `ProfileRegistry`**，未命中即抛 `ResourceNotFoundException`（404）——`process` 里的那条 `IllegalStateException` 因此只在"会话存在、但它归属的 Agent 后来被删了"这种真·内部状态下才出现，落 503 是对的。

**理由**：错误码口径要"一开始就定死"（课件原话），业务系统对接时才不用逐端点猜。消歧那条不新增类型、只在端点入口加一次注册表查询（本就是"参数校验"的一部分，不违反端点薄度）。

**备选被否**：把 `IllegalStateException` 一刀切成 404（会把真的服务端故障报成"资源不存在"，掩盖故障）；为其新造专门的异常类型（不必要，注册表查询已把两类情况分开）。

---

## D4 `fourfeetcat-web` 新增两条模块依赖边（→ tool、→ provider）

**决策**：web 模块的 pom 增加两条**编译期**依赖：`fourfeetcat-tool`（取 `ToolRegistry.all()`）与 `fourfeetcat-provider`（取 `ProviderConfiguration.ProviderProperties`）。

**核实事实**：
- `ToolRegistry` 有 `List<CatTool> all()`（`fourfeetcat-tool` 的 `registry` 包），`CatTool` 的 `getName/getDescription/getInputSchema` 三方法齐全（`getInputSchema` 返回 **String**，即 JSON Schema 文本——与 `/tools` 要展示的"参数说明"同一口径，不需转换）。
- `ProfileRegistry.all()` 已于第25节交付（`fourfeetcat-core`），`/profiles` 直接复用它，**零新增**。
- `ProviderProperties` 是 `fourfeetcat-provider` 里的 `@ConfigurationProperties` 记录（`fourfeetcat.providers` 的声明，含 `name` / `baseUrl` / `apiKey`），由该模块的 `ProviderConfiguration` 注册为 Bean，可注入。

**依赖方向检查**：`tool → core`、`provider → core`，`web → {core, storage}`；新增 `web → {tool, provider}` 后**仍无环**（web 是叶子消费者，没有任何模块依赖 web），模块间循环依赖的红线不触碰。

**理由**：这是**不新增任何对外概念**就能拿到所需数据的唯一路径。core 现有的 `ToolTable` 端口只有"按名单取描述 / 按名执行"，**没有"列全部"**；若为了这两个只读展示在 core 新造端口（如 `ToolCatalog`、`ProviderCatalog`），就是给前序节交付的契约面新增两个类型——为展示功能扩契约，方向反了。

**备选被否**：①在 core 新造两个只读端口（新对外概念，且要动 core）；②在 web 里直接读 `application.yaml` 或扫描容器 Bean 取 Provider（违反原则三的显式映射口径，且绕开了配置记录的类型安全）。

---

## D5 会话端点的数据来源，与"归档"落在哪一层

**决策**：
- **历史与消息**：`SessionManager.get(sessionId)`（既有契约）取到内存态会话，`Session.getMessages()`（既有方法）取消息快照。
- **元数据（状态 / 创建时间 / 最后活跃时间）**：`SessionRepository.findById(...)`（既有仓储）。
- **归档**：控制器直接经既有仓储读-改-存——`findById` → 不存在则 404；`status` 置归档、`archivedAt` 仅在首次归档时写 → `save`。**幂等**（重复归档不报错、不改首次归档时间）。
- **列表**：`SessionRepository.findAllByOrderByLastActiveAtDesc()`（既有方法，`session list` 命令用的就是它）。

**理由**：
- `SessionManager` 的接口没有"归档"这个动作，**本节不往它上面加方法**——那是改前序节交付的公共接口（core 的跨模块契约），而课件并未把它列为改造点；加了会让所有 `SessionManager` 实现（含测试替身）被迫实现一个与"会话标识生成"无关的方法。
- 入口层直接借仓储读一次库，本仓**已有先例**：`session list` 命令行就是 `context.getBean(SessionRepository.class).findAllByOrderByLastActiveAtDesc()`。归档同属"一层薄壳 + 一次库操作"，沿用该先例。
- 端点"薄"的含义是**不夹带业务逻辑**（处理链、工具、模型一律不碰），不是"不许碰仓储"；归档没有可委托的核心服务，硬造一个只会多一层空壳。

**备选被否**：给 `SessionManager` 加 `archive(String)`（改前序契约，且要被实现的只是三行库操作）；在 storage 新建一个 `SessionArchiveService`（为三行代码新增一个公开类 = 凭空多一层）。

---

## D6 一次性调用的会话身份：每次独立

**决策**：`POST /agents/{name}/invoke` 每次调用都调 `sessionManager.getOrCreate("web", <唯一用户标识>, name)`，唯一标识取 `UUID.randomUUID().toString()` 去掉横线（32 位小写十六进制）。

**理由**：spec FR-005 要求"每次调用 MUST 使用独立的会话身份"。会话标识由既有唯一位置按 `渠道:用户:Agent` 拼出，唯一性因此只需落在"用户"这一分量上；32 位十六进制的随机串碰撞概率可忽略，且长度可预测。

**会话标识列长边界（登记项）**：`sessions.session_id` 是 `VARCHAR(64)`，拼接式为 `web:<32位>:<Agent名>`，即 Agent 名超过 27 个字符时会越界。SQLite 不强制该长度（当前默认档无碍），PostgreSQL 部署会拒绝——这是**既有列宽约束**（第18节定的），本节不为此改表（改表属跨节结构变更），只在 contracts 里写明该边界。若将来需要，缩短随机串或用哈希即可，不改本节代码结构。

**备选被否**：同一 Agent 的所有调用共用一条会话（历次无关调用互相污染上下文，"无状态"名不副实——经主公裁决否）；每次新建但列表端点过滤掉一次性会话（要塞一个额外标记，且"列表 = 全部会话"变成有例外）。

---

## D7 `POST /sessions` 的缺省口径

**决策**：请求体可为空。缺省规则：渠道固定 `web`；用户缺省 `anonymous`；Agent 缺省取 `ProfileRegistry.all()` 的**第一个**（顺序 = 加载顺序，第25节已把它确定化）。若一个 Agent 都没加载 → 404（资源不存在，不是服务不可用）。

**理由**：课件"怎么做"里的首个 curl 就是**不带请求体**的建会话（`curl -X POST localhost:8080/api/v1/sessions`），所以端点必须容忍空体；三个分量都有确定缺省，才不至于让调用方先猜 A gent 名。缺省取"第一个"而非随便一个：注册顺序已确定化（第25节为定时注册做的 `LinkedHashMap` 改造），同一份配置每次启动取到同一个，行为可复现。

**幂等性说明**：同一三元组 `getOrCreate` 返回同一条会话（第18节的既有契约），因此"同一用户对同一 Agent 重复建会话"得到同一条——这是既有语义，本节如实透出，不另加"每次新建"的分支。

**备选被否**：要求请求体必须给 Agent 名（课件首个 curl 会 400，且与"建会话"这一步的用法不符）；缺省随机取 Agent（同一份配置每次启动结果不同，不可复现）。

---

## D8 防呆边界：32KB / 空消息 / 历史 100 条（含截断标记）

**决策**：
- 消息体：`content` 为 `null`、空白、或长度 > 32 * 1024 → `InvalidRequestException`（400）。**恰好等于上限放行**（边界值不因防呆被误伤）。
- 历史：`GET /sessions/{id}` 只返回**最近 100 条**消息，并在响应里带一个"是否已截断"的布尔字段——不静默给出不完整历史。

**理由**：两条限制是课件与 §7.4 定死的防呆值（"防呆不是治理"）。加"截断标记"是 spec 边界要求（"让客户端能看出这是被截断后的结果"），一个布尔字段的成本。校验放在端点方法首行（属"参数校验"职责，不违规）。

**备选被否**：超限直接截断消息内容（静默改动用户输入，最坏的失败形态）；历史不带截断标记（客户端无从判断历史是否完整）。

---

## D9 长期记忆全文的取用路径（含一处耦合登记）

**决策**：`GET /memory` 调既有门面 `MemoryService.buildContext(null)`，返回其字符串结果。

**核实事实**：`MemoryServiceImpl.buildContext(session)` 的实现体是 `return store.load();`——**`session` 参数当前不参与任何计算**（门面 javadoc 已写明"本节不参与作用域圈定，保留它是为了签名稳定"）。因此传 `null` 是安全的，不会 NPE。

**理由**：门面在 `fourfeetcat-core`（跨模块契约），web 只依赖 core 就能拿到记忆全文，**无须**依赖 `fourfeetcat-memory`、也无须碰后端实现。传 `null` 时会在调用点写明注释（"门面口径：session 不参与圈定，此处无会话上下文"），并把它登记为**已知耦合点**：将来若门面按 Agent 圈定记忆作用域，这个调用点需要补上下文——这是 spec 里"签名稳定是为将来留位"那句话的兑现处，也是本节唯一一处"传 null"的地方。

**备选被否**：让 web 依赖 `fourfeetcat-memory` 的 store 读原始文件（多一条模块依赖 + 绕过门面，等于在门面之外开旁路）；给门面加一个"取全文"方法（改前序节的跨模块契约，而既有方法已能取到全文）。

---

## D10 60 秒超时的落地：客户端读超时 + 单次尝试（含 API 可得性证据）

**决策**：在 `ProviderConfiguration` 里给每个 Provider 的 HTTP 客户端设 **60 秒读超时**，并把重试压到**单次尝试**；超时类失败由 `GlobalExceptionHandler` 映射为 504（`AgentTimeoutException`）。

**本机核实（动笔前跑过）**：

| 事实 | 证据 |
|------|------|
| 能给连接设施设读超时 | `org.springframework.boot.http.client.ClientHttpRequestFactorySettings.defaults().withReadTimeout(Duration)`、`ClientHttpRequestFactoryBuilder.detect().build(settings)`（Boot 3.5.16 自带，`javap` 实测） |
| 能把上面那座工厂装进模型客户端 | `OpenAiApi.Builder.restClientBuilder(RestClient.Builder)`（spring-ai-openai 1.1.2，`javap` 实测）——显式走同步 RestClient 路径，不落 WebClient |
| 能把重试压到单次 | `OpenAiChatModel.Builder.retryTemplate(RetryTemplate)` 存在，且 `RetryTemplate.builder().maxAttempts(1).build()` 可用（`spring-retry 2.0.13` 在依赖树中，经 `spring-ai-retry` 传递，`javap` 实测） |

**为什么必须压重试**：不显式给重试模板，框架自带的默认模板可能对超时类失败重试若干次——一次 60 秒超时会被放大成数倍墙钟，504"最长 60 秒"这句话就不成立了。

**超时以什么形态浮现（实测，不是推测）**：写代码前先跑了一次真实超时——对着一个只接受连接、不应答的本地服务，用 300 毫秒读超时发一次调用，异常链是
`org.springframework.web.client.ResourceAccessException <- java.net.http.HttpTimeoutException`。据此把 504 的判据定为"**传输层失败 + 原因链里认得出超时**"：
外层是 Spring 统一的传输层包装（连接失败走同一层），所以映射按原因链分流（超时 → 504，其余 → 503）。判据刻意不锁死单个异常类名——换 HTTP 客户端实现后只要这条性质还在，映射就不必改。
这条实测被固化成了常驻测试 `fourfeetcat-provider/src/test/java/.../ProviderReadTimeoutTest.java`（本地起服务、不依赖外网）。

**为什么落在这里而不是整轮限时**：整轮限时要在 Web 层用"另一个线程 + 限时等待"包住处理，一是与原则七（同步执行模型）和不变量 ⑤（无自建线程）冲突，二是超时返回后那个工作线程可能仍在写库（留下半截状态）。落在模型调用的连接参数上：真超时、真 504、零线程模型变更；局限（**登记项**）：一轮多轮工具循环的总时长不受此约束，受约束的是单次模型调用——这条已写进 spec 的 Assumptions。

**改造点声明**：这会动到第16节交付的 `ProviderConfiguration`（**唯一一处触碰前序节文件**），但**其对外 Bean 契约一字不改**（`LlmCaller providerService(ProviderProperties, LlmCallRecorder)` 的签名、Bean 名、语义全不动），改动只在方法体内构造 `OpenApiApi`/`ChatModel` 时多传两个参数。变更总结里会单独标出这一处。

**备选被否**：只做异常映射不做超时（504 在真链路里永远拿不到，等于写了一条死分支）；整轮限时等待线程（见上）。

---

## D11 "零凭证可 boot"用测试守，不写排除配置

**决策**：**不**在 `application.yaml` 写课件"决策四"点名的那个自动装配排除项；改用 `WebSmokeIT` 的断言守住该决策的**意图**（零凭证下上下文起得来 + 运行信息端点如实报告凭证未就位）。

**本机核实（动笔前跑过）**：

| 事实 | 证据 |
|------|------|
| 目标自动装配类不在此仓类路径 | `mvn dependency:tree`（boot 模块）：spring-ai 侧只有 `spring-ai-model` / `spring-ai-commons` / `spring-ai-template-st` / `spring-ai-openai` / `spring-ai-retry`，**无任何 `autoconfigure` 件**；`jar tf spring-ai-openai-1.1.2.jar` 过滤 `AutoConfiguration|imports|spring.factories` **全空** |
| 排除一个不在类路径上的类是安全的（不会启动失败） | 既有 `application.yaml` 已在排除 `...dashscope.DashScopeAutoConfiguration`（同样不在类路径上，依赖树里根本没有 alibaba 件），而 `AgentSchedulerWiringTest`（`@SpringBootTest` 真上下文）**BUILD SUCCESS** |

**理由**：那个坑的成因是"引了 starter、框架急切构造了一个用不到的 `ChatModel` Bean 并索要第二份 key"；本仓的 `ChatModel` 自第16节起就是显式构造的（原则三），**从不引 starter**，坑不存在。写一行指向不存在类的排除配置，读者会以为该装配件在类路径上，是负资产。决策的**意图**照守，手段换成可验证的断言。

**备选被否**：照课件加上那行（死配置，且误导——经主公裁决否）。

---

## D12 管理台的前端工程与构建串联

**决策**：
- 工程：`fourfeetcat-web/src/main/frontend/`（Vue 3 + Vite，与官网首页同栈），`vite.config` 设 `base: '/admin/'`、`build.outDir` 指向 `../resources/static/admin`。
- 串联：`frontend-maven-plugin` 绑定 `install-node-and-npm`（Node 版本固定写死，不复用机器上的）→ `npm ci` → `npm run build`，绑到 `generate-resources` 阶段；`npm ci` 需要入库的 `package-lock.json`。
- 产物：`static/admin/` **不入库**（加 `.gitignore`），由构建产出。
- 兜底：`fourfeetcat-web/src/main/resources/static/admin/index.html` 是构建产物，仓库里不存在；因此**未构建时访问 `/admin` 会 404**——这是"一条命令产出完整产物"的必然结果，写进 quickstart 的前置说明。

**理由**：课件要求 plan 二选一并说明——选插件绑进构建，让"全量门禁全绿"这一条 DoD 同时覆盖前端（前端坏掉会让 `mvn clean verify` 红，而不是等到人工打开浏览器才发现）。

**已知代价（登记项）**：首次构建需联网（插件下载固定版本的 Node/npm，之后 `npm ci` 拉依赖）；`mvn -o` 离线模式**跑不通**本模块的构建；构建时间增加（首次约 1~2 分钟）。这三条在 quickstart 的前置里写明。

**实施中的一处调整（实测驱动）**：工具链的安装目录**没有**放在 `target/` 下，而是放 `fourfeetcat-web/.frontend-tools/`（不入库）。
原因：放 `target/` 则每次 `mvn clean` 都要重新下载 35MB 的 Node 分发件；实测 `nodejs.org` 在国内网络下时好时坏（同一台机器 `curl` 能拿到，插件自带的下载器被连接重置过一次），
"每次 clean 都重下"会把这条不稳定链路放大成"每次全量构建都可能卡住"。移出 `target/` 后装一次长期复用，换机器时首次构建自动下载（离线构建仍跑不通，已在 quickstart 写明三种解法）。

**备选被否**：手动 `npm run build` 后再 `mvn package`（CI 与全量门禁都覆盖不到前端，管理台坏掉没人知道——经主公裁决否）。

---

## D13 设计 token 的真值来源，与本仓文档同步清单

**决策**：管理台的设计 token 逐项取自 `website/.vitepress/theme/styles.css`（官网首页当前生效的样式定义）的 `:root` 变量：

| 用途 | 取值 |
|------|------|
| 页面底色 / 卡片 | `#FFFFFF` / `#FFFFFF`（浅蓝片 `#F2F7FF`） |
| 主色（强调、激活项、链接、数值） | `#3D6FD6`；副色用 `#6EA8E6`（渐变副色） |
| 描边 / 分隔 | `#E3EAF5` |
| 文字：主 / 次 | `#1E293B`（墨字）/ `#5B6B84`（灰蓝） |
| 字体 | 正文：`'PingFang SC','Microsoft YaHei','Noto Sans SC',…`；代码/标识/JSON：`ui-monospace,'JetBrains Mono','Cascadia Code',Consolas,monospace` |
| 阴影 | `0 10px 34px rgba(30, 64, 140, 0.08)` |

**登记：课件的色值为过期值。** 课件"页面风格"章写的是深色底 `#000000` + 主色橙 `#f97316` + 字体 Inter，并称"值取自官网首页、与首页完全一致"——但本仓官网首页**现为明亮白底 + 猫蓝**（`styles.css` 的注释原文即"FourFeetCat 视觉基调：明亮白底 · 蓝白猫色"），橙色在首页里根本没出现，课件点名的样式文件名（`custom.css`）在本仓也不存在。按过期值落地会造出与首页**不同源**的界面，正好违背该章"一个字别自创、钉死到首页"的意图。经主公裁决取官网实际值（spec Clarifications 已记）。**不自行改课件**，只在验收报告里点名。

**文档同步清单（本节裁决 ① 的既定代价）**：

| 文件 | 位置 | 现文 | 改后 |
|------|------|------|------|
| `CLAUDE.md` | 端点表标题行 | 核心阶段 10 个端点 | 11 个端点（表内补一行会话列表） |
| `docs/TechnicalSolution.md` | §7.2 标题 | 核心阶段 10 个端点 | 11 个端点（会话管理 4 → 5 个） |
| `docs/TechnicalSolution.md` | §10 附近 | "六个 `ApiController` 的核心 10 个端点" | 11 个 |
| `docs/TechnicalSolution.md` | 现状章节 | "核心 10 个端点" | 11 个 |

另：`CLAUDE.md` 的官网章节把首页描述为"自主设计**深色**首页"，与本仓实际的明亮白底不符（同一个事实的三处呈现之一漂移了）——本节顺手纠正为与官网实际一致（**1 处事实纠正**）。README 与官网首页均未列端点计数，无需改动。

**备选被否**：不改文档（三处表述就此漂移，下次开发按"10 个"对账会误判）；改课件（课件是既定的教材原件，不属本仓可改范围）。

---

## D14 `WebSmokeIT` 落 boot，且会被 `mvn verify` 真跑

**决策**：`WebSmokeIT` 放 `fourfeetcat-boot`，`@SpringBootTest(webEnvironment = RANDOM_PORT)`（或用 `MockMvc` + 全量上下文，二选一在实现时按最小依赖定），打 `@Tag("integration")`，与其他测试一样进 `mvn verify`。

**核实事实**：
- 父 pom 是 `spring-boot-starter-parent` 3.5.16，**自带 `maven-failsafe-plugin` 配置**（包含 `**/*IT.java`）；`fourfeetcat-boot` 已具备 `spring-boot-starter-test`；`fourfeetcat-web` 目前**没有** test 依赖（需在本节补）。
- 既有 `ProviderSmokeIT` 是同类先例：`@Tag("integration")` + 无 key 时 `assumeTrue` 跳过。
- 本地基线：`mvn test -pl fourfeetcat-boot -am -Dtest=AgentSchedulerWiringTest` **BUILD SUCCESS**（本节动工前的绿基线）。

**理由**：真上下文（全部能力 Bean + JPA 仓储扫描声明 `@EnableJpaRepositories` / `@EntityScan`）只有 boot 有。课件强调这条冒烟"会真实触发仓储扫描——18 节那个'Found 0 repositories'的坑如果在 web 模块复发，这里第一时间红"——只有跑在**生产装配**上，这句话才成立。

**⚠️ 实施中的一处实情纠正（本节最值得记的一条）**：`*IT` 命名**并不会**让测试自动被执行。Spring Boot 父 pom 只在 **pluginManagement** 里备好了 failsafe 的配置（includes `**/*IT.java`、版本号），
**不在模块的 `<plugins>` 里声明这个插件，它就永远不会跑**。实测踩过：第一轮 `mvn clean verify` 的日志里只有 `*Test`（`WebSmokeIT` 一次都没出现）——
冒烟测试看起来"在库里"，实际等于没有，而且构建是**绿的**。修法：在 `fourfeetcat-boot/pom.xml` 显式声明 `maven-failsafe-plugin`（`integration-test` + `verify` 两个 goal），配置沿用父 pom 的 pluginManagement。
修完复跑，`verify` 日志里出现 `Running org.fourfeetcat.boot.WebSmokeIT` 且 3 个用例通过。

> 顺带暴露一个既有的同类缺口：`ProviderSmokeIT`（第16节）同样从没被执行过（它是 `*IT`）。本节只在 boot 模块声明了 failsafe，**没有**顺手改根 pom 去"统一修好全仓的 IT"——那会改变所有模块的构建语义，超出本节范围；这里登记为已知缺口，留待需要时一次性处理。

**备选被否**：放 web 并自建测试装配（测试里复制一份生产装配，装配漂移时测试不红——恰恰丢了这条测试最值钱的部分）；把类名改成 `*Test` 让 surefire 收走（改动课件已定的类名字面量，且把"集成冒烟"降级成普通单测层的跑法）。
