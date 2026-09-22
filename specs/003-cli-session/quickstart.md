# Phase 1 Quickstart: CLI 入口层与会话持久化（第18节）

**Input**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Contracts**: [contracts/java-contracts.md](contracts/java-contracts.md)

本文件是**验证指南**（判卷命令块），不含实现代码。

## 0. 环境前提（本机实测口径，2026-09-21）

本机 `MAVEN_HOME` 指向的目录不存在、`mvn` 不在 PATH；可用的 Maven 是 IntelliJ 内置的 3.9.11，且 `JAVA_HOME` 现指向 JDK 8，**必须显式切到 JDK 21**：

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1"
MVN="/d/tools/IntelliJ IDEA 2026.1.4/plugins/maven/lib/maven3/bin/mvn"
"$MVN" -v     # 期望：Java version: 21.x, Maven 3.9.x
```

下文命令块里的 `mvn` 一律指上面的 `"$MVN"`。

## 1. 依赖核实（H3 门禁，动手前）

```bash
"$MVN" -o dependency:tree -pl fourfeetcat-cli     | grep -E "picocli|fourfeetcat-(core|storage|channel-cli)"
"$MVN" -o dependency:tree -pl fourfeetcat-storage | grep -E "jackson-databind|fourfeetcat-core"
```

期望：`info.picocli:picocli:jar:4.7.7:compile`、`com.fasterxml.jackson.core:jackson-databind:jar:2.21.4:compile`、两个模块内依赖为 `compile`。核不到 → 停下软报，不换依赖自行发挥。

## 2. 全量门禁（节级 DoD 第 1 项）

```bash
"$MVN" clean verify
```

期望：`BUILD SUCCESS`，且 Spotless(google-java-format) / PMD 7 / Checkstyle(google_checks) / SpotBugs+FindSecBugs 四个检查插件均无 red。任一 red 即失败，不得放宽阈值。

## 3. 课件 harness 对号（节级 DoD 第 2 项）

```bash
"$MVN" test -pl fourfeetcat-storage -Dtest=SessionManagerTest+SessionRepositoryTest
"$MVN" test -pl fourfeetcat-channel-cli -Dtest=CliChannelTest
"$MVN" test -pl fourfeetcat-core            # 第16/17节回归（跨节契约证据）
```

期望：全绿。关键回归点逐个对号：

| 课件原文测试 | 落地方法名（英文） | 守的断言 |
|---|---|---|
| `同一三元组_历次getOrCreate都是同一个Session` | `getOrCreate_sameTriple_returnsSameSession` | 两次同三元组 id 相等；`channel` 换成 `web` 则 id 不等 |
| （同上，user/profile 分量） | `getOrCreate_differentUserOrProfile_isDifferentSession` | `user` 与 `profileName` 各自不同即不同会话 |
| （幂等落库口径） | `getOrCreate_calledTwice_persistsExactlyOneRow` | 库里行数恰为 1（主键唯一约束兜底） |
| `手工建表脚本建出的sessions表_能存能读` | `scriptBuiltTable_savesAndReads` | 脚本建表后写入、读回字段一致 |
| `messages_json_序列化回读后消息完整` | `messagesJson_roundTrip_preservesAllMessages` | 条数/顺序/工具调用 id 与参数逐字保真 |
| `模拟重启_历史还在` | `reopenedContext_historyStillPresent` | 新建上下文重查，历史仍在 |

> 方法名以 harness 落地为准，课件中文原文进 `@DisplayName`（纪律：测试方法名必须是英文）。

## 4. 交付物存在性核对（节级 DoD 第 3 项）

```bash
ls fourfeetcat-cli/src/main/java/org/fourfeetcat/cli/
ls fourfeetcat-channel-cli/src/main/java/org/fourfeetcat/channel/cli/
ls fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/ | grep -i session
ls fourfeetcat-storage/src/main/resources/db/migration/{sqlite,postgresql}/V18__sessions.sql
```

期望：12 个命令类 + 根命令 + 两个包内私有辅助类（cli）、`CliChannel`（channel-cli）、四个会话类（storage）、双轨 V18 脚本各一份。

## 5. 人工项（课件"五、做完怎么验"，harness 判不了）

```bash
# 5.1 轻命令秒回（不打开容器：输出里不应出现 Spring/Flyway 启动日志）
time java -jar fourfeetcat-boot/target/*.jar profile list
java -jar fourfeetcat-boot/target/*.jar init

# 5.2 12 个子命令与 --help
for c in init status chat serve gateway provider session tool profile; do
  java -jar fourfeetcat-boot/target/*.jar $c --help | head -3
done

# 5.3 重命令启动日志里仓储扫描数 > 0（课件"坑四"的验收点）
java -jar fourfeetcat-boot/target/*.jar status 2>&1 | grep -i "repository interfaces"

# 5.4 交互（需真实 provider key；Demo 一对话版）
export DEEPSEEK_API_KEY=...
java -jar fourfeetcat-boot/target/*.jar chat          # 多轮对话 + /quit 正常退出
java -jar fourfeetcat-boot/target/*.jar chat --profile <别的 Agent>

# 5.5 三种模式共享同一份存储：chat 聊两句 → status/session list 能看到同一条历史；
#     重启进程后再 chat，历史接着上文（不丢）
```

**逐条人工确认清单**：

- [ ] `chat` 能进入交互，完成一次多轮对话，`/quit` 正常退出（Demo 一对话版走通）
- [ ] 轻命令（`init`、`profile list`）秒回；`chat`/`serve`/`gateway` 才启动容器
- [ ] `chat` 启动日志里仓储扫描到的接口数 > 0
- [ ] 三种运行模式共享同一份 Profile 与 Session 存储，切换模式数据不丢
- [ ] 12 个子命令都能跑、`--help` 正常
- [ ] `profile list` 列出的是 `init` 生成的 `default`；`profile create/show/delete` 一轮走通
- [ ] 会话幂等、隔离、持久化——已由 harness 覆盖（§3 全绿即打勾）

## 6. 留给后续节的口子（本节明确不做，验收时不要误判为缺陷）

- `tool list` 输出"当前无可用工具"：工具注册归第20节（届时只换数据源）。
- `serve` 只把 Web 容器起起来：REST 端点内容归第26节（既有 `/api/v1/ping` 可用来确认容器活着）。
- `gateway` 启动后阻塞保活：可挂的 IM 通道（飞书/企微/钉钉）归后续节。
- `archived` 状态列已建但无迁移入口：归档命令/端点归后续节。
