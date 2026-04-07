import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())

  return {
    plugins: [
      react(),
      // index.html의 __KAKAO_JS_KEY__ 플레이스홀더를 실제 env 값으로 치환
      {
        name: 'html-transform',
        transformIndexHtml(html) {
          return html.replace('__KAKAO_JS_KEY__', env.VITE_KAKAO_JS_KEY || '')
        },
      },
    ],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
