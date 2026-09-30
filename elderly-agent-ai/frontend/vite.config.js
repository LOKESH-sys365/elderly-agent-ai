import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
// Dev: proxies API + WebSocket to Spring Boot. Build: outputs straight into Spring's static folder.
export default defineConfig({
  plugins: [react()],
  server: { port: 3000, proxy: {
    '/api': 'http://localhost:8080',
    '/ws': { target: 'ws://localhost:8080', ws: true } } },
  build: { outDir: '../src/main/resources/static', emptyOutDir: true }
});
