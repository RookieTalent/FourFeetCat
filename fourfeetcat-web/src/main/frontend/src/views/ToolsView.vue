<script setup>
import ResourceState from '../components/ResourceState.vue'
import { useResource } from '../useResource'

// GET /api/v1/tools —— 工具表里全部已注册工具（内置的 + 注解挂进来的 + 外部服务接进来的）
const { data, error, loading, reload } = useResource('/tools')
</script>

<template>
  <h1 class="page-title">工具列表</h1>
  <p class="page-lead">底座当前注册的全部工具，以及模型看到的那份参数说明。某个 Agent 能不能用某个工具，看的是它自己声明的清单。</p>

  <div class="card">
    <ResourceState :loading="loading" :error="error" @retry="reload">
      <p v-if="!data || data.length === 0" class="state">还没有注册任何工具。</p>
      <table v-else>
        <thead>
          <tr>
            <th>工具名</th>
            <th>说明</th>
            <th>参数</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="tool in data" :key="tool.name">
            <td class="mono">{{ tool.name }}</td>
            <td class="muted">{{ tool.description }}</td>
            <td class="mono">
              <span class="truncate" :title="tool.inputSchema">{{ tool.inputSchema }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </ResourceState>
  </div>
</template>
