<script setup>
import { computed } from 'vue'
import { withBase } from 'vitepress'

const props = defineProps({ lang: { type: String, default: 'zh' } })
const isZh = computed(() => props.lang === 'zh')
const t = (zh, en) => (isZh.value ? zh : en)
const base = (p) => withBase(p)

const gates = [
  { n: '01', title: ['定义 Agent 要写代码', 'Defining an Agent takes code'], pain: ['最懂业务的人反而做不了', 'The domain experts are locked out'], fix: ['自然语言定义：一个目录 = 一个 Agent', 'NL-defined: one directory = one Agent'] },
  { n: '02', title: ['云平台把数据拿走', 'Cloud platforms take your data'], pain: ['合规过不去', 'Compliance says no'], fix: ['私有部署：数据不出企业域', 'Private deploy: data never leaves'] },
  { n: '03', title: ['执行是黑盒', 'Execution is a black box'], pain: ['没审计、没白名单、没人审批', 'No audit, no allowlist, no approval'], fix: ['全链路审计 + 沙箱白名单', 'Full-chain audit + sandbox allowlists'] },
  { n: '04', title: ['跑一个容易、跑一群难', 'One Agent is easy, a fleet is hard'], pain: ['没人给你 Agent 的操作系统', 'Nobody hands you the Agent OS'], fix: ['一整队 Agent 的生命周期与治理', 'Lifecycle & governance for the whole fleet'] }
]

const features = [
  { icon: '🤖', title: ['一个目录 = 一个 Agent', 'One directory = one Agent'], desc: ['AGENT.md 定义一切，零代码，多 Agent 并存', 'AGENT.md defines everything — zero code, multi-Agent coexistence'] },
  { icon: '☕', title: ['Java 原生', 'Java native'], desc: ['JDK 21 + Spring Boot 3.x，单 JAR 部署，复用企业运维链', 'JDK 21 + Spring Boot 3.x, single-JAR deploy, reuses your ops toolchain'] },
  { icon: '🔒', title: ['私有可控', 'Private & sovereign'], desc: ['装在自己的 K8s / 物理机上，不锁任何云', 'Runs on your own K8s / bare metal, locked to no cloud'] },
  { icon: '🛡️', title: ['安全隔离', 'Security by design'], desc: ['白名单沙箱 + 凭证不落地 + 全链路审计，第一天就在架构里', 'Allowlist sandbox, non-landing credentials, full audit — day one'] },
  { icon: '🧠', title: ['自实现 ReAct', 'Hand-rolled ReAct'], desc: ['推理循环自己写，约数十行 Java，机制完全可控', 'The loop is ours — tens of lines of Java, fully controllable'] },
  { icon: '🔌', title: ['开放标准', 'Open standards'], desc: ['工具用 MCP、协作用 A2A、技能借 Anthropic Agent Skills 形态', 'MCP for tools, A2A for collaboration, Anthropic Agent Skills shape'] },
  { icon: '🧩', title: ['三档扩展', 'Three extension tiers'], desc: ['零代码目录 → 自写 MCP server → @Tool Bean，按门槛自选', 'Zero-code dir → custom MCP server → @Tool Bean, pick your tier'] },
  { icon: '💾', title: ['跨对话记忆', 'Memory across sessions'], desc: ['会话 + 长期两层记忆，Agent 记得住上下文与偏好', 'Session + long-term memory — the Agent remembers'] },
  { icon: '🌐', title: ['无状态可扩展', 'Stateless & scalable'], desc: ['实例无状态、状态外置，为分布式留好路', 'Stateless instances, externalized state, distributed-ready'] }
]
</script>

<template>
  <div class="oryx-home">
    <!-- ═══════════ 顶栏 ═══════════ -->
    <header class="topbar">
      <a :href="base(isZh ? '/' : '/en/')" class="brand">
        <img :src="base('/images/logo-icon.svg')" alt="OryxOS" class="brand-icon" />
        <span class="brand-name">Oryx<span class="brand-os">OS</span></span>
      </a>
      <nav class="topnav">
        <a :href="base(isZh ? '/en/' : '/')" class="nav-lang">{{ isZh ? 'English' : '简体中文' }}</a>
        <a href="https://github.com/RookieTalent/OryxOS" target="_blank" class="nav-gh">
          <svg width="15" height="15" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27s1.36.09 2 .27c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z"/></svg>
          GitHub
        </a>
      </nav>
    </header>

    <!-- ═══════════ Hero ═══════════ -->
    <section class="hero">
      <div class="hero-glow hero-glow-l"></div>
      <div class="hero-glow hero-glow-r"></div>
      <div class="hero-grid"></div>
      <div class="hero-inner">
        <img :src="base('/images/logo-icon.svg')" alt="OryxOS logo" class="hero-logo" />
        <h1 class="hero-title">
          {{ t('企业 Agent 操作系统', 'The Agent Harness OS for Enterprises') }}
        </h1>
        <p class="hero-sub">
          {{ t(
            '用一句自然语言发布一个任务 → 底座把它拆解 → 组织一支 Agent 团队 → 多个 Agent 分工协作 → 交付一个结果。',
            'Publish a task in one sentence of natural language → the harness decomposes it → assembles an Agent team → Agents collaborate → and delivers the result.'
          ) }}
        </p>
        <code class="hero-formula">
          {{ t('自然语言(md) + Memory + Tool + MCP + Skill + 知识库 + Notify = 一个 Agent', 'nl(md) + Memory + Tool + MCP + Skill + Knowledge + Notify = one Agent' ) }}
        </code>
        <div class="hero-actions">
          <a href="https://github.com/RookieTalent/OryxOS" target="_blank" class="btn btn-primary">
            {{ t('GitHub 仓库', 'View on GitHub') }}
          </a>
          <a href="#quickstart" class="btn btn-ghost">{{ t('快速开始', 'Quick Start') }}</a>
        </div>
        <div class="hero-chips">
          <span class="chip">{{ t('🔒 私有部署 · 数据不出域', '🔒 Private · data stays in-domain') }}</span>
          <span class="chip">{{ t('☕ Java 原生 · JDK 21', '☕ Java native · JDK 21') }}</span>
          <span class="chip">{{ t('🧠 自实现 ReAct', '🧠 Hand-rolled ReAct loop') }}</span>
          <span class="chip">{{ t('🔌 MCP · A2A 开放标准', '🔌 MCP · A2A open standards') }}</span>
        </div>
      </div>
    </section>

    <!-- ═══════════ 四道门槛 ═══════════ -->
    <section class="oryx-section">
      <p class="oryx-kicker">{{ t('为什么需要 OryxOS', 'Why OryxOS') }}</p>
      <h2 class="oryx-h2">{{ t('Agent 卡在 demo，卡在四道门槛上', 'Agents stall at the demo — four gates block them') }}</h2>
      <p class="oryx-lead">
        {{ t('让 Agent 在生产环境可靠工作，瓶颈通常不在模型，而在运行环境。OryxOS 做的不是又一个 Agent，而是让一群 Agent 可靠运行和协同的底座本身。',
             'The bottleneck for reliable production Agents is rarely the model — it is the runtime. OryxOS is not another Agent; it is the harness that lets a fleet of Agents run reliably.') }}
      </p>
      <div class="gates">
        <div class="gate" v-for="g in gates" :key="g.n">
          <span class="gate-no">{{ g.n }}</span>
          <h3>{{ t(g.title[0], g.title[1]) }}</h3>
          <p class="gate-pain">{{ t(g.pain[0], g.pain[1]) }}</p>
          <p class="gate-fix">
            <span class="gate-arrow">↳</span>{{ t(g.fix[0], g.fix[1]) }}
          </p>
        </div>
      </div>
    </section>

    <!-- ═══════════ 一个目录 = 一个 Agent ═══════════ -->
    <section class="oryx-section">
      <p class="oryx-kicker">{{ t('核心理念', 'Core Idea') }}</p>
      <h2 class="oryx-h2">{{ t('一个目录 = 一个 Agent', 'One directory = one Agent') }}</h2>
      <div class="agent-demo">
        <div class="agent-demo-left">
          <p class="oryx-lead" style="margin-top:0">
            {{ t('一个包含 AGENT.md 的目录就定义一个完整 Agent：frontmatter 是运行配置，正文是任务指令。不用写代码，多个 Agent 同实例并存。',
                 'A directory containing AGENT.md defines a complete Agent: frontmatter is the runtime profile, the body is the task instruction. No code. Multiple Agents coexist on one instance.') }}
          </p>
          <ul class="ticks">
            <li>{{ t('Provider / Tool / Channel / 定时规则，全部在 frontmatter 声明', 'Provider / Tools / Channels / cron schedules — all declared in frontmatter') }}</li>
            <li>{{ t('Skill 经本地软连接绑定，渐进式披露按需加载', 'Skills bind via local symlinks, progressively disclosed on demand') }}</li>
            <li>{{ t('改完目录即生效，无需重启', 'Edit the directory, changes take effect — no restart') }}</li>
          </ul>
        </div>
        <div class="codecard">
          <div class="codecard-bar"><span class="dot r"></span><span class="dot y"></span><span class="dot g"></span><span class="codecard-path">.oryxos/agents/ops-agent/AGENT.md</span></div>
          <pre class="codecard-body"><code>---
<span class="k">name</span>: ops-agent
<span class="k">provider</span>:
  name: deepseek
  model: deepseek-chat
  api_key: ${DEEPSEEK_API_KEY}
<span class="k">tools</span>: [read_file, shell, http_get, notify]
<span class="k">mcp_servers</span>: [github-mcp]
---
<span class="c"># 正文即指令，注入 system prompt</span>
你是一个运维助手。每天 08:00 查询告警，
调日志工具定位根因，处理完推送群通知。</code></pre>
        </div>
      </div>
    </section>

    <!-- ═══════════ 核心特性 ═══════════ -->
    <section class="oryx-section">
      <p class="oryx-kicker">{{ t('核心特性', 'Features') }}</p>
      <h2 class="oryx-h2">{{ t('五大核心能力，一套底座', 'Five core capabilities, one harness') }}</h2>
      <div class="features">
        <div class="feat" v-for="f in features" :key="f.icon">
          <span class="feat-icon">{{ f.icon }}</span>
          <h3>{{ t(f.title[0], f.title[1]) }}</h3>
          <p>{{ t(f.desc[0], f.desc[1]) }}</p>
        </div>
      </div>
    </section>

    <!-- ═══════════ 架构 ═══════════ -->
    <section class="oryx-section">
      <p class="oryx-kicker">{{ t('架构', 'Architecture') }}</p>
      <h2 class="oryx-h2">{{ t('三个入口，一个引擎', 'Three entrances, one engine') }}</h2>
      <p class="oryx-lead">
        {{ t('CLI（人推）、REST API（人推）、定时任务（钟推）最终都汇入同一个 AgentService——ReAct 引擎不感知消息从哪个入口来。',
             'CLI (human push), REST API (human push) and cron scheduler (clock push) all converge into one AgentService — the ReAct engine never knows where a message came from.') }}
      </p>
      <figure class="arch">
        <img :src="base('/images/architecture.svg')" :alt="t('OryxOS 整体架构图', 'OryxOS architecture diagram')" />
        <figcaption>{{ t('五层结构：接入层 → 引擎层 → 能力层 → 基础层；虚线为应用边界之外的外部依赖', 'Five layers: access → engine → capability → foundation; dashed = external dependencies beyond the boundary') }}</figcaption>
      </figure>
    </section>

    <!-- ═══════════ 快速开始 ═══════════ -->
    <section id="quickstart" class="oryx-section">
      <p class="oryx-kicker">{{ t('快速开始', 'Quick Start') }}</p>
      <h2 class="oryx-h2">{{ t('三步跑起来', 'Up and running in three steps') }}</h2>
      <div class="codecard steps-card">
        <div class="codecard-bar"><span class="dot r"></span><span class="dot y"></span><span class="dot g"></span><span class="codecard-path">{{ t('终端', 'terminal') }}</span></div>
        <pre class="codecard-body"><code><span class="c"># 1. 初始化工作区（幂等，不覆盖已有文件）</span>
oryxos init

<span class="c"># 2. 注入模型凭证（不明文写进配置）</span>
export DEEPSEEK_API_KEY=sk-...

<span class="c"># 3. 开始对话</span>
oryxos chat</code></pre>
      </div>
      <p class="oryx-lead steps-note">
        {{ t('业务系统集成走 REST API（oryxos serve :8080），三种运行模式共享同一份 Agent 配置与 Session 存储。',
             'Business systems integrate via REST (oryxos serve :8080); all three run modes share the same Agent config and session store.') }}
      </p>
    </section>

    <!-- ═══════════ 路线图 ═══════════ -->
    <section class="oryx-section">
      <p class="oryx-kicker">{{ t('路线图', 'Roadmap') }}</p>
      <h2 class="oryx-h2">{{ t('慢就是快，克制且聚焦', 'Slow is fast — restrained and focused') }}</h2>
      <div class="roadmap">
        <div class="phase now">
          <span class="phase-tag">{{ t('当前', 'NOW') }}</span>
          <h3>{{ t('阶段一 · 单机运行时内核', 'Phase 1 · Standalone runtime kernel') }}</h3>
          <p>{{ t('五大核心能力跑通：配置即 Agent、多 Agent 并存、REST API、对接 MCP', 'Five core capabilities: config-as-Agent, multi-Agent coexistence, REST API, MCP') }}</p>
        </div>
        <div class="phase">
          <span class="phase-tag">{{ t('规划', 'NEXT') }}</span>
          <h3>{{ t('阶段二 · 底座分布式', 'Phase 2 · Distributed harness') }}</h3>
          <p>{{ t('节点无状态化、状态外置、多副本部署，支撑更大规模与高可用', 'Stateless nodes, externalized state, replicas for scale and HA') }}</p>
        </div>
        <div class="phase">
          <span class="phase-tag">{{ t('愿景', 'VISION') }}</span>
          <h3>{{ t('阶段三 · 跨节点 Agent 协作', 'Phase 3 · Cross-node collaboration') }}</h3>
          <p>{{ t('Agent 通信底座，对接 A2A，跨节点发现、委托、可靠异步协同', 'Agent communication fabric over A2A — discovery, delegation, reliable async') }}</p>
        </div>
      </div>
    </section>

    <!-- ═══════════ 页脚 ═══════════ -->
    <footer class="footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <img :src="base('/images/logo-icon.svg')" alt="OryxOS" class="footer-logo" />
          <span>{{ t('一个目录定义一个 Agent，一个底座运行一群 Agent。', 'One directory defines an Agent; one harness runs them all.') }}</span>
        </div>
        <div class="footer-links">
          <a href="https://github.com/RookieTalent/OryxOS" target="_blank">GitHub</a>
          <span>Apache License 2.0</span>
          <span>{{ t('由 oryx-labs 社区维护', 'Built by the oryx-labs community') }}</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
/* ── 顶栏 ── */
.topbar { position: sticky; top: 0; z-index: 50; display: flex; justify-content: space-between; align-items: center;
  max-width: 1080px; margin: 0 auto; padding: 16px 24px;
  background: rgba(15, 23, 42, 0.72); backdrop-filter: blur(12px); }
.brand { display: flex; align-items: center; gap: 10px; text-decoration: none; }
.brand-icon { width: 30px; height: 30px; }
.brand-name { font-size: 20px; font-weight: 800; color: #F8FAFC; letter-spacing: 0.5px; }
.brand-os { color: var(--oryx-indigo); }
.topnav { display: flex; gap: 18px; align-items: center; }
.nav-lang { color: var(--oryx-muted); text-decoration: none; font-size: 14px; }
.nav-lang:hover { color: #E2E8F0; }
.nav-gh { display: inline-flex; align-items: center; gap: 7px; font-size: 14px; color: #E2E8F0;
  text-decoration: none; padding: 7px 14px; border: 1px solid var(--oryx-border); border-radius: 999px; }
.nav-gh:hover { border-color: rgba(99, 102, 241, 0.6); background: rgba(99, 102, 241, 0.12); }

/* ── Hero ── */
.hero { position: relative; overflow: hidden; text-align: center; padding: 96px 24px 110px; }
.hero-glow { position: absolute; width: 640px; height: 640px; border-radius: 50%; filter: blur(120px); opacity: 0.32; }
.hero-glow-l { background: #4F46E5; top: -220px; left: -140px; }
.hero-glow-r { background: #7C3AED; bottom: -280px; right: -160px; }
.hero-grid { position: absolute; inset: 0;
  background-image: linear-gradient(rgba(148,163,184,0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(148,163,184,0.05) 1px, transparent 1px);
  background-size: 56px 56px; mask-image: radial-gradient(ellipse 70% 60% at 50% 40%, #000 40%, transparent 100%); }
.hero-inner { position: relative; max-width: 860px; margin: 0 auto; }
.hero-logo { width: 96px; height: 96px; margin-bottom: 28px; filter: drop-shadow(0 0 28px rgba(99, 102, 241, 0.45)); }
.hero-title { font-size: 52px; font-weight: 900; line-height: 1.15; color: #F8FAFC; margin: 0 0 18px; }
.hero-sub { font-size: 17px; line-height: 1.85; color: var(--oryx-muted); max-width: 700px; margin: 0 auto 30px; }
.hero-formula { display: inline-block; font-family: var(--oryx-mono); font-size: 13px; color: #A5B4FC;
  background: rgba(99, 102, 241, 0.1); border: 1px solid rgba(99, 102, 241, 0.35);
  border-radius: 10px; padding: 10px 18px; margin-bottom: 34px; }
.hero-actions { display: flex; gap: 14px; justify-content: center; margin-bottom: 34px; }
.btn { font-size: 15px; font-weight: 600; text-decoration: none; border-radius: 10px; padding: 12px 26px; }
.btn-primary { background: linear-gradient(120deg, #6366F1, #8B5CF6); color: #fff; box-shadow: 0 8px 24px rgba(99, 102, 241, 0.35); }
.btn-primary:hover { filter: brightness(1.1); }
.btn-ghost { border: 1px solid var(--oryx-border); color: #E2E8F0; }
.btn-ghost:hover { border-color: rgba(99, 102, 241, 0.6); background: rgba(99, 102, 241, 0.1); }
.hero-chips { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }
.chip { font-size: 13px; color: var(--oryx-muted); background: var(--oryx-card); border: 1px solid var(--oryx-border); border-radius: 999px; padding: 6px 14px; }

/* ── 四道门槛 ── */
.gates { display: grid; grid-template-columns: repeat(4, 1fr); gap: 18px; margin-top: 40px; }
.gate { background: var(--oryx-card); border: 1px solid var(--oryx-border); border-radius: 14px; padding: 24px 20px; }
.gate-no { font-family: var(--oryx-mono); font-size: 12px; color: var(--oryx-indigo); letter-spacing: 2px; }
.gate h3 { font-size: 16px; font-weight: 700; color: #F8FAFC; margin: 10px 0 6px; }
.gate-pain { font-size: 13px; color: var(--oryx-muted); margin: 0 0 16px; min-height: 2.6em; }
.gate-fix { font-size: 13px; color: #A5B4FC; margin: 0; border-top: 1px dashed var(--oryx-border); padding-top: 12px; }
.gate-arrow { margin-right: 6px; color: var(--oryx-indigo); }

/* ── 一个目录 = 一个 Agent ── */
.agent-demo { display: grid; grid-template-columns: 1fr 1.15fr; gap: 36px; align-items: center; margin-top: 40px; }
.ticks { list-style: none; padding: 0; margin: 22px 0 0; }
.ticks li { font-size: 14.5px; color: var(--oryx-text); line-height: 1.7; padding: 9px 0 9px 30px; position: relative; border-bottom: 1px dashed var(--oryx-border); }
.ticks li::before { content: '✓'; position: absolute; left: 4px; color: var(--oryx-indigo); font-weight: 800; }

/* ── 代码卡（终端风） ── */
.codecard { background: #0B1020; border: 1px solid var(--oryx-border); border-radius: 14px; overflow: hidden;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.4); }
.codecard-bar { display: flex; align-items: center; gap: 8px; padding: 12px 16px; background: rgba(30, 41, 59, 0.6); border-bottom: 1px solid var(--oryx-border); }
.dot { width: 11px; height: 11px; border-radius: 50%; }
.dot.r { background: #F87171; } .dot.y { background: #FBBF24; } .dot.g { background: #34D399; }
.codecard-path { margin-left: 10px; font-family: var(--oryx-mono); font-size: 12px; color: var(--oryx-muted); }
.codecard-body { margin: 0; padding: 20px 22px; font-family: var(--oryx-mono); font-size: 13px; line-height: 1.85; color: #CBD5E1; overflow-x: auto; }
.codecard-body :deep(.k) { color: #A5B4FC; }
.codecard-body :deep(.c) { color: #64748B; }

/* ── 特性 ── */
.features { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; margin-top: 40px; }
.feat { background: var(--oryx-card); border: 1px solid var(--oryx-border); border-radius: 14px; padding: 26px 24px; transition: border-color 0.2s, transform 0.2s; }
.feat:hover { border-color: rgba(99, 102, 241, 0.5); transform: translateY(-3px); }
.feat-icon { font-size: 26px; }
.feat h3 { font-size: 16px; font-weight: 700; color: #F8FAFC; margin: 14px 0 8px; }
.feat p { font-size: 13.5px; line-height: 1.7; color: var(--oryx-muted); margin: 0; }

/* ── 架构图 ── */
.arch { margin: 40px 0 0; }
.arch img { width: 100%; border-radius: 14px; border: 1px solid var(--oryx-border); display: block; }
.arch figcaption { text-align: center; font-size: 13px; color: var(--oryx-muted); margin-top: 14px; }

/* ── 快速开始 ── */
.steps-card { margin-top: 40px; }
.steps-note { margin-top: 18px; }

/* ── 路线图 ── */
.roadmap { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; margin-top: 40px; }
.phase { background: var(--oryx-card); border: 1px solid var(--oryx-border); border-radius: 14px; padding: 26px 24px; }
.phase.now { border-color: rgba(99, 102, 241, 0.55); background: linear-gradient(160deg, rgba(99, 102, 241, 0.14), rgba(30, 41, 59, 0.55)); }
.phase-tag { display: inline-block; font-size: 11px; font-weight: 800; letter-spacing: 2px; color: #A5B4FC;
  border: 1px solid rgba(99, 102, 241, 0.4); border-radius: 999px; padding: 3px 10px; margin-bottom: 14px; }
.phase h3 { font-size: 16px; font-weight: 700; color: #F8FAFC; margin: 0 0 10px; }
.phase p { font-size: 13.5px; line-height: 1.7; color: var(--oryx-muted); margin: 0; }

/* ── 页脚 ── */
.footer { margin-top: 100px; border-top: 1px solid var(--oryx-border); background: rgba(11, 16, 32, 0.6); }
.footer-inner { max-width: 1080px; margin: 0 auto; padding: 34px 24px; display: flex; justify-content: space-between; align-items: center; gap: 20px; flex-wrap: wrap; }
.footer-brand { display: flex; align-items: center; gap: 12px; color: var(--oryx-muted); font-size: 13.5px; }
.footer-logo { width: 26px; height: 26px; }
.footer-links { display: flex; align-items: center; gap: 20px; font-size: 13px; color: var(--oryx-muted); }
.footer-links a { color: #A5B4FC; text-decoration: none; }
.footer-links a:hover { color: #C7D2FE; }

/* ── 响应式 ── */
@media (max-width: 960px) {
  .gates { grid-template-columns: repeat(2, 1fr); }
  .features { grid-template-columns: repeat(2, 1fr); }
  .agent-demo { grid-template-columns: 1fr; }
  .roadmap { grid-template-columns: 1fr; }
  .hero-title { font-size: 38px; }
}
@media (max-width: 600px) {
  .gates { grid-template-columns: 1fr; }
  .features { grid-template-columns: 1fr; }
  .hero-title { font-size: 30px; }
  .hero-formula { font-size: 11px; padding: 8px 12px; }
}
</style>
