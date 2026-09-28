# Phase 1 Contracts：定时任务——第三种触发源（第25节）

本节是**内部库契约**（无 REST 端点、无 CLI 子命令、无外部协议）。契约面 = ①新建两个类的成员清单（含哪些刻意不进对外契约）②四处既有契约的接触面声明 ③Agent 配置里定时条目的配置契约 ④装配契约 ⑤本节不触碰的契约回归清单。

---

## 一、新建类的成员（`fourfeetcat-core` 的 `schedule` 包）

```java
/** 一条定时任务的值对象。 */
public record ScheduleConfig(String id, String cron, String zone, String message) {}

/** 薄薄一层调度：把配置里的定时规则接到框架的调度设施上。 */
public class AgentScheduler {

  public AgentScheduler(
      TaskScheduler taskScheduler,       // 框架设施，boot 装配（接口而非具体类，D3）
      ProfileRegistry profileRegistry,
      AgentService agentService,
      SessionManager sessionManager) { ... }

  /** 启动期扫一遍所有已加载 Agent 的定时配置并逐条注册（boot 经 @Bean(initMethod=...) 调它）。 */
  public void registerAll() { ... }

  /** 触发一次：拿锁 → 取/建钟推会话 → 交给 AgentService → 放锁。 */
  void runOnce(Profile profile, ScheduleConfig scheduleConfig) { ... }

  /** 取/建某个任务的执行权（包内可见：包内测试要用它占住锁，构造"上一次还在跑"的场景）。 */
  Lock lockFor(String taskId) { ... }
}
```

**可见性口径（三条都是刻意的）**：

| 成员 | 可见性 | 为什么 |
|------|--------|--------|
| `ScheduleConfig`（record） | `public` | 与 `AgentScheduler` 同属 core 的公开类型；将来第28节的立即执行接口也要用它 |
| 构造器 / `registerAll` | `public` | `registerAll` **必须** public：boot 用 `@Bean(initMethod = "registerAll")` 经反射调用，私有方法调不到 |
| `runOnce` | **包内可见** | 课件 harness 的诀窍是"别真等时间——`runOnce` 拆成独立方法，直接调它就能测全部行为逻辑"，故它必须能被同包测试直接调；但不进对外契约（外部只有"注册"这一个入口） |
| `lockFor` | **包内可见** | 只为包内测试占锁用（课件 harness 第一句就是 `scheduler.lockFor("task-1")`）。外部拿到锁没有正当用途，升为 public 纯属多一个不该有的对外概念——与 `FourFeetCatCli.engine()` 同款处理 |

**不做的东西（同样重要）**：没有 `ScheduleRegistry`、没有 `SchedulerFacade`、没有 `TaskStatus` 之类的类型。本节只有"注册"和"触发一次"两个动作，多一层抽象就是给未来写用不上的代码。

## 二、四处既有契约的接触面（本节改动一览）

| 既有契约 | 本节动作 | 理由 |
|---------|---------|------|
| `ProfileRegistry` | **新增只读方法** `all()`（遍历全部已加载 Profile）；内部容器 `HashMap` → `LinkedHashMap` | 注册要"扫一遍所有 Agent"（技术方案 §8.5）。既有成员 `find(String)` 与构造器签名、语义一律不变；改成有序容器只是让重名裁决可复现（D7） |
| `Profile` / `ProfileLoader` | **零改动** | `schedules` 字段第16节已建，`ProfileLoader` 已有对应解析；本节只接消费方（D6） |
| `AgentService` | **零改动**（只被调用） | `process(Session, String)` 是三种触发源的唯一入口，这正是本节要证明的事 |
| `SessionManager` | **零改动**（只被调用） | 只递三元组，会话标识拼接仍在实现内部唯一一处（D9） |
| 审计路径（`LlmCallRecorder` / `ToolInvocationRecorder`） | **零改动** | 定时触发那一轮照旧记账，不为定时另开通路（FR-009） |

## 三、配置契约：Agent 配置里的一条定时任务

```yaml
schedules:
  - id: daily-digest            # 可选：不写则按 profileName#序号 派生
    cron: "0 0 9 * * *"         # 必填：6 段（秒 分 时 日 月 周）
    zone: Asia/Shanghai         # 可选：不写则按服务器系统时区
    message: 汇总昨天的 PR 评审进度   # 必填：到点发给 Agent 的话
```

| 键 | 类型 | 必填 | 缺省 | 非法值行为 |
|----|------|------|------|-----------|
| `id` | 字符串 | 否 | `profileName#序号` | 与已注册任一条重名 → 记错误日志、跳过该条、点名两条来源 |
| `cron` | 字符串 | **是** | — | 缺失/空白 → 跳过；语法非法（含写成 5 段 Unix 写法）→ 跳过。两种都记错误日志 |
| `zone` | 字符串 | 否 | 服务器系统时区 | 非法时区标识 → 记错误日志、跳过该条 |
| `message` | 字符串 | **是** | — | 缺失/空白 → 记错误日志、跳过该条 |

**配置说明（须写进 Agent 配置模板的注释，验收人工项之四）**：

1. `cron` 是 **6 段**（秒 分 时 日 月 周），例：每天 09:00 = `0 0 9 * * *`。写成 5 段的 Unix 写法会被**拒绝**（不会按另一种意思静默跑起来），该条被跳过并在日志里报错。
2. `zone` 建议**显式写**：不写就按服务器时区跑，"用户以为的早上 9 点"容易和实际触发时刻对不上。
3. 一条定时任务跑挂不影响别的任务，也不影响它自己下一次到点触发；上一次没跑完时，下一次到点**直接跳过**（不排队、不堆积）。
4. 定时触发的对话落在**定时专用的会话**上（渠道与用户都固定、Agent 为该任务所属的 Agent），同一条任务的历次触发复用同一条会话历史。
5. 改触发时间只需改这份配置、重启进程即生效；**无需**改代码或重新编译。

## 四、装配契约（`fourfeetcat-boot` 的 `AgentRuntimeConfiguration`）

```java
@Bean
public ThreadPoolTaskScheduler taskScheduler() {
  ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
  scheduler.setPoolSize(4);                 // 下限是 2，见 research D4
  scheduler.setThreadNamePrefix("ffc-sched-");
  return scheduler;
}

@Bean(initMethod = "registerAll")           // 注册期就是 Bean 创建期
public AgentScheduler agentScheduler(
    TaskScheduler taskScheduler, ProfileRegistry profileRegistry,
    AgentService agentService, SessionManager sessionManager) { ... }
```

- 池容量 **4**（D4：下限 2，否则一条长任务会把别的任务到点的触发堵在队列里）；**不设配置键**——没有实测依据前不引入调优旋钮。
- `initMethod` 而非 `@PostConstruct`：core 保持零 Spring 注解（D5）。
- 装配点在 boot 的既有 `@Configuration` 里，不新增装配文件、不新增模块依赖边。
- **不新增 `application.yaml` 键**：定时任务的配置在 Agent 配置里，不在全局配置里。

## 五、本节不触碰的契约（回归面清单）

| 契约 | 为什么必须不动 |
|------|---------------|
| `ReActLoop` / `PromptBuilder` / `ToolExecutor` | 定时只是第三个入口，循环内部不该知道消息从哪来 |
| `ProviderService` 与 provider 映射 | 不碰路由（宪法原则三） |
| 四个涉外工具的沙箱调用位与 `ToolExecutor` 审计路径 | 定时触发那一轮的工具调用照旧受沙箱约束、照旧留痕 |
| `Profile` 记录与 `ProfileLoader` | 本节用既有字段，不动其结构与加载器 |
| `SessionManager` 接口与 `JpaSessionManager` | 会话标识拼接的唯一处不得被第二节引入 |
| 数据表与迁移脚本 | 本节零落库 |
| README / 官网首页 / 技术方案正文 | 本节是把 §8.5 的既定设计落成代码，不改任何对外表述（一处方言口径登记为文档差，见 research 末节） |
