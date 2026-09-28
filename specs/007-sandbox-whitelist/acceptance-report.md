# 节级验收报告：Sandbox 白名单校验（第24节）

**分支**：`024-lesson24-sandbox`　**日期**：2026-09-28　**规格目录**：`specs/007-sandbox-whitelist/`

**一句话**：第23节那道墙后面的第一档填上了——三个配置件 + 一个实现类 + 一处装配替换，四个工具的调用位、审计路径、契约四件一行未改。

---

## §1 证据一：硬门禁 `mvn clean verify` 全绿

```
Reactor Summary:
FourFeetCat ................. SUCCESS    FourFeetCat :: Core ......... SUCCESS
FourFeetCat :: Provider ..... SUCCESS    FourFeetCat :: Storage ...... SUCCESS
FourFeetCat :: Memory ....... SUCCESS    FourFeetCat :: Tool ......... SUCCESS
FourFeetCat :: Channel CLI .. SUCCESS    FourFeetCat :: Web .......... SUCCESS
FourFeetCat :: CLI .......... SUCCESS    FourFeetCat :: Boot ......... SUCCESS
BUILD SUCCESS
```

**全模块合计**：`Tests run: 169, Failures: 0, Errors: 0, Skipped: 1`

四个静态门禁全部通过（本节实际触发并修正过两处，见 §7）：

| 门禁 | 结果 |
|------|------|
| Spotless（google-java-format） | 35 个文件 clean，0 需改动 |
| PMD 7（bestpractices + errorprone） | 首轮 2 处 `PreserveStackTrace` → 修正后 0 违规 |
| Checkstyle（google_checks） | `0 Checkstyle violations` |
| SpotBugs + FindSecBugs（effort=Max / threshold=Low） | 首轮 1 处 `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` → 修正后 `No errors/warnings found` |

> 唯一的 1 个 skip 是**软链用例**：本机（Windows）账户没有 `SeCreateSymbolicLinkPrivilege`，`Files.createSymbolicLink` 抛权限异常，用例走 `Assumptions` 跳过。CI（`ubuntu-latest`）上该用例真跑。这是**平台能力不足，不是放宽断言**——用例本体一条断言未删。

---

## §2 证据二：课件 harness 逐项对号

| 课件 harness 项 | 落地 | 实测 | 守点 |
|----------------|------|------|------|
| 文件路径：白名单内放行 / 白名单外拒绝 / 相对路径穿越被拦 | `WhitelistSandboxTest` | 4 项全过 | `@DisplayName("相对路径穿越必须被拦")`——课件的回归点逐字落地 |
| 文件路径（宪法加严）：软链指向白名单外被拦 | 同上 | 本机 skip，CI 真跑 | `@DisplayName("软链指向白名单之外_必须被拦_校验的是真实路径")` |
| 文件路径（spec 加严）：白名单项写成相对路径 → 构造即拒 | 同上 | 过 | 报错点名该项，不静默按工作目录解析 |
| Shell：白名单内放行（含前导空格）/ 白名单外拒绝 / 大小写变体 | 同上 | 3 项全过 | 课件未点名大小写预期，按技术方案 §6.7"精确比对"落为**大小写敏感**，`@DisplayName` 已写明口径 |
| HTTP：精确匹配放行 / 白名单外拒绝 / `*.example.com` 命中子域 | 同上 | 4 项全过 | `@DisplayName("通配符域名_不能被形似域名绕过")`——`evil-example.com` 与裸域 `example.com` 双双拦下 |
| HTTP（spec 加严）：只比主机名、忽略端口与路径、大小写敏感；URL 解析不出一律拒绝 | 同上 | 过 | 解析异常与主机名为空都归到拒绝侧（fail-closed） |
| 接线回归：四个工具各一条"白名单外的输入被拦、真正的 IO 没有发生" | `FileToolsTest` / `ShellToolsTest` / `HttpToolsTest` / `NotifyToolsTest` | 4 项全过 | 证据是**副作用**：目标文件内容逐字未变、进程未起（标记文件不存在）、stub 服务端零请求、发送端替身零调用 |
| 端到端（本节加严）：被拦动作走既有审计路径 | `FileToolsTest.blockedAction_landsInAuditAsFailureWithReadableReason` | 过 | 真白名单 → `ToolExecutor` → 审计得到 `success=false` 且原因含"路径不在白名单内"；`ToolExecutor` 与审计类**零改动** |

**harness 统计**：新增 16 条用例（`WhitelistSandboxTest` 12 + 四个工具类各 1 加严 + 端到端 1）。反作弊自查：本节**零断言删除、零 `@Disabled`、零阈值放宽**；四个工具类的既有用例（含第20节的替身沙箱顺序断言）全部保持通过。

---

## §3 证据三：交付物逐项存在性核对

```text
fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/sandbox/
  WhitelistSandbox.java          ← 本节交付（实现）
  FileSandboxProperties.java     ← 本节交付（配置件）
  ShellSandboxProperties.java    ← 本节交付（配置件）
  HttpSandboxProperties.java     ← 本节交付（配置件）
  Sandbox.java / SandboxAction.java / ActionType.java / SandboxViolationException.java  ← 第20节交付，本节零 diff
  PermissiveSandbox.java         ← 删除（第20节登记的"由沙箱节替换"）

fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/sandbox/WhitelistSandboxTest.java  ← 本节交付
```

| 课件"本节交付物" | 状态 |
|-----------------|------|
| `Sandbox` / `SandboxAction` / `ActionType` / `SandboxViolationException` | 第20节已交付；本节 `git diff --stat` 四件**空输出**（零改动） |
| `WhitelistSandbox` | 新增 ✓ |
| 三个 `@ConfigurationProperties`（file / shell / http） | 新增 ✓ |
| `WhitelistSandboxTest` | 新增 ✓（12 条） |
| 四个 Tool 的拦截回归用例 | 四个既有测试类各加一条真白名单用例 ✓ |
| `application.yaml` 三键 + 配置说明 | `file.allowed_paths` / `shell.allowed_commands` / `http.allowed_domains` ✓，五条配置说明写在键旁注释里 ✓ |
| 四个 Tool 执行首行加 `enforce(...)`（改造点） | 第20节已就位：`FileTools` 38/53/67 行、`ShellTools` 48 行、`HttpTools` 32/43 行、`NotifyTools` 44 行——**本节零改动**，只由真白名单证明其生效 |
| 临时装配替换 | 装配位换成 `WhitelistSandbox`，占位类删除 ✓ |

---

## §4 证据四：前序节回归全绿（跨节契约证据）

`mvn clean verify` 覆盖全部 10 个模块、169 条用例，第16~22 节全部保持绿：

| 模块 | 与本节的关系 | 结果 |
|------|-------------|------|
| Core | `ToolExecutor` 失败审计路径（零改动） | 34 条全绿 |
| Provider / Storage / Memory | 未触碰 | 全绿 |
| Tool | 本节主战场 | 60 条（含本节 16 条）全绿，1 skip（软链/平台） |
| Channel CLI / Web / CLI / Boot | 未触碰（Boot 的装配位与 yaml 除外） | 全绿 |

**跨节触碰一处**：`fourfeetcat-boot/src/test/java/.../FourFeetCatApplicationTests.java` 的测试属性 `FOURFEETCAT_ROOT=target`（相对）→ `${user.dir}/target`（绝对）。原因是文件白名单默认引用同一个变量，而"白名单项必须绝对路径"这条裁决会让上下文加载测试变红（analyze D1，经主公裁决）。

---

## §5 证据五：H4 全局不变量自查

| # | 不变量 | 结果 | 证据 |
|---|--------|------|------|
| ① | 涉外 IO 首行过 `Sandbox.enforce` | ✅ | 四个工具七处调用点全在方法体首行：`FileTools:38/53/67`、`ShellTools:48`、`HttpTools:32/43`、`NotifyTools:44`（本节未动这些文件） |
| ② | LLM 调用与工具执行成败都落审计 | ✅ | `git diff --stat fourfeetcat-core/` **空**：`ToolExecutor` 与 `ToolInvocationRecorder` 零改动。沙箱违规是普通 `RuntimeException`，由既有 try/catch 接住落 `tool_invocations`（`success=false` + 原因）——新增的端到端用例实测了这一条 |
| ③ | 无明文 key | ✅ | 本节 diff 里 `api-key` / `password` / `secret` 赋值字面量 grep = **0**；新增配置只有三个白名单键，无凭证 |
| ④ | `session_id` 只在 `SessionManager` 内拼接 | ✅ | 本节新增文件 `grep sessionId` = **0**，未触碰拼接口径 |
| ⑤ | 无 Reactor / `CompletableFuture` / 自建线程池 | ✅ | 本节涉及文件四者 grep = **0**，全同步阻塞（宪法原则七） |
| ⑥ | 无 Spring AI 自动工具执行路径 | ✅ | 本节新增文件 `grep chatClient / .tools(` = **0**；沙箱不接触工具调用链，只在工具内部拦在 IO 之前 |
| ⑦ | 不使用 Java SecurityManager（原则六） | ✅ | 全仓 `grep SecurityManager`（`fourfeetcat-*/src`）= **0** |

---

## §6 证据六：剩余人工项（harness 判不了，请主公过目）

| # | 人工项 | 状态 |
|---|--------|------|
| 1 | **真实链路集成验证**：配一份只允许单一命令的白名单，真跑一次白名单外的命令，确认链路上抛 `SandboxViolationException`、`tool_invocations` 有 `success=false` 记录、`error_message` 人能读懂 | **待主公过**。自动化部分已覆盖到"真白名单 → `ToolExecutor` → 审计记录 `success=false` + 可读原因"（端到端用例）；剩下的"真模型发起命令 + 真 SQLite 落库"需人工跑一次（步骤见 `quickstart.md` §2） |
| 2 | **接口中立性自查（思维练习）**：`Sandbox.enforce(SandboxAction)` 换成 `KataMicroVmSandbox` 实现需要加方法吗？ | **待主公过**。臣的核对结论：实现类对外可见成员只有构造器与 `enforce`，三个校验方法与两个助手全是 private；设想 microVM 档只需「把动作交给 VM 执行，通过即返回、拒绝即抛同一个异常」→ 需要新增的对外方法数 = **0** |
| 3 | **回归**：改造后四个工具原有测试全绿 | ✅ 已由 §4 判卷（四个测试类既有用例一条未改，全绿） |
| 4 | **配置边界写进文档**（空 = 什么都不允许） | ✅ 已写进 `application.yaml` 三个键旁的注释（另含绝对路径要求、解释器警告、域名口径、"劝阻级非强隔离"共五条） |

---

## §7 实施期裁决与偏差记录（逐条留痕）

| # | 事项 | 处置 |
|---|------|------|
| 1 | **路径校验取宪法严格版**（课件只写 `normalize+startsWith`） | 经主公裁决：目标存在时用 `toRealPath()` 复验真实路径，新建路径校验最近存在父目录的真实路径。严格版是课件版的**超集**，课件穿越用例照过，另加软链用例 |
| 2 | **悬空软链的处理**（实施期发现的新边界） | 判存在用 `LinkOption.NOFOLLOW_LINKS`：悬空软链算"存在"→ 走 `toRealPath` 抛错 → **拒绝**。若改用跟随链接的判存在，悬空软链会被当成"新建路径"放行，随后的写入会顺着链接落到白名单外——这是个真实绕过，故归到拒绝侧 |
| 3 | **域名口径**：只比主机名（忽略端口与路径）、大小写敏感 | 经主公裁决（B）。实测 `URI.getHost()` 保留原大小写、无方案输入抛 `IllegalArgumentException`、无主机返回 `null`——三条行为都归到拒绝侧 |
| 4 | **白名单项写成相对路径 → 启动即拒** | 经主公确认。校验点落在 `WhitelistSandbox` 构造器（单一入口，不新增配置校验类），报错点名该项 |
| 5 | **SMTP 端点白名单本节不做**（文档-代码差，登记不静默） | 经主公裁决。`docs/TechnicalSolution.md` §6.7、`.specify/memory/constitution.md` 原则六、`CLAUDE.md` 原则六均列 `smtp.allowed_endpoints` / `checkSmtpEndpoint`，但其消费方（邮件通知实现，§6.8 标注扩展阶段）在代码库里尚不存在，且 `ActionType` 四值由第20节定死、本节不得改字面量。**待邮件通知实现落地时随其新增**（届时是"加实现、扩调用点"，不是改这道墙） |
| 6 | **删除 `PermissiveSandbox`** | 第20节登记的替换动作（其 javadoc 自述"由沙箱节替换"）。装配位由白名单实现承接，四个工具与 `ToolRegistry` 的构造方式零改动 |
| 7 | **跨节触碰：boot 测试夹具改绝对路径**（analyze D1） | `FOURFEETCAT_ROOT=target` → `${user.dir}/target`。不这么改，白名单默认值解析出相对路径，前序节的上下文加载测试会被本节规则拦下 |
| 8 | **静态门禁两处修正（真修，非压制）** | ① PMD `PreserveStackTrace`×2：解析失败的原始异常（权限、悬空软链、路径过长）必须留在因果链里——用 `initCause` 挂上，**没有删规则、没有加 suppression**；也因此没有给 `SandboxViolationException` 新增构造重载（它的构造签名归第20节，动它就把"契约零 diff"这条证据弄丢了） ② SpotBugs `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE`：`getParent()`/`getFileName()` 在根路径上可能为 null，改成显式双空判（逻辑本就安全，只是让分析器看得见） |
| 9 | **文档级修正（analyze C1/E1）** | ① 配置键名口径统一为下划线（`file.allowed_paths`），与课件、宪法、`CLAUDE.md` 逐字一致（Spring 宽松绑定按 `allowedPaths` 接收） ② 补 T025（夹具改动）、T017 增补"`grep SecurityManager` 零命中"以覆盖 FR-011 |
| 10 | **未动的内容**（守范围边界） | 四个工具、`ToolExecutor`、`ToolInvocationRecorder`、`ToolRegistry`、`Sandbox` 契约四件、其它模块、`README` / 官网 / `docs/` 三处**全部零改动**（本节不改变对外定位与特性表述，内置工具数不变） |
| 11 | **一处观察项（非本节引入）** | `AgentRuntimeConfiguration.workspaceRoot()` 走 `System.getenv`，而 `application.yaml` 的占位走 Spring `Environment`：生产环境下两者一致（变量来自真实环境变量或都不设）；在 `@SpringBootTest(properties=...)` 里只设 Spring 属性，故 boot 测试日志会出现一行工作区目录读取失败（既有行为，本节未改）。**若要统一口径，属独立的技术债，不在本节范围** |
