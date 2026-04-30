import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { loadEnv } from 'vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendPort = env.VITE_BACKEND_PORT || '18090'

  return {
    plugins: [
      vue(),
      Components({
        dts: 'src/components.d.ts',
        resolvers: [
          ElementPlusResolver({
            importStyle: 'css',
          }),
        ],
      }),
    ],
    build: {
      chunkSizeWarningLimit: 1000,
      rollupOptions: {
        output: {
          manualChunks(id) {
            const moduleId = id.replaceAll('\\', '/')
            if (!moduleId.includes('/node_modules/')) return undefined
            if (moduleId.includes('/node_modules/echarts/')) return 'charts'
            if (
              moduleId.includes('/node_modules/vue/')
              || moduleId.includes('/node_modules/@vue/')
              || moduleId.includes('/node_modules/vue-router/')
              || moduleId.includes('/node_modules/pinia/')
            ) {
              return 'vue'
            }
            return undefined
          },
        },
      },
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: `http://localhost:${backendPort}`,
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      include: ['src/**/*.test.ts'],
    },
  }
})
