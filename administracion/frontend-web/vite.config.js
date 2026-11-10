import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Panel de administración: puerto 5174 (el jugador usa 5173). Ambos consumen el backend Django en :8000.
export default defineConfig({
  plugins: [react()],
  server: { port: 5174, strictPort: true },
})
