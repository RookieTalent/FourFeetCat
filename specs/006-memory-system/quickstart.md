# Quickstart: Memory 记忆能力（第22节）验证指南

**Input**: [spec.md](spec.md) | [plan.md](plan.md) | [contracts/java-contracts.md](contracts/java-contracts.md)

> 本机 Maven 不在 PATH 上，命令里的 `mvn` 等价于 `E:\maven3.9.16\bin\mvn`（JDK 21，本地库 `E:\mavenRep`）。

---

## §1 硬门禁（全量判卷，一行命令）

```bash
# 在仓库根执行。四道静态门禁（Spotless/GJF + PMD7 + Checkstyle + SpotBugs+FindSecBugs）+ 全部单测
mvn clean verify
```

**预期**：`BUILD SUCCESS`，各模块测试全绿，SpotBugs findings 为 0（新增的注入式协作者排除项已写进 `config/spotbugs/spotbugs-exclude.xml` 并附理由）。

---

## §2 只跑本节的 harness

```bash
# 1) 契约测试：同一套断言对三档（文件档 / 库档 / 内存替身档）
mvn -B -ntp test -pl fourfeetcat-memory -am -Dtest=MemoryStoreContractTest

# 2) 三档专属 + 门面 + 工具
mvn -B -ntp test -pl fourfeetcat-memory -am -Dtest='MarkdownMemoryStoreTest,SqliteMemoryStoreTest,Mem0MemoryStoreTest,MemoryServiceImplTest,MemoryToolsTest'

# 3) 仓储与迁移脚本（真 SQLite 文件库）
mvn -B -ntp test -pl fourfeetcat-storage -am -Dtest=MemoryEntryRepositoryTest

# 4) 前序节受影响的回归（接缝适配）
mvn -B -ntp test -pl fourfeetcat-core -am -Dtest=PromptBuilderTest
```

**预期**：全部 `Tests run: N, Failures: 0, Errors: 0, Skipped: 0`。

---

## §3 课件 harness 映射表（逐个对号）

| 课件点名的测试类 | 本节落地文件 | 守点 |
|---|---|---|
| `MemoryStoreContractTest` | `fourfeetcat-memory/src/test/.../MemoryStoreContractTest.java` | 参数化遍历三档，同一套断言全过：写后立读（契约一）、截断只裁归档且核心一字不少（契约二）、分区路由正确（契约三）、检索只搜归档（契约四） |
| `MarkdownMemoryStoreTest` | `fourfeetcat-memory/src/test/.../MarkdownMemoryStoreTest.java` | 空文件不报错；截断边界（恰好不裁 / 超过才裁）；核心与归档写入不串区 |
| `SqliteMemoryStoreTest` | `fourfeetcat-memory/src/test/.../SqliteMemoryStoreTest.java` | 归档条数上限**只加在归档查询**、核心区全量取、检索走"只在归档内匹配"的那条查询 |
| `Mem0MemoryStoreTest` | `fourfeetcat-memory/src/test/.../Mem0MemoryStoreTest.java` | 进程内假服务：写入体带内容与分区 metadata；检索被转发并解析；服务端 5xx 异常上抛不静默吞 |
| `MemoryToolsTest` | `fourfeetcat-memory/src/test/.../builtin/MemoryToolsTest.java` | 分区缺省归档；显式核心；**非法分区点名报错且不落库**；未命中返回"没有找到相关记忆"不抛异常 |
| `MemoryServiceImplTest` | `fourfeetcat-memory/src/test/.../MemoryServiceImplTest.java` | 取到的上下文含长期记忆且核心区完整在内；`remember` / `recall` 转发到底层 |
| （课件清单未列，参考实现有、本节补上） | `fourfeetcat-storage/src/test/.../MemoryEntryRepositoryTest.java` | 迁移脚本建出的表能存能读；归档条数上限生效（取最近 N 且最新在前）；库内匹配只命中归档 |

**方法名口径**：测试方法名英文（驼峰），课件中文原文进 `@DisplayName`——对号时以 `@DisplayName` 为准。

---

## §4 关键回归点（写出来，reviewer 一眼能核）

课件 harness 里写出代码的那两个参数化测试，落地形态（方法名英文化、断言逐条保真）：

```java
@ParameterizedTest(name = "[{0}]")
@MethodSource("allStores")   // 文件档 / 结构化库档 / 内存替身档
@DisplayName("截断只裁归档区_核心记忆一字不能少")
void truncationKeepsCoreIntact(String name, Supplier<LongTermMemoryStore> factory) {
  LongTermMemoryStore memory = factory.get();
  memory.append("用户叫小王，偏好用 Java", MemoryScope.CORE);
  for (int i = 0; i < 500; i++) {
    memory.append("归档流水 " + i, MemoryScope.ARCHIVAL);   // 灌到远超阈值
  }

  String loaded = memory.load();

  assertTrue(loaded.contains("用户叫小王，偏好用 Java"), name + ": 核心区完整——始终在场的底线");
  assertFalse(loaded.contains("归档流水 0"), name + ": 归档区最早的被裁掉");
  assertTrue(loaded.contains("归档流水 499"), name + ": 保留的是最近的");
}

@ParameterizedTest(name = "[{0}]")
@MethodSource("allStores")
@DisplayName("写入后立刻可读_不允许有缓存")
void writeIsImmediatelyReadable_noCache(String name, Supplier<LongTermMemoryStore> factory) {
  LongTermMemoryStore memory = factory.get();
  memory.append("刚记的事", MemoryScope.ARCHIVAL);

  assertTrue(memory.load().contains("刚记的事"), name + ": 下一次 load 立即可见");
  assertFalse(memory.recallByKeyword("刚记的事").isEmpty(), name + ": 检索同样立即命中");
}
```

**必须原样落地的第二个场景**：同一关键词"只写在核心区"时**检索不到**（契约三 + 四的交叉守卫）——任何一档把核心区也搜了，这一行参数立刻红。

---

## §5 三档切换验证（人工项之一，最有说服力）

```bash
# 1) 文件档（默认）：跑一轮对话，让它记一件事，然后看文件
#    application.yaml 里 memory.backend 缺省即 markdown
mvn -B -ntp -pl fourfeetcat-boot -am spring-boot:run     # 或 bin/start.sh
#    对话里说一句值得记的话 → 打开 .fourfeetcat/memory/MEMORY.md，应看到判分区的条目行

# 2) 结构化库档：改一行配置再跑同一段对话
#    application.yaml:  memory.backend: sqlite
mvn -B -ntp -pl fourfeetcat-boot -am spring-boot:run
#    验证：同一段对话、同一个体感，只是记忆落到了 memory_entries 表里
sqlite3 .fourfeetcat/fourfeetcat.db "select id,scope,substr(content,1,40) from memory_entries;"
```

**判据**：切换前后**上层一行代码未改**（可 `git diff` 佐证：改动只有一行 yaml），记忆条目分别出现在文件与表里。

---

## §6 真链路手验（依赖真模型，人工项之二）

1. 配好 key（`DEEPSEEK_API_KEY`），`mvn -pl fourfeetcat-boot -am spring-boot:run`。
2. 对话里说一句值得长期记住的话（例："以后回答问题先给结论，再给理由"），Agent 应**主动**调 `save_memory`（不是系统自动提炼）。
3. 关掉进程、**开一个全新会话**，问一句无关的话——系统提示里应已带着核心记忆，Agent 按该偏好作答（"始终在场"的真实体感）。
4. 查留痕：`tool_invocations` 里应能看到 `save_memory` / `recall_memory` 的成败记录。

---

## §7 剩余人工项清单（harness 判不了的）

- **三档切换的体感验证**（§5）：文件档与结构化库档各跑一轮对话，比对"同一段对话、同一个体感"。
- **外部服务档真连一次（可选）**：部署一个自托管 Mem0，`memory.backend: mem0` + `memory.mem0.base-url` 指向它，验证 `save_memory` 真的进了它、`recall` 能语义召回（依赖真服务，测不了）。
- **真模型完整走一遍**（§6）。
- **只读初始设定文件不被写**：code review 确认没有写该文件的代码路径（`USER.md` 全程只读；可 `grep` 佐证）。
- **`memory.backend` 非法取值与外部服务档地址缺失的启动报错**：两条各起一次进程看报错文案（已由装配代码保证，但属进程级行为，人工过一眼）。

---

## §8 本节不验（跨节，别误判为缺项）

- **按 Agent 隔离**（一个 Agent 一份记忆、`agents/<name>/MEMORY.md`）——对账结论：不在本节，登记为后续节。
- **跨档统一检索大小写**——登记为后续节修订；本节两档口径不同是已知状态。
- **Web 端点**（查长期记忆的 REST 接口）——归 Web Service 节。
- **沙箱校验位**——记忆读写不是涉外 IO 的工具路径，不接沙箱；唯一出网的是外部服务档的一次后端调用（已按配置声明的后端处理，plan 的 Complexity Tracking 有记录）。
- **自动提炼 / 记忆压缩 / 语义与向量检索 / 知识图谱 / 情景记忆 / 记忆 CRUD**——明确不做（课件"有几样先别做"）。
