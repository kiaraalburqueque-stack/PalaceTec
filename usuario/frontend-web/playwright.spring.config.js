import { defineConfig } from '@playwright/test';
import path from 'node:path';
import process from 'node:process';

// Recorrido real: runroulette (Django) + Spring + React sobre una base PostgreSQL propia (palacetec_e2e), con el reloj
// de 15 segundos de verdad. Ver "Verificar" en el README. Requiere JAVA_HOME (JDK 21) y la base ya migrada.
const win = process.platform === 'win32';
const djangoDir = path.resolve('../../administracion/backend');
const python = `"${path.join(djangoDir, '.venv', win ? 'Scripts' : 'bin', win ? 'python.exe' : 'python')}"`;
const springDir = path.resolve('../backend');
const mvnw = `"${path.join(springDir, win ? 'mvnw.cmd' : 'mvnw')}"`;
const dbName = process.env.E2E_DB_NAME || 'palacetec_e2e';

export default defineConfig({
  testDir: './e2e', testMatch: '**/spring.e2e.js', workers: 1,
  timeout: 120000, expect: { timeout: 30000 },
  use: { baseURL: 'http://localhost:5173', channel: 'msedge', trace: 'retain-on-failure' },
  webServer: [
    { command: `${python} -u manage.py runroulette`, cwd: djangoDir, wait: { stdout: /Reloj de ruleta iniciado/ },
      env: { DB_NAME: dbName, USE_REDIS: 'false' }, reuseExistingServer: false },
    { command: `${mvnw} -q spring-boot:run`, cwd: springDir, url: 'http://localhost:8080/api/v1/auth/csrf', timeout: 180000,
      env: { DB_URL: `jdbc:postgresql://localhost:5432/${dbName}` }, reuseExistingServer: false },
    { command: 'npm run dev -- --host localhost --port 5173 --strictPort', url: 'http://localhost:5173', reuseExistingServer: false },
  ],
});
