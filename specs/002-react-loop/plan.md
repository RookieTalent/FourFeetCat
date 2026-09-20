# Implementation Plan: ReAct 循环（第17节）

**Branch**: `017-lesson17-react-loop` | **Date**: 2026-09-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-react-loop/spec.md`

## Summary

实现 Agent 的调度内核：`ReActLoop` 自己写下"想—做—看"的循环（上限默认 10，转满强制收尾），`PromptBuilder` 每轮按固定四部分组装请求（system 含启动上下文与当前时间、长期记忆未启用即跳过、历史按最近 N 轮截断、工具说明随 Provider 请求下发），`ToolExecutor` 是工具执行的唯一入口且成败都落 `tool_invocations`，`AgentService` 是三种触发源共用的编排者（`ProfileContext` 进 set 出 clear），`ContextLoader` 每次现读启动信息与能力包元数据。跨模块只经四个 core 端口倒置（`LlmCaller`/`SessionManager`/`ToolTable`/`ToolInvocationRecorder`），会话持久化与工具注册的真实实现分别归 18/20 节。

## Technical Context

**Language/Version**: Java 21（Maven 多模块，spring-boot-starter-parent 3.5.16）

**Primary Dependencies**: 本节**不新增任何依赖**。复用：Spring AI 1.1.2 `spring-ai-model`（`ChatResponse`/`AssistantMessage`/`ToolResponseMessage`/`Prompt`，core 已于第16节直依，已 `javap` 核实，见 research.md D11）；测试用既有的 JUnit 5 + Mockito；storage 侧复用既有 Spring Data JPA + sqlite-jdbc。工具输入输出走 JSON 字符串，不引 Jackson。

**Storage**: SQLite（默认）+ PostgreSQL 双轨建表脚本 `fourfeetcat-storage/src/main/resources/db/migration/{sqlite,postgresql}/V17__tool_invocations.sql`（同版本号、方言各自正确，逐字摘自 `docs/class/schema.sql` 的 `tool_invocations` 段）；`ddl-auto: none`；测试建表走手工脚本 + `jdbc:sqlite:<@TempDir>/test.db` 文件库（不用 `:memory:`）。

**Testing**: JUnit 5 + Mockito。单测（默认跑）：`ReActLoopTest`、`PromptBuilderTest`、`ToolExecutorTest`、`AgentServiceTest`、`ContextLoaderTest`（core）、`ToolInvocationRepositoryTest`（storage）。本节无集成冒烟（不碰网络与真模型）。

**Target Platform**: JVM 服务端（后续 CLI/HTTP/定时三类入口消费）

**Project Type**: library（多模块底座）

**Performance Goals**: 无专项指标（单轮同步阻塞；Virtual Thread 处理并发）

**Constraints**: 宪法原则一（循环自实现，不得用 Spring AI Agent 抽象）、原则二（不引任何自动工具执行路径）、原则五（审计 Day One）、原则七（同步，无 Reactor/CompletableFuture/自建线程池）；语法禁区——避开 P3C/ASM 解析不了的 Java 18+ 形态（增强 switch 的 `default ->` 等）；测试方法名英文 + `@DisplayName` 保留课件中文原文。

**Scale/Scope**: 第17节交付物清单 + 四个前向端口，不加不减（多出的 `ToolInvocationRepositoryTest` 为建表脚本口径的守卫测试，见 tasks 比对说明）。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|---|---|---|
| 一：自实现 ReAct | 循环手写数十行（`ReActLoop`），不引 `ChatClient`/Agent 抽象；执行权唯一落在 `ToolExecutor` | ✅ 通过 |
| 二：Spring AI 只用两件事 | 仅消费其消息/请求类型；第16节已显式 `internalToolExecutionEnabled(false)`，本节不新增任何工具执行路径（`ToolTable` 是自有端口） | ✅ 通过 |
| 三：Provider 显式映射 | 本节不碰（经 `LlmCaller` 端口消费第16节成果） | ✅ 通过 |
| 四：一个目录 = 一个 Agent | 能力包元数据本节按 Profile 名单直读公共库，第29节换软连接视图（Clarifications Q2） | ✅ 通过（口径已记录） |
| 五：审计 Day One | 每次工具执行成败都落 `tool_invocations`（含 `success`/`error_message`），实体+Repository+建表脚本同节交付 | ✅ 通过 |
| 六：无 SecurityManager、真实路径校验 | 本节不涉文件白名单；沙箱检查位留调用点并注明 24 节接线（research.md D7） | ✅ 通过（记录取舍） |
| 七：同步执行 | 全链路同步阻塞，无异步类型 | ✅ 通过 |
| 技术约束：Flyway 双轨 | 本节起双轨落地（sqlite + postgresql，同版本号）；第16节 V16 的 sqlite-only 欠账不动（Clarifications Q3） | ✅ 通过 |
| 技术约束：依赖倒置 | 跨模块四端口全落 core，core 不依赖 provider/storage | ✅ 通过 |

无违宪项，Complexity Tracking 不需要。

## Project Structure

### Documentation (this feature)

```text
specs/002-react-loop/
├── plan.md              # 本文件
├── research.md          # Phase 0：12 条设计决策 + 依赖核实
├── data-model.md        # Phase 1：tool_invocations 表（双方言）+ Session 结构
├── quickstart.md        # Phase 1：验证指南（判卷命令块）
├── contracts/           # Phase 1：跨模块 Java 契约（四个端口 + 五个交付类）
└── tasks.md             # /speckit-tasks 产出（本命令不创建）
```

### Source Code (repository root)

```text
fourfeetcat-core/src/
├── main/java/org/fourfeetcat/core/
│   ├── ToolDescriptor.java                     # 既有（第16节）
│   ├── LlmCallRecorder.java                    # 既有（第16节）
│   ├── session/
│   │   ├── Session.java                        # 新增：内存态会话（id/profileName/channel/userId + List<Message>）
│   │   └── SessionManager.java                 # 前向端口：本节只有 save(Session)
│   ├── tool/
│   │   ├── ToolTable.java                      # 前向端口：descriptors + execute（20 节合并）
│   │   ├── ToolExecutionResult.java            # 前向 record：与 20 节 ToolResult 四字段对齐
│   │   └── ToolInvocationRecorder.java         # 审计端口（依赖倒置，storage 实现）
│   └── react/
│       ├── LlmCaller.java                      # 前向端口：与 ProviderService.chat 同签名
│       ├── ReActLoop.java                      # 调度内核（课件主角）
│       ├── PromptBuilder.java                  # 每轮请求组装（四部分 + 历史截断）
│       ├── ToolExecutor.java                   # 工具执行唯一入口 + 审计
│       ├── AgentService.java                   # 编排者（ProfileContext 进 set 出 clear）
│       ├── ProfileContext.java                 # ThreadLocal 当前 Agent
│       ├── ContextLoader.java                  # 启动信息 + 能力包元数据，现读不缓存
│       └── ProfileSettings.java                # 包内私有：max_iterations=10 / max_history_turns=20 的读取与回落
└── test/java/org/fourfeetcat/core/react/
    ├── ReActLoopTest.java                      # 课件 harness
    ├── PromptBuilderTest.java                  # 课件 harness
    ├── ToolExecutorTest.java                   # 课件 harness
    ├── AgentServiceTest.java                   # 课件 harness
    └── ContextLoaderTest.java                  # 课件 harness

fourfeetcat-storage/src/
├── main/java/org/fourfeetcat/storage/
│   ├── ToolInvocation.java                     # JPA 实体（列名与 V17 脚本逐字一致）
│   ├── ToolInvocationRepository.java           # JpaRepository
│   └── ToolInvocationRecorderImpl.java         # 端口实现（@Component，与 16 节同款）
├── main/resources/db/migration/sqlite/V17__tool_invocations.sql
├── main/resources/db/migration/postgresql/V17__tool_invocations.sql
└── test/java/org/fourfeetcat/storage/ToolInvocationRepositoryTest.java
```

**Structure Decision**: 跨模块契约（四个端口 + 值对象）全落 `fourfeetcat-core`，下游模块实现（依赖倒置）；循环与编排作为一个能力域落 `core/react`，会话与工具契约各自独立成包（`core/session`、`core/tool`）以便 18/20 节就地接续；持久化（实体 + 仓储 + 双轨脚本）归 `fourfeetcat-storage`。与 `docs/TechnicalSolution.md` §10 一致（oryxos-* → fourfeetcat-* 映射已记入 spec Assumptions）。模块无新建、无改名，无需同步 §10 模块表。
