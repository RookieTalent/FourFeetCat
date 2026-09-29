<script setup>
import ResourceState from '../components/ResourceState.vue'
import { useResource } from '../useResource'

// GET /api/v1/profiles —— 全部已加载 Agent 的可读字段（不含凭证、不含系统提示正文）
const { data, error, loading, reload } = useResource('/profiles')

function listText(items) {
  return items && items.length ? items.join('、') : '—'
}
</script>

<template>
  <h1 class="page-title">Agent 列表</h1>
  <p class="page-lead">当前加载了哪些 Agent、各自挂在哪家模型上、手里有哪些工具与渠道。凭证类字段不在这里呈现。</p>

  <div class="card">
    <ResourceState :loading="loading" :error="error" @retry="reload">
      <p v-if="!data || data.length === 0" class="state">还没有加载任何 Agent。往工作区放一个 Agent 目录后重启即可。</p>
      <table v-else>
        <thead>
          <tr>
            <th>名称</th>
            <th>展示名</th>
            <th>描述</th>
            <th>模型</th>
            <th>工具</th>
            <th>渠道</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="agent in data" :key="agent.name">
            <td class="mono">{{ agent.name }}</td>
            <td>{{ agent.agentName || '—' }}</td>
            <td class="muted">{{ agent.description || '—' }}</td>
            <td>
              <span class="pill">{{ agent.provider || '—' }}</span>
              <span class="muted"> {{ agent.model || '' }}</span>
            </td>
            <td class="muted">{{ listText(agent.tools) }}</td>
            <td class="muted">{{ listText(agent.channels) }}</td>
          </tr>
        </tbody>
      </table>
    </ResourceState>
  </div>
</template>
