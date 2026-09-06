<script setup>
import DefaultTheme from 'vitepress/theme'
import { useData } from 'vitepress'
import Home from './Home.vue'

const { frontmatter } = useData()
</script>

<template>
  <!-- frontmatter.home = true 时走自主设计的整页首页，否则回落默认主题（docs 页面用） -->
  <Home v-if="frontmatter.home" :lang="frontmatter.lang || 'zh'" />
  <DefaultTheme.Layout v-else />
</template>
