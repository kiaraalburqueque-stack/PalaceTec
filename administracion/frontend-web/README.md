# PalaceTec · Panel de administración (Sprint 3)

Panel en React + Vite para las historias HU41 a HU50. Consume la app `admin_api` del backend Django
(`usuario/backend`), que comparte la misma base de datos que el jugador.

## Ejecutar

```powershell
cd administracion/frontend-web
npm install
npm run dev      # http://localhost:5174
npm test         # pruebas con Vitest
```

El backend debe estar en `http://localhost:8000` (`python manage.py runserver` y `python manage.py runroulette`).
Usa `localhost` (no `127.0.0.1`) para que las cookies de sesión funcionen. Para otra URL del backend, define
`VITE_API_BASE_URL`.

Solo entran cuentas con rol ADMIN (`is_staff`). Para crear una:

```powershell
python manage.py createsuperuser
```

## Pantallas

| Ruta | Historia |
| --- | --- |
| `/` Dashboard | HU47 KPIs del día (caché de 60 s) y HU48 gráfico apostado vs. pagado |
| `/usuarios` | HU41 listado y búsqueda · HU42 suspensión y activación con motivo |
| `/juegos`, `/juegos/nuevo` | HU43 alta con portada · HU44 edición · HU45 desactivación lógica |
| `/juego-responsable` | HU46 monitoreo con insignias de estado |
| `/auditoria/transacciones` | HU49 auditoría transaccional y exportación CSV |
| `/auditoria/registro` | HU50 registro de auditoría de administradores |
| `/promociones` | Envío de promociones que respeta las preferencias del jugador (HU58) |
