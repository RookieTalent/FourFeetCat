<script setup>
// 三态占位：加载中 / 出错（带重试）/ 正常（插槽）。
// 出错时显示的是后端统一信封里的 message —— 那是写给调用方看的话，不要把状态码或异常对象怼给用户。
defineProps({
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})
defineEmits(['retry'])
</script>

<template>
  <p v-if="loading" class="state">加载中…</p>
  <p v-else-if="error" class="state error">
    {{ error }}
    <button type="button" @click="$emit('retry')">重试</button>
  </p>
  <slot v-else />
</template>
