# Implementation Plan: 插件化 Agent —— 一个目录定义一个会自己跑的 Agent

**Branch**: `029-lesson29-plugin-agent` | **Date**: 2026-09-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/010-agent-directory/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command.

## Summary

给底座添一条"定义一个 Agent"的标准来源：把 `.fourfeetcat/agents/<name>/` 一个目录定义成一个会自己跑的 Agent。核心改动集中在 `fourfeetcat-core`：新增 `AgentLoader`（`deriveProfile` 把 `AGENT.md` frontmatter 派生成 `Profile`）、`ProfileRegistry` 改可变并发、`AgentScheduler` 抽 `registerProfile` 并新增 `scheduledTasks` 句柄表、`ContextLoader` 注入 Agent 正文并按宪法四软连接视图注入技能元数据；装配层启动扫描 `agents/` 注册。不改底座执行链路，不改 pom、不新建模块。设计结论见 `research.md`。

## Technical Context

**Language/Version**: Java 21（必须，virtual thread）

**Primary Dependencies**: Spring Boot 3.x、SnakeYAML（均既有，**无新增第三方依赖**，故无需 dependency:tree 验证新增项）

**Storage**: N/A（本 feature 读文件系统布局，不动 SQLite/表结构）

**Testing**: JUnit 5 + AssertJ（既有）；单测默认跑，`@Tag("integration")` CI 跳过，软连接用例本地 skip/CI 真跑

**Target Platform**: 服务器/工作区 `.fourfeetcat/agents/` 文件系统

**Project Type**: 多模块 Maven（`fourfeetcat-core` 改造 + `fourfeetcat-boot` 装配接线）

**Performance Goals**: N/A（启动期低频扫描 + 每轮 prompt 组装读文件；沿用 ContextLoader 无缓存现读）

**Constraints**: 宪法四软连接视图（`toRealPath` 校验、渐进披露）、原则六真实路径校验、原则七同步、P3C 静态门禁可解析语法

**Scale/Scope**: 每实例 N 个 Agent 目录，各独立自足

## Constitution Check

*GATE: passed before Phase 0; re-checked after Phase 1 design — stable, no violations.*

- 原则一（自实现 ReAct）：不改 `ReActLoop`，派生执行链路走既有 `AgentService.process` ✅
- 原则二（Spring AI 两件事）：本 feature 不新增任何 Spring AI 用法 ✅
- 原则三（显式 Provider 映射）：复用既有 `knownProviders`/`ProviderService` 映射，不新增路由 ✅
- 原则四（一个目录=一个 Agent + 软连接渐进披露）：**本 feature 即其实现**——`AgentLoader.deriveProfile`、软连接绑定、三层渐进披露、AGENT.md 归 ContextLoader（不进 Tool 模块）✅
- 原则五（审计 Day One）：不改审计写路径；定时触发审计经既有 `AgentScheduler`/`AgentService` 落账 ✅
- 原则六（软连接真实路径校验）：ContextLoader 用 `toRealPath()` 校验目标位于公共 `skills/` 根，拒绝越界链接 ✅
- 原则七（同步执行）：全同步阻塞，无 Reactor/CompletableFuture/自建线程池 ✅
- 原则八（Tool 三合一）：不建新模块；AGENT.md 加载归 core 的 ContextLoader ✅

## Project Structure

### Documentation (this feature)

```text
specs/010-agent-directory/
├── plan.md              # 本文件
├── spec.md              # (/speckit-specify)
├── research.md          # Phase 0：六个设计决策
├── data-model.md        # Phase 1：Agent 目录布局 + frontmatter→Profile 映射
├── quickstart.md        # Phase 1：验证指南
├── contracts/           # Phase 1：profile-derivation.md 契约
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
fourfeetcat-core/src/main/java/org/fourfeetcat/core/
├── profile/
│   ├── AgentLoader.java            # 新增：scan / deriveProfile / detectResources（复用 ProfileLoader）
│   ├── ProfileRegistry.java        # 改：可变并发 Map + register/remove/exists
│   └── ProfileLoader.java          # 改：抽 fromYamlMap(Map) 包内可见，loadOne 复用（行为不变）
├── profile/ → (DerivedAgent/AgentResources 小 record 设于 AgentLoader 文件内)
├── react/
│   ├── ContextLoader.java          # 改：注入 agent 正文 + 软连接技能元数据（兼容分派）
│   └── (ContextLoaderTest)         # 存量不受影响；新增软连接用例（本地 skip）
├── schedule/
│   └── AgentScheduler.java         # 改：public registerProfile + scheduledTasks 句柄表
└── profile/（tests）
    ├── AgentLoaderTest.java        # 新
    ├── DeriveProfileTest.java      # 新（或并入 AgentLoaderTest）
    ├── AgentScanRegisterTest.java  # 新
    ├── ProfileRegistryRuntimeTest.java # 新
    ├── AgentSchedulerRegisterTest.java # 新（core/schedule）
    └── ProgressiveDisclosureTest.java  # 新（core/react）

fourfeetcat-boot/src/main/java/org/fourfeetcat/boot/
└── AgentRuntimeConfiguration.java  # 改：profileRegistry Bean 扫描 agents/ 注册

.fourfeetcat/agents/daily-reconcile/  # 运行时示例（gitignore 不随仓库提交）
├── AGENT.md + scripts/reconcile.py + skills/report-format.md + REFERENCE.md
```

**Structure Decision**: 仅动 `fourfeetcat-core`（Agent 目录机制本体）与 `fourfeetcat-boot`（装配接线），遵循宪法八"不建新模块、跨模块契约在 core"。示例 Agent 目录放运行时工作区，因 `.fourfeetcat/` 被 gitignore 不随仓库提交。

## Complexity Tracking

> 无 Constitution Check 违规需自证。沿用既有模式（ProfileLoader 复用、build-yield assembly），无新增抽象层。