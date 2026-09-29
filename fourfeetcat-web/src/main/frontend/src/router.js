import { createRouter, createWebHistory } from 'vue-router'
import SessionsView from './views/SessionsView.vue'
import AgentsView from './views/AgentsView.vue'
import ToolsView from './views/ToolsView.vue'
import MemoryView from './views/MemoryView.vue'
import StatusView from './views/StatusView.vue'
import SchedulesView from './views/SchedulesView.vue'

// history 模式 + base '/admin/'：子路由直接访问或刷新时，服务端会把未命中的非静态路径回落到入口页
const routes = [
  { path: '/', redirect: '/sessions' },
  { path: '/sessions', name: 'sessions', component: SessionsView },
  { path: '/agents', name: 'agents', component: AgentsView },
  { path: '/tools', name: 'tools', component: ToolsView },
  { path: '/memory', name: 'memory', component: MemoryView },
  { path: '/status', name: 'status', component: StatusView },
  { path: '/schedules', name: 'schedules', component: SchedulesView }
]

export default createRouter({
  history: createWebHistory('/admin/'),
  routes
})
