# Phase 1 Quickstart：定时任务——第三种触发源（第25节）

本文件是**可执行的验证指南**：怎么跑、跑出什么算过、哪些必须人工过。

---

## 0. 前置

- JDK 21（本机：`C:\Program Files\Java\jdk-21.0.12.1`；注意系统 `JAVA_HOME` 默认指向 JDK 8，跑 Maven 前必须覆盖）。
- Maven 3.9（本机：`D:\maven3.9.16`）。
- **自动化部分零前置**：不需要模型 key、不需要网络（装配冒烟用替身处理入口，不真调模型）。
- **人工项一需要真 key**（`DEEPSEEK_API_KEY`），因为"到点自动发起 → 跑完 ReAct 循环 → 留下审计"这条链路只有真模型能跑通。

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1"
MVN="/d/maven3.9.16/bin/mvn"
```

## 1. 自动化验收（harness）

```bash
# 只跑本节主战场：调度单测（四个坑逐一成对）
"$MVN" -q -pl fourfeetcat-core -am test -Dtest=AgentSchedulerTest

# 装配冒烟：真容器起一遍、真等到点、断言处理入口被调到（本节的接线守点，进 CI）
"$MVN" -q -pl fourfeetcat-boot -am test -Dtest=AgentSchedulerWiringTest

# 全量门禁（含 Spotless / PMD7 / Checkstyle / SpotBugs+FindSecBugs），也是"完成"的定义
"$MVN" clean verify
```

**期望结果**：

| 命令 | 期望 |
|------|------|
| `AgentSchedulerTest` | 全绿；四条守点：①注册时交给调度器的**触发规则与配置逐字一致**（捕获触发器的 `getExpression()`），且**时区真的生效**（用 `nextExecution` 断言：同一表达式在 `Asia/Shanghai` 下 09:00 → 次日 `01:00Z`，在 `America/New_York` 下 → 当日 `13:00Z`）②执行权被占时本次触发**直接跳过**、处理入口零调用（不排队、不叠加）③`runOnce` 内抛异常**不外抛**、且锁在 `finally` 释放——用"二进宫"断言证明（再触发一次仍能进处理入口）④同一任务两次触发拿到**同一条会话**，且三元组固定为 `("scheduler", "scheduler", profileName)` |
| `AgentSchedulerWiringTest` | 全绿：容器里**真的**装上了调度器，且配置里那条每秒触发的任务**真的**到点把消息交给了处理入口（`Awaitility` 限时等待，`AgentService` 用替身，不真调模型） |
| `mvn clean verify` | BUILD SUCCESS；四个静态门禁零违规 |

## 2. 人工项一：真实到点触发一次（课件"做完怎么验"第一条）

目的：证明"**钟推**"这条链路在真实进程里成立——到点自动发起对话、跑完 ReAct 循环、留下审计。

```bash
# 1) 给工作区里某个 Agent 加一条每分钟触发一次的任务（第 0 秒触发）
#    编辑 .fourfeetcat/profiles/default.yaml，加：
#      schedules:
#        - id: smoke-tick
#          cron: "0 * * * * *"
#          zone: Asia/Shanghai
#          message: 说一句"到点了"，并告知当前时间
# 2) 打包并常驻运行（定时任务随常驻模式持续调度）
"$MVN" -q -DskipTests -pl fourfeetcat-boot -am package
DEEPSEEK_API_KEY=xxx java -jar fourfeetcat-boot/target/fourfeetcat-boot-0.1.0-SNAPSHOT.jar serve --port 8080
```

判据（三条同时成立才算过）：

1. 启动日志里出现该条任务已注册；此后**每分钟**日志里出现一次触发（不需要任何人在终端里说话）。
2. 这一轮真的跑完了 ReAct 循环：`llm_calls` 里有对应记录。
3. `sessions` 里出现**定时专用**的那条会话：`channel='scheduler'`、`user_id='scheduler'`、`profile_name` 为该 Agent。

```bash
sqlite3 .fourfeetcat/fourfeetcat.db \
  "select session_id, profile_name, channel, user_id from sessions where channel='scheduler';"
sqlite3 .fourfeetcat/fourfeetcat.db \
  "select provider, model, total_tokens, created_at from llm_calls order by id desc limit 5;"
```

## 3. 人工项二：改触发时间不用重新编译（配置驱动的体感）

把那条任务的 `cron` 从 `"0 * * * * *"` 改成 `"0 0 10 * * *"`（每天 10:00），**只改配置文件、只重启进程**，不改一行 Java、不重新编译：

1. 重启后日志里该任务的注册信息应显示新的触发规则；
2. 把 `zone` 从 `Asia/Shanghai` 改成 `America/New_York`，触发时刻应相应平移（这条正是"不让服务器时区替用户做主"的手感验证）。

## 4. 人工项三：坏配置被响亮拒绝、且不牵连邻居

在同一个 Agent 下同时写两条：一条合法、一条写 5 段 Unix cron（`0 9 * * *`）或一个不存在的时区：

1. 启动日志里那条坏配置被**点名报错**并跳过（错误文案含"6 fields"或"Unknown time-zone"这类可读原因）；
2. 同 Agent 的另一条合法任务**照常注册并到点触发**；
3. 进程正常启动，不因一条坏配置拒绝启动。

## 5. 人工项四：配置说明写进用户看得到的地方

确认 Agent 配置模板（`fourfeetcat-cli/src/main/resources/templates/profile.yaml`，`fourfeetcat init` 生成的起点）的注释里写清了：

- `cron` 是 **6 段**（秒 分 时 日 月 周），5 段写法会被拒绝；
- `zone` 建议显式写，不写按服务器时区；
- 上一次没跑完时下一次到点**直接跳过**（不排队）；
- 历次触发复用同一条定时专用会话。

## 6. 回归面

```bash
# 跨节契约证据：全部模块测试（前序节全绿）
"$MVN" clean verify
```

**期望**：前序各节测试全部保持绿，零断言删改、零用例跳过。本节的改动只新增一条触发路径（默认不注册任何任务——没有 `schedules` 的 Agent 行为与第 24 节完全一致），不放宽任何既有断言。
