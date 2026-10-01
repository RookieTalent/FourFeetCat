# 拓展目录：FourFeetCat 循序追随 oryxos 的升级档案

## 目录用途

本目录存放 FourFeetCat 对照升级版 [oryxos](D:\resume\企业级AI编程实战\oryxos) 的**逐模块升级文档**，一篇文档 = 一个升级单元。升级流程由 `.claude/skills/four-feet-cat-upgrade/SKILL.md` 驱动：

```
读 oryxos 对应模块 → 写升级文档（本目录）→ 停点等主公确认 → 编码 → 停点走查 → 总结
```

文档命名：`<NN>-<module>.md`（NN 为路线图序号，如 `01-persona.md`）。每篇固定五段：

1. **oryxos 为什么这么做**——目的与解决什么问题（引自源码 + specs）
2. **FourFeetCat 现状差距**——已有的、缺的、不一样的
3. **迁移方案**——要建/改哪些模块、类、表
4. **有没有更好的方式**——批判性评估，不盲从
5. **面试考点**——这段能力在面试里怎么讲、会被追问什么

## 迁移路线图

顺序原则：循 oryxos 自身演进（单机内核 → 管理台 → 渠道 → 企业增强），依赖前置、风险递增。W1 完成后 CLAUDE.md 所列 14 模块与实际对齐。

| 批次 | 升级单元 | 对应 oryxos | 理由 | 进度 |
|---|---|---|---|---|
| W0 试点 | [01 persona](01-persona.md) | oryxos-persona（7 文件） | 最小模块，先跑通升级流程 | ☐ |
| W1 补洞 | 02 knowledge | spec 014 | CLAUDE.md 已规划未建，契约在 core/knowledge | ☐ |
| W1 补洞 | 03 channel-feishu | spec 017 | 渠道契约已在 core/channel，教学重点 | ☐ |
| W1 补洞 | 04 channel-wecom | 对称飞书 | 复用共享编排，验证依赖倒置 | ☐ |
| W1 补洞 | 05 channel-dingtalk | 对称企微 | 含断线重连对齐 | ☐ |
| W2 存量升级 | 06 provider | 动态注册表 CRUD / ToolSchemaAdapter / Embedding 工厂 / 模型路由(051) | 对照 oryxos 同名模块增强 | ☐ |
| W2 存量升级 | 07 tool | 容器沙箱(024) / WebSearch、Pdf、ExecuteCode 等 builtin / delegate_agent | | ☐ |
| W2 存量升级 | 08 memory | Mem0 后端补第四档 | | ☐ |
| W2 存量升级 | 09 storage | MySQL 支持 / 审计增强 | | ☐ |
| W2 存量升级 | 10 web + cli | SSE 流式 / 管理台增强 / 新子命令 | | ☐ |
| W3 企业增强 | 11 HITL 审批 | spec 042 | 依赖 web+渠道，先于 Flow | ☐ |
| W3 企业增强 | 12 A2A 协议 | Agent Card / JSON-RPC / peer 路由 | TeamTask 的前置 | ☐ |
| W3 企业增强 | 13 TeamTask 编排 | 多 Agent 团队任务（north star） | oryxos 最新主打 | ☐ |
| W3 企业增强 | 14 Flow DSL | spec 045/046 + durable 执行 | 依赖 A2A/TeamTask 节点 | ☐ |
| W3 企业增强 | 15 Eval 评测 | spec 048 | 依赖 Agent/Flow 已稳 | ☐ |
| W3 企业增强 | 16 Cost 成本治理 | spec 050 | | ☐ |
| W3 企业增强 | 17 集群分布式 | 版本总线/CAS/租约/文件面(027) | 最难、重运维，压轴 | ☐ |

已定决策（2026-09-29）：

- **IM 渠道只做飞书/企微/钉钉三家**——讲透一个渠道契约比罗列 19 个有说服力，且是课件正文内容
- **企业增强四组全做**：A2A+TeamTask 编排、Flow DSL+HITL、Eval+Cost、集群+容器沙箱+模型路由
- **一次只做一个升级单元**，不一次性搬代码；编码前后各有一个固定停点

每完成一个单元：勾选上表进度、总结面试话术要点（后续可整理进 `docs/interview/`）、按三处同步规则（README / 官网 / docs）更新模块表述。

> 集群分布式（17）依赖真实多实例环境，届时评估是否降级为单机演示版。

## 试点引导

首个升级单元为 **01 persona**（oryxos 最小模块，含 12 内置人格预设 + `.fourfeetcat/personas/` 自定义 CRUD）。确认路线图无误后，执行 `/four-feet-cat-upgrade 01` 启动第一轮。
