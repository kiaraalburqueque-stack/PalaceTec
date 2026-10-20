import { defineConfig } from '@playwright/test';
import process from 'node:process';
export default defineConfig({
  testDir: './e2e', testMatch: '**/*.e2e.js', workers: 1,
  timeout: 60000, expect: { timeout: 15000 },
  use: { baseURL: 'http://localhost:5173', channel: 'msedge', trace: 'retain-on-failure' },
  webServer: [
    { command: `${process.platform === 'win32' ? '..\\backend\\.venv\\Scripts\\python.exe' : '../backend/.venv/bin/python'} e2e/server.py`, url: 'http://localhost:8010/__e2e__/health', timeout: 60000, reuseExistingServer: false },
    { command: 'npm run dev -- --host localhost --port 5173 --strictPort', url: 'http://localhost:5173', env: { VITE_API_BASE_URL: 'http://localhost:8010' }, reuseExistingServer: false },
  ],
});
