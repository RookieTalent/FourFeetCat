import { onMounted, ref } from 'vue'
import { getJson } from './api'

/**
 * 一个只读端点的加载状态：加载中 / 出错 / 数据。
 *
 * 五个页面都是"调一个 GET、把 JSON 渲染出来"，把这三态抽成一处，页面里就只剩"怎么渲染"这一件事。
 * 少写一份状态逻辑，就少一处"某个页面忘了处理出错"的机会。
 */
export function useResource(path) {
  const data = ref(null)
  const error = ref('')
  const loading = ref(true)

  async function load() {
    loading.value = true
    error.value = ''
    try {
      data.value = await getJson(path)
    } catch (cause) {
      error.value = cause.message
      data.value = null
    } finally {
      loading.value = false
    }
  }

  onMounted(load)

  return { data, error, loading, reload: load }
}
