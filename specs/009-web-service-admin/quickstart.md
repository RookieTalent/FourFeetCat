# Phase 1 Quickstart：Web Service 与第一版管理平台（第26节）

本文件是**可执行的验证指南**：怎么跑、跑出什么算过、哪些必须人工过。

---

## 0. 前置

- JDK 21（本机：`C:\Program Files\Java\jdk-21.0.12.1`；注意系统 `JAVA_HOME` 默认指向 JDK 8，跑 Maven 前必须覆盖）。
- Maven 3.9（本机：`D:\maven3.9.16`）。
- Node / npm——**不需自己装**：前端构建由 Maven 插件负责，它把**固定版本**的 Node 装进 `fourfeetcat-web/.frontend-tools/`（不入库、`mvn clean` 不会清掉，装一次长期复用），然后 `npm ci` + `npm run build`。
  - 换机器/首次构建需要联网（下 Node 分发件 35MB + npm 依赖）。实测：`nodejs.org` 在国内网络下**时好时坏**（同一台机器 `curl` 能拿到，插件的下载器可能被连接重置）。
  - 卡在这一步时的解法（任选）：① 重跑一次构建；② 用镜像把 `node-v<版本>-win-x64.zip` 下载到插件缓存目录（`<本地仓库>/com/github/eirslett/node/<版本>/`）；③ 手工解包成 `fourfeetcat-web/.frontend-tools/node/`（要能直接看到 `node.exe`）。
  - `mvn -o`（离线模式）**跑不通本模块**：插件至少要能解析到工具链。
- **自动化部分零前置**：不需要模型 key、不需要外网。切片测试 mock 掉处理入口；冒烟测试起真上下文但不调模型。
- **人工项一需要真 key**（`DEEPSEEK_API_KEY`），因为"发一条消息 → 跑完 ReAct 循环 → 留下审计"这条链路只有真模型能跑通。

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1"
MVN="/d/maven3.9.16/bin/mvn"
```

---

## 1. 自动化验收（harness）

```bash
# 只跑本节切片单测：Controller 薄度 + 防呆边界 + 异常映射 + 500 不泄漏
"$MVN" -q -pl fourfeetcat-web -am test

# 真上下文冒烟：四端点真实链路可达 + 零凭证可 boot（落在 boot，随 verify 一起跑）
"$MVN" -q -pl fourfeetcat-boot -am test -Dtest=WebSmokeIT -Dsurefire.failIfNoSpecifiedTests=false

# 全量门禁（含 Spotless / PMD7 / Checkstyle / SpotBugs+FindSecBugs），也是"完成"的定义
"$MVN" clean verify
```

**期望结果**：

| 命令 | 期望 |
|------|------|
| web 切片单测 | `SessionApiControllerTest` 与 `GlobalExceptionHandlerTest` 全绿；断言含"处理入口恰被调用一次"与"500 响应不含内部细节" |
| boot 冒烟 | `WebSmokeIT` 全绿：`/health`、`/info`、`/profiles`、`/tools` 四个 GET 真实可达；`/info` 在零凭证下如实报"凭证未就位" |
| 全量门禁 | `BUILD SUCCESS`，且**前序各节测试无一失败、无一跳过** |

> 注意 `WebSmokeIT` 的类名以 `IT` 结尾——它由父 pom 的 **failsafe** 在 `verify` 阶段执行，`mvn verify` **会**真跑它，不会被跳过。

---

## 2. 起服务并人工走一遍

```bash
# 2.1 构建（含管理台：一条命令产出完整产物）
"$MVN" -q clean package -DskipTests
export DEEPSEEK_API_KEY=sk-xxxx          # 真跑对话需要；只想看列表/状态可以不配

# 2.2 起服务（默认 8080；FOURFEETCAT_ROOT 可整体搬移工作区）
java -jar fourfeetcat-boot/target/fourfeetcat-boot-0.1.0-SNAPSHOT.jar serve
```

```bash
# 2.3 两个主路径
curl -X POST localhost:8080/api/v1/sessions                     # 建会话（体可空）
curl -X POST localhost:8080/api/v1/sessions/{id}/messages \
     -H 'Content-Type: application/json' -d '{"content":"今天北京天气怎么样"}'
curl localhost:8080/api/v1/sessions/{id}                         # 查历史（含 truncated 标记）
curl -X DELETE localhost:8080/api/v1/sessions/{id}               # 归档（幂等，再删一次仍 200）
curl -X POST localhost:8080/api/v1/agents/ops-agent/invoke \
     -H 'Content-Type: application/json' -d '{"content":"一句话介绍你自己"}'

# 2.4 信息与状态
curl localhost:8080/api/v1/sessions
curl localhost:8080/api/v1/profiles
curl localhost:8080/api/v1/tools
curl localhost:8080/api/v1/memory
curl localhost:8080/api/v1/health
curl localhost:8080/api/v1/info

# 2.5 浏览器侧
open http://localhost:8080/admin        # 管理台（五个页面）
open http://localhost:8080/swagger-ui   # 接口文档
```

---

## 3. 人工验收清单（harness 判不了的部分）

| # | 项 | 怎么验 | 通过标准 |
|---|----|-------|---------|
| 1 | **11 个端点真链路** | 上面 2.3 ~ 2.4 逐条 curl（含真模型的发消息） | 全部按契约返回；发消息那一轮的模型/工具调用在 `llm_calls` / `tool_invocations` 里**有账** |
| 2 | **两人推入口共享存储** | 先 `fourfeetcat chat` 聊两轮，再用 `GET /api/v1/sessions` 与会话详情查那条会话 | 命令行聊过的会话，REST 侧**同一标识、同一历史** |
| 3 | **503 与 504 故障注入** | 503：配一个假的/不可达的 Provider 端点后发消息；504：把 Provider 端点指到一个只接受连接、不回响应的地方（或临时把读超时调到 1 秒）后发消息 | 分别拿到 503 与 504，且响应体是统一信封；服务本身不崩、下一请求照常 |
| 4 | **并发** | 对 `POST /agents/{name}/invoke` 打约 200 并发 | 全部有响应（无连接拒绝、无线程饥饿）；虚拟线程扛得住 |
| 5 | **管理台五页与三态** | 打开 `/admin` 逐页切换；清空数据看空态；停掉服务后刷新看错误态；在子路由上刷新浏览器 | 五页都渲染真实数据；**无任何写操作入口**；三态齐备；子路由刷新不 404 |
| 6 | **接口文档齐全** | 打开 `/swagger-ui` | 11 个端点都在，参数与响应可读；文档由代码生成（仓库内无手写接口文档文件） |
| 7 | **零凭证可启动** | 清掉所有 Provider 凭证环境变量后 `serve` | 服务**启动成功**；`/info` 如实报告"凭证未就位"；不因缺 key 启动失败 |
| 8 | **视觉同源** | 管理台与官网首页并排看；对照 `.claude/skills/four-feet-cat-admin-ui/SKILL.md` 的 token 表 | 底色/主色/字体/圆角气质一致；token 取值能逐项对到官网首页当前样式定义 |

> 第 1 ~ 3 与第 5 项里的"真模型链路"依赖真实环境，harness 覆盖不到，属**必须人工过**的部分；其余项 harness 已能靠 `mvn verify` 判绿。

---

## 4. 失败信号速查（红在哪，多半是什么）

| 现象 | 多半原因 |
|------|---------|
| `WebSmokeIT` 报"Found 0 repositories" | 数据访问层扫描声明在真上下文里没生效（18 节的坑复发）——检查 boot 的扫描声明 |
| `/info` 报 500 或 Provider 列表为空 | 全局 Provider 声明的注入没接上（检查 web 的 provider 依赖与装配） |
| `/tools` 报 500 | 工具表读取路径没接上（检查 web 的 tool 依赖） |
| `/admin` 404 | 管理台产物没构建（构建产物不入库，须先 `clean package` 或跑前端构建） |
| `mvn verify` 在 web 模块报依赖解析失败 | 前端构建插件需要联网（首次）；离线模式 `-o` 跑不通本模块 |
| 某条断言报"处理入口被调用 0 次或 2 次" | Controller 穿透了处理入口，或自行加了一层调用——端点薄度被破坏 |
