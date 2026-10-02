import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// Dev rejimində /api backend-ə proksi olunur (CORS lazım olmur). Serverdə nginx eyni işi görür.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: { '/api': env.BACKEND_URL || 'http://localhost:8080' },
    },
  }
})
