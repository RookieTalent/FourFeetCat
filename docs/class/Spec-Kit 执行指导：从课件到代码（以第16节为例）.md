<!-- 本文是 four-feet-cat-lesson-dev skill 的流程原理：讲从课件到代码为什么要过 Spec-Kit、每一步在干什么。
     skill 是本文的可执行版本，条款冲突以 SKILL.md 为准。 -->

# Spec-Kit 执行指导：从课件到代码（以第16节为例）

课件是给人"教"的，不是给 AI"执行"的。这份文档讲清楚一件事：一节课的课件，怎么经过 Spec-Kit 变成可靠的代码。

## 一、为什么中间要过 Spec-Kit

课件有三个特点，决定了它不能直接当实现指令用：

1. **讲原理**：课件花大量篇幅讲"为什么这么设计"（三个坑、职责边界），这些是理解材料，不是可执行规格；
2. **示意代码**：课件里的 Java 代码是教学示意（骨架、伪 API），照抄进工程必然跑不通，还容易把示意当成契约；
3. **颗粒度不齐**：一段闲聊式的背景和一段必须逐字保真的 YAML 混在一起，AI 分不清哪句是硬要求。

直接把课件丢给实现，结果就是漂移：AI 写出一个"看起来像课件说的"系统，细节处处走样。Spec-Kit 的作用是把课件**翻译成一串可校验的中间制品**，每个制品回答一个问题：

| 制品 | 回答的问题 | 对应课件的部分 |
|------|-----------|---------------|
| spec.md | **要做什么（WHAT/WHY）**，可测量的验收标准 | 一、二部分（是什么、想清楚） |
| plan.md | **怎么落**：技术栈、模块落位、测试策略 | 技术方案 + 课件交付物 |
| tasks.md | **按什么顺序做**：带 DoD 的任务序列 | 交付物清单 + harness |

制品齐了，实现（`/speckit-implement`）才有判卷依据——这也正是《Harness 设计》那篇讲的边界从哪来。

## 二、流程总览

```text
课件一、二部分（是什么 / 想清楚 / 先别做）
  → /speckit-specify   产出 spec.md（只写 WHAT/WHY，不带类名）
  → /speckit-clarify   消歧：答案权威 = 课件 → 技术方案 → 都没有则停下问人
  → /speckit-plan      固定技术栈句 + 模块落位 + 测试策略句 + 语法禁区
  → /speckit-tasks     任务清单 ↔ 课件交付物逐项比对 → 【固定软停点：等用户确认】
  → /speckit-implement 逐任务执行，写前/写中/写后三级门禁
  → 节级验收           六项证据 DoD + 变更总结
```

四类课型，不是每节都走全程：**代码课**（如 16~20 节）走上面全程；**评审课**（21、23 节）不产码，产出是下一节的 specify 素材；**串联课**（27、28 节）不开新 feature，只对账并把对账固化成集成测试；**Demo 课**（31 节）按课件定义两个示例 Agent 做调试与发布。下文以第 16 节（Provider）为代码课的样板走全程。

## 三、逐步走第 16 节

### specify：从课件提炼 WHAT，不带 HOW

`/speckit-specify` 的输入从课件**一、二部分**提炼，铁律是只写 WHAT/WHY、不带类名和技术栈——类名和落位是 plan 的事，spec 里混进 HOW，后面就没法判"设计变了没"。

第 16 节"想清楚"部分的坑和要点，逐条翻成可测的 FR：

| 课件原话 | 提炼成的 FR |
|----------|------------|
| 坑一：多 provider 靠类型扫描分不清 | FR：多 provider 并存时按 Profile 声明的名字路由，不串台；引用未知名的 provider 必须报错 |
| 坑二：Spring AI 自作主张执行工具 | FR：工具只翻译成模型可读的 schema 传入请求，全程不存在自动执行路径 |
| 调用失败也记一笔（success=false） | FR：每次 LLM 调用成败都落审计记录，失败记录带原因 |
| 坏 Profile 不阻断启动 | FR：单个 Profile 解析失败记错误日志，不影响其余 Profile 加载 |
| `${ENV}` 占位 | FR：凭证从环境变量解析，代码与配置无明文 key |

课件"有几样先别做"逐项照搬成"明确不做"的边界：fallback、hedge racing、熔断、成本看板，都放扩展阶段——边界写进 spec，AI 才不会"顺手"做多。

### clarify：答案只从两份权威里找

`/speckit-clarify` 对 spec 扫歧义。答问题的顺序是固定的：**先查课件，再查技术方案**；两处都没有答案，就是软门禁——停下问用户，不自行发挥。典型如"max_iterations 默认值是多少"，课件写了就是它，没写就问。

### plan：四个固定件

`/speckit-plan` 的输入 = 固定技术栈句 + 本节模块落位 + 测试策略句 + 语法禁区。为什么是这四个固定件：

**1. 固定技术栈句**——把宪法原则压进 plan：JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前 `mvn dependency:tree` 确认锁定 BOM 里有目标依赖）、SQLite + Spring Data JPA、凭证走环境变量占位不落明文、手工建表脚本不依赖 `hibernate.ddl-auto=update`。这些是全项目不变量，不随节变，所以固定成一句话，每节原样带入。

**2. 模块落位表**——本节的类落进哪个 Maven 模块。第 16 节的落位：

| 类 | 模块 | 为什么 |
|----|------|--------|
| `Profile` / `ProfileLoader` / `ProfileRegistry` | oryxos-core | Profile 是所有下游模块消费的核心契约，放 core |
| `ProviderService` / 工具格式适配器 | oryxos-provider | Provider 能力域 |
| `LlmCall` 实体 + Repository | oryxos-storage | 持久化归存储模块 |

原则是依赖倒置：跨模块契约放 core，下游实现。落位错了（比如 ProviderService 引用 storage 的细节），后面每节都要付利息。

**3. 测试策略句**——从课件"验收 harness"部分原样抄过来，这是 **Harness 与 Spec-Kit 的接口**：

```text
harness 定义在课件第四部分 → plan 里承诺（测试策略句）
  → tasks 里落地（测试任务先行）→ implement 里兑现（代码 + 测试同落地）
  → verify 判卷（mvn clean verify 全绿 = 实现完成）
```

第 16 节的测试策略句就是那五个测试类：`ProfileLoaderTest`、`ProviderServiceTest`、`ToolSchemaAdapterTest`、`LlmCallRepositoryTest`、`ProviderSmokeIT`，单测默认跑、集成冒烟打 `@Tag("integration")` CI 跳过。

**4. 语法禁区句**——避开 P3C/ASM 解析不了的 Java 18+ 语法形态，因为静态检查是构建门禁，写了解析不了的语法，门禁自己先红。

### tasks + 固定软停点

`/speckit-tasks` 产出任务清单后，做一次**自动比对**：任务清单 ↔ 课件"本节交付物"（代码/测试/配置/表）逐项核对，输出"齐 / 缺什么 / 多什么"，并确认测试任务先于或伴随对应实现任务。

然后**停下，等用户确认**。这是整个流程唯一的固定停点，不许跳过。为什么停在这里：spec 和 plan 都是文本，AI 对课件的拆解有没有跑偏——交付物多了什么、少了什么、顺序反了没有——机器判不了，这一眼必须人来看。确认过了，后面 implement 阶段就是机器的地盘。

### implement：三级门禁

`/speckit-implement` 逐任务执行，每级都有门禁：

**写前（H3）**：涉及第三方 API 的任务，先在本地依赖里核实方法存在。这就是课件坑三的执行形态——教程里的 `deepseek`、`kimi`、`qwen` 只是 provider 名字的示意，想接哪家，先 `mvn dependency:tree` 确认那个 starter 在锁定 BOM 里能下载、能解析；核实不到 → 软门禁，停下报告。

**写中（H1/H5）**：

- 只创建交付物点名的对外概念——课件没点名的 public 类型、配置键，一个都不建；
- 已定字面量逐字保真——课件写 `llm_calls` 就是 `llm_calls`，写 `success` / `error_message` 列就是这两列；
- 异常不吞——catch 必落审计/日志或上抛；
- 不建文档外抽象层；注释只写"为什么"；
- **测试方法名用英文**（驼峰或 snake_case），课件 harness 里的中文方法名（如 `按名路由_两个provider不串台`）译成语义等价的英文名落地，`@DisplayName` 保留课件原文，方便对号。

**写后（任务级 DoD）**：实现与测试一起落地，跑该模块测试，红了当场修，不攒到最后。

特别地，课件"验收 harness"里**写出代码的那几个关键回归测试必须原样落地**，断言逻辑逐条保真（对应《Harness 设计》第四节的"每个坑钉一个测试"）。

### 节级验收：六项证据 + 变更总结

implement 完不等于节完成。六项证据 DoD（全绿输出 / 测试类对号 / 交付物存在性 / 前序节回归 / 全局不变量自查 / 剩余人工项）见《Harness 设计》第六、七节，此处不重复。此外给 reviewer 一份变更总结三段导读（改动点按模块分组 / 重点 review 清单按风险排序 / 可复制的验证命令块），直接输出在对话里。

收尾即停：**不 commit、不 push、不跑 package.sh**——同步时机由用户决定。

## 四、与 skill 的关系

本文讲**为什么**：门禁为什么是这三类、specify 为什么不带 HOW、停点为什么钉在 tasks 之后。`four-feet-cat-lesson-dev` 这个 skill 是本文的可执行版本——备料、命令参数、停点、DoD 都固化成了操作步骤。**条款冲突时，以 SKILL.md 为准**：本文负责让人看懂并信服这套流程，skill 负责让机器一次不差地执行它。
