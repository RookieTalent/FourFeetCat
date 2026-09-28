---

description: "Task list for Sandbox 白名单校验（第24节）implementation"

---

# Tasks: Sandbox 白名单校验（第24节）

**Input**: Design documents from `/specs/007-sandbox-whitelist/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/java-contracts.md, quickstart.md

**Tests**: harness 承载验收（spec SC-001~SC-003）——测试任务先于或伴随对应实现任务；课件关键回归断言逐条保真、方法名英文 + `@DisplayName` 保留课件中文原文。

**Organization**: 按 User Story 分组（spec：US1 越界当场被拦 P1、US2 审计同路 P1、US3 接口中立 P2）。

**范围说明（主公裁决，开工前五处）**：
- **路径校验取宪法严格版**（`toRealPath()` + 最近存在父目录），课件只写 `normalize()+startsWith`；严格版是课件版的超集，课件穿越用例逐字照过（research D1）。
- **域名只比主机名、忽略端口与路径、大小写敏感**（research D2，主公裁决 B）。
- **白名单项写成相对路径 → 启动即拒**（research D3，主公确认），校验点落在实现类构造器，不新增配置校验类。
- **SMTP 端点白名单本节不做**：`ActionType` 四值由第20节定死，本节不改字面量；差在验收报告显式登记（research "已知文档-代码差"、T023）。
- **删除第20节的临时装配** `PermissiveSandbox`：其 javadoc 已自述"由沙箱节替换"，删除是第20节登记好的动作（T010）。

**第20节已交付、本节只核对不改的件**（不得出现在本节 diff 里）：`Sandbox` / `SandboxAction` / `ActionType` / `SandboxViolationException` 四个契约件、四个工具的 `sandbox.enforce(...)` 调用位、`ToolExecutor` 的失败审计路径。

**参考实现可比对**（实现期逐行对齐语义、按本仓命名改写）：`/d/oneByDay/ai_code/oryxos` 的提交 `aa781d28` 中 `oryxos-tool/src/main/java/io/oryxos/tool/sandbox/`（`Sandbox` / `SandboxAction` / `ActionType` / `SandboxViolationException` / `WhitelistSandbox` / 三个 `*SandboxProperties`）与三个工具类的 `enforce` 接线——**注意**：参考实现的路径校验只做 `normalize+startsWith` 且域名匹配不带点号边界，本仓按 research D1/D2 取严格版与带边界版，不逐字照搬。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- 含确切文件路径；路径以 `fourfeetcat-<module>/src/...` 表达

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖可得性与回归基线

- [x] T001 H3 硬门禁：核实本节要用的件在锁定依赖里可达——`java.net.URI`/`java.nio.file`（JDK 21 内置，`URI.getHost()` 保留大小写、无方案输入抛 `IllegalArgumentException`、无主机返回 `null` 三条行为已在 research D2 实测）、`org.springframework.boot:spring-boot` 提供 `@ConfigurationProperties`/`@EnableConfigurationProperties`（与 `fourfeetcat-provider` 同款用法，核 `fourfeetcat-tool` 的依赖里 `spring-boot` 可达即可）；核不到立即停下软报，不得换依赖自行发挥
- [x] T002 基线核对：`mvn clean test` 确认第16~22节全部测试绿（作为本节起点的回归基线）
- [x] T003 [P] 核对第20节已就位的四处接线（`FileTools` 三处 / `ShellTools` 一处 / `HttpTools` 两处 / `NotifyTools` 一处，均在方法体第一行）与四个契约件——**只核对位置与存在，不改一行**

**Checkpoint**: 依赖解析通过；基线全绿；接线位一览表确认（本节不产生对这些文件的 diff）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 三个配置件与配置键——US1 的实现与 US2 的装配都建在这上面

**⚠️ CRITICAL**: 本阶段完成前，`WhitelistSandbox` 无配置可读

- [x] T004 [P] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/sandbox/FileSandboxProperties.java`：`@ConfigurationProperties(prefix = "file")` 的 record `FileSandboxProperties(List<String> allowedPaths)`；紧凑构造器把 null 兜成 `List.of()`（D10，与既有 `ProviderProperties` 同构）；javadoc 写明"空 = 什么都不允许""项必须是绝对路径，否则启动即拒（D3）"
- [x] T005 [P] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/sandbox/ShellSandboxProperties.java`：前缀 `shell`、组件 `allowedCommands`；javadoc 写明"可执行文件名**精确比对**（大小写敏感）；列入解释器 = 管理员显式授予本机代码执行权限，不构成隔离"
- [x] T006 [P] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/sandbox/HttpSandboxProperties.java`：前缀 `http`、组件 `allowedDomains`；javadoc 写明"只比主机名（忽略端口与路径）、大小写敏感；`*.x.com` 匹配带点号边界、不命中裸域"
- [x] T007 [P] `fourfeetcat-boot/src/main/resources/application.yaml`：加 `file.allowed_paths: ${FOURFEETCAT_ROOT:${user.dir}/.fourfeetcat}`、`shell.allowed_commands: []`、`http.allowed_domains: []` 三键 + 注释（空值语义 / 绝对路径要求 / 命令精确比对与解释器警告 / 域名口径 / "劝阻级非强隔离"五条，contracts 第四节）
  > 键名一律下划线写法（`allowed_paths`），与课件、宪法原则六、`CLAUDE.md` 逐字一致；Spring 的宽松绑定按 `allowedPaths` 接收（analyze C1）
- [x] T025 [P] `fourfeetcat-boot/src/test/java/org/fourfeetcat/boot/FourFeetCatApplicationTests.java`：测试属性由 `FOURFEETCAT_ROOT=target`（相对）改为 `FOURFEETCAT_ROOT=${user.dir}/target`（绝对）——analyze D1 裁决：白名单默认引用该变量，相对值会被 FR-013 拦下，既有上下文加载测试将被迫变红；**跨节触碰，登记进变更总结**

**Checkpoint**: 三份配置可被 Spring 绑定；缺省键不 NPE（空列表 = 全拒）；boot 上下文加载测试在绝对工作区根下继续绿

---

## Phase 3: User Story 1 - 越界动作当场被拦 (Priority: P1) 🎯 MVP

**Goal**: `WhitelistSandbox` 把三类校验落地，越界即抛、被拦原因点名越界物；装配位从临时装配换成白名单实现

**Independent Test**: `mvn test -pl fourfeetcat-tool -Dtest=WhitelistSandboxTest`

### Tests（harness 先行）

- [x] T008 [US1] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/sandbox/WhitelistSandboxTest.java`：三类校验各"允许 + 拒绝"成对 + 六个边界场景——① `relativeTraversal_isBlocked`（`@DisplayName("相对路径穿越必须被拦")`）② `symlinkEscapingRoot_isBlocked`（宪法严格版追加：`@TempDir` 里造指向白名单外的软链）③ `wildcardDomain_looksAlikeIsNotMatched`（`@DisplayName("通配符域名_不能被形似域名绕过")`：命中 `api.example.com`，拦下 `evil-example.com` 与裸域 `example.com`）④ `domainComparison_ignoresPortAndPath_butIsCaseSensitive`（`https://api.example.com:8443/x` 放行、`API.Example.com` 拦下）⑤ `emptyWhitelists_denyEverything`（三份全空 = 全拒）⑥ `relativeWhitelistEntry_failsAtConstruction`（构造即抛且报错点名该项）
  > 与 T009 同批落地（编译依赖，同第22节 T015 的处理）

### Implementation for User Story 1

- [x] T009 [US1] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/sandbox/WhitelistSandbox.java`：`implements Sandbox`，构造收三个配置件；路径白名单项**非绝对路径即抛 `IllegalStateException` 点名该项**（D3）；三份白名单在构造期固化成不可变形态（`List`/`Set.copyOf`）；`enforce` 用**穷尽枚举箭头式 switch**（无 `default`，D7）路由三类；三个校验方法**全部 private**；`realPath` 私有助手按 D1 三落点实现（存在 → `toRealPath()`；不存在 → 最近存在祖先的真实路径 + 剩余段；IO 异常 → 拒绝，fail-closed），白名单根同样经它归一并在构造期算一次（`ponytail:` 注释点明"根的真实形态缓存一次，运行期不重算"这个上限与升级路径）
- [x] T010 [US1] 装配替换：`fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/AgentRuntimeConfiguration.java` 的 `sandbox()` Bean 改为构造 `WhitelistSandbox`（收三个配置件），类上加 `@EnableConfigurationProperties({FileSandboxProperties.class, ShellSandboxProperties.class, HttpSandboxProperties.class})`；删除 import 与 `PermissiveSandbox.java` 文件；类注释更新为"第24节起挂白名单实现，替换第20节的临时装配"
- [x] T011 [US1] 模块级跑通：`mvn test -pl fourfeetcat-tool -am` 全绿（T008/T009 的验证点 + 既有工具测试未被打断）

**Checkpoint**: 三类校验可独立验证；越界动作抛异常且原因可读；容器里装配的是白名单实现（临时装配已删）

---

## Phase 4: User Story 2 - 被拦下的动作走同一条审计路径 (Priority: P1)

**Goal**: 四个已接线工具在白名单真实生效后，越界动作**真正的 IO 没发生**，且失败走既有审计路径

**Independent Test**: `mvn test -pl fourfeetcat-tool -Dtest='FileToolsTest+ShellToolsTest+HttpToolsTest+NotifyToolsTest'`

### Tests for User Story 2

- [x] T012 [P] [US2] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/builtin/FileToolsTest.java` 追加：用**真** `WhitelistSandbox`（白名单只含 `@TempDir`）越界写白名单外的文件 → 抛 `SandboxViolationException` 且**目标文件内容逐字未变**；`@DisplayName` 保留"越界会被拦_且目标文件一个字节都没被改动"语义
- [x] T013 [P] [US2] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/builtin/ShellToolsTest.java` 追加：真白名单只放行一个无关命令，请求一条**若执行会创建标记文件**的命令（按 OS 选 `sh -c` / `cmd /c`）→ 抛异常且**标记文件不存在**（进程没起来）
- [x] T014 [P] [US2] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/builtin/HttpToolsTest.java` 追加：真白名单不含本地 stub 主机 → 抛异常且**stub 服务端一次请求都没收到**（复用既有 `StubServer` 的请求计数）
- [x] T015 [P] [US2] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/notify/NotifyToolsTest.java` 追加：真白名单下渠道 webhook 域名不在名单内 → 抛异常且**发送端替身一次都没被调用**（`verify(adapter, never()).send(...)`）
- [x] T016 [US2] 审计路径零新增核对：`git diff` 确认 `fourfeetcat-core/src/main/java/org/fourfeetcat/core/react/ToolExecutor.java` 与 `ToolInvocationRecorder` 类零改动；grep 确认本节没有新增任何落库调用 / 数据表（宪法原则五）

**Checkpoint**: 四个工具各自证明"拦得住 + 副作用没发生 + 零新增审计代码"

---

## Phase 5: User Story 3 - 换更重的隔离只新增实现 (Priority: P2)

**Goal**: 对外契约保持中立，升级到容器 / microVM 只需新增实现类

**Independent Test**: 契约文件零 diff + 人工推演（quickstart §3）

- [x] T017 [US3] 接口中立性自查与证据固化：`git diff` 证明 `Sandbox.java` / `SandboxAction.java` / `ActionType.java` / `SandboxViolationException.java` 四个契约件**零改动**（尤其 `ActionType` 仍是四值）；通读 `WhitelistSandbox` 的对外可见成员，确认只有 `enforce(SandboxAction)`；grep 全仓 `SecurityManager` 零命中（FR-011）；把"`KataMicroVmSandbox implements Sandbox` 需要新增方法数 = 0"的推演结论写进验收报告（quickstart §3）

**Checkpoint**: 第23节那道墙在代码上可证中立

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T018 [P] 配置说明完整性核对：`application.yaml` 注释覆盖 contracts 第四节五条（空值语义 / 绝对路径 / 命令精确比对与解释器警告 / 域名口径 / 劝阻级非强隔离）
- [x] T019 全量门禁：`mvn clean verify` 全绿（Spotless / PMD7 / Checkstyle / SpotBugs+FindSecBugs），贴关键输出
- [x] T020 前序节回归：全模块测试绿（跨节契约证据；第20节"Agent 真的能动手"在工作区内仍成立——文件白名单默认含工作区根）
- [x] T021 H4 六条全局不变量逐条自查（涉外 IO 首行过校验 / 审计双写 / 无明文 key / `session_id` 只在 `SessionManager` 拼接 / 无异步栈 / 无 Spring AI 自动工具执行路径）
- [x] T022 课件"本节交付物"逐项存在性核对（用 `ls`/`grep` 输出做证据）
- [x] T023 登记已知文档-代码差：SMTP 端点白名单不在本节（`ActionType` 四值不动、消费方不存在），写进验收报告并指向 `docs/TechnicalSolution.md` §6.8；**不静默、不改文档口径**
- [x] T024 验收报告 `specs/007-sandbox-whitelist/acceptance-report.md`：六项证据 DoD + 课件"做完怎么验"剩余人工项清单（真实链路集成 / 接口中立性 / 配置边界）+ 变更总结（改动点 / 重点 review 清单 / 如何验证）

---

## 课件"本节交付物"↔ 任务映射（固定停点比对表）

| 课件交付物 | 状态 | 承载任务 |
|-----------|------|---------|
| `Sandbox` 接口 | 第20节已交付 | T003（核对不改）、T017（零 diff 证据） |
| `SandboxAction` | 第20节已交付 | T003、T017 |
| `ActionType` | 第20节已交付 | T003、T017（四值不变，不加 SMTP） |
| `SandboxViolationException` | 第20节已交付 | T003、T017 |
| `WhitelistSandbox` | **本节交付** | T009 |
| 三个 `@ConfigurationProperties`（file / shell / http） | **本节交付** | T004、T005、T006 |
| `WhitelistSandboxTest` | **本节交付** | T008 |
| 四个 Tool 的拦截回归用例 | **本节交付**（在既有测试类上追加） | T012~T015 |
| `application.yaml` 三键 + 配置说明 | **本节交付** | T007、T018 |
| 四个 Tool 执行首行加 `sandbox.enforce(...)`（改造点） | 第20节已就位，**本节零改动** | T003（核对位置）、T012~T015（用真白名单证明生效） |
| 临时装配 `PermissiveSandbox` 的替换/删除 | 本节动作（第20节登记） | T010 |

**差**：
- **多**（课件清单外，经主公裁决/登记）：无新增概念类型。严格版 `realPath` 软链校验（T008 用例②、T009 实现）属宪法原则六的 MUST，课件只写到 `normalize+startsWith` —— 是加强不是新增交付物；`WhitelistSandboxTest` 第④⑤⑥条同理属 spec 加严项。
- **缺 / 未做**（有据）：SMTP 端点白名单（T023 登记，随邮件通知实现落地）；容器 / microVM 实现（第23节明写归扩展阶段，本节边界内不做）。

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始
- **Foundational (Phase 2)**: 依赖 Setup；**阻塞** US1（实现类要读配置件）
- **US1 (Phase 3)**: 依赖 Phase 2；产出的实现是 US2 的测试前提
- **US2 (Phase 4)**: 依赖 US1（真白名单实例）
- **US3 (Phase 5)**: 依赖 US1（契约零 diff 需要实现落地后才可证）
- **Polish (Phase 6)**: 依赖全部

### Within Each User Story

- 测试与实现**同批落地**（T008+T009；编译相互依赖，同第22节 T015 的先例），落地后立刻跑模块测试，红了当场修
- US2 的四条追加用例分属四个不同文件 → 可并行
- US3 是核对 / 推演性质，无代码产出

### Parallel Opportunities

```bash
# Phase 2 三个配置件 + yaml 可并行（四个不同文件）
Task: "T004 FileSandboxProperties"
Task: "T005 ShellSandboxProperties"
Task: "T006 HttpSandboxProperties"
Task: "T007 application.yaml 三键与注释"

# US2 四条接线回归可并行（四个不同测试文件）
Task: "T012 FileToolsTest 越界写"
Task: "T013 ShellToolsTest 越界命令"
Task: "T014 HttpToolsTest 越界域名"
Task: "T015 NotifyToolsTest 越界 webhook"
```

---

## Implementation Strategy

### MVP First

Phase 1 → Phase 2 → Phase 3（US1）：三类校验 + 装配替换完成，越界动作当场被拦 —— 这就是本节的 MVP（也是课件的核心验收）。**STOP and VALIDATE**：`mvn test -pl fourfeetcat-tool -Dtest=WhitelistSandboxTest`。

### Incremental Delivery

1. Phase 2 → 配置就位
2. US1 → 校验本体 + 装配替换 → 独立验证
3. US2 → 四个工具的"拦得住且副作用没发生"证据 → 独立验证
4. US3 → 契约中立性证据
5. Phase 6 → 全量门禁 + 六项证据验收报告

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖
- 本节**不新增**第三方依赖、**不新增**数据表、**不新增**跨模块契约、**不新增**模块
- 未全绿不得宣称完成；不许删断言 / `@Disabled` / 放宽阈值
- 不自动 commit / push / 跑 `package.sh`
