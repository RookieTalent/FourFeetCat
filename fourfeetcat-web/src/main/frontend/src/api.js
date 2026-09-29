// 管理台唯一的对外通道：只走 /api/v1 下已发布的只读 GET 端点。
// 它自己没有后端——某个页面调不动，说明端点设计缺了一块，该去补端点，而不是在这里绕开 API。

const BASE = '/api/v1'

/**
 * 取一个只读端点。统一信封 {code, message, data, timestamp}：
 * - 非 2xx：抛后端给的 message（面向调用方写的那句话），页面直接显示
 * - 2xx：返回 data
 * 网络层失败（连不上、非 JSON）也收敛成一句可读的话，绝不把原始异常渲染到页面上。
 */
export async function getJson(path) {
  let response
  try {
    response = await fetch(BASE + path, { headers: { Accept: 'application/json' } })
  } catch (cause) {
    throw new Error('连不上服务，请确认底座是否在运行')
  }

  let payload = null
  try {
    payload = await response.json()
  } catch (cause) {
    throw new Error(`接口返回了非 JSON 内容（HTTP ${response.status}）`)
  }

  if (payload && typeof payload.code === 'number' && payload.code !== 200) {
    throw new Error(payload.message || `请求失败（HTTP ${response.status}）`)
  }
  if (!response.ok) {
    throw new Error(`请求失败（HTTP ${response.status}）`)
  }
  return payload ? payload.data : null
}
