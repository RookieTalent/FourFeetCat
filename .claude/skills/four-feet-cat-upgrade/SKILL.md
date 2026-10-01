---
name: four-feet-cat-upgrade
description: >-
  按模块升级 FourFeetCat 追随 oryxos：输入路线图序号或模块名（如 01 / persona），走
  读 oryxos 源码 → 写升级文档到 docs/extension/ → 停点等确认 → 编码 → 停点走查 →
  总结的固定流程，一次只做一个升级单元。当用户说「升级第 N 个模块 / 迁移 oryxos 的
  某模块 / 用升级流程做 persona」时使用。
argument-hint: "路线图序号或模块名，如：01 或 persona"
user-invocable: true
---

# FourFeetCat 逐模块升级 Skill

## User Input

```text
$ARGUMENTS
```

升级单元来自上面的输入（序号或模块名），以 `docs/extension/README.md` 路线图为唯一事实来源。本 skill 的目标是**让主公学会**，不是把代码搬完——每一步都以可讲解为先。

## 不做什么（边界）

- 一次只做一个升级单元，做完停下，不连做
- 全程不自动 commit / push，同步时机由主公决定
- 两个固定停点（第 3、6 步）必须等主公，不许跳过
- 遵守 CLAUDE.md 宪法八原则，oryxos 与宪法冲突时停下报告，不静默照搬

## 第 1 步：定位升级单元

读 `docs/extension/README.md` 路线图，按输入（序号或模块名）找到目标行。找不到、或该单元的前置单元未完成 → 报错退出并说明。在 feature 分支上开工（不在 main 直接开发）。

## 第 2 步：读 oryxos 对应实现

用 Read 完整读 oryxos（`D:\resume\企业级AI编程实战\oryxos`）对应模块源码与 `specs/` 规格（如有）。同时读 FourFeetCat 中与之对应的现状代码，列出差异清单。

## 第 3 步：写升级文档 → 停点一

写 `docs/extension/<NN>-<module>.md`，五段固定结构：

1. **oryxos 为什么这么做**——目的、解决什么问题（引自源码/规格的具体证据，不空谈）
2. **FourFeetCat 现状差距**——已有的、缺的、设计不一样的
3. **迁移方案**——要建/改哪些模块、类、表、配置；对齐现有测试惯例（单测进各模块，`*IT` + `@Tag("integration")` 进 boot）
4. **有没有更好的方式**——批判性评估：oryxos 的取舍是否有更简单的等价做法，FourFeetCat 该照搬还是改良
5. **面试考点**——这段能力怎么讲、会被追问什么

**停点一**：输出文档路径与摘要，停下等主公读完确认。主公未确认不得编码。

## 第 4 步：编码迁移

按已确认的文档实施，附加门禁：

- 只建文档点名的对外概念，字面量（类名/配置键/表列名/端点路径）与 oryxos 语义对齐时逐字保真
- 测试与实现一起落地，红了当场修；测试方法名必须英文，课件/文档原文放 `@DisplayName`
- 每模块留最小可跑测试；构建门禁 `mvn clean verify` 全绿（本机构建须先设 `JAVA_HOME` 指向 JDK 21，mvn 不在 PATH，见构建工具链备忘）

## 第 5 步：验收

- `mvn clean verify` 全绿，贴关键输出
- 升级文档"迁移方案"逐项存在性核对（ls/glob）
- 前序模块测试回归绿（跨模块契约证据）
- 涉及新表/新列：Flyway 双轨脚本（sqlite + postgresql，同版本号只增不改）

## 第 6 步：走查与总结 → 停点二

1. **停点二**：输出变更总结（`git status --short` / `git diff --stat` 实测为准）——改动点按模块分组、重点 review 清单 3~6 条（按风险排序）、可复制的验证命令块。与主公一起走查关键代码，讲解实现，等主公确认。
2. 主公确认后收尾：
   - 勾选 `docs/extension/README.md` 路线图进度
   - 提炼本单元面试话术要点（输出在对话里，后续由主公决定是否整理进 `docs/interview/`）
   - 按 CLAUDE.md 三处同步规则更新 README.md / 官网 Home.vue / docs 设计文档中受影响的模块表述

收尾完停止——commit/push 由主公决定。
