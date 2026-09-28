# Phase 1 Contracts：Sandbox 白名单校验（第24节）

本节是**内部库契约**（无 REST 端点、无 CLI 命令、无外部协议）。契约面 = ①对外的接口契约（本节不改它，但它的语义在本节被兑现）②四个接线点的调用契约 ③三个配置键的绑定契约。

---

## 一、对外接口契约（第20节已定，**本节一字不改**）

```java
public interface Sandbox {
  void enforce(SandboxAction action);   // 校验；不通过即抛 SandboxViolationException
}
```

**语义**：

- 表达"在受控环境里执行一个动作"的**意图**，不表达任何一档实现；签名里不得出现"白名单 / 容器 / 镜像 / 配额"。
- 调用点固定：**所有涉外动作在真正动手之前的第一行**调它。校验不通过即抛异常，动作不发生。
- 它是幂等、无副作用、线程安全的读校验（见 data-model 的"状态迁移：无"）。

**中性性自查（本节验收人工项之一）**：设想一个 `KataMicroVmSandbox implements Sandbox`——它需要新增方法吗？不需要（把动作交给 microVM 执行，通过则返回、拒绝则抛同一个异常）。据此判定这道墙立住了。

## 二、异常契约（第20节已定，本节不改）

| 项 | 契约 |
|---|------|
| 类型 | `SandboxViolationException extends RuntimeException` |
| 触发 | `enforce` 判定不通过（含"判不了"的 fail-closed 分支，D8） |
| 原因文本 | 点名越界物 + 所属白名单类别（如"命令不在白名单内: rm"、"域名不在白名单内: evil-example.com"、"路径不在白名单内: …"、"URL 无法解析，拒绝: …"） |
| 落库 | **不新增通路**：由 `ToolExecutor` 既有 try/catch 接住 → `tool_invocations`（`success=false`、`error_message` = 原因），并回填给模型看 |

## 三、接线点契约（第19/20节已就位，本节**不改任何一处代码**）

| 工具 | 类 | 调用位置 | 动作类型与目标 | 本节的"副作用未发生"证据（测试断言方式） |
|------|----|---------|--------------|----------------------------------------|
| `read_file` / `list_dir` | `FileTools` | 方法体第一行 | `FILE_READ` + 路径 | 真白名单下越界读：抛异常；目标不可读且未被触碰 |
| `write_file` | `FileTools` | 方法体第一行 | `FILE_WRITE` + 路径 | 真白名单下越界写：**目标文件内容逐字未变** |
| `shell` | `ShellTools` | 方法体第一行 | `SHELL_COMMAND` + 命令首名 | 真白名单下越界命令：**标记文件未被创建**（进程没起来） |
| `http_get` / `http_post` | `HttpTools` | 方法体第一行 | `HTTP_REQUEST` + URL | 真白名单下越界域名：**本地 stub 服务端一次请求都没收到** |
| `notify` | `NotifyTools` | 解析渠道后、发送前 | `HTTP_REQUEST` + 渠道 webhook URL | 真白名单下越界 webhook：**发送端替身一次都没被调用** |

> 顺序本身就是守点：校验写在 IO 之后等于没写。第20节已用替身沙箱钉过顺序（`InOrder` / `verify`），本节追加的是**用真白名单**证明"拦得住且副作用没发生"。

**装配契约**：`Sandbox` 唯一 Bean 由 `AgentRuntimeConfiguration.sandbox()` 产出（第20节的装配位，本节替换其返回值）；四个工具与 `ToolRegistry` 的构造方式不变。全容器内**只有一个** `Sandbox` 实现被装配（`PermissiveSandbox` 随本节删除）。

## 四、配置键契约（新增）

| 键 | 类型 | 默认（本节 `application.yaml`） | 空值语义 | 非法值行为 |
|----|------|-------------------------------|---------|-----------|
| `file.allowed_paths` | 绝对路径列表 | `${FOURFEETCAT_ROOT:${user.dir}/.fourfeetcat}` | 什么都不允许 | 含非绝对路径项 → 启动失败并点名该项 |
| `shell.allowed_commands` | 可执行文件名列表 | `[]`（全拒） | 什么都不允许 | 无格式校验（写什么配什么，精确比对） |
| `http.allowed_domains` | 域名列表（支持 `*.` 通配） | `[]`（全拒） | 什么都不允许 | 无格式校验（不匹配即拒绝） |

**配置说明（须写进 `application.yaml` 注释，验收人工项之三）**：

1. **白名单为空 = 什么都不允许**，不是"不校验"。安全配置的默认姿态是关着门。
2. 文件白名单项**必须写绝对路径**：相对路径在工作目录不同的三处（CLI / 守护进程 / 容器）指向不同位置，故启动即拒。
3. 命令项是**可执行文件名精确比对**（大小写敏感）：把解释器（`sh` / `bash` / `cmd`）列进去，等于管理员显式授予本机代码执行权限，**不构成隔离**。
4. 域名项只比**主机名**（忽略端口与路径、大小写敏感）；`*.example.com` 命中子域、不命中裸域。
5. 本节是**第一层劝阻**，不是强隔离：防模型犯傻与误操作，防不住蓄意攻击。核心阶段不建议用它跑完全不可信的代码，也不建议对外做多租户。

## 五、本节不触碰的契约（回归面清单）

| 契约 | 为什么必须不动 |
|------|---------------|
| `Sandbox` / `SandboxAction` / `ActionType` / `SandboxViolationException` 的签名与字面量 | 接口中立性是第23节的验收核心；改一个字面量就要连带改全部调用方 |
| `ToolExecutor`（执行 + 失败审计） | 沙箱违规走既有路径是本节的设计判断；动它等于给沙箱开专用通路 |
| 四个工具的 `@Tool` 签名、参数、描述与执行逻辑 | 只加校验、不改行为；`@Tool` 描述变了会影响模型的选择 |
| `ToolRegistry` 的注册与按 Profile 过滤 | 本节不新增工具 |
| `tool_invocations` 表结构与 `ToolInvocationRecorder` | 零新增审计代码 |
