<script setup>
import ResourceState from '../components/ResourceState.vue'
import { useResource } from '../useResource'

// GET /api/v1/sessions —— 全部会话（含已归档），最近活跃在前
const { data, error, loading, reload } = useResource('/sessions')

function statusLabel(status) {
  return status === 'archived' ? '已归档' : '活跃'
}
</script>

<template>
  <h1 class="page-title">会话列表</h1>
  <p class="page-lead">底座上全部会话（含已归档）。命令行聊过的会话也会出现在这里——两个人推入口共用同一份存储。</p>

  <div class="card">
    <ResourceState :loading="loading" :error="error" @retry="reload">
      <p v-if="!data || data.length === 0" class="state">还没有会话。从命令行或 REST 发起一次对话后，这里就有了。</p>
      <table v-else>
        <thead>
          <tr>
            <th>会话标识</th>
            <th>Agent</th>
            <th>渠道</th>
            <th>用户</th>
            <th>状态</th>
            <th>最后活跃</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="session in data" :key="session.sessionId">
            <td class="mono"><span class="truncate" :title="session.sessionId">{{ session.sessionId }}</span></td>
            <td>{{ session.profileName }}</td>
            <td>{{ session.channel }}</td>
            <td>{{ session.userId }}</td>
            <td>
              <span class="status" :class="session.status">{{ statusLabel(session.status) }}</span>
            </td>
            <td class="muted">{{ session.lastActiveAt }}</td>
          </tr>
        </tbody>
      </table>
    </ResourceState>
  </div>
</template>
