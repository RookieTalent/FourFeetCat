import { defineConfig } from 'vitepress'

const REPO = 'https://github.com/RookieTalent/FourFeetCat'
// 部署在 GitHub Pages 项目站时用环境变量注入 base（/FourFeetCat/），本地 dev 用 /
const base = process.env.BASE_URL || '/'

export default defineConfig({
  base,
  lang: 'zh-CN',
  title: 'FourFeetCat',
  description: '四脚猫 FourFeetCat——企业 Agent 操作系统（Agent Harness OS）：一个目录定义一个 Agent，一个底座运行一群 Agent，私有部署，数据不出域。',
  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: `${base}images/logo-icon.svg` }],
    ['meta', { name: 'theme-color', content: '#FFFFFF' }]
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
      description: 'FourFeetCat — the Agent Harness OS for enterprises. One directory defines an Agent, one harness runs them all. Private deployment, data never leaves your domain.',
      themeConfig: {
        nav: [
          { text: 'GitHub', link: REPO },
          { text: '简体中文', link: '/' }
        ]
      }
    }
  }
})
