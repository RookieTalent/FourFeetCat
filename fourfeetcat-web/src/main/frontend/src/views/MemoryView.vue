<script setup>
import ResourceState from '../components/ResourceState.vue'
import { useResource } from '../useResource'

// GET /api/v1/memory —— 长期记忆全文（底下是文件、本地库还是外部服务，这里无感）
const { data, error, loading, reload } = useResource('/memory')
</script>

<template>
  <h1 class="page-title">长期记忆</h1>
  <p class="page-lead">Agent 通过对话里的记忆工具攒下来的内容。这里是只读的——它怎么长，由对话决定。</p>

  <div class="card">
    <ResourceState :loading="loading" :error="error" @retry="reload">
      <p v-if="!data" class="state">还没有任何长期记忆。</p>
      <pre v-else class="memory">{{ data }}</pre>
    </ResourceState>
  </div>
</template>
