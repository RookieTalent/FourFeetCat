# Quickstart: ReAct 循环（第17节）验证指南

本节全部验收由单测承载（不碰网络、不碰真模型）。以下命令在仓库根执行。

## 1. 核心模块单测（判卷主体）

```bash
mvn -q test -pl fourfeetcat-core
```

预期：`ReActLoopTest`、`PromptBuilderTest`、`ToolExecutorTest`、`AgentServiceTest`、`ContextLoaderTest` 全绿。

对号入座（课件 harness 映射）：

| 测试类 | 守的验收点 |
|---|---|
| `ReActLoopTest` | 无工具调用一轮收尾；有调用则执行并回填下一轮；转满最大轮数强制停（恰好等于上限次数）；每轮响应与工具结果都累积进 Session |
| `PromptBuilderTest` | 四部分顺序正确；历史超 N 轮被截断；system 末尾含当前日期时间 |
| `ToolExecutorTest` | 成功写审计 success=true；失败也写 success=false 带原因（异常不吞） |
| `AgentServiceTest` | 处理期间 ProfileContext 可取到 Profile；抛异常时 finally 也清掉；结束后 Session 被持久化 |
| `ContextLoaderTest` | 改文件后下一次 build 立即读到新内容（无缓存）；Skill 引用缺失报错；Bootstrap 缺失 WARN |

## 2. 存储模块单测（建表脚本口径）

```bash
mvn -q test -pl fourfeetcat-storage
```

预期：`ToolInvocationRepositoryTest` 全绿——用 `db/migration/sqlite/V17__tool_invocations.sql` 手工建表（`ddl-auto=none`，`jdbc:sqlite:<@TempDir>/test.db` 文件库），`success`/`error_message` 两列真实存在，成败两类记录往返保真。

## 3. 全量门禁

```bash
mvn clean verify
```

预期：BUILD SUCCESS，含 Spotless(google-java-format) / 阿里 P3C / Checkstyle / PMD / SpotBugs+FindSecBugs 全过；前序节（第16节）测试一并回归绿。

## 4. 关键回归单测（单独点名跑）

```bash
mvn -q test -pl fourfeetcat-core -Dtest='ReActLoopTest#loopingToolCalls_stopsExactlyAtMaxIterations+AgentServiceTest#runThrows_profileContextStillCleared+ToolExecutorTest#failedExecution_auditsFailureWithReason'
```

预期：三条钉坑测试通过——模型一直要调工具时恰好转满上限、处理抛异常时 ThreadLocal 仍被清除、工具失败也有审计。

## 5. 依赖方向自查（架构门禁）

```bash
grep -rn "org.fourfeetcat.provider\|org.fourfeetcat.storage" fourfeetcat-core/src/main/java || echo "OK: core 无对 provider/storage 的依赖"
```

预期：输出 `OK:`（跨模块只经四个端口：`LlmCaller` / `SessionManager` / `ToolTable` / `ToolInvocationRecorder`）。

## 6. 人工项（机器判不了的，课件"做完怎么验"）

1. 用真模型跑通 Demo 一（每日天气）的对话版：多轮里 Agent 调了 `http_get`、拿到数据、给出穿搭建议。（依赖 18 节 CLI 入口与 20 节内置工具，本节尚无可运行入口，届时回归。）
2. code review 确认：循环是自己实现的，没有用框架现成的 Agent 封装（测不出来，靠人看）。
3. `mvn dependency:tree -pl fourfeetcat-core` 目视确认 `spring-ai-model` 在锁定 BOM 内解析（本节未新增依赖，属例行核对）。
