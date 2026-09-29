# Quickstart: 插件化 Agent 验证指南

本文件是**运行/验证指南**，不含实现代码（实现细节在 `tasks.md`）。目标：证明"一个目录派生一个会自己跑的 Agent + 运行时注册就位 + 渐进披露"端到端成立。

## 前置

- mvn 与 JDK 21（本项目构建工具链：mvn 不在 PATH、JAVA_HOME 默认 JDK 8，须覆盖后再 build——见记忆 `local-build-toolchain`）
- Harness 测试（`mvn test`）即可全绿；真模型定时触发是人工项

## 1. Harness 自动化门禁（实现完成 = 全绿）

在 core 模块跑本节测试套件，验证链路上每一环：

```bash
cd fourfeetcat-core
# 只跑本节
mvn -q test -Dtest='AgentLoaderTest,DeriveProfileTest,AgentScanRegisterTest,ProfileRegistryRuntimeTest,AgentSchedulerRegisterTest,ProgressiveDisclosureTest'
# 全量回归（含前序节）
cd .. && mvn -q clean verify
```

**预期**：六个测试类全绿；`mvn clean verify` 含 P3C/SpotBugs/PMD 全绿。软连接用例在本地（Windows 无建软链权限）标记 skip、CI 真跑。

## 2. 手动路径：扫描一个 Agent 目录

用运行时工作区（gitignore 不提交）验证真实扫描链路：

```bash
# 构造复现（shell 命令；GCOS 软连接在 Linux/CI 才有，如何 guarded）
fourfeetcat init                      # 建 .fourfeetcat/ 工作区
# 放入示例 Agent（见 tasks：daily-reconcile/ 四文件）
mkdir -p .fourfeetcat/agents/daily-reconcile/{scripts,skills}
# AGENT.md + scripts/reconcile.py + skills/report-format.md + REFERENCE.md 落位后：
fourfeetcat profile list               # → daily-reconcile 出现在列表（未写一行 Java）
fourfeetcat status
```

**预期**：`profile list` / `GET /api/v1/profiles` 出现该 Agent；若 frontmatter 带 `schedules`，`AgentScheduler` 已按它注册定时（`fourfeetcat session list` 或管理台可见任务登记）。

## 3. 人工项（harness 判不了，须主公人工过）

- **真模型定时触发**：配置真实 provider + `OPS_WEBHOOK_URL`，等 `schedules` 到点 → Agent 自动跑 ReAct→推送报告→审计（`llm_calls`/`tool_invocations`/`task_executions`）有账。
- **正文即时生效**：改 `AGENT.md` 正文，下一次触发即用新说明，不重启。
- **渐进披露**：Agent 跑到需要时用 `read_file` 读 `skills/report-format.md` / `REFERENCE.md`、用 `shell` 跑 `scripts/reconcile.py`，脚本只有输出进上下文、代码不进。

> 契约细节：Profile 派生映射见 `data-model.md`；软连接浓度、运行时注册语义见 `contracts/profile-derivation.md`。