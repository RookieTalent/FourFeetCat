# Phase 1 Quickstart: Notify 出站通知（第19节）

**Input**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Contracts**: [contracts/java-contracts.md](contracts/java-contracts.md)

本文件是**验证指南**（判卷命令块），不含实现代码。

## 0. 环境前提（本机实测口径）

本机 `MAVEN_HOME` 指向的目录不存在、`mvn` 不在 PATH；可用 Maven 是 IntelliJ 内置的，且需显式切 JDK 21：

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1"
MVN="/d/tools/IntelliJ IDEA 2026.1.4/plugins/maven/lib/maven3/bin/mvn"
"$MVN" -v     # 期望：Java version: 21.x
```

下文 `mvn` 一律指上面的 `"$MVN"`。

## 1. 依赖核实（H3 门禁，动手前）

```bash
"$MVN" -o dependency:tree -pl fourfeetcat-tool | grep -E "spring-web|spring-boot|fourfeetcat-core"
```

期望：`org.springframework:spring-web:jar:6.2.19:compile`、`spring-boot`、`fourfeetcat-core` 均为 `compile`。核不到 → 停下软报，不换依赖自行发挥。

## 2. 全量门禁（节级 DoD 第 1 项）

```bash
"$MVN" clean verify
```

期望：`BUILD SUCCESS`；Spotless/PMD 7/Checkstyle/SpotBugs+FindSecBugs 四道检查全过（SpotBugs findings 应为 0）。

## 3. 课件 harness 对号（节级 DoD 第 2 项）

```bash
"$MVN" test -pl fourfeetcat-tool -am -Dtest=WebhookNotifyAdapterTest
"$MVN" test -pl fourfeetcat-storage -am -Dtest=NotifyChannelRepositoryTest
"$MVN" test -pl fourfeetcat-core -am      # 前序节回归（跨节契约证据）
```

| 课件 harness 批次 | 落地测试类 | 守的断言 |
|---|---|---|
| 第一批（本节即可跑） | `WebhookNotifyAdapterTest` | POST body 带所传内容；**地址取自通知目标配置**（换配置即换地址，无硬编码）；接收端返 5xx 时异常上抛、不静默吞 |
| 第二批（**跨节**，本节只登记） | `NotifyToolsTest` | 渠道未配置 → 明确报错；渠道名缺省 → 取第一个；**`enforce` 先于 `send`**（`InOrder`，顺序反了就是漏洞）——完成时点在沙箱节之后 |
| 按裁决补 | `NotifyChannelRepositoryTest` | 手工建表脚本建出的渠道表能存能读、字段往返一致 |

> 测试方法名英文 + `@DisplayName` 保留课件中文原文（如 `sendsBeforeWhitelistCheck_isRejectedByInOrder` 类的顺序断言留给第二批）。

## 4. 交付物存在性核对（节级 DoD 第 3 项）

```bash
ls fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/notify/
ls fourfeetcat-storage/src/main/java/org/fourfeetcat/storage/ | grep -i notify
ls fourfeetcat-storage/src/main/resources/db/migration/{sqlite,postgresql}/V19__notify_channels.sql
ls fourfeetcat-tool/src/test/java/org/fourfeetcat/tool/notify/
```

期望：三个类（契约 / 目标 / webhook 实现）、两个 storage 类、双轨 V19 脚本各一份、测试类在位。

## 5. 人工项（课件"五、做完怎么验"，harness 判不了）

- **真 webhook 端到端**：假接收端测的是**协议**（body 形状、状态码处理），真 webhook 验的是**配置**（地址对不对、群里收不收得到）。需要主公在某个真群机器人上验一次——那属于 24 节接完工具、Agent 能对话调用之后的事。
- **接口中立性自查**（思维练习）：请主公审 `NotifyChannelAdapter.send(NotifyTarget, String)` 这个签名——换成企业微信官方 SDK 的实现、或换成 SMTP 邮件实现，签名要不要改？**答案应该是不需要**（`channelType` 与 `config` 足够承载任何一档的差异）。

## 6. 本节明确**不做完**的部分（验收时不要误判为缺陷）

- `NotifyTools`（通知工具）与其测试 `NotifyToolsTest`：完成时点在**沙箱节之后**（课件 L40 自述：依赖 20 节的工具注册与工具结果类型、23/24 节的沙箱校验）。任务在 tasks.md 中登记，**不得被标记完成**。
- 渠道的写入入口（管理台 / CRUD 端点）：扩展阶段。
- 按名解析渠道的端口与"当前 Agent 用哪些渠道"的接线：归 24 节（research.md D8）。
