<script setup>
// 整页骨架：左侧竖直导航 + 右侧内容区。五项导航各对应一个已发布的只读 GET 端点。
// 这里刻意不出现任何"新建/编辑/删除"入口——第一版管理台只能看。
const logo = `${import.meta.env.BASE_URL}logo-icon.svg`

const navItems = [
  { to: '/sessions', label: '会话列表' },
  { to: '/agents', label: 'Agent 列表' },
  { to: '/tools', label: '工具列表' },
  { to: '/memory', label: '长期记忆' },
  { to: '/status', label: '运行状态' }
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
