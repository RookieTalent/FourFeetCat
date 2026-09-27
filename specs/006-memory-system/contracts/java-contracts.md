# Phase 1 Contracts: Memory 记忆能力（第22节）

**Input**: [spec.md](spec.md) | [plan.md](plan.md) | [data-model.md](data-model.md)

本节是库/模块级契约（无对外 HTTP 接口——Web 端点归后续节）。逐条写明**签名、行为口径、错误口径**，作为实现与 review 的对照表。

---

## §1 `fourfeetcat-core`：门面契约与分区枚举

```java
package org.fourfeetcat.core.memory;

public interface MemoryService {
  /** 拼进系统提示的长期记忆：核心区全量 + 归档区截断后。不拼会话历史（由组装器的历史段独立负责）。 */
  String buildContext(Session session);

  /** save_memory 转发：记一条到指定分区。写入失败向上抛错，不静默成功。 */
  void remember(String content, MemoryScope scope);

  /** recall_memory 转发：按关键词只在归档区检索。未命中返回空列表；读取失败向上抛错。 */
  List<String> recall(String keyword);
}

public enum MemoryScope { CORE, ARCHIVAL }
```

| 口径 | 取值 |
|---|---|
| 落点 | `fourfeetcat-core` 的 `memory` 包（跨模块契约层，research D1） |
| 接口墙 | 三方法的名字与语义本节定死；后续升级存储 MUST NOT 改动 |
| null 口径 | 三个方法都不接受 null 入参（`content` / `keyword` 为空串时按"空内容"处理，由实现决定记录与否）；返回集合非 null |
| 线程/异步 | 全同步阻塞（宪法原则七） |

### 被改动的既有签名（**改造点**，一处）

```java
// fourfeetcat-core/src/main/java/org/fourfeetcat/core/react/PromptBuilder.java
- public PromptBuilder(ContextLoader contextLoader, Function<Profile, String> longTermMemory)
+ public PromptBuilder(ContextLoader contextLoader, MemoryService memoryService)
```

| 项 | 口径 |
|---|---|
| 为什么改 | 门面签名 `buildContext(Session)` 与"按 Profile 供给文本"的函数类型对不上，泛型擦除后无法用重载共存（research D2，主公裁决） |
| 未装配时 | `memoryService == null` ⇒ 记忆段整体跳过，组装结果与第17节基线逐字一致 |
| 保留项 | 单参构造 `PromptBuilder(ContextLoader)` **保留**（内部转调 `null`）；`build(Session, Profile)` 签名与消息序列结构**不变** |
| 连带改动 | `PromptBuilderTest`（断言随接缝调整 + 补未装配的逐字回归）、boot 的 `promptBuilder` Bean |
| 段落位置 | 记忆段仍是**独立的 system 消息**，插在系统文本消息之后、历史消息之前（research D14） |

### `fourfeetcat-core/src/main/java/org/fourfeetcat/core/package-info.java`

跨模块契约清单补 `memory` 一句（原清单已列 `channel / knowledge`），保持模块说明与实际契约一致。

---

## §2 `fourfeetcat-memory`：后端接口、四档实现、工具

### §2.1 后端接口

```java
package org.fourfeetcat.memory;

public interface LongTermMemoryStore {
  void append(String content, MemoryScope scope);
  String load();
  List<String> recallByKeyword(String keyword);
}
```

四条行为契约见 [data-model.md](data-model.md) §3；契约测试逐条断言、对四档统一跑。

### §2.2 四档实现

| 类 | 构造签名 | 关键口径 |
|---|---|---|
| `MarkdownMemoryStore` | `MarkdownMemoryStore(Path workspaceRoot)` | 文件 = `<root>/memory/MEMORY.md`；两区块标题；条目行 `- [yyyy-MM-dd] 内容`；读取缺文件⇒空；写入整文件重写（创建父目录）；截断取归档段尾部 4000 字符；检索按行包含、**区分大小写** |
| `SqliteMemoryStore` | `SqliteMemoryStore(MemoryEntryRepository repository)` | 核心区 `findByScopeOrderByIdAsc("CORE")`；归档区 `findByScopeOrderByIdDesc("ARCHIVAL", PageRequest.of(0,100))` 后翻正序；检索 `searchArchival("%关键词%")`；条目行渲染 `- 内容`；`createdAt` 写 ISO-8601 字符串 |
| `Mem0MemoryStore` | `Mem0MemoryStore(RestClient restClient, String userId)` | 写入 `POST /v1/memories/`（体含 messages / user_id / metadata.scope）；读取 `GET /v1/memories/?user_id=&scope=`；检索 `POST /v1/memories/search/`；响应解析 `results[].memory`（缺 results 时按数组根），**空或解析不出 ⇒ 空列表**；**5xx 上抛不吞** |
| `InMemoryMemoryStore` | `InMemoryMemoryStore()` | 进程内两个 List；归档取尾部 100 条；检索 `contains`（区分大小写，与文件档同口径） |

**签名中立性**：四档的实现细节都收在构造参数与私有方法里，接口签名与门面签名里不出现任何一档特有的词。

### §2.3 `MemoryTools`（`fourfeetcat-memory` 的 `builtin` 包）

```java
public class MemoryTools {
  public MemoryTools(MemoryService memoryService);

  @Tool(name = "save_memory", description = "记住一件值得长期记住的事")
  public String saveMemory(String content, String scope);

  @Tool(name = "recall_memory", description = "按关键词检索长期记忆")
  public String recallMemory(String keyword);
}
```

| 情形 | 行为 |
|---|---|
| `scope` 为 null / 空白 | 取 `ARCHIVAL` |
| `scope` 大小写不一（`Core` / `CORE` / `core`） | 归一后接受 |
| `scope` 取值非法 | 返回**点明该取值**的错误文本（含"应为 core 或 archival"），**不改写记忆** |
| 写入成功 | 返回"已记住" |
| 检索命中 | 命中行逐条以换行拼接 |
| 检索未命中 | 返回"没有找到相关记忆"——**不抛异常** |
| 底层抛错（库不可写 / 外部服务 5xx） | 异常上抛，由工具执行路径记成失败结果回填给模型换招 |

---

## §3 `fourfeetcat-storage`：实体、仓储、迁移

```java
@Entity @Table(name = "memory_entries")
public class MemoryEntry { Long id; String scope; String content; String createdAt; /* getters/setters */ }

public interface MemoryEntryRepository extends JpaRepository<MemoryEntry, Long> {
  List<MemoryEntry> findByScopeOrderByIdAsc(String scope);
  List<MemoryEntry> findByScopeOrderByIdDesc(String scope, Pageable pageable);
  @Query("SELECT m FROM MemoryEntry m WHERE m.scope = 'ARCHIVAL' AND m.content LIKE :pattern ORDER BY m.id ASC")
  List<MemoryEntry> searchArchival(@Param("pattern") String pattern);
}
```

| 项 | 口径 |
|---|---|
| 表结构 | 走既有 Flyway 双轨：`db/migration/{sqlite,postgresql}/V20__memory_entries.sql`，同版本号、只增不改；SQLite 轨取自课程参考脚本，PG 轨同列名同约束、方言差异照 V17~V19 写法 |
| `ddl-auto` | 保持 `none`（既有配置），不依赖 JPA 建表 |
| 实体口径 | 与既有四张表的实体同款（列名逐字对齐迁移脚本；时间列为 `String`，研究 D12 记录了跨轨风险的沿用理由） |
| 仓储口径 | 一律走派生查询 / `@Query`，不写原生 SQL；**核心区查询永不带条数上限**，上限只出现在归档查询上 |
| 不新增 | 不加关联、不加外键、不加审计相关列 |

---

## §4 `fourfeetcat-boot`：装配

```java
// fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/AgentRuntimeConfiguration.java（既有装配类内，不新建自动配置）
@Bean LongTermMemoryStore longTermMemoryStore(@Value("${memory.backend:markdown}") String backend,
                                              MemoryEntryRepository repository, RestClient restClient,
                                              @Value("${memory.mem0.base-url:}") String mem0BaseUrl,
                                              @Value("${memory.mem0.user-id:fourfeetcat}") String mem0UserId);
@Bean MemoryService memoryService(LongTermMemoryStore store);
@Bean ToolRegistry toolRegistry(..., MemoryService memoryService);   // + registerAnnotated(new MemoryTools(...))
@Bean PromptBuilder promptBuilder(ContextLoader contextLoader, MemoryService memoryService);
```

| 情形 | 行为 |
|---|---|
| `memory.backend=markdown` 或空 | `MarkdownMemoryStore(workspaceRoot())` |
| `memory.backend=sqlite` | `SqliteMemoryStore(memoryEntryRepository)` |
| `memory.backend=mem0` 且 `base-url` 非空 | `Mem0MemoryStore(以该地址为基址的 HTTP 客户端, userId)` |
| `memory.backend=mem0` 且 `base-url` 为空 | **装配期抛异常并点名 `memory.mem0.base-url`** |
| `memory.backend` 为其他取值 | **装配期抛异常并点名该取值**（不得静默回落 `markdown`） |
| 工具注册 | 记忆工具与既有内置工具同一行挂一个；不加新机制、不加新审计表 |

**连带改动**：`fourfeetcat-boot/pom.xml` 加 `fourfeetcat-memory` 依赖；`application.yaml` 加 `memory.backend` / `memory.mem0.base-url` / `memory.mem0.user-id` 三键（地址与作用域标识走环境变量占位）。

---

## §5 模块依赖方向（新增边，全部无环）

```text
core ←── memory ──► storage ←── boot
         │                        ▲
         └────────────────────────┘
（memory → {core, storage}；storage → core；boot → {core, memory, storage, tool, provider, cli, channel-cli, web}）
```

| 新增边 | 理由 |
|---|---|
| `fourfeetcat-memory` → `fourfeetcat-storage` | 结构化库档要用实体与仓储（持久化件按裁决落 storage） |
| `fourfeetcat-boot` → `fourfeetcat-memory` | 装配与打包可见性（同第19节教训） |

**不新增的边（明确禁止）**：`core → memory`（门面接口在 core，core 不认识实现模块）；`storage → memory`；`tool → memory`（记忆工具在 memory 模块，工具注册表在 tool 模块，两者只经接口相接）。
