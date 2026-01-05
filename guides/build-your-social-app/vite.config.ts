import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  base: '/',
  build: {
    outDir: 'dist',
  },
  server: {
    proxy: {
      '/graph': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    }
  }
  ,
})

