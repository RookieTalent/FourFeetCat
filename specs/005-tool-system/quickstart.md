# Quickstart: Tool 体系（第20节）验证指南

**Input**: [spec.md](spec.md) | [Plan](plan.md) | [Contracts](contracts/java-contracts.md) | [Data Model](data-model.md)

本文件只讲**怎么验**，不复述实现。

---

## §1 硬门禁（全量判卷，一行命令）

```bash
# 在仓库根执行。四道静态门禁（Spotless/GJF + PMD7 + Checkstyle + SpotBugs+FindSecBugs）+ 全部单测
mvn clean verify
```

预期：`BUILD SUCCESS`，9 个模块全绿，SpotBugs findings 为 0。

基线（本节开工前实测）：`mvn clean test` → 9 模块 `BUILD SUCCESS`，36s。

---

## §2 只跑本节的 harness

```bash
# 统一抽象与注册表
mvn test -pl fourfeetcat-tool -am -Dtest='CatToolContractTest,ToolRegistryTest'

# 内置工具三组（各自"正常能跑通 + 越界会被拦"两条）
mvn test -pl fourfeetcat-tool -am -Dtest='FileToolsTest,ShellToolsTest,HttpToolsTest'

# MCP 接入（mock 客户端，不碰网、不起进程）
mvn test -pl fourfeetcat-tool -am -Dtest='McpClientServiceTest,McpToolAdapterTest'

# 通知工具（第19节 harness 第二批，含 enforce→send 的顺序断言）
mvn test -pl fourfeetcat-tool -am -Dtest='NotifyToolsTest'

# core 侧随改名的既有回归
mvn test -pl fourfeetcat-core -am -Dtest='ToolExecutorTest,ReActLoopTest'
```

预期：全部 `Tests run: N, Failures: 0, Errors: 0`。

---

## §3 课件 harness 映射表（逐个对号）

| 课件测试类 | 落地位置 | 守住的验收点 |
|---|---|---|
| `OryxToolContractTest` → **`CatToolContractTest`** | `fourfeetcat-tool` | 参数化遍历注册表：name/description/inputSchema 三者都非空——任一工具漏实现参数说明立刻红 |
| `ToolRegistryTest` | `fourfeetcat-tool` | 三种来源都以 `CatTool` 身份注册；按工具名清单过滤后子集**恰好相等**（多一个 / 少一个都失败） |
| `FileToolsTest` / `ShellToolsTest` / `HttpToolsTest` | `fourfeetcat-tool` | 各自"正常能跑通 + 越界会被拦"两条 |
| `McpClientServiceTest` | `fourfeetcat-tool` | 某个 server 失联 → 只 WARN，好的照常注册，**启动不抛异常** |
| `McpToolAdapterTest` | `fourfeetcat-tool` | listTools 返回的工具被包装注册；execute 转发参数原样、结果包成 `ToolResult` |
| `NotifyToolsTest` | `fourfeetcat-tool` | 渠道未配置 → 明确报错；渠道名缺省 → 取第一个；**`enforce` 先于 `send`**（`InOrder` 钉死） |

> 方法名一律英文驼峰 / snake_case，课件原文进 `@DisplayName` 以便对号。

---

## §4 关键回归点（写出来，reviewer 一眼能核）

```java
// ① 契约三件套：新工具自动纳入，漏了 inputSchema 就红
@ParameterizedTest @MethodSource("allRegisteredTools")
void everyToolExposesNameDescriptionAndInputSchema(CatTool tool) {
    assertThat(tool.getName()).isNotBlank();
    assertThat(tool.getDescription()).isNotBlank();
    assertThat(tool.getInputSchema()).isNotBlank();
}

// ② 外部依赖失联不能拖垮启动和其他工具
when(badClient.listTools()).thenThrow(new RuntimeException("refused"));
service.connectAll(factory);                              // 不抛异常
assertThat(registry.contains("good_mcp_tool")).isTrue();
assertThat(registry.contains("bad_mcp_tool")).isFalse();

// ③ 推送前必须先过白名单校验（顺序反了就是漏洞）
notifyTools.notify("hello", "default");
InOrder inOrder = inOrder(sandbox, adapter);
inOrder.verify(sandbox).enforce(argThat(a -> a.type() == ActionType.HTTP_REQUEST));
inOrder.verify(adapter).send(any(), eq("hello"));
```

---

## §5 按清单过滤的"不多不少"

```bash
# Profile 声明的工具名清单 → 过滤后的子集必须精确相等
mvn test -pl fourfeetcat-tool -am -Dtest=ToolRegistryTest#*filter*
```

断言写"集合相等"而不是"包含"——包含式断言会漏掉"过滤过头"这一侧的错。

---

## §6 真链路手验（真实进程，人工项之一）

```bash
# 1) 工作区里声明一个真实 MCP server（例：官方 filesystem server）
cat .fourfeetcat/mcp_servers.yaml
#   servers:
#     - name: fs
#       transport: stdio
#       command: npx -y @modelcontextprotocol/server-filesystem /tmp

# 2) 列出当前注册的工具（应看到内置工具 + 该 server 暴露的工具）
fourfeetcat tool list

# 3) 对话里让它调一次（依赖真模型、真 key）
fourfeetcat chat
#   > 帮我读一下 /tmp/demo.txt 的内容
```

预期：`tool list` 里能看到 `read_file`/`write_file`/`list_dir`/`shell`/`http_get`/`http_post`/`notify` 以及 `fs` server 暴露的工具名；对话里 Agent 能挑中工具并拿到结果。

---

## §7 剩余人工项清单（harness 判不了的）

| # | 人工项 | 为什么机器判不了 |
|---|---|---|
| 1 | **方式一真跑一次**：写一份 SKILL.md + 连一个真实 MCP server，Agent 读懂意图并调用外部工具完成任务 | 依赖真模型与真 server |
| 2 | **方式三真跑一次**：`@Tool` 示例工具在 `tool list` 里可见、Agent 能调通 | 依赖真模型 |
| 3 | **沙箱临时装配的替换时点**：确认 `PermissiveSandbox` 的类注释、tasks.md 与验收报告三处都写了"由沙箱节替换" | 是文档约定，不是行为 |
| 4 | **真实命令执行**：`shell` 在白名单里的真实命令上跑通、超时可复现 | 需要按本机环境配白名单 |
| 5 | 每次工具调用都写进 `tool_invocations` | 已由第17节 `ToolExecutorTest` 覆盖；跑真链路时顺手目检一眼 |

---

## §8 本节不验（跨节，别误判为缺项）

- 五个扩展工具（`edit_file`/`grep`/`glob`/`ask_user`/`web_search`）——登记为后续补充，**本节不实现**。
- 记忆工具（`save_memory`/`recall_memory`）——第22节。
- 沙箱规则本体（`WhitelistSandbox` 与四个白名单配置键）——沙箱节。
