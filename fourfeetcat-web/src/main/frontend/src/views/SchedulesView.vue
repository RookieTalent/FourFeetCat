<script setup>
import { ref } from 'vue'
import ResourceState from '../components/ResourceState.vue'
import { useResource } from '../useResource'
import { requestJson } from '../api'

// 定时任务里"立即执行 / 启用停用"是写操作（管理台第一个），每行操作后重拉列表，让 run_count / 状态即时刷新。
const { data, error, loading, reload } = useResource('/schedules')
const feedback = ref('')
const busyId = ref(null)

async function runNow(task) {
  const text = `立即执行「${task.scheduleKey}」一次？会真实触发一轮 ReAct（无视启用状态）。`
  if (!window.confirm(text)) return
  await act(task, { method: 'POST', path: `/schedules/${task.scheduleId}/run` })
}

async function toggle(task) {
  const to = !task.enabled
  const label = to ? '启用' : '停用'
  if (!window.confirm(`${label}「${task.scheduleKey}」？`)) return
  await act(task, { method: 'PUT', path: `/schedules/${task.scheduleId}`, body: { enabled: to } })
}

/** 写操作：占住该行按钮防重复点击 → 调端点 → 重拉列表让状态即时刷新；失败把后端那句 message 亮出来。 */
async function act(task, { method, path, body }) {
  busyId.value = task.scheduleId
  feedback.value = ''
  try {
    await requestJson(path, { method, ...(body ? { body } : {}) })
  } catch (cause) {
    feedback.value = cause.message
  } finally {
    busyId.value = null
  }
  reload()
}

function enabledLabel(task) {
  return task.enabled ? '启用' : '停用'
}
function statusDot(task) {
  return task.enabled ? 'enabled' : 'disabled'
}
function lastStatus(task) {
  if (!task.lastStatus) return '从未触发'
  return task.lastStatus === 'success' ? '成功' : '失败'
}
</script>

<template>
  <h1 class="page-title">定时任务</h1>
  <p class="page-lead">Agent 配置的 schedules 在到点落地的运行面：下次触发 / 上次结果 / 次数都记在 SQLite，重启不丢。这里也能手动立即执行、或临时停用某条任务。</p>

  <p v-if="feedback" class="state error">{{ feedback }}</p>

  <div class="card">
    <ResourceState :loading="loading" :error="error" @retry="reload">
      <p v-if="!data || data.length === 0" class="state">还没有定时任务。给某个 Agent 配置里加一条 schedules，重启底座后这里就有了。</p>
      <table v-else>
        <thead>
          <tr>
            <th>任务</th>
            <th>Agent</th>
            <th>cron</th>
            <th>时区</th>
            <th>下次触发</th>
            <th>上次结果</th>
            <th>次数</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in data" :key="task.scheduleId">
            <td class="mono">{{ task.scheduleKey }}</td>
            <td>{{ task.profileName }}</td>
            <td class="mono">{{ task.cron }}</td>
            <td>{{ task.zone }}</td>
            <td class="muted">{{ task.nextRunAt || '—' }}</td>
            <td>
              <span class="status" :class="task.lastStatus === 'success' ? 'ok' : 'missing'">
                {{ lastStatus(task) }}
              </span>
            </td>
            <td>{{ task.runCount }}</td>
            <td>
              <span class="status" :class="statusDot(task)">{{ enabledLabel(task) }}</span>
            </td>
            <td>
              <button type="button" class="btn" :disabled="busyId === task.scheduleId" @click="runNow(task)">立即执行</button>
              <button type="button" class="btn" :disabled="busyId === task.scheduleId" @click="toggle(task)">
                {{ task.enabled ? '停用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </ResourceState>
  </div>
</template>