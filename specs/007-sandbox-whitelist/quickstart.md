# Phase 1 Quickstart：Sandbox 白名单校验（第24节）

本文件是**可执行的验证指南**：怎么跑、跑出什么算过、哪些必须人工过。

---

## 0. 前置

- JDK 21（本机：`C:\Program Files\Java\jdk-21.0.12.1`；注意系统 `JAVA_HOME` 默认指向 JDK 8，跑 Maven 前必须覆盖）。
- Maven 3.9（本机：`D:\maven3.9.16`）。
- 无需任何真实模型 key、无需网络：本节全部测试是自足的单元 / 本地 stub 测试。

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1"
MVN="/d/maven3.9.16/bin/mvn"
```

## 1. 自动化验收（harness）

```bash
# 只跑本节主战场：白名单校验单测
"$MVN" -q -pl fourfeetcat-tool -am test -Dtest=WhitelistSandboxTest

# 四个接线工具的回归（含本节追加的"真白名单"接线用例）
"$MVN" -q -pl fourfeetcat-tool -am test -Dtest=FileToolsTest,ShellToolsTest,HttpToolsTest,NotifyToolsTest

# 全量门禁（含 Spotless / PMD7 / Checkstyle / SpotBugs+FindSecBugs），也是"完成"的定义
"$MVN" clean verify
```

**期望结果**：

| 命令 | 期望 |
|------|------|
| `WhitelistSandboxTest` | 全绿；用例覆盖三类校验各"允许 + 拒绝"成对，外加六个边界：①`../` 穿越被拦 ②软链指向白名单外被拦 ③`*.example.com` 命中 `api.example.com` 但不命中 `evil-example.com` 与裸域 ④`API.Example.com:8443` 因大小写敏感被拦、同域小写带端口被放行 ⑤三份白名单全空时任何动作都被拦 ⑥文件白名单项写成相对路径时构造即抛、报错点名该项 |
| 四个工具测试 | 全绿，且每条越界用例都**同时**断言"抛了沙箱异常"与"副作用没发生"（文件未变 / 进程未起 / 请求未发 / 未发送） |
| `mvn clean verify` | BUILD SUCCESS；四个静态门禁零违规 |

## 2. 人工项一：真实链路集成验证（课件"做完怎么验"）

目的：证明被拦下的一次动作，在**完整链路上**是"抛异常 + 审计有记录 + 原因可读"三件事同时成立，而不只是单测里的一个断言。

```bash
# 1) 配一份只允许单一命令的白名单（工作区根照旧，域名与其它命令全拒）
#    编辑 fourfeetcat-boot/src/main/resources/application.yaml:
#      shell:
#        allowed_commands: ["java"]        # 演示用：只放行一个存在的可执行名
# 2) 起一个能对话的入口（无 key 时用假 provider，或直接跑单测链路）
"$MVN" -q -pl fourfeetcat-boot -am spring-boot:run
```

判据（三条同时成立才算过）：

1. 让 Agent 执行一条白名单外的命令（如 `shell("cmd", ["/c", "echo x > marker"])`）——链路上抛出 `SandboxViolationException`，且 `marker` 文件**没有**被创建。
2. `tool_invocations` 里新增一条记录：`tool_name='shell'`、`success=0`、`error_message` 含"命令不在白名单内"。
3. `error_message` 人能读懂（点名越界物），不是"调用失败"这类无信息量文本。

```bash
# 查审计（SQLite）
sqlite3 .fourfeetcat/fourfeetcat.db \
  "select tool_name, success, error_message from tool_invocations order by id desc limit 5;"
```

## 3. 人工项二：接口中立性自查（思维练习）

对着 `fourfeetcat-tool/src/main/java/org/fourfeetcat/tool/sandbox/Sandbox.java` 问两句话：

1. 签名里有没有混进"路径""命令""白名单""容器"这类第一档专属词？→ 应无。
2. 设想 `KataMicroVmSandbox implements Sandbox`：需要给它加方法吗？→ 不需要，才算这道墙立住了。

## 4. 人工项三：配置边界写进文档

确认 `fourfeetcat-boot/src/main/resources/application.yaml` 三个白名单键的注释里写清楚了：

- **空 = 什么都不允许**（不是"不校验"）；
- 文件白名单项必须绝对路径（相对路径启动即拒）；
- 命令项是精确比对，列入解释器 = 授予本机代码执行权限；
- 域名项只比主机名、大小写敏感、`*.` 匹配带点号边界；
- 本节是"劝阻级"防线，不建议跑完全不可信代码或多租户。

## 5. 回归面

```bash
# 跨节契约证据：全部模块测试（前序节全绿）
"$MVN" clean verify
```

**期望**：前序各节测试全部保持绿；本节的改动只增加行为（越界被拦），不放宽任何既有断言——`file.allowed_paths` 默认取工作区根，故第20节"Agent 真的能动手"那条验收在工作区内仍然成立。
