import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// base 必须是 '/admin/'：产物由 Spring 托在 /admin 子路径下，base 配错会让所有资源请求打到根路径（页面白屏）
// outDir 指到 web 模块的静态资源目录，由 Spring 直接托管；产物不入库（见 .gitignore）
export default defineConfig({
  base: '/admin/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    outDir: '../resources/static/admin',
    emptyOutDir: true
  }
})
