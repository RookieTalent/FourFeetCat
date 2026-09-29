<script setup>
import ResourceState from '../components/ResourceState.vue'
import { useResource } from '../useResource'

// GET /api/v1/info —— 应用信息 + 各 Provider 的配置态（凭证是否就位）。
// 这里报的是**配置态**不是连通态：不发探活请求，所以"就位"只表示配了凭证，不表示此刻一定通。
const { data, error, loading, reload } = useResource('/info')
</script>

<template>
  <h1 class="page-title">运行状态</h1>
  <p class="page-lead">底座对外报的运行信息。凭证状态是"配没配"，不是"此刻通不通"——排查连通性问题请看服务日志。</p>

  <div class="card">
    <ResourceState :loading="loading" :error="error" @retry="reload">
      <table v-if="data">
        <thead>
          <tr>
            <th>Provider</th>
            <th>端点</th>
            <th>凭证</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="provider in data.providers" :key="provider.name">
            <td class="mono">{{ provider.name }}</td>
            <td class="mono muted">{{ provider.baseUrl }}</td>
            <td>
              <span class="status" :class="provider.credentialConfigured ? 'ok' : 'missing'">
                {{ provider.credentialConfigured ? '已就位' : '未配置' }}
              </span>
            </td>
          </tr>
          <tr v-if="!data.providers || data.providers.length === 0">
            <td colspan="3" class="state">一个 Provider 都没声明。对话一旦触发就会报错，请先补全局 Provider 声明。</td>
          </tr>
        </tbody>
      </table>
      <p v-if="data" class="state">
        <span class="muted">应用</span> <span class="pill">{{ data.application }}</span>
        <span class="muted"> 版本</span> <span class="pill">{{ data.version || '未打包（开发态）' }}</span>
      </p>
    </ResourceState>
  </div>
</template>
