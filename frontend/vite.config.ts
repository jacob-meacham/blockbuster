import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    base: '/',
    server: {
      port: 8586,
      allowedHosts: env.VITE_ALLOWED_HOSTS?.split(',') ?? [],
      proxy: {
        '/search': 'http://localhost:8585',
        '/library': 'http://localhost:8585',
        '/play': 'http://localhost:8585'
      }
    },
    build: {
      outDir: 'dist',
      emptyOutDir: true
    }
  }
})
