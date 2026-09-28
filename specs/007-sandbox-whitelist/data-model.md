# Phase 1 Data Model：Sandbox 白名单校验（第24节）

**本节零持久化**：不新增实体、不新增表、不新增迁移脚本。被拦下的动作走既有 `tool_invocations` 审计路径，没有本模块自己的存储。下面描述的是**内存里的值对象与配置模型**。

---

## 一、值对象

### ActionType（既有，第20节，本节不改）

四值枚举，读与写分开取值以保留按读 / 写分权限的余地。

| 取值 | 语义 | 本节路由到 |
|------|------|-----------|
| `FILE_READ` | 读文件 / 列目录 | `checkFilePath` |
| `FILE_WRITE` | 写文件 | `checkFilePath`（与读共用同一份路径白名单） |
| `SHELL_COMMAND` | 执行命令 | `checkShellCommand` |
| `HTTP_REQUEST` | 发出 HTTP 请求（含通知推送的出站请求） | `checkHttpUrl` |

> 本节**不新增取值**（SMTP 端点白名单的消费方还不存在，见 research"已知文档-代码差"）。

### SandboxAction（既有，第20节，本节不改）

| 字段 | 类型 | 约束 |
|------|------|------|
| `type` | `ActionType` | 非空（既有记录类构造器已校验） |
| `target` | `String` | 纯字符串；解释方式由 `type` 决定：路径 / 可执行文件名 / 完整 URL |

记录类里**不出现**"白名单""容器""镜像""配额"任一档实现特有的字段——这是接口中立性的具体判据。

### SandboxViolationException（既有，第20节，本节不改）

`RuntimeException` 子类，携带可读原因。它不是一个"沙箱专属的处理分支"：抛出后由 `ToolExecutor` 既有的 try/catch 接住，按普通工具失败落审计（`success=false` + 原因）。

---

## 二、配置模型（新增三个配置件，`fourfeetcat-tool` 的 `sandbox` 包）

| 配置件 | 前缀 | 组件 | 绑定键 | 空值语义 | 默认值（本节） |
|--------|------|------|--------|---------|----------------|
| `FileSandboxProperties` | `file` | `List<String> allowedPaths` | `file.allowed_paths` | 空 = 什么都不允许 | 工作区根（绝对路径，见 D6） |
| `ShellSandboxProperties` | `shell` | `List<String> allowedCommands` | `shell.allowed_commands` | 空 = 什么都不允许 | 空（全拒） |
| `HttpSandboxProperties` | `http` | `List<String> allowedDomains` | `http.allowed_domains` | 空 = 什么都不允许 | 空（全拒） |

**绑定规则**：

- 键缺省时组件可能为 null → 紧凑构造器兜成 `List.of()`（D10），使"没配"与"配成空列表"语义一致：**全拒**。
- 文件白名单项**必须是绝对路径**：非绝对路径 → 构造期抛 `IllegalStateException` 并点名该项，Bean 创建失败即启动失败（D3 / FR-013）。
- 命令与域名白名单项不做格式校验：命令是按名精确比对（写什么配什么），域名支持精确项与 `*.` 通配项两种形态。

---

## 三、实现类内部的校验模型（`WhitelistSandbox` 的私有状态）

| 状态 | 类型 | 构造期来源 | 用途 |
|------|------|-----------|------|
| `allowedRoots` | `List<Path>` | `file.allowedPaths` 各项经 `realPath` 归一 | 路径白名单根（真实形态） |
| `allowedCommands` | `Set<String>` | `shell.allowedCommands` 的 `Set.copyOf` | 命令精确比对 |
| `allowedDomainPatterns` | `List<String>` | `http.allowedDomains` 的 `List.copyOf` | 域名精确 / 通配匹配 |

三个校验方法**全部 private**：外部只看得到 `enforce(SandboxAction)` 这一个入口。这是接口中立性的一部分——若把"查路径""查命令"摆到对外契约上，等于把契约按第一档实现裁剪，未来 container / microVM 实现就套不进来。

### 校验规则表

| 动作类型 | 判定 | 通过条件 | 不通过时 |
|---------|------|---------|---------|
| `FILE_READ` / `FILE_WRITE` | `realPath(目标)` 是否落在任一白名单根之下 | 存在任一根 `是 realPath(目标) 的前缀` | 抛违规异常，原因点名原始路径 |
| `SHELL_COMMAND` | 首 token（`trim()` 后按空白切分）是否在白名单内 | `allowedCommands.contains(首 token)` | 抛违规异常，原因点名首 token |
| `HTTP_REQUEST` | 主机名是否命中任一白名单项 | 精确项 `host.equals(项)`；通配项 `host.endsWith(项.substring(1))`（带点号边界） | 抛违规异常，原因点名主机名（或"无法解析"） |

### `realPath` 的三种落点（D1）

```text
目标存在              → toRealPath()                         // 软链被解析
目标不存在            → 最近存在祖先.toRealPath() + 剩余段     // 新建文件的场景
解析失败（IOException）→ 视为不通过（fail-closed）
```

**状态迁移**：无。白名单是启动期一次读入、运行期只读的配置，校验过程没有状态变化，因而是天然线程安全的（下次调用看到同一份白名单）——这一点对跑在虚拟线程上的工具执行是必要的。

---

## 四、被拦动作的留痕（复用既有模型，不新增）

| 字段（`tool_invocations`，既有） | 取值 |
|--------------------------------|------|
| `tool_name` | 被调用的工具名（如 `shell`） |
| `input_json` | 原始参数 |
| `success` | `false` |
| `error_message` | 沙箱给出的可读原因（如"命令不在白名单内: rm"） |
| `duration_ms` | 校验极快，接近 0 |

**审计路径零改动**：`ToolExecutor.execute` 的 try/catch 是唯一入口，本节只是让它多接住一种异常。
