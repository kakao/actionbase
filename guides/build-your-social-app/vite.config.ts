import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react-swc'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  base: '/',
  build: {
    outDir: 'dist/hands-on-social',
  },
  server: {
    proxy: {
      '/api/command': {
        target: 'http://localhost:9300',
        changeOrigin: true,
        secure: false,
      },
      '/graph': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    }
  }
})

