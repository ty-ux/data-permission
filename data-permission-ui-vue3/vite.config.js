import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: [
      {
        find: '@/utils/request',
        replacement: path.resolve(root, 'demo/utils/request.js')
      },
      {
        find: '@',
        replacement: path.resolve(root, 'src')
      }
    ]
  },
  server: {
    host: '127.0.0.1',
    port: 5174,
    strictPort: true
  },
  build: {
    outDir: 'dist'
  }
})
