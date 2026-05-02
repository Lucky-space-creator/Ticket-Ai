import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const gatewayUrl = env.VITE_GATEWAY_URL || 'http://127.0.0.1:8080'
  const customerWsUrl = env.VITE_CUSTOMER_WS_URL || 'http://127.0.0.1:8085'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    server: {
      port: 3001,
      proxy: {
        '/api': {
          target: gatewayUrl,
          changeOrigin: true
        },
        '/ws': {
          target: customerWsUrl,
          ws: true,
          changeOrigin: true
        }
      }
    }
  }
})