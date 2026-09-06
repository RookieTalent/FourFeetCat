import { defineConfig } from 'vitepress'

const REPO = 'https://github.com/RookieTalent/OryxOS'

export default defineConfig({
  lang: 'zh-CN',
  title: 'OryxOS',
  description: '企业 Agent 操作系统（Agent Harness OS）——一个目录定义一个 Agent，一个底座运行一群 Agent，私有部署，数据不出域。',
  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/images/logo-icon.svg' }],
    ['meta', { name: 'theme-color', content: '#0F172A' }]
  ],
  locales: {
    // 中文为默认站点根
    '/': {
      lang: 'zh-CN',
      label: '简体中文',
      link: '/',
      themeConfig: {
        nav: [
          { text: 'GitHub', link: REPO },
          { text: 'English', link: '/en/' }
        ],
        outline: { label: '本页目录' },
        docFooter: { prev: '上一篇', next: '下一篇' },
        lastUpdated: { text: '最后更新' },
        returnToTopLabel: '回到顶部',
        sidebarMenuLabel: '菜单',
        darkModeSwitchLabel: '主题',
        lightModeSwitchTitle: '切换到浅色模式',
        darkModeSwitchTitle: '切换到深色模式'
      }
    },
    // 英文站点
    '/en/': {
      lang: 'en-US',
      label: 'English',
      link: '/en/',
      description: 'The Agent Harness OS for enterprises — one directory defines an Agent, one harness runs them all. Private deployment, data never leaves your domain.',
      themeConfig: {
        nav: [
          { text: 'GitHub', link: REPO },
          { text: '简体中文', link: '/' }
        ]
      }
    }
  }
})
