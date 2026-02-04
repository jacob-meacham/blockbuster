import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendPort = env.BACKEND_PORT ?? 8584
  return {
    plugins: [react()],
    base: '/',
    server: {
      port: env.VITE_PORT ?? 8585,
      allowedHosts: env.VITE_ALLOWED_HOSTS?.split(',') ?? [],
      proxy: {
        '/search/all': `http://localhost:${backendPort}`,
        '/search/plugins': `http://localhost:${backendPort}`,
        '/search/roku': `http://localhost:${backendPort}`,
        '/roku/channels': `http://localhost:${backendPort}`,
        '/spotify/channels': `http://localhost:${backendPort}`,
        '/library': {
          target: `http://localhost:${backendPort}`,
          bypass(req) {
            if (req.headers.accept?.includes('text/html')) {
              return '/index.html'
            }
          }
        },
        '/play': `http://localhost:${backendPort}`,
        '/health': `http://localhost:${backendPort}`,
        '/auth': `http://localhost:${backendPort}`,
      }
    },
    build: {
      outDir: 'dist',
      emptyOutDir: true
    }
  }
})
