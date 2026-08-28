import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发环境通过代理把 /api 与 /ws 转发到后端，前端无需写死后端地址。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true
      }
    }
  }
})