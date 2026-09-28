# Implementation Plan: Sandbox 白名单校验（第24节）

**Branch**: `024-lesson24-sandbox` | **Date**: 2026-09-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-sandbox-whitelist/spec.md`

## Summary

第23节评审把沙箱拆成三句话：方向想清楚、接口设计对、实现只做第一档。第20节已经把中间那句落成了代码——`Sandbox` 接口、`SandboxAction`、`ActionType`、`SandboxViolationException` 四个前向件在 `fourfeetcat-tool` 里立住，四个涉外工具（文件 / 命令 / 请求 / 通知）的执行首行也都留好了 `sandbox.enforce(...)` 调用位，当时挂的是一个**不做任何校验的临时装配**。

本节把第一档填上：**一个实现类（`WhitelistSandbox`）+ 三个配置绑定（file / shell / http）+ 一处装配替换（删掉临时装配）**。四个工具的调用位、`ToolExecutor` 的审计路径、`Sandbox` 接口的签名，一行都不改——这正是第23节"接口设计对了，接入成本小到不成比例"的验证点。

本节真正的验收线不是"放行对不对"，而是**绕不过去**：相对路径穿越、软链指向白名单外、形似通配域名（`evil-example.com`）三个绕过场景必须逐一被拦，且被拦时**真正的 IO 没发生**（文件未改动、进程未启动、请求未发出）。

## Technical Context

**Language/Version**: Java 21（Maven 多模块，`maven.compiler.release=21`）

**Primary Dependencies**: Spring Boot 3.x（仅用 `@ConfigurationProperties` / `@EnableConfigurationProperties` 做配置绑定）；**不新增任何第三方依赖**——全部校验逻辑用 JDK 内置件完成（`java.nio.file.Path` / `Files`、`java.net.URI`）。动手前已核实：`URI.create(url).getHost()` 保留主机名原大小写（`https://API.Example.com:8443/x` → `API.Example.com`）、无方案输入（`not a url`）抛 `IllegalArgumentException`、无主机输入返回 `null`——三条行为共同决定域名校验的写法（见 research D2）。

**Storage**: 不适用。本节**不新增表、不新增落库调用**：被拦下的动作走既有的 `tool_invocations` 失败审计路径（第17节交付，本节零改动）。

**Testing**: JUnit 5 + AssertJ + Mockito（既有栈）。新增 `WhitelistSandboxTest`（三类校验各"允许 + 拒绝"成对 + 六个绕过与边界场景）；四个已接线工具各追加一条**用真白名单**的接线回归（不只用替身沙箱看调用顺序，还要看副作用）。集成冒烟打 `@Tag("integration")`，CI 跳过。

**Target Platform**: 服务端（开发机 Windows / 部署 Linux）；校验逻辑平台无关，只用 `Path` / `URI` 抽象。

**Project Type**: 多模块库（分布式 Agent 底座）——本节只碰 `fourfeetcat-tool` 与 `fourfeetcat-boot` 两个模块，不新建、不改名模块。

**Constraints**:
- `mvn clean verify` 全绿是完成的定义，含 Spotless(GJF) / PMD7(bestpractices+errorprone) / Checkstyle(google) / SpotBugs+FindSecBugs。
- 不使用 `SecurityManager`（JDK 21 已不可用）；隔离只由白名单校验实现。
- 全同步阻塞，不出现 Reactor / `CompletableFuture` / 自建线程池。
- 不引入 `ToolExecutor` 之外的执行路径；沙箱违规不得自建审计通路。
- 避开 P3C/ASM 解析不了的 Java 18+ 语法形态；本节实现的 `switch` 用**穷尽枚举的箭头式**且不写 `default`（PMD 的 `SwitchStmtsShouldHaveDefault` 在本版本已 deprecated 并指向 `NonExhaustiveSwitch`，穷尽时不报警——already 核实，见 research D7）。

**Scale/Scope**: 新增 4 个源文件、删除 1 个源文件、改 2 个既有文件（装配 + 配置）、新增 1 个测试类、改 4 个既有测试类；净增代码量在 200 行以内。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|------|------|------|
| 一：自实现 ReAct | 本节不碰循环；`ReActLoop` 零改动。沙箱校验发生在工具执行内部，循环对"工具为什么失败"无感知（照旧拿 `ToolResult.errorMessage`） | ✅ 通过 |
| 二：Spring AI 只用两件事 ⚠️ | 本节完全不碰 Spring AI：不新增工具、不新增 tool schema。四个工具的 `@Tool` 注解与管线一字不改 | ✅ 通过 |
| 三：Provider 显式映射 | 本节不碰 provider 路由 | ✅ 通过 |
| 四：一个目录 = 一个 Agent | 不引入 Agent 目录 / Skill 相关概念；`Profile` 既有字段不改（本节不新增 Profile 字段） | ✅ 通过 |
| 五：审计 Day One | **零新增审计代码**：`SandboxViolationException` 是普通 `RuntimeException`，被 `ToolExecutor` 既有 try/catch 接住，按普通工具失败落 `tool_invocations`（`success=false` + 可读原因）。**不得**为沙箱另造审计路径或数据表 | ✅ 通过 |
| 六：不使用 SecurityManager；软连接必须校验真实路径 | 不碰 `SecurityManager`。本节正是这条原则的落点：**路径校验按宪法严格版**——目标存在时用 `toRealPath()` 校验真实路径仍在白名单根内，新建路径校验最近存在父目录的真实路径（课件只写了 `normalize()+startsWith`，本节取更严的一档，见 research D1）。Skill 软连接校验不属本节（第29节） | ✅ 通过（强于课件口径，主公裁决） |
| 七：同步执行 | 全同步阻塞；无异步栈 | ✅ 通过 |
| 八：Tool 模块三合一 | 沙箱实现落在 `fourfeetcat-tool` 的 `sandbox` 包，与内置工具同址——这与课件"和三个工具挨在一起，不往 core 塞新概念"完全一致；工具模块仍是三合一，不拆新模块 | ✅ 通过 |
| 技术约束：依赖倒置 | `Sandbox` 契约与四个工具在 `fourfeetcat-tool`；装配在 `fourfeetcat-boot`（boot → tool 既有边）。本节**不新增跨模块契约**，不新增模块依赖边，无环 | ✅ 通过 |
| 技术约束：模块结构 | **不新建、不改名模块**；本节内容扩张与该模块既定职责完全一致，无需同步 `docs/TechnicalSolution.md` §10 的模块表 | ✅ 通过 |
| 技术约束：Flyway 双轨 | 本节**不新增迁移脚本**（不落库、不改表） | ✅ 通过 |
| 技术约束：配置与凭证 | 新增三个配置键（`file.allowed_paths` / `shell.allowed_commands` / `http.allowed_domains`），**无凭证**、不需要加密存储、不需要环境变量占位；默认值见 research D6 | ✅ 通过 |
| 技术约束：文档同步 | 本节**不改变**对外定位与特性表述：内置工具数不变（九个）、模块表不变、技能/渠道不变。README / 官网 `Home.vue` / `docs/` 三处**均无需改动**。唯一要写的说明是**沙箱配置项的空值语义**（空 = 什么都不允许）——落在 `application.yaml` 的注释里，属配置自说明，不是对外表述 | ✅ 通过 |
| 技术约束：中文注释惯例 | 新增类 / 方法均带中文 javadoc：写清"谁消费、什么口径、为什么这么设计（尤其宪法严格版的理由与两条裁决）" | ✅ 通过 |
| 技术约束：第三方 API 可得性 | 本节只用 JDK 内置 `java.nio.file` / `java.net.URI` 与已在本工程 classpath 上的 `spring-boot` 配置绑定注解；**无新增依赖**，无 API 可得性风险 | ✅ 通过 |

**Gate 结果：全部通过，无 Complexity Tracking 项属于"违规"**——下面记的是三处**有意的口径选择**，逐条给出理由与备选被否的原因。

## Project Structure

### Documentation (this feature)

```text
specs/007-sandbox-whitelist/
├── plan.md                      # 本文件
├── spec.md                      # 需求（含五条开工裁决）
├── research.md                  # Phase 0：D1~D10 技术裁决
├── data-model.md                # Phase 1：值对象/配置模型/校验规则
├── contracts/java-contracts.md  # Phase 1：契约与接线点（对外 + 内部）
├── quickstart.md                # Phase 1：可执行的验证指南
├── checklists/requirements.md   # spec 质量清单（specify 阶段产出）
└── tasks.md                     # Phase 2 产出（/speckit-tasks）
```

### Source Code (repository root)

```text
fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/sandbox/
├── Sandbox.java                  # 既有（第20节）：不变
├── SandboxAction.java            # 既有（第20节）：不变
├── ActionType.java               # 既有（第20节）：不变（四值，不新增 SMTP）
├── SandboxViolationException.java# 既有（第20节）：不变
├── FileSandboxProperties.java    # 新增：@ConfigurationProperties(prefix="file")
├── ShellSandboxProperties.java   # 新增：@ConfigurationProperties(prefix="shell")
├── HttpSandboxProperties.java    # 新增：@ConfigurationProperties(prefix="http")
├── WhitelistSandbox.java         # 新增：核心阶段唯一实现（三类校验 + 启动期配置校验）
└── PermissiveSandbox.java        # 删除：第20节的临时装配，由白名单实现承接同一装配位

fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/
├── sandbox/WhitelistSandboxTest.java   # 新增：harness 主体（三类校验成对 + 绕过场景）
├── builtin/FileToolsTest.java          # 追加：真白名单的接线回归（越界读/写被拦且文件未改动）
├── builtin/ShellToolsTest.java         # 追加：真白名单的接线回归（越界命令被拦且进程未启动）
├── builtin/HttpToolsTest.java          # 追加：真白名单的接线回归（越界域名被拦且请求未发出）
└── notify/NotifyToolsTest.java         # 追加：真白名单的接线回归（越界 webhook 被拦且未发送）

fourfeetcat-boot/src/main/
├── java/org/fourfeetcat/boot/AgentRuntimeConfiguration.java  # 改：装配位换成白名单实现 + 绑定三个配置件
└── resources/application.yaml                                # 改：三个白名单配置键 + 空值语义注释
```

**Structure Decision**：沿用既有 Maven 多模块布局，**不新建模块**。沙箱实现与内置工具同址（`fourfeetcat-tool/sandbox`），配置件与实现同类同包；装配位留在 `fourfeetcat-boot` 的 `AgentRuntimeConfiguration`（第20节宣告 `Sandbox` Bean 的地方——本节把它从临时装配换成白名单实现，属同一处的替换而非新增装配点）。**落位声明**：skill 落位表写"24 sandbox 包 → tool 模块"，本节完全照此执行，无偏离。

## Complexity Tracking

| 项 | 为什么需要 | 为什么不用更简单的做法 |
|---|-----------|----------------------|
| **路径校验取宪法严格版**（`toRealPath()` + 最近存在父目录） | 宪法原则六对文件白名单写的是 MUST：目标存在时必须校验真实路径仍位于白名单根内，新建路径校验最近存在父目录的真实路径。只看字符串前缀挡不住"白名单目录里放一个指向外面的软链"这类绕过——而这正是本节"绕不过去"这条验收线要覆盖的 | 严格照课件只写 `normalize().toAbsolutePath().startsWith(根)`：少 10 行，但软链绕过敞着，且与宪法 MUST 条款冲突。课件的穿越用例在严格版下同样被拦（更严的方案是原方案的超集），故取严格版不牺牲任何课件守点。经主公确认 |
| **域名只比主机名、忽略端口与路径、大小写敏感** | 白名单的名字是"域名"，端口与路径不属其管辖；大小写敏感与命令白名单同口径（配什么写什么，不做归一），避免"配了 `example.com` 却放行了 `EXAMPLE.COM`"这种口径漂移 | 大小写不敏感更贴 RFC 的 DNS 语义，但会让白名单的比对口径与命令白名单不一致（两处一个归一一个不归一），且无法用"配什么写什么"一句话讲清。经主公裁决取大小写敏感 |
| **白名单项写成相对路径即启动拒绝** | 相对路径按当前工作目录解析，而同一份配置在 CLI、守护进程、容器三处的工作目录各不相同——安全配置的语义一旦随环境变形，"本机测着能跑、上线白名单指向别处"就成事故 | 按当前工作目录静默解析：省掉 5 行启动校验，但把一处确定性问题推迟到运行时才暴露。项目既有口径本就是"配置不静默失败"（`ConfigLoader` 缺项/非法即报错），取启动拒绝。经主公确认 |
| **SMTP 端点白名单不在本节**（登记为文档-代码差） | 课件本节只交付文件 / 命令 / 域名三类与三个配置键；`ActionType` 四值由第20节定死，本节不得改其字面量；而 `smtp.allowed_endpoints` 的消费方（邮件通知实现）在 `docs/TechnicalSolution.md` §6.8 标注为扩展阶段、代码库中尚不存在 | 为凑齐技术方案 §6.7 的四个校验而给 `ActionType` 加第五个取值：属于修改前序节已定字面量，且加出来的校验没有任何调用点（邮件通知还不存在），等于给未来写用不上的代码。经主公裁决本节不做，差在验收报告里显式登记 |
| **删除 `PermissiveSandbox`** | 它自己的类注释已声明"由沙箱节替换"；本节交付白名单实现后它成为无人引用的临时装配，留着就是一个"什么都不拦"的沙箱在库里等着被误装配 | 保留类、只改装配：多一个悬空类 + 一次"这玩意儿还在"的误读风险。删除是本节的原定动作（第20节 tasks 与注释均如此登记） |

## Phase 0 摘要

`research.md` 收敛 D1~D10 十项技术裁决，全部无 NEEDS CLARIFICATION 残留，其中三处来自主公裁决（严格路径校验 / 域名口径 / 相对项启动拒绝），两处来自本地第三方行为实测（`URI.getHost()` 保留大小写与解析异常、PMD `SwitchStmtsShouldHaveDefault` 已 deprecated）。核心结论：

- **D1** 严格路径校验算法：`realPath(path) = 目标存在 ? toRealPath() : 最近存在祖先的 toRealPath() + 剩余段`；白名单根在构造器里预算成同样的真实形态；解析失败一律**拒绝**（fail-closed）。
- **D2** 域名：`URI.create(url).getHost()`；解析异常或无主机 → 拒绝；只比主机名、大小写敏感；通配项匹配用 `.example.com` 带点前缀（`pattern.startsWith("*.") ? host.endsWith(pattern.substring(1)) : host.equals(pattern)`）。
- **D3** 命令：首 token（`trim()` 后按空白切分）与白名单 `Set` 精确比对；argv 直传不变。
- **D4** 装配：替换 `AgentRuntimeConfiguration.sandbox()` Bean 的返回值，并在该类加 `@EnableConfigurationProperties`；不给实现类加 `@Component`（避免与显式装配重复）。
- **D6** 默认值：`file.allowed_paths: ${FOURFEETCAT_ROOT:${user.dir}/.fourfeetcat}`（**必须是绝对路径**，否则触发 D3 的启动拒绝），`shell.allowed_commands: []`、`http.allowed_domains: []`（空 = 全拒）。
- **D8** 失败处置统一 fail-closed：无法判定即拒绝，绝不"解析不了就当放行"。

## Phase 1 摘要

- `data-model.md`：三个配置件 + `ActionType` / `SandboxAction`（既有）+ 实现类内部三份白名单的形态与校验规则表；不涉及持久化实体（本节零落库）。
- `contracts/java-contracts.md`：对外契约（`Sandbox.enforce` 语义、异常语义、四个接线点的调用位置与副作用证明方式）+ 三个配置件的绑定契约（键名、类型、空值语义、非法值行为）。
- `quickstart.md`：可复制的验证命令（只跑本节测试 / 全量门禁）+ 三条人工验收路径（真实链路集成、接口中立性自查、配置边界）。
