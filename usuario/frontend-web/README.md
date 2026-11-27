# PalaceTec - Frontend del jugador

React y Vite. Creditos simulados, sin dinero real. La integracion del Sprint 2
cubre HU21-HU40 sobre el frontend del Sprint 1. Consulta la
[trazabilidad de Sebastian](../../docs/SPRINT2_SEBASTIAN.md) y el
[contrato del backend](../../docs/SPRINT2_API.md).

## Ejecutar

```powershell
npm ci
npm run dev
```

Abrir `http://localhost:5173`. La API (REST, WebSockets y fotos `/media/`) es el
backend Spring en `http://localhost:8080` por defecto; `VITE_API_BASE_URL`
permite cambiarlo (por ejemplo, `http://localhost:8000` para usar Django). Usar
el mismo host para las cookies: `localhost` en el navegador y en la URL.
Para la aplicacion integrada, levantar PostgreSQL con las migraciones de Django,
Spring (`usuario/backend`) y `python manage.py runroulette`, que abre y liquida
las rondas (ver el README de `usuario/backend`).
Antes de apostar, configurar el limite diario en **Mis limites**.

## Verificar

```powershell
npm test
npm run lint
npm run build
npm run test:e2e          # contra Django con fixtures (SQLite temporal)
npm run test:e2e:spring   # contra runroulette + Spring reales (ver abajo)
```

### Recorrido contra Spring (`test:e2e:spring`)

Levanta `runroulette`, Spring (8080) y Vite (5173) con el reloj real de 15 segundos y recorre: registro, login, límite
diario, cuatro apuestas en la ruleta, rechazo por límite, liquidación en vivo, notificación e historial. Usa una base
PostgreSQL propia, **`palacetec_e2e`**, para no escribir en `palacetec`. Una sola vez, desde `administracion/backend`:

```powershell
.\.venv\Scripts\python.exe -c "import os,psycopg; from dotenv import load_dotenv; load_dotenv('.env'); c=psycopg.connect(dbname='postgres',user=os.getenv('DB_USER'),password=os.getenv('DB_PASSWORD'),host='localhost',autocommit=True); c.execute('create database palacetec_e2e')"
$env:DB_NAME = "palacetec_e2e"; $env:USE_REDIS = "false"; .\.venv\Scripts\python.exe manage.py migrate
```

Después, con `JAVA_HOME` apuntando a un JDK 21 y los puertos 5173 y 8080 libres: `npm run test:e2e:spring`.

`npm test` usa jsdom para React. `test:e2e` necesita Microsoft Edge,
Python y las dependencias instaladas en `../../administracion/backend/.venv`. Los puertos 5173 y
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
