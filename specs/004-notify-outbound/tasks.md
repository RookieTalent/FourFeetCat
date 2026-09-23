---
description: "Task list for Notify 出站通知 (Lesson 19) implementation"
---

# Tasks: Notify 出站通知（第19节）

**Input**: Design documents from `/specs/004-notify-outbound/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/java-contracts.md, quickstart.md

**Tests**: harness 承载验收（spec SC-005）——测试任务先于或伴随对应实现任务，课件关键回归断言逐条保真、方法名英文 + `@DisplayName` 保留课件原文。

**Organization**: 按 User Story 分组（spec：US1 结果主动送出 P1、US2 换渠道不改代码 P1、US3 渠道全局按名引用 P2、US4 对话里直推 P3-**跨节**）。

**范围说明**：课件 L40 自述「NotifyTools 的完整接线有三个依赖在后面的课（@Tool/ToolResult 在 20 节、Sandbox.enforce 在 23/24 节）……其完成时点在 24 节之后」——T015 为**跨节登记**任务，**本节不实现、不勾选**。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- 含确切文件路径；路径以 `fourfeetcat-<module>/src/...` 表达

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖与基线就位（本节零新增第三方依赖，只有框架内 BOM 托管库的声明）

- [x] T001 H3 硬门禁：`mvn -o dependency:tree -pl fourfeetcat-tool -am` 确认 `org.springframework:spring-web` 与 `spring-boot` 在锁定 BOM 内解析成功（实测解析为 `spring-web 6.2.19`）；核不到立即停下软报，不得换依赖自行发挥
- [x] T002 基线核对：`mvn test` 确认第16/17/18节全部测试绿（作为本节起点的回归基线）
- [x] T003 [P] `fourfeetcat-tool/pom.xml`：加 `org.springframework:spring-web`、`org.springframework.boot:spring-boot`（`@Component` 所需的 spring-context，与 `fourfeetcat-cli` 同款声明）、`org.springframework.boot:spring-boot-starter-test`（test 作用域）——**版本全由父 pom / Boot BOM 托管，不写 version**

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 出站通知的两个契约（US1/US2/US3 共用）

**⚠️ CRITICAL**: 本阶段完成前，webhook 实现无处可落

- [x] T004 [P] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/notify/NotifyChannelAdapter.java`：接口，唯一方法 `void send(NotifyTarget target, String content)`；**签名与其参数类型不得出现任何具体渠道名或某一档实现特有的词**（FR-001）
- [x] T005 [P] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/notify/NotifyTarget.java`：`record NotifyTarget(String channelType, Map<String, String> config)`；只承载"渠道类型 + 一份配置"，配置里是地址还是别的认证信息由实现解释（FR-002）

**Checkpoint**: 契约就位，实现与渠道表可并行

---

## Phase 3: User Story 1 - 到点自动跑完的 Agent 能把结果主动送出去 (Priority: P1) 🎯 MVP

**Goal**: 通用 webhook 实现：从目标配置取地址、POST 一条带内容的 JSON；失败上抛不静默

**Independent Test**: `mvn test -pl fourfeetcat-tool -am -Dtest=WebhookNotifyAdapterTest`（本地假接收端，不碰外网）

### Tests (harness 先行)

- [x] T006 [US1] `fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/notify/WebhookNotifyAdapterTest.java`：课件 harness 第一批逐条落地——① 发送后假接收端收到的 POST body 带所传内容；② **地址取自通知目标配置而非硬编码**（换配置即换地址）；③ 接收端返 5xx 时异常上抛、不静默吞；④ 目标配置缺地址时发送前就报错（spec Edge Cases）。假接收端写成**测试类的私有静态嵌套类**（`com.sun.net.httpserver.HttpServer`，不新增文件、不进对外概念面）

### Implementation

- [x] T007 [US1] `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/notify/WebhookNotifyAdapter.java`：`@Component`，构造注入 `RestClient`；`send` 取 `config.get("url")`（缺失即抛 `IllegalArgumentException`），以 `application/json` POST `{"content": ...}`；失败不包装（`RestClient` 默认抛异常，research.md D7）；**类注释写明白名单校验归属调用链上游（工具层），第24节接线**（research.md D6）
- [x] T008 [US1] 容器接线两处（`/speckit-analyze` 发现 `fourfeetcat-tool` 此前**没有任何模块依赖它**）：① `fourfeetcat-boot/pom.xml` 加 `fourfeetcat-tool` 依赖——不接则新类既进不了容器（`@Component` 成死注解）也进不了 fat jar；与既有 web/cli/provider/storage 并列，无环；② `fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/AgentRuntimeConfiguration.java` 补 `@Bean RestClient restClient(RestClient.Builder builder)`——用 Boot 自动配置的 Builder 造 Bean，`WebhookNotifyAdapter` 才能被实例化（不补则启动期报"找不到 RestClient 类型的 Bean"，连带打断既有上下文测试）。24 节接工具时零改动

**Checkpoint**: US1 独立可测——推送协议被测试钉死

---

## Phase 4: User Story 2 - 换渠道不改 Agent、不改通知代码 (Priority: P1)

**Goal**: 接口中立性——签名里没有某一档实现的词；换一档实现，调用方零改动

**Independent Test**: 测试里用"另一档实现"的替身替换 webhook 实现，同一段调用代码无需改动即可跑通（编译通过即证明）

### Tests

- [x] T009 [US2] `WebhookNotifyAdapterTest`（同文件）补中立性守卫：用一个内联的 `NotifyChannelAdapter` 替身（模拟另一档渠道实现）替换 webhook 实现，证明**调用方代码零改动**即可切换；并逐条断言签名中立（接口只暴露 `send(NotifyTarget, String)`）

**Checkpoint**: 接口一旦带上一档实现的词汇就会被这个测试挡住

---

## Phase 5: User Story 3 - 渠道是全局配置，Agent 只按名引用 (Priority: P2)

**Goal**: `notify_channels` 表（双轨）+ 实体 + 仓储，按名可读；Agent 侧只有名字

**Independent Test**: `mvn test -pl fourfeetcat-storage -am -Dtest=NotifyChannelRepositoryTest`

### Implementation

- [x] T010 [P] [US3] `fourfeetcat-storage/src/main/resources/db/migration/sqlite/V19__notify_channels.sql`：`notify_channels` 表，逐字摘自 `docs/class/schema.sql` 的该段（name 主键 / type NOT NULL / url / description / config）
- [x] T011 [P] [US3] `fourfeetcat-storage/src/main/resources/db/migration/postgresql/V19__notify_channels.sql`：同版本号、同列名同约束、方言各自正确
- [x] T012 [P] [US3] `fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/NotifyChannel.java`：JPA 实体，列名与 V19 逐字一致；`@Id String name`（**不用** `@GeneratedValue`——名字由运营方指定）
- [x] T013 [P] [US3] `fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/NotifyChannelRepository.java`：`JpaRepository<NotifyChannel, String>`（本节只需父接口能力，"按名解析"的封装归第24节）
- [x] T014 [US3] `fourfeetcat-storage/src/test/java/org/fourfeetcat/storage/NotifyChannelRepositoryTest.java`：手工建表脚本建出的渠道表能存能读、字段往返一致（含可空列）；跑 `mvn test -pl fourfeetcat-storage` 转绿

**Checkpoint**: 渠道注册表可读，为 24 节的按名解析备好数据面

---

## Phase 6: User Story 4 - 在对话里直接让 Agent 推一条（**跨节，本节不实现**）

**Goal**: 登记通知工具的完整接线任务，明确其完成时点在沙箱节之后

- [ ] T015 [US4] **跨节登记（本节不实现、验收时不勾选）**：`NotifyTools`（`fourfeetcat-tool`，内置 `notify(content, channel)`）+ `NotifyToolsTest`（课件 harness 第二批：渠道未配置 → 明确报错；渠道名缺省 → 取第一个；`enforce` 先于 `send`）。**依赖**：第20节的工具注册机制与工具结果类型、第23/24节的 `Sandbox.enforce`；另需先定"按名解析渠道"的落点（core 端口 + storage 实现，research.md D8）。课件 L40 已明示其完成时点在 24 节之后，27/28 节串联时做全量验证

**Checkpoint**: 任务有据可查，不会被误当成"本节漏做"

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T016 `mvn clean verify` 全绿（Spotless/GJF + PMD7 + Checkstyle(google_checks) + SpotBugs+FindSecBugs 四道门禁一起过，SpotBugs findings 应为 0）
- [x] T017 前序节回归：`fourfeetcat-core` / `provider` / `storage` / `cli` / `channel-cli` / `boot` 既有全部测试绿（跨节契约证据；特别确认 boot 上下文测试没被新 Bean 影响）
- [x] T018 H4 六条全局不变量逐条自查（①本节唯一涉外 IO 是 webhook 发送，**校验归属调用链上游**、已在类注释记录，24 节接线——见 research.md D6；②审计走既有工具执行路径、本节不新增；③无明文凭证；④`session_id` 拼接未被本节触碰；⑤无 Reactor/`CompletableFuture`/自建线程池；⑥无 Spring AI 自动工具执行路径——本节不碰 `@Tool`）
- [x] T019 交付物存在性核对（三个 notify 类、两个 storage 类、双轨 V19 脚本、两个测试类）+ **继承性条款对号**（spec SC-006「Agent 配置里只有渠道名」与 FR-007「凭证占位」由第16节交付的字段类型与"本节无写入端"共同保证，报告里显式引用，不改代码）+ 人工项清单 + 变更总结

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖
- **Foundational (Phase 2)**: 依赖 Setup（T003 的 pom 声明）；**阻塞 US1**
- **US1 (Phase 3)**: 依赖 Phase 2；T008 需先有 T007（`@Component` 要有 Bean 才起得来）
- **US2 (Phase 4)**: 依赖 US1（在同一个测试文件里补守卫）
- **US3 (Phase 5)**: 只依赖 Setup——与 US1/US2 完全并行（不同模块、不同文件）
- **US4 (Phase 6)**: 无需实现，仅登记
- **Polish (Phase 7)**: 依赖 US1~US3

### Within Each Story

- 测试（harness）先写、先红，再补实现转绿
- 契约 → 实现 → 端到端校验

### Parallel Opportunities

- Phase 1：T003 与 T001/T002 可并行推进（T001 需先于动手）
- Phase 2：T004/T005 两个独立文件（[P]）
- Phase 5：T010~T013 四个独立文件（[P]）；T014 在它们之后
- **跨阶段**：Phase 5（storage 三件套）与 Phase 3/4（tool 三件套）互不依赖，可并行

---

## Parallel Example: Phase 5

```bash
Task: "sqlite V19__notify_channels.sql（T010）"
Task: "postgresql V19__notify_channels.sql（T011）"
Task: "storage NotifyChannel.java（T012）"
Task: "storage NotifyChannelRepository.java（T013）"
# 随后 T014 写仓储测试并跑绿
```

---

## Implementation Strategy

### MVP First（US1）

1. Phase 1 Setup → 2. Phase 2 契约 → 3. Phase 3 US1（harness 先行）→ **停下验证**：`WebhookNotifyAdapterTest` 全绿 → 4. 这就是本节的核心价值：结果有出口了

### Incremental Delivery

1. Setup + 契约 → 地基就位
2. US1 → 推送协议被钉死（自动判卷）
3. US2 → 中立性有机器守卫
4. US3 → 渠道注册表数据面就位
5. Polish → 四道静态门禁 + 回归 + 报告

---

## Notes

- [P] = 不同文件、无未完成依赖
- 课件中文测试方法名/描述一律译成语义等价的英文名落地，课件原文进 `@DisplayName` 保留对号
- 交付物**多出项**（均经主公裁决或有文档依据，须在 T019 报告中声明）：`notify_channels` 表三件套（双轨脚本 + 实体 + 仓储 + 仓储测试）——依据课程 `schema.sql`「19→notify_channels」与技术方案 §6.8；boot 的 `RestClient` Bean——让 `@Component` 能落地的必要接线；US2 的中立性守卫测试——把"接口先行"从人工自查变成机器守卫
- 交付物**按裁决改形**（非缺项）：课件的「Profile 新增 `notify_channels` 字段（type + url）」改为**全局注册表形态**（Profile 字段第16节已按技术方案交付为名字清单，本节不改其类型与语义）
- **跨节、本节不做完**：`NotifyTools` + `NotifyToolsTest`（T015 仅登记）
