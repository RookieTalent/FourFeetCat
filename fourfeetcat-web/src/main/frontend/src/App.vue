<script setup>
// 整页骨架：左侧竖直导航 + 右侧内容区。前几项各对应一个已发布的只读 GET 端点；
// 第 28 节起"定时任务"是第一个带写操作（立即执行 / 启用停用）的页。
const logo = `${import.meta.env.BASE_URL}logo-icon.svg`

const navItems = [
  { to: '/sessions', label: '会话列表' },
  { to: '/agents', label: 'Agent 列表' },
  { to: '/tools', label: '工具列表' },
  { to: '/memory', label: '长期记忆' },
  { to: '/status', label: '运行状态' },
  { to: '/schedules', label: '定时任务' }
]
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        <img :src="logo" alt="FourFeetCat" />
        <span>FourFeetCat 管理台</span>
      </div>
      <nav class="nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          active-class="active"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
    </aside>

    <main class="content">
      <RouterView />
    </main>
  </div>
</template>
