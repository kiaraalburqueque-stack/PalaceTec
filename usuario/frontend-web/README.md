# PalaceTec - Frontend del jugador

React y Vite. Creditos simulados, sin dinero real. La integracion del Sprint 2
cubre HU21-HU40 sobre el frontend del Sprint 1. Consulta la
[trazabilidad de Sebastian](../../docs/SPRINT2_SEBASTIAN.md) y el
[contrato del backend](../backend/SPRINT2_API.md).

## Ejecutar

```powershell
npm ci
npm run dev
```

Abrir `http://localhost:5173`. La API usa `http://localhost:8000` por defecto;
`VITE_API_BASE_URL` permite cambiarlo. Usar el mismo host para las cookies.
Para la aplicacion integrada, preparar PostgreSQL, Redis, las migraciones y
los procesos HTTP/WebSocket y `runroulette` segun el README del backend.
Antes de apostar, configurar el limite diario en **Mis limites**.

## Verificar

```powershell
npm test
npm run lint
npm run build
npm run test:e2e
```

`npm test` usa jsdom para React. `test:e2e` necesita Microsoft Edge,
Python y las dependencias instaladas en `../backend/.venv`. Los puertos 5173 y
8010 deben estar libres. Playwright levanta y detiene Vite y Django/Channels;
crea una base SQLite temporal y usa Channels/cache en memoria. No modifica la
base de desarrollo ni exige Redis para este recorrido aislado.

Las rutas de fixtures de `e2e/server.py` existen exclusivamente en ese servidor
de pruebas; no forman parte del backend de la aplicacion. Las rondas de fixtures
tienen un plazo largo y se liquidan explicitamente con resultado controlado.
El plazo real de 15 segundos y la concurrencia se validan en las pruebas del
backend; SQLite no demuestra bloqueos por fila de PostgreSQL.

Los recorridos de Edge verifican registro, login, limite obligatorio, docena,
liquidacion, repeticion, historial, estadisticas, renovacion de cookie, perfil,
roles, contrasena, autoexclusion, recuperacion de pausas y aviso de realidad.
Incluyen comprobaciones de distribucion movil y generan una captura en
`test-results/sprint2-mobile.png`. Si fallan, conservan trazas en `test-results`.
