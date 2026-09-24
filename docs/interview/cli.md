# CLI 模块 面试话术

> 模块：`fourfeetcat-cli` · 课件：第 18 节
> 一句话定位：**消息进出的门，不是干活的人**——读、转交、打印，通篇没有半点 Agent 智能。

---

## 一、30 秒版（照念）

> CLI 是 FourFeetCat 的命令行入口，12 个子命令，是"人推"的两个入口之一（另一个是 Web Service）。它的定位很清楚：**它是门，不是干活的人**——一个 `chat` 命令通篇就三步：读用户输入、交给引擎、打印结果。它自己不想、不调模型、不执行工具，那些全在引擎里。所以代码薄得下来，唯一"自己判断"的逻辑就是 `/quit`。
>
> 命令分三类：跑 Agent（`chat` / `serve` / `gateway`）、看情况（`status` / `profile` / `provider` / `tool` / `session`）、起项目（`init`）。三个"跑 Agent"的模式区别只在**消息从哪进来**——chat 走终端、serve 走 HTTP、gateway 挂多个通道，但共享同一份 Profile 和同一套 Session 存储，底下是同一个引擎。
>
> 一个关键设计是**轻重分流**：`profile list` 这种看一眼就退的命令不该等 Spring 启动那 2~4 秒。我的做法不是维护一张"哪些算重命令"的名单，而是**引擎懒启动**——用到才启动，轻命令一次都不会调到它，所以从结构上就不可能为容器启动付代价。
>
> CLI 本身是薄壳，真正值得自动化测试的是它交付的会话层：`session_id` 是 channel + user + profile 三元组，**拼接只发生在 SessionManager 内部这一处**，所有入口只提供三元组、不自己拼字符串。

---

## 二、展开版

### ① 三类命令（12 个叶子命令）

| 类别 | 命令 | 说明 |
|---|---|---|
| 跑 Agent（重命令） | `chat`、`serve`、`gateway` | 三种运行模式，区别只在"消息从哪进来" |
| 看情况（轻命令） | `status`、`profile list/create/show/delete`、`provider list`、`tool list`、`session list` | 查状态、列配置 |
| 起项目（轻命令） | `init` | 初始化工作区 |

`chat` 的骨架：`getOrCreate` 拿到当前 Session → `while(true)` 读一行 → `/quit` 退出 → 否则 `agentService.process(session, line)` → 打印 → 回到读下一行。

### ② 轻重分流：判据一句话

**这个命令要不要调模型 / 跑引擎？** 要 → 起 Spring 上下文；不要 → 直接干文件操作。

- 理由：Spring Boot 在 JDK 21 下启动要 2~4 秒。对 `serve` 这种常驻服务无所谓，但 `profile list` 等 4 秒才出结果太难受。
- **实际实现比"分两类"更漂亮**：根命令持有一个引擎工厂（由 boot 注入），`engine(type)` **用到才启动、同进程只启一次**。轻命令根本不碰这个方法——所以不需要维护"哪些命令算重命令"的名单，结构本身就保证了轻命令不付启动代价。
- 这个分流必须一开始就定，不然要么全都慢，要么后面改起来伤筋动骨。

### ③ `SessionManager` ——本节真正的交付物

CLI 是第一个真正"用起来" Session 的入口，所以会话持久化归它交付。

- `session_id` = **channel + user + profile 三元组**，且**拼接只允许发生在实现内部这一处**：
  > 所有入口（CLI 传 `"cli"`、Web 传 `"web"`、定时传 `"scheduler"`）只提供三元组。两处各拼一遍、格式差一个分隔符，同一个人就会出现两条互不相认的历史。
- `getOrCreate(channel, user, profileName)` **必须幂等**：同一三元组历次调用返回同一条会话（多轮对话靠它串起来），任一分量不同就是不同会话。
- 另外两个方法：`get(sessionId)`（不存在返回空——"还没开始聊"是正常分支，不是错误）、`save(session)`。
- 持久化实现：`sessions` 表把对话历史整体序列化成 JSON 存 `messages_json` 一列，核心阶段不做按条拆表。

**为什么值得测**：CLI 本身是薄壳，测它成本大于收益；但会话层是后面所有入口共用的地基，出口径问题最难查——所以在这就钉死。

### ④ 真实踩过的两个坑（面试含金量最高的一段）

**坑一：`scanBasePackages` 带不动 JPA 扫描。**
`@SpringBootApplication(scanBasePackages = "...")` 只管普通 Bean 的组件扫描，**不会带动** `@EnableJpaRepositories` / `@EntityScan`——后两者的默认扫描范围是"主类自己所在的包"，跟 `scanBasePackages` 是两套独立逻辑。CLI 模块和存 Session / 审计数据的模块通常是不同 Maven 模块（不同 Java 包），不显式声明 basePackages，启动就得到 **"Found 0 JPA repository interfaces"**，审计写不进去、直接报错退出。这是"照着轻重命令分流走几乎绕不开"的坑，得提前想到。

**坑二：输出编码一半好一半坏。**
Picocli 内部的 writer 用的是 `file.encoding`（本机 UTF-8），而 `System.out/err` 用的是**平台编码**（中文 Windows 是 GBK）——不显式绑定就会出现"正常输出正常、报错与 `--help` 花屏"这种一半好一半坏的现象。修法是 `commandLine()` 里把 out/err 显式绑到 `System.out.charset()` 上。同一个坑的另一面是输入侧：控制台读入同样要按平台编码解码，否则中文输入直接变乱码。

### ⑤ 其它设计点

- **不自己撸 args 解析**，用 Picocli：子命令、参数、帮助、报错提示它都做好了，一个命令一个 `@Command` 类。
- **依赖方向**：命令树不自己持有 Spring 应用配置类，容器由 boot 的应用配置类起动（那里正是"坑一"要显式声明注解的地方）。这条让依赖方向成立。
- `fourfeetcat` 不带子命令时打印用法，**别静默退 0**。

---

## 三、追问预演

| 追问 | 答 |
|---|---|
| 为什么不用 GraalVM native image 或 Spring Boot 懒加载省启动时间？ | 我用的是更直接的办法——轻命令压根不进容器。懒加载会引入不确定性（哪个 Bean 什么时候初始化），我的做法是"要么不起、要么起全"，行为可预测。 |
| 会话持久化为什么不做成一张消息表？ | 核心阶段整体序列化成 JSON 存一列就够，够用且简单；按条拆表是有查询需求之后的事。 |
| 三种运行模式怎么复用一套配置？ | 共享同一份 Profile 配置和同一套 Session 存储，只是把 `channel` 分量换掉（`cli` / `web` / `scheduler`），引擎是同一个。 |
| CLI 怎么知道自己该不该起引擎？ | 它不需要知道。根命令持有引擎工厂，重命令自己调 `engine(type)`，轻命令根本不碰——不需要名单。 |

---

## 四、证据坐标

| 讲点 | 代码位置 |
|---|---|
| 根命令 + 9 个顶级子命令 + 引擎懒启动 + 输出编码修复 | `fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/FourFeetCatCli.java` |
| 轻命令示例（不碰容器） | `.../cli/ProfileListCommand.java` |
| chat 交互壳 | `.../cli/ChatCommand.java` |
| 会话出口端口（三元组口径） | `fourfeetcat-core/src/main/java/org/fourfeetcat/core/session/SessionManager.java` |
| 会话实现与表 | `fourfeetcat-storage/`（SessionRepository + `db/migration/*/`） |
| 备注 | 命令树为 9 个顶级命令 + 嵌套子命令，共 12 个叶子命令，与课件"12 个命令"口径一致 |
