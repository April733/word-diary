import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3003,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // SSE 长连接：避免代理超时把流式响应掐断/整段缓冲
        timeout: 0,
        proxyTimeout: 0,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes, _req, res) => {
            const ct = String(proxyRes.headers['content-type'] || '')
            if (ct.includes('text/event-stream')) {
              // 关闭代理侧缓冲，让 chunk 尽快到浏览器
              res.setHeader('Cache-Control', 'no-cache, no-transform')
              res.setHeader('X-Accel-Buffering', 'no')
              delete proxyRes.headers['content-length']
              if (typeof (res as any).flushHeaders === 'function') {
                ;(res as any).flushHeaders()
              }
            }
          })
        },
      },
    },
  },
})
