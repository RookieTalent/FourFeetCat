# 节级验收报告：Web Service 与第一版管理平台（第26节）

**分支**：`009-lesson26-web-service`　**日期**：2026-09-28　**规格**：[spec.md](./spec.md)　**计划**：[plan.md](./plan.md)　**任务**：[tasks.md](./tasks.md)

**一句话**：把已经就位的五大能力包成 11 个 REST 端点（六个 Controller，每个只做"校验 + 包装 + 错误处理"，逻辑全委托给与 CLI 同一个入口），并交付一个跑在 `/admin` 的只读管理台——它自己没有后端，五个页面全部只调已发布的 GET 端点。

---

## §1 证据一：硬门禁 `mvn clean verify` 全绿

```
[INFO] BUILD SUCCESS                                    （verify exit=0）
[INFO] Tests run: 51  fourfeetcat-core
[INFO] Tests run: 9   fourfeetcat-provider              （含本节新增 ProviderReadTimeoutTest 1）
[INFO] Tests run: 20  fourfeetcat-storage
[INFO] Tests run: 34  fourfeetcat-memory
[INFO] Tests run: 60  fourfeetcat-tool                 （Skipped: 1 —— Windows 无建软链权限，既有约定）
[INFO] Tests run: 6   fourfeetcat-channel-cli
[INFO] Tests run: 16  fourfeetcat-web                  （本节新增 SessionApiControllerTest 6 + GlobalExceptionHandlerTest 10）
[INFO] Tests run: 2   fourfeetcat-cli
[INFO] Tests run: 2   fourfeetcat-boot                 （surefire）
[INFO] Tests run: 4   fourfeetcat-boot                 （failsafe：WebSmokeIT 4/4，见 §7 偏差 2）
合计 204 个用例，0 失败 0 错误
[INFO] You have 0 Checkstyle violations.               （各模块一致）
[INFO] BugInstance size is 0                           （SpotBugs + FindSecBugs，effort=Max / threshold=Low）
PMD 7.17.0：0 violations                               （首轮曾报 3 处 GuardLogStatement，已按本仓既有形态补 isWarnEnabled() 守卫）
Spotless(GJF)：check 通过
```

> 首轮 verify 的两处红都是真问题、按实现修：PMD `GuardLogStatement`（3 条）、SpotBugs `CRLF_INJECTION_LOGS`（3 条，日志不再插值外来消息）+ `EI_EXPOSE_REP`（8 条，记录里的列表做防御性拷贝）。**没有删断言、没有放宽阈值。**

## §2 证据二：课件 harness 逐项对号

课件"验收 harness"三类测试 → 全部落地，方法名英文、课件原文进 `@DisplayName`：

| 课件测试类 | 落地点 | 课件守点 | 对号 |
|-----------|--------|---------|------|
| `SessionApiControllerTest` | `fourfeetcat-web`（切片，mocked 处理入口） | 超 32KB → 400；会话不存在 → 404；正常请求处理入口**恰被调一次** | ✅ 6 个用例：`sendMessage_over32Kb_returnsBadRequest`、`sendMessage_unknownSession_returnsNotFound`、`sendMessage_callsProcessingEntryExactlyOnce`、`sendMessage_toArchivedSession_returnsBadRequest`、`listSessions_ordersByLastActiveDesc`、`invoke_createsDistinctSessionPerCall` |
| `GlobalExceptionHandlerTest` | `fourfeetcat-web`（切片 + 探针端点） | 每类异常映射到约定状态码；响应体都是统一信封；**500 不让内部 message 泄漏** | ✅ 10 个用例（8 条参数化映射 + 不泄漏 + 超时不回显内部端点） |
| `WebSmokeIT` | `fourfeetcat-boot`（真上下文 + 真 HTTP） | `/health`、`/info`、`/profiles`、`/tools` 真实可达，验证 Bean 装配与扫描范围 | ✅ 4 个用例（四只读端点 + 会话列表走通数据访问层 + 零凭证可启动 + 管理台入口与 SPA 回落） |

**"门面分寸"那段最值钱的回归**（课件原文断言 → 本节落地）：

| 课件断言 | 本节落地 | 守点 |
|---------|---------|------|
| `status().isInternalServerError()` | 同 | 一字未改 |
| `jsonPath("$.errorCode").value("INTERNAL_ERROR")` | `jsonPath("$.code").value(500)` | 等价：既有信封的状态码字段在 `code`（与 HTTP 同值），见 §7 偏差 5 |
| `jsonPath("$.message").value("内部错误")` | 取既有 `ErrorCode.INTERNAL_ERROR.getReason()` | 等价：话术沿用既有常量，不新造字符串 |
| `content().string(not(containsString("jdbc:sqlite")))` | 同，并加断库名与 `connect failed` | 一字未改 |

## §3 证据三：交付物逐项存在性核对

| 课件交付物 | 落地 | 存在性 |
|-----------|------|--------|
| 六个 Controller（Session/Agent/Profile/Memory/Tool/System） | `web/{session,agent,profile,memory,tool,system}/` | ✅ 6 个类，11 个端点 |
| `GlobalExceptionHandler`（扩展既有、复用 `ApiResponse`） | `web/api/GlobalExceptionHandler.java`（改） | ✅ 扩展 5 条映射；信封零新增 |
| `springdoc-openapi` 集成 | 依赖已在（地基阶段引入） | ✅ 现场 `/v3/api-docs` 列出全部端点；`/swagger-ui` 200 |
| 前端 Vue 3 + Vite（五页）+ 产物落 `static/admin` + SPA 回落 | `web/src/main/frontend/`、`web/.../static/admin/`、`AdminSpaConfig` | ✅ 14 个前端文件；构建产物 3 个（html/css/js + logo） |
| 构建串联（二选一） | `frontend-maven-plugin` 绑 `generate-resources` | ✅ `mvn package` 一条命令产出含管理台的产物（`jar tf` 可见 `static/admin/index.html`） |
| 风格 skill（token + 工程约定 + 三态 + 验收清单） | `.claude/skills/four-feet-cat-admin-ui/SKILL.md` | ✅ 六节，token 逐项取自官网首页当前样式定义 |
| 配置：虚拟线程 + 8080；32KB / 100 条 | 前两项**已就位于既有 `application.yaml`**（本节零改动）；后两项 `MessageRequest`（32KB）与 `SessionApiController`（100 条 + 截断标记） | ✅ 前者现场核对；后者有断言 |

## §4 证据四：前序节回归全绿（跨节契约证据）

- 前序各节测试**零删改、零跳过**（唯一 skip 是既有的 Windows 软链用例）。
- 前序节文件被本节触碰的仅四处，且均为"加一条映射 / 加一次构造参数 / 加一个 Bean / 修一个保活缺陷"：`ErrorCode`（加一项枚举）、`GlobalExceptionHandler`（加映射）、`ProviderConfiguration`（客户端超时改造）、`ServeCommand`/`GatewayCommand`/`FourFeetCatCli`（保活位，见 §7 偏差 1）。**签名、Bean 名、语义一律未动。**
- 跨节契约守点：`AgentService.process` 仍是唯一处理入口（`verify(agentService, times(1))`）；会话标识拼接仍只在存储实现里（全库检索无第二处）；审计路径零改动。

## §5 证据五：H4 六条全局不变量自查

| # | 不变量 | 结论 |
|---|--------|------|
| ① | 涉外 IO 首行过 `Sandbox.enforce` | ✅ 本节**不新增任何工具**，已交付四个工具的调用位一行未动；Web 层唯一的出站 IO 是模型调用（不是工具，不受沙箱管），沙箱语义未被绕开 |
| ② | LLM 调用成败都落 `llm_calls`、工具执行都落 `tool_invocations` | ✅ `grep` 证明 web 层不直接碰模型与工具执行（无 `ChatModel` / `toolRegistry.execute` 引用），全部经既有 ReAct 路径落审计 |
| ③ | 无明文 key | ✅ 源码扫描无命中；`/info` 只报"是否就位"，现场响应体无 `apiKey` 字段（有断言） |
| ④ | `session_id` 只在会话管理器内拼接 | ✅ web 只递三元组（`getOrCreate(WEB_CHANNEL, user, agent)`）；渠道名集中一处常量 |
| ⑤ | 无 Reactor / `CompletableFuture` / 自建线程池 | ✅ 新增文件零命中；保活用 `CountDownLatch.await()`（同步阻塞，零额外线程） |
| ⑥ | 无 Spring AI 自动工具执行路径 | ✅ 零命中；唯一与 Spring AI 的接触面是给连接设施设读超时 |

## §6 证据六：现场真链路冒烟（本节第一次让 `serve` 常驻，当场跑过）

起真服务（`java -jar … serve --port 18082`，零凭证）实打实 curl：

| 项 | 结果 |
|----|------|
| `GET /health` `/info` `/profiles` `/tools` `/memory` `/sessions` | 全部 200 + 统一信封；`/info` 如实报 `credentialConfigured:false` 且**不回显凭证** |
| `POST /sessions`（空体） | 200，按缺省口径建出 `web:anonymous:default` |
| `GET /sessions/{id}` | 200，含 `truncated:false` 与空历史 |
| `DELETE /sessions/{id}`；再来一次 | 200；**重复归档幂等，归档时间未被覆盖** |
| `POST /sessions/{id}/messages`（已归档） | **400**，明确拒绝不静默复活 |
| `POST /agents/ghost/invoke` | **404**（不是 503 —— 名字写错是客户端问题） |
| 消息超 32KB | **400**，回我们的话术 |
| `GET /admin`、`/admin/`、`/admin/sessions`、`/admin/sessions/abc`、`/admin/tools` | 全部 200 `text/html` |
| `/admin/assets/index-*.js`、`.css`、`logo-icon.svg` | 200，内容类型正确 |
| `/admin/missing.js` | 404（**不**回落成页面） |
| `/api/v1/nope` | 404 **JSON**（**不**回落成 HTML） |
| `/swagger-ui`、`/v3/api-docs` | 200；文档列出全部端点 |
| **CLI 建过的会话在 REST 侧可见** | ✅ `cli:81470:default` 出现在 `GET /sessions` 里——人工项"两个人推入口共享存储"当场验掉 |

## §7 实施期裁决与偏差记录（逐条留痕）

1. **修前序节缺陷：`serve` 起来就退**（触碰 `ServeCommand`/`GatewayCommand`/`FourFeetCatCli`）。现场冒烟发现服务在启动后约 0.1 秒优雅停机、**退出码还是 0**：`main` 在派发返回后无条件 `System.exit`，而 `serve` 没有保活位（`gateway` 从一开始就有）。修法：保活位收到根命令一处，两条长驻命令共用。不改任何对外契约。
2. **`*IT` 类原本永远不会跑**：Spring Boot 父 pom 只在 pluginManagement 里给了 failsafe 配置，**不在模块里声明插件就不执行**——第一轮 verify 里 `WebSmokeIT` 一次都没出现而构建是绿的。已在 `fourfeetcat-boot/pom.xml` 显式声明 failsafe（顺带登记：既有 `ProviderSmokeIT` 也从未被执行过，见 §8）。
3. **管理台裸路径 404**：`/admin/**` 的资源映射不覆盖 `/admin`（解析器还会把裸路径规范化成 `"."`，被"含点即静态资源"误判）。已用视图控制器认下 `/admin` 与 `/admin/`，并把这两条 + 两条反向断言写进冒烟测试。
4. **课件骨架的 `AgentTimeoutException` 未采用**：实测超时以 `ResourceAccessException <- java.net.http.HttpTimeoutException` 浮现，504 由统一出口按原因链映射而来；多一个没人抛的异常类只是死代码。同时**新增**了课件假定"已存在"的 `IllegalArgumentException → 400` 映射（代码里原本没有）。
5. **信封字段改译**：课件 harness 断言 `$.errorCode`，与它自己指定的既有信封（`code`/`message`/`data`/`timestamp`）不符。按正文口径复用既有信封，断言**守点**逐条保真（见 §2）。
6. **`AdminSpaController` 改写成 `AdminSpaConfig`**：`@GetMapping("/admin/**")` 会把 `/admin/index.html` 也匹配进去，形成自转发死循环；资源解析器才能按"是不是静态资源"分流。
7. **两处共享记录**（`MessageRequest` / `ReplyResponse`）：两个控制器对消息的规则完全一致，校验口径只此一处——两处各写一份迟早出现两套上限。
8. **前端工具链装在 `target/` 之外**（`web/.frontend-tools/`，不入库）：放 `target/` 则每次 `mvn clean` 都重下 35MB Node 分发件，而 `nodejs.org` 在国内网络下时好时坏（实测被连接重置过）。
9. **一次性调用每次独立会话身份 / 归档软标记 + 拒发**（spec Clarifications 两条裁决）已落地并有断言。

## §8 已知文档-代码差（登记，不静默；本节不越界修改）

| # | 差 | 说明 |
|---|----|------|
| 1 | 课件点名的 `website/.viteppress/theme/custom.css` 不存在 | 真实文件是 `website/.vitepress/theme/styles.css`；且课件那份色值（深色 + 橙）描述的是**旧版官网**——本仓官网现为明亮白底 + 猫蓝（已按主公裁决以官网实际为准，见 plan ③） |
| 2 | 课件第 75 行的虚拟线程与端口配置 | **已在**既有 `application.yaml` 里，本节零改动 |
| 3 | `ProviderSmokeIT`（第16节）从未被执行 | 它是 `*IT`，同样受 §7 偏差 2 影响。本节只在 boot 模块声明了 failsafe，**没有**顺手改根 pom 去统一修全仓的 IT——那会改变所有模块的构建语义，超出本节范围 |
| 4 | `CLAUDE.md` 的工作区结构写 `agents/`，代码加载的是 `profiles/` | 现场冒烟时确认：`ProfileLoader` 扫 `.fourfeetcat/profiles/*.yaml`。属第16节遗留（早于"一个目录 = 一个 Agent"的字段），属功能改名，不在本节范围 |
| 5 | `CLAUDE.md` 的 Docker 部署形态章节所描述的 `docker/`、`bin/` 目录在仓库里不存在 | 现场 `ls` 确认。属文档先行，登记待办 |

---

## §9 剩余人工项（harness 判不了，请主公过目）

harness 已判卷的部分不再重复（见上）。**仍需人工过**：

1. **真模型链路**：配 `DEEPSEEK_API_KEY` 后 `POST /sessions/{id}/messages` 走完一轮 ReAct，并核对 `llm_calls` / `tool_invocations` 有账（本节现场只验到"零凭证下 400/404/500 类路径"，真调用未打）。
2. **503 故障注入**：把 Provider 端点指到不可达地址后发消息，期望 503 且服务不崩。
3. **200 并发压测**：对 `POST /agents/{name}/invoke` 打并发，观察虚拟线程表现（本节未压）。
4. **管理台视觉与三态**：浏览器打开五页逐项对照 `.claude/skills/four-feet-cat-admin-ui/SKILL.md` 的 token 表；断网/清库各看一次三态（本节只验了 HTTP 层可达与内容类型）。
5. **`POST /sessions/{id}/messages` 的 504**：把读超时临时调到 1 秒、对着一个不应答的端点发消息（`ProviderReadTimeoutTest` 已在单测层证明超时会浮现，端到端未跑）。
