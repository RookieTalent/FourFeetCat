# Phase 1 Data Model: Memory 记忆能力（第22节）

**Input**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

本节新增**一张表**（`memory_entries`，结构化库档的后端）与**一个工作区文件约定**（`memory/MEMORY.md`，文件档的后端）；其余全是内存中的值对象与端口形状（`spec.md` 的 Key Entities 逐条落成 Java）。

---

## §1 `MemoryService` —— 记忆统一门面（`fourfeetcat-core` 的 `memory` 包）

上层（组装器、记忆工具）只认这三个方法。**接口落 core**（依赖倒置）：组装器在 core 必须注入它，接口留在实现模块会成环（research D1）。

| 方法 | 语义 | 约束 |
|---|---|---|
| `String buildContext(Session session)` | 本轮要注入系统提示的记忆上下文 | 返回长期记忆（核心区全量 + 归档区截断后，由后端 `load` 保证）；**不拼会话历史**（会话历史由组装器的历史段独立负责）；文件/库为空时返回带两个空区块的文本，不返回 null |
| `void remember(String content, MemoryScope scope)` | 记一条到指定分区 | `scope` 由调用方显式指定，系统不猜；写入失败向上抛错，**不静默成功** |
| `List<String> recall(String keyword)` | 按关键词检索 | **只在归档区**匹配；未命中返回空列表（不是错误）；读取失败向上抛错，**不静默返回空** |

**接口墙**：这三个操作的名字与语义本节定死，后续升级存储形态 MUST NOT 改动（spec FR-002）。`session` 参数按对账结论**当前不参与作用域圈定**（本节全局单份），保留它是为了签名稳定——将来按 Agent 隔离时接缝不必再动。

---

## §2 `MemoryScope` —— 长期记忆的两类分区（`fourfeetcat-core`）

```java
public enum MemoryScope { CORE, ARCHIVAL }
```

| 取值 | 语义 | 参与截断 | 参与检索 |
|---|---|---|---|
| `CORE` | 核心记忆：始终在场、小而恒定 | ❌ 永不 | ❌ 不参与（它本来就全量注入） |
| `ARCHIVAL` | 归档记忆：按时间累积的流水 | ✅ 超水位只留最近 | ✅ 只搜这一区 |

**缺省方向**：工具层的分区参数缺省取 `ARCHIVAL`（spec FR-013）。

---

## §3 `LongTermMemoryStore` —— 可插拔后端接口（`fourfeetcat-memory`）

```java
public interface LongTermMemoryStore {
  void append(String content, MemoryScope scope);
  String load();                                // 核心区全量 + 归档区（截断后）
  List<String> recallByKeyword(String keyword); // 只在归档区
}
```

**四条行为契约（三档 + 替身档都必须守，契约测试逐条断言）**：

| # | 契约 | 可测形式 |
|---|---|---|
| 一 | **不缓存**：每次重新读文件/查库/调服务 | 写完立刻 `load()`，必命中；写完立刻 `recallByKeyword`，必命中 |
| 二 | **核心区永不被截断**：截断只作用归档区 | 归档灌到远超水位后，核心区那条一字不少 |
| 三 | **分区由调用方经 `scope` 显式指定**，系统不猜 | 写核心的只在核心区、写归档的只在归档区；`load` 两者都在，检索只命中归档的 |
| 四 | **检索只在归档区做简单关键词匹配** | 只写进核心区的关键词检索不到；归档区的关键词检索得到 |

**签名中立性**：接口只表达"追加 / 取全量 / 按关键词检索"，不出现"文件""表""HTTP""向量"这类某一档特有的词。

---

## §4 四档实现的行为矩阵

| 实现 | 在哪 | 载体的定位 | 截断手法 | 检索手法 | 水位 |
|---|---|---|---|---|---|
| `MarkdownMemoryStore`（默认） | `fourfeetcat-memory` | 工作区 `memory/MEMORY.md` 一个文件，两区块 | 归档段**字符串裁尾**（只接归档段，物理碰不到核心区） | 按行包含匹配（**区分大小写**） | 4000 字符 |
| `SqliteMemoryStore` | `fourfeetcat-memory` | `memory_entries` 一张表 | 归档查询的**条数上限**（LIMIT），核心区查询不受影响 | 库内 `LIKE`（对 ASCII 不区分大小写） | 最近 100 条 |
| `Mem0MemoryStore` | `fourfeetcat-memory` | 自托管外部服务的记忆集合 | 交给该服务自己管（契约二变成"信任它的作用域机制"） | 用它自带的**语义检索**（契约四的加强版） | 由该服务决定 |
| `InMemoryMemoryStore` | `fourfeetcat-memory`（主源码） | 进程内两个 List | 归档取尾部 N 条 | `contains`（**区分大小写**，与文件档同口径） | 最近 100 条 |

> **跨档差异是已知状态**：文件档与内存档区分大小写、库档对 ASCII 不区分——对账结论是本节**不统一**（登记为后续节），故契约测试不对大小写行为下断言（research D7）。

---

## §5 工作区记忆文件形态（文件档，默认）

路径：`<FOURFEETCAT_ROOT>/memory/MEMORY.md`（环境变量缺省值时即 `.fourfeetcat/memory/MEMORY.md`）。

```markdown
## 核心记忆
- [2026-09-26] 用户偏好 Java，早上九点前不打扰

## 归档记忆
- [2026-09-26] 项目叫 FourFeetCat
- [2026-09-26] 昨天排查过一次 Hikari 连接池打满
```

| 约定 | 取值 |
|---|---|
| 分区标题 | `## 核心记忆` / `## 归档记忆`（一级标题之下的二级标题，逐字） |
| 条目行 | `- [yyyy-MM-dd] 内容` |
| 文件不存在 | 视作两个空区块；`load()` 仍返回带两个标题的文本 |
| 取某区 | 该标题之后到下一个标题之前，`strip()` |
| 写入 | 读全文 → 分区追加 → **整文件重写**为两区块形态；父目录不存在则创建 |

**已知后果**：整文件重写 ⇒ 工作区初始化时生成的占位说明文字在首次写入后**不被保留**（有意为之，spec Edge Cases 已录）。

---

## §6 `memory_entries` 表（结构化库档的后端，`fourfeetcat-storage`）

### DDL（双轨同版本号：`V20__memory_entries.sql`）

SQLite 轨（取自课程参考建表脚本 `docs/class/schema.sql` 的该表一段）：

```sql
CREATE TABLE IF NOT EXISTS memory_entries (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    scope       VARCHAR(16) NOT NULL,                    -- CORE / ARCHIVAL
    content     TEXT        NOT NULL,
    created_at  TEXT        NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_memory_entries_scope ON memory_entries(scope);
```

PostgreSQL 轨（同列名同约束，只方言不同：自增改标识列、`TEXT` 时间列改原生 `TIMESTAMP`——与既有 V17~V19 的跨轨写法同口径）。

### 实体 `MemoryEntry`（`fourfeetcat-storage`）

| 字段 | 类型 | 列 | 约束 | 谁填 |
|---|---|---|---|---|
| `id` | `Long` | `id` | 主键、自增 | 容器 |
| `scope` | `String` | `scope` | 非空，`CORE` / `ARCHIVAL` | `SqliteMemoryStore.append` 写枚举名 |
| `content` | `String` | `content` | 非空 | 同上，写原文 |
| `createdAt` | `String` | `created_at` | 非空，ISO-8601 | 同上，写 `Instant.now().toString()`（research D12） |

**无关联、无外键**：记忆条目之间没有关系，也不引用会话或 Agent（本节全局单份）。

### 仓储 `MemoryEntryRepository`（`fourfeetcat-storage`）

| 方法 | 语义 | 契约落点 |
|---|---|---|
| `findByScopeOrderByIdAsc(String scope)` | 某分区全量、按写入顺序 | 核心区走这条——**永不带 LIMIT**（契约二） |
| `findByScopeOrderByIdDesc(String scope, Pageable pageable)` | 某分区最近 N 条 | 归档区走这条，由调用方传 `PageRequest.of(0, 100)`——**LIMIT 只加在归档**（契约二） |
| `searchArchival(String pattern)` | `@Query` 显式限定 `scope = 'ARCHIVAL'` + `content LIKE :pattern`，按 id 正序 | 检索只命中归档（契约四） |

---

## §7 `MemoryTools` —— 两个内置工具（`fourfeetcat-memory` 的 `builtin` 包）

只认门面，对底下是哪一档完全无感。

| 工具名 | 参数 | 行为口径 |
|---|---|---|
| `save_memory` | `content`（要记住的内容）、`scope`（`core` / `archival`，不确定就填 `archival`） | 分区缺省取归档；取值大小写不敏感归一；**取值非法 → 返回点明该取值的错误且不落库**；成功返回"已记住" |
| `recall_memory` | `keyword`（检索关键词） | 命中 → 逐行拼接返回；**未命中 → 返回"没有找到相关记忆"**，不抛异常 |

**注册方式**：`@Tool` 注解方法，经第20节的 `AnnotatedToolAdapter` → `ToolRegistry.registerAnnotated(...)` 挂进注册表——与既有内置工具**同一行挂一个**，不加新机制、不加新审计表。

---

## §8 配置键（`fourfeetcat-boot/src/main/resources/application.yaml`）

| 键 | 取值 | 缺省 | 语义 |
|---|---|---|---|
| `memory.backend` | `markdown` / `sqlite` / `mem0` | `markdown` | 选哪一档后端；**取值非法 → 启动即拒并点名该取值**（不得静默当默认） |
| `memory.mem0.base-url` | 形如 `${MEM0_BASE_URL:}` | 空 | 仅外部服务档使用；**该档被选中而此键为空 → 装配期明确报错点名** |
| `memory.mem0.user-id` | 形如 `${MEM0_USER_ID:fourfeetcat}` | `fourfeetcat` | 外部服务档的作用域标识 |

**凭证口径**：地址与作用域标识都走环境变量占位，代码里不出现明文 key（宪法技术约束）。

---

## §9 组装器的接缝（`fourfeetcat-core` 的 `react` 包）

`PromptBuilder.build(session, profile)` 产出的消息序列（**本节只动第 2 段**）：

| 序 | 内容 | 谁供给 | 本节变化 |
|---|---|---|---|
| 1 | 系统文本：身份 + 任务指令 + 启动信息 + 当前时间 | `ContextLoader` | 不变 |
| 2 | **记忆上下文** | **门面 `buildContext(session)`** | ← 由"按 Profile 供给的函数"改为门面；未装配门面或返回空白 ⇒ **整段跳过** |
| 3 | 会话历史（最近 N 轮） | 组装器自建 | **不变，且不重复注入**（记忆段不含历史） |

**回归底线**：未装配门面时，组装结果与第17节基线**逐字一致**（spec SC-006）。
