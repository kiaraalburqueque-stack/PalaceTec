# Backend del Sprint 3 (HU41 a HU60)

Amplía el backend del Sprint 2 con la administración del casino (app `admin_api`), estadísticas del jugador (`stats`),
notificaciones en tiempo real (`notifications`) y seguridad de la cuenta. Todo comparte la misma base de datos.

## Instalación

Desde `usuario/backend`, con el entorno virtual activado (no hay dependencias nuevas):

```powershell
python manage.py migrate
python manage.py test --settings=config.test_settings
python manage.py runserver        # HTTP y WebSockets (Daphne)
python manage.py runroulette      # reloj de ruleta, en otra terminal
```

Los correos de HU55 y HU56 usan el backend de consola: el enlace aparece en la terminal de `runserver`.
`FRONTEND_URL` (por defecto `http://localhost:5173`) define a dónde apuntan esos enlaces.
Con SQLite (`DB_ENGINE=sqlite`) las transacciones usan modo `IMMEDIATE` para evitar `database is locked`.

Frontends:

| Carpeta | Puerto | Uso |
| --- | --- | --- |
| `usuario/frontend-web` | 5173 | Jugador (HU51 a HU60) |
| `administracion/frontend-web` | 5174 | Panel de administración (HU41 a HU50). `npm install` y `npm run dev` |

Para entrar al panel se necesita una cuenta con `is_staff` (por ejemplo `python manage.py createsuperuser`).
El grupo `Auditor` (creado por migración) puede consultar `/admin/audit/transactions` sin ser administrador.

## Administración (`/api/v1/admin`, solo ADMIN; un USER recibe 403)

| Método y ruta | Historia | Contrato |
| --- | --- | --- |
| GET `/users?page=&status=active\|suspended&role=admin\|user&search=&ordering=` | HU41 | 10 por página; `search` busca correo o DNI. |
| PATCH `/users/{id}/status` | HU42 | `{is_active, reason}`. Al suspender revoca sesiones, envía `FORCE_DISCONNECT` por WebSocket y el login responde 403 `account_suspended`. |
| GET `/users/{id}/sanctions` | HU42 | Historial `SanctionLog`. |
| GET, POST `/games` | HU43 | Alta JSON o multipart (`cover` JPG/PNG/WEBP hasta 2 MB). Crea una mesa principal para que aparezca en el catálogo. |
| GET, PUT `/games/{id}` | HU44 | Edición parcial. Rechaza `min_bet > max_bet`. Cada sala se ajusta dentro de los nuevos límites y recibe `room:limits`. |
| PATCH `/games/{id}/soft-delete`, `/games/{id}/restore` | HU45 | `is_published=false` y `deleted_at`. Las rondas y apuestas se conservan. |
| GET `/responsible-gaming/users?status=LIMIT_ACTIVE\|SELF_EXCLUDED\|RISK_ALERT` | HU46 | Jugadores con límite, autoexclusión o alertas de 30 días e insignias. |
| GET `/kpis` | HU47 | Usuarios en línea, apostado, pagado y ganancia del día y variación contra ayer. Caché de 60 s. |
| GET `/reports/financial-flow?days=30` | HU48 | Serie diaria `{date, wagered, paid, net_margin}` y totales. |
| GET `/audit/transactions`, `/audit/transactions/export` | HU49 | Filtros `user_id`, `transaction_id`, `search`, `type`, `startDate`, `endDate`. El export entrega CSV. |
| GET `/audit-logs?search=&method=` | HU50 | Registro inmutable que escribe `AdminAuditMiddleware` en cada POST, PUT, PATCH y DELETE de un administrador. |
| POST `/promotions` | HU58 | `{title, message, send_email}`. Respeta las preferencias de cada jugador. |

## Jugador

| Método y ruta | Historia | Contrato |
| --- | --- | --- |
| GET `/stats/win-rate` | HU51 | `won`, `lost`, `push`, `total`, `win_rate = ganadas / total × 100`. Acepta `startDate` y `endDate`. |
| GET `/stats/biggest-win` | HU52 | `record` con mayor ganancia neta o `null`. |
| GET `/stats/balance-history?period=7\|30` | HU53 | Saldo al cierre de cada día de Lima y saldo inicial. |
| GET `/users/bets/{id}` | HU54 | Detalle con fichas y número ganador. Otro usuario recibe 404. |
| POST `/auth/forgot-password`, `/auth/reset-password` | HU55 | Token de un solo uso, 15 minutos, guardado con hash. La clave nueva debe ser distinta. |
| POST `/auth/not-me` | HU56 | `{token}` del correo de alerta. Bloquea la cuenta 24 h y revoca sesiones. |
| GET `/notifications`, POST `/notifications/read-all`, POST `/notifications/{id}/read` | HU57 | Últimas 30 y `unread_count`. |
| GET, PUT `/users/notification-preferences` | HU58 | `{promotions, security, game_alerts}`. |
| GET `/games/{id}/rules` | HU60 | Reglas, tabla de pagos, RTP y `play_room_id`. |

## WebSockets

- `ws/notifications/`: eventos `notification:count`, `notification:new`, `wallet:update` y `FORCE_DISCONNECT` (HU57 y HU42).
- `ws/roulette/{sala}/`: el cliente envía `{"event": "room:resume"}` al reconectar y recibe `RECONNECT_SUCCESS` con
  el estado de la sala y sus apuestas de la ronda aún no liquidada (HU59). Nuevos eventos: `room:limits` y `room:closed`.
