# Phase 0 Research：Sandbox 白名单校验（第24节）

本文件收敛本节全部技术裁决。**无 NEEDS CLARIFICATION 残留**。其中 D1 / D2 的域名口径 / D3 来自主公裁决（记录在 spec 的 Clarifications），D7 与 D2 的第三方行为来自本机实测。

---

## D1 路径校验：取宪法严格版，而非课件的字符串前缀版

**决策**：文件路径校验分两步，都以"真实路径"为准绳：

```text
realPath(p) =
  目标存在            → p.toRealPath()                       // 软链被解析
  目标不存在          → 最近存在祖先的 toRealPath() + 剩余段  // 新建路径的场景
  解析抛 IOException   → 拒绝（fail-closed，绝不当作放行）

允许 = 任一白名单根的真实现（构造期算一次）是 realPath(动作目标) 的前缀
```

白名单根在构造期用同一个 `realPath` 归一（根还不存在时同样回退到最近存在祖先），保证"根没建出来"和"根建出来了"两种时刻的判定口径一致。根一旦算出即缓存——这正是"构造期算一次"的意思：路径字符串不变，软链被后续替换属管理员动作，不在本节防线范围内（`ponytail:` 注释在实现里点明这个上限）。

**理由**：
- 宪法原则六对文件白名单写的是 MUST——目标存在时用 `toRealPath()` 校验真实路径仍位于白名单根，新建路径校验最近存在父目录的真实路径。项目宪法是硬约束。
- 只看 `normalize()` 后的字符串前缀挡不住"白名单目录里放一个指向 `/etc` 的软链"这类绕过，而本节的验收线恰恰是"绕不过去"。
- 严格版是课件版的**超集**：`../` 穿越在严格版下同样被拦（标准化语义已含在 `realPath` 里），课件 harness 的穿越用例逐字照过，不牺牲任何课件守点。

**备选被否**：严格照课件写 `Path.of(x).normalize().toAbsolutePath().startsWith(根)` —— 少十来行，但软链绕过敞着，且与宪法 MUST 条款直接冲突。经主公确认取严格版。

## D2 域名校验：`URI` 取主机名，只比主机名、大小写敏感

**决策**：

```text
host = URI.create(url).getHost()     // 解析异常 / host == null → 拒绝
允许 = 白名单任一项匹配
       通配项 "*.x.com" → host.endsWith(".x.com")        // substring(1) 保留点号边界
       精确项           → host.equals(项)
```

忽略端口与路径；**大小写敏感**；`*.example.com` 不命中裸域 `example.com`，不命中 `evil-example.com`。

**理由**：白名单项叫"域名"，端口与路径不属其管辖；大小写敏感与命令白名单同口径（配什么写什么，不做归一），避免两处白名单一个归一一个不归一的漂移。经主公裁决。

**实测事实（JDK 21，`java.net.URI`，本节动笔前跑过）**：

| 输入 | `URI.create(...).getHost()` |
|------|------------------------------|
| `https://API.Example.com:8443/x` | `API.Example.com`（**保留原大小写**，端口已剥离） |
| `https://api.example.com/x` | `api.example.com` |
| `not a url` | 抛 `IllegalArgumentException`（不是返回 null） |
| `api.example.com/x`（无方案） | `null`（被当成路径，无主机） |

三条行为直接决定写法：`IllegalArgumentException` 与 `null` 都必须落到"拒绝"，不许有一个漏成放行。**这也解释了课件那句"通配符不能被形似域名绕过"为什么必须带点号边界**——`endsWith("example.com")` 对 `evil-example.com` 为真，是经典漏洞。

**备选被否**：①大小写不敏感（更贴 DNS 语义，但与本项目命令白名单口径不一致）；②把端口纳入比对（白名单项要写成 `host:port`，与课件给的 `*.example.com` 形态不符）。

## D3 白名单项是相对路径 → 启动即拒

**决策**：`WhitelistSandbox` 构造时逐项检查文件白名单，非绝对路径即抛 `IllegalStateException` 并**点名该项**；Bean 创建失败即启动失败。

**理由**：相对路径按当前工作目录解析，而同一份配置在 CLI、守护进程、容器三处的工作目录各不相同——安全配置的语义不得随运行环境变形。项目既有口径本就是"配置不静默失败"（`ConfigLoader` 缺项或非法即给清晰报错）。经主公确认。

**备选被否**：①按当前工作目录静默解析（省 5 行，把确定性问题推迟到运行期）；②相对项记 WARN 后忽略（既不拒绝启动也不静默，但"部分白名单悄悄失效"比直接拒更绕）。

## D4 装配位：替换既有 `Sandbox` Bean，不给实现类加 `@Component`

**决策**：在第20节宣告 `Sandbox` Bean 的 `AgentRuntimeConfiguration.sandbox()` 里改为构造 `WhitelistSandbox`，并在该类加 `@EnableConfigurationProperties({FileSandboxProperties, ShellSandboxProperties, HttpSandboxProperties})`。实现类本身不加 `@Component`。

**理由**：本工程装配一律显式（内置工具不靠扫描，由 `toolRegistry` Bean 一行挂一个）；`Sandbox` 的装配位第20节就已定在这里，本节是**同一处替换**而不是新增装配点——四个工具的构造参数、`ToolExecutor`、`ToolRegistry` 全不动，正是"换一档实现、调用方零改动"的兑现。课件里的 `@Component` 是示意写法，若照搬会与显式 Bean 重复注册。

**备选被否**：照课件加 `@Component` + `@ConfigurationPropertiesScan`——多一个扫描契约，且与既有"显式装配 + `@EnableConfigurationProperties`（`ProviderConfiguration` 已立的先例）"两种风格并存。

## D5 删除 `PermissiveSandbox`

**决策**：删除该类及其 import，装配位改由白名单实现承接。

**理由**：它自己的 javadoc 已写明"由沙箱节替换"；留着就是一个"什么都不拦"的沙箱躺在库里等误装配。删除是第20节就登记好的动作。

**备选被否**：保留类、只改装配——多一个悬空类与一次误读风险。

## D6 默认配置：文件 = 工作区根（绝对），命令 / 域名 = 空（全拒）

**决策**：`application.yaml` 写三个键：

```yaml
file:
  allowed_paths: ${FOURFEETCAT_ROOT:${user.dir}/.fourfeetcat}
shell:
  allowed_commands: []
http:
  allowed_domains: []
```

**理由**：文件白名单默认取工作区根——Agent 开箱只能碰自己的东西，而"开箱能读写自己的东西"是第20节"Agent 真的能动手"那条验收的前提；命令与域名默认全拒——把解释器列入白名单是管理员的显式授予，出网白名单同理（数据不出域）。

**关键点**：`${user.dir}` 必须显式写在缺省值里。工作区根在代码侧是 `Path.of(FOURFEETCAT_ROOT or ".fourfeetcat")`（**相对**），而 D3 要求白名单项必须是绝对路径——若照抄 `.fourfeetcat` 就会在启动时被自己的配置校验拒掉。`${user.dir}` 是 JVM 工作目录（绝对），与代码侧默认值指向同一处；管理员把 `FOURFEETCAT_ROOT` 设成相对路径则启动即拒，这正是 D3 想要的显式失败。

**备选被否**：①三键全空（开箱 Agent 什么都干不了，与既有验收冲突）；②命令 / 域名给宽松默认（把"劝阻级"防线进一步放宽，方向错）。

## D7 `switch` 用穷尽枚举的箭头式，不写 `default`

**决策**：

```java
switch (action.type()) {
  case FILE_READ, FILE_WRITE -> checkFilePath(action.target());
  case SHELL_COMMAND -> checkShellCommand(action.target());
  case HTTP_REQUEST -> checkHttpUrl(action.target());
}
```

**理由（实测）**：本节动笔前解包本工程锁定的 `pmd-java-7.17.0.jar` 核实——`bestpractices.xml` 里 `SwitchStmtsShouldHaveDefault` 已标 `deprecated="true"` 且 `ref="NonExhaustiveSwitch"`，即只有**非穷尽**的 switch 才会被报。枚举四值全列即穷尽，不写 `default` 也不违规；反过来，为过门禁硬塞一个 `default ->` 反而踩上 skill 的语法禁区。

**备选被否**：if/else 链（可读性更差，且丢了"新增枚举取值时编译器替我报警"这层保护）。

## D8 失败处置统一 fail-closed

**决策**：路径 `toRealPath()` 抛 IO 异常、URL 解析异常、主机名为 null、白名单项为空——一律**拒绝**并给出可读原因。

**理由**：安全校验里"判不了就放行"是最隐蔽的漏洞形态。本节所有"判不了"的分支都归到拒绝一侧，且原因文本点名越界物（模型看到原因能在下一轮换策略）。

**备选被否**：任何形式的"解析失败则跳过校验"。

## D9 命令首 token 比对：先 `trim()` 再切，精确匹配、大小写敏感

**决策**：`command.trim().split("\\s+")[0]` 与白名单 `Set` 精确比对（`Set.copyOf` 一次）；argv 直传、不经 shell 解释——后者是第20节既有实现，本节不改。

**理由**：课件明确要求处理"首 token 带前导空格"（不 trim 就等于用空格绕过白名单）；`ActionType` 与 `TechnicalSolution` §6.7 对命令写的是"精确比对"，故大小写敏感——与 D2 的域名口径一致，两处白名单一个口径。

**备选被否**：大小写归一（Windows 上文件系统大小写不敏感，归一看似顺手，但白名单是管理员逐项列的清单，归一会让"配什么写什么"这句话失效）。

## D10 配置件形态：record + 紧凑构造器兜 null

**决策**：三个配置件按课件形态定义（`FileSandboxProperties(List<String> allowedPaths)` 等），紧凑构造器把 null 兜成 `List.of()`；实现类构造时 `List.copyOf` / `Set.copyOf` 固化。

**理由**：配置键缺省时 Spring 绑定可能给出 null，而"null 直接 NPE"在启动期的表现是一句难懂的栈；兜成空列表语义正确（空 = 全拒，D6），且与既有 `ProviderProperties` 的紧凑构造器手法同构。

**备选被否**：在 `@Value` 层面给缺省——三个列表用 `@Value` 表达会散成一堆字符串拆分逻辑，不如 `@ConfigurationProperties` 绑定干净。

---

## 已知文档-代码差（登记，不静默）

| 差 | 文档怎么说 | 本节怎么做 | 去向 |
|---|-----------|-----------|------|
| SMTP 端点白名单 | `docs/TechnicalSolution.md` §6.7、`.specify/memory/constitution.md` 原则六、`CLAUDE.md` 原则六都列了 `smtp.allowed_endpoints` / `checkSmtpEndpoint` | 本节**不做**（课件本节只三类；`ActionType` 四值由第20节定死不得改；消费方邮件通知实现尚不存在） | 随邮件通知实现（`docs/TechnicalSolution.md` §6.8 标注的扩展阶段件）一起落地；本节验收报告显式登记 |

> 说明：这不是偷工，是"不给未来写用不上的代码"。三处文档的目标态本身没错，缺的是它的调用点——调用点一到，加校验是"加实现、扩调用点"，不是改这道墙。

## 已核实依赖（动手前检查，H0 门禁）

| 依赖 | 状态 | 证据 |
|------|------|------|
| `Sandbox` / `SandboxAction` / `ActionType` / `SandboxViolationException`（第20节） | 已在库 | `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/sandbox/` 四个文件 |
| 四个工具的 `enforce` 调用位（第19/20节） | 已在库且位置正确 | `FileTools`（3 处）/ `ShellTools`（1 处）/ `HttpTools`（2 处）/ `NotifyTools`（1 处）均在方法体第一行 |
| `ToolExecutor` 失败审计路径（第17节） | 已在库 | `fourfeetcat-core/react/ToolExecutor.java` 的 try/catch → `ToolInvocationRecorder` |
| 通知渠道注册表（第19节） | 已在库 | `NotifyChannelSource` / `NotifyTarget` |
| `MemoryService` 与记忆工具（第22节） | 已在库 | `fourfeetcat-memory/` 门面 + `builtin/MemoryTools`（本节不改） |
| 临时装配 `PermissiveSandbox`（第20节） | 已在库，本节删除 | 其 javadoc 自述"由沙箱节替换" |
| 新增第三方依赖 | **无** | 全部用 JDK 内置 `java.nio.file` / `java.net.URI`；配置绑定用已在 classpath 的 `spring-boot` |
