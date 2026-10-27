# Backend del Sprint 2

Guía de integración para Kiara y Sebastián. Amplía las historias HU21 a HU40 sobre el motor del Sprint 1. Los cambios de esta entrega son de backend; las pantallas y su integración corresponden al frontend.

La ampliación posterior de blackjack y deportes virtuales se documenta en [casino/README.md](casino/README.md). Comparte estas sesiones y controles, y agrega sus jugadas al historial, estadísticas y resumen de sesión; no modifica la numeración de historias del Sprint 2.

## Preparación del entorno

Usar PostgreSQL para la aplicación integrada y las pruebas de concurrencia. Instalar `requirements.txt`, configurar las variables de conexión locales y mantener Redis activo. Cada integrante utiliza su propio `.env`; las bases de datos y credenciales no se comparten por Git.

En PowerShell, desde `usuario/backend`, con el entorno virtual activado:

```powershell
$env:DB_ENGINE = "postgresql"
python manage.py migrate
python manage.py check
python manage.py test --settings=config.test_settings
python manage.py makemigrations --check --dry-run
```

En Git Bash, la selección equivalente es `DB_ENGINE=postgresql python manage.py migrate`. No generar migraciones nuevas para instalar las ya existentes.

Iniciar `python manage.py runserver` y, en otra terminal con la misma base y Redis, `python manage.py runroulette`. La capa de memoria se usa exclusivamente en pruebas. PostgreSQL y Redis deben ser los mismos para HTTP, WebSockets y reloj.

`EXCLUSION_HMAC_KEY` permite configurar una clave estable para las huellas del registro de excluidos. En desarrollo se usa `SECRET_KEY` como alternativa. Cambiar la clave requiere reconstruir las huellas desde la fuente autorizada; no editarla entre peticiones. El registro es administrado por el proyecto, no una consulta automática a un registro oficial externo.

## Reglas para el cliente

- Base: `http://localhost:8000/api/v1`, sin barra final en las rutas. Usar el mismo host para las cookies; no mezclar `localhost` y `127.0.0.1`.
- Enviar `credentials: "include"` en todas las peticiones. Obtener `GET /auth/csrf` y enviar su `csrfToken` en `X-CSRFToken` para POST y PUT, incluido refresh y logout.
- El JWT de acceso dura 15 minutos. El login agrega una cookie de renovación HttpOnly restringida a `/api/v1/auth/`; la sesión tiene duración máxima de ocho horas.
- Ante 401, intentar `POST /auth/refresh` una sola vez, con una promesa compartida si fallan varias solicitudes simultáneamente. Si funciona, reintentar la petición original una vez; si falla, limpiar sesión y cerrar sockets. No renovar ante 403 ni repetir indefinidamente.
- Renovar antes de que expire el socket (por ejemplo cada diez minutos mientras la sesión esté activa) y reconectarlo para que tome la nueva cookie. Un socket con JWT vencido se cierra aunque se haya renovado la cookie del navegador.
- El login anterior a estas migraciones no tiene cookie de renovación: iniciar sesión nuevamente. Logout, cambio de contraseña y autoexclusión impiden renovar sesiones revocadas.
- Dinero en texto decimal de dos posiciones. No confundir pago total con ganancia neta. La preconsulta de saldo es orientativa: la apuesta definitiva siempre vuelve a validar todos los controles.
- Antes de habilitar la primera apuesta, consultar el límite diario. Si `configured` es false, pedir al jugador que lo defina. El backend no asigna silenciosamente un límite por él.

## Rutas de autenticación y perfil

| Método y ruta | Entrada o respuesta |
| --- | --- |
| POST `/auth/login` | `{email, password}`. Crea cookies de acceso y renovación y una sesión persistida. |
| GET `/auth/me` | `{id, email, role}`; `role` es USER o ADMIN según los permisos nativos de Django. |
| POST `/auth/refresh` | Sin cuerpo obligatorio. Renueva acceso conservando inicio e historial de sesión. No prolonga la duración absoluta. |
| POST `/auth/logout` | Revoca la sesión de ambas cookies y las elimina. Una copia del token deja de servir. |
| GET `/users/profile` o `/users/me` | id, email, dni, nickname, avatar_url, first_name, last_name y phone. Solo el usuario autenticado. |
| PUT `/users/profile` | Campos editables presentes. Conservar los demás. Correo y DNI en la entrada se rechazan con 400. |
| PUT `/users/change-password` | `{current_password, new_password}`. Exige contraseña actual, nueva diferente y validaciones. Revoca todas las sesiones; exige nuevo login. |
| GET `/admin/status` | Prueba de acceso administrativo: USER recibe 403, ADMIN recibe 200. El registro público no asigna permisos administrativos. |

Para el perfil, nombre y apellido admiten letras y separadores de nombre, hasta 80 caracteres. El teléfono es texto, puede quedar vacío y admite prefijo `+`, dígitos, espacios, paréntesis y guiones, entre 7 y 20 caracteres cuando se proporciona. Se conservan las reglas existentes de apodo y los seis avatares.

## Juego responsable

| Método y ruta bajo `/responsible-gaming` | Contrato |
| --- | --- |
| GET `/daily-limit` | configured, limit, spent, remaining, pending_limit, effective_at, date y timezone. |
| POST `/daily-limit` | `{amount: "100.00"}`. Primer límite o reducción inmediatos; aumento después de 24 horas. Cero bloquea nuevas apuestas. |
| POST `/self-exclude` | `{days: 7}`, 30 o 90. Responde self_excluded_until, revoca acceso y elimina cookies. No hay cancelación anticipada. |
| GET `/session-summary` | session_started_at, duration_seconds, total_wagered, total_paid, net, pending_wagered, pending_count y reality_check_interval_seconds=3600. |
| POST `/pause` | `{incident_id: 123}`. Solo incidente propio; devuelve pause_until. Repetir el mismo incidente no extiende la pausa. |
| GET `/risk-alerts` | Recupera hasta veinte incidentes propios y pause_until para restaurar la interfaz al recargar o reconectar. |
| POST `/excluded-people` | Solo ADMIN: `{dni: "00000000", source: "fixture", reason: "..."}`. DNI válido de ocho dígitos; se persiste únicamente su HMAC en el registro de exclusiones. Responde id y created. |

Los ejemplos de DNI son ficticios. No registrar información sensible en logs ni exponer el motivo de exclusión al jugador. El usuario ya existente conserva su DNI de registro; el registro de excluidos guarda una huella separada. La cuenta excluida recibe un error genérico y no puede registrarse, iniciar sesión, renovar ni apostar.

El límite diario mide monto aceptado, no pérdidas. Los premios no restauran cupo. Una fecha nueva en `America/Lima` comienza un contador distinto a medianoche; no depende de que un cron borre registros. Solo existe un aumento pendiente. Repetir la misma solicitud de aumento no reinicia su espera; pedir otro importe sí. Una reducción elimina el aumento pendiente.

El resumen usa solo apuestas de la sesión actual: total_wagered, total_paid y net corresponden a apuestas liquidadas; pending_wagered informa lo todavía comprometido. A los 3600 segundos, el frontend presenta el aviso; recargar no reinicia el inicio persistido. Continuar inicia el próximo intervalo visual. El modal bloquea acciones del jugador, no detiene la ronda de los demás.

El detector agrupa apuestas por ronda, no por ficha. Cuatro rondas perdidas con tres aumentos consecutivos de al menos tres veces disparan un incidente: 10, 30, 90 y 270. Una ronda ganada o que no respeta la secuencia rompe el patrón. Publica `responsible:risk-alert` con incident_id y suggested_pause_seconds=600. El jugador decide tomar la pausa; una vez aceptada, el servidor bloquea nuevas apuestas durante diez minutos. Las apuestas ya aceptadas se liquidan aunque luego haya pausa o exclusión.

## Ruleta catálogo e historial

| Método y ruta | Contrato |
| --- | --- |
| GET `/games` | Juegos publicados con salas activas: id, slug, title, min_bet, max_bet, rooms_url. Incluye Ruleta Europea. |
| GET `/rooms?game=1` | Filtro opcional por ID de juego. Conserva el listado de salas y sus límites. |
| GET `/rooms/{id}/state` | Añade recent_results, máximo diez `{round_id, number, color}`, más recientes primero. |
| POST `/rooms/{id}/bets` | Conserva round_id, kind, selection, chips y reference. Ahora valida límite y exclusiones además del saldo. |
| GET `/rooms/{id}/bets/previous` | `{source_round_id, bets}` de la última ronda liquidada que el jugador apostó en esa sala. Sin anterior: null y lista vacía. |
| POST `/rooms/{id}/bets/repeat` | `{round_id, source_round_id, reference}`. source_round_id es opcional, pero se recomienda enviarlo desde la consulta previa. Copia todo el conjunto o revierte todo. |
| GET `/users/bets-history` | Historial privado general: count, next, previous y results. Admite page y page_size (20 por defecto, máximo 100). |
| GET `/stats/summary` | total_wagered, total_paid, net, scope y timezone. Solo apuestas liquidadas del usuario. |

Tipos permitidos: STRAIGHT_UP ("0" a "36"), COLOR ("rojo"/"negro"), PARITY ("par"/"impar"), DOZEN y COLUMN ("1", "2" o "3"). Fichas: 10, 50, 100 y 500; hasta 100 fichas por solicitud. Docenas: 1–12, 13–24 y 25–36. Columnas: 1/4/7…, 2/5/8… y 3/6/9…. El cero pierde en color, paridad, docena y columna.

Devolución total al ganar: pleno ×36, color/paridad ×2 y docena/columna ×3. Ganancia neta = pago total menos monto apostado.

Cada apuesta y cada repetición utiliza una referencia UUID nueva. Un reintento de la misma operación conserva referencia y contenido; devuelve 200 sin otro cargo (primera respuesta: 201). Reutilizar referencia con otro contenido devuelve 409. La repetición verifica saldo, cupo diario, máximo acumulado y ronda abierta para todas sus apuestas en una transacción.

Historial y estadísticas aceptan startDate y endDate con formato AAAA-MM-DD. El día final completo se interpreta en Lima. Rango invertido o formato inválido: 400. Las apuestas de historial incluyen game, room_id, status (PENDING/WON/LOST) y net. Pendientes: payout/net nulos. Nunca se aceptan IDs de otro usuario para ampliar la consulta.

## Eventos y reconexión

Conservar `room:state`, `timer:start`, `timer:tick`, `bets:closed`, `timer:stop`, `round:result`, `round:winnings` y `wallet:update`. `round:result` añade color y parity; el cero tiene parity=null. Los eventos monetarios y de riesgo son privados. `wallet:update`, `/wallet/balance` y `/wallet/summary` añaden low_balance_threshold="10.00" e is_low_balance.

El consumidor cierra conexiones revocadas y rechaza reconexiones sin credenciales válidas. Los clientes solo envían ping y room:sync; las apuestas siguen por HTTP con CSRF. El reloj y resultado siguen siendo decisiones del servidor.

Las notificaciones son posteriores al commit. Si Redis falla no se deshace un saldo confirmado; el cliente recupera estado e historial por HTTP al reconectar. Consultar también `/responsible-gaming/risk-alerts` para recuperar avisos y pausa. Mantener deduplicación por round_id para resultados y por ID para incidentes. No interpretar ausencia de evento como permiso para apostar.

## Errores para la interfaz

| Estado | Tratamiento |
| --- | --- |
| 400 `daily_limit_required` | Pedir configuración de límite antes de apostar. |
| 400 `daily_limit_exceeded` | Mostrar cupo disponible y acceso a Mis límites; no sugerir eludir el control. |
| 400 `pause_active` | Bloquear apuesta hasta terminar la pausa. |
| 400 `insufficient_balance` / `bets_closed` | Conservar el tratamiento existente. |
| 400 por campo | Presentar validación junto al campo; no mostrar como fallo interno. |
| 401 | Renovar una sola vez o pedir login. |
| 403 | CSRF, acceso no permitido o rol insuficiente; no iniciar bucle de renovación. |
| 409 `reference_conflict` | No reintentar cambiando importes bajo la misma referencia. |

## Trazabilidad

| HU | Backend y pruebas principales |
| --- | --- |
| 21 | consumers.py, realtime/authentication.py; test_hu21.py y test_websocket.py. |
| 22 | services.py, runroulette; test_hu22.py y test_game.py. Reutiliza reloj existente. |
| 23 | services.py y payout.py; test_hu23.py. |
| 24 | modelos, serializer y pagos DOZEN/COLUMN; test_hu24.py. |
| 25 | responsible/models.py, services.py y endpoint administrativo; test_hu25.py. |
| 26 | DailyLimit, DailySpending y LimitService; test_hu26.py y test_concurrency.py. |
| 27 | SelfExclusion y control de acceso; test_hu27.py. |
| 28 | AuthSession, vínculo Bet.session y SessionSummaryView; test_hu28.py y test_session_refresh.py. |
| 29 | pattern_detector.py, RiskIncident y PauseView; test_hu29.py. |
| 30 | AuthSession, revocación y cookies; test_hu30.py y test_session_refresh.py. |
| 31 | history.py y HistoryBetSerializer; test_hu31.py. |
| 32 | queries.py y filtros; test_hu32.py. |
| 33 | state.py y recent_results; test_hu33.py. |
| 34 | Game, migraciones y GameListView; test_hu34.py. |
| 35 | repeat.py y RepeatOperation; test_hu35.py y responsible/test_concurrency.py. |
| 36 | wallet/presentation.py y respuestas/eventos de saldo; test_hu36.py. |
| 37 | campos y validación de perfil; test_hu37.py y test_profile.py. |
| 38 | ChangePasswordView y versionado de credenciales; test_hu38.py. |
| 39 | IsAdminRole y AdminStatusView; test_hu39.py. No duplica permisos en un segundo campo role. |
| 40 | settled_summary y StatsSummaryView; test_hu40.py. |

## Pendientes del frontend para completar las historias

Integrar Mis límites antes de apostar; zonas de docena/columna; acumulación de fichas; repetición; autoexclusión; aviso de realidad; alerta y pausa; historial/fechas; diez resultados; catálogo de juegos; perfil ampliado; cambio de contraseña; permisos; estadísticas; aviso único de saldo bajo y renovación de sesión. Mantener web adaptable y accesibilidad de teclado.

Un backend probado no equivale a una historia completa de punta a punta sin estas pantallas. La revisión final de ambos integrantes debe incluir registro, límite, apuesta, liquidación, historial, repetición, bloqueo por cupo y cierre de sesión.

## Validación de esta entrega

Validación del 18 de octubre de 2026: 151 pruebas de backend aprobadas en PostgreSQL, sin omisiones; incluye carreras de apuestas entre salas y reintentos simultáneos de repetición. Las 61 pruebas existentes del frontend, lint y build también aprobaron. `check`, `makemigrations --check --dry-run` y `migrate --check` no encontraron problemas ni pendientes.

El recorrido HTTP real con Redis y PostgreSQL comprobó 37 solicitudes: cookies y CSRF, registro, login, permiso administrativo, límite obligatorio, docena, idempotencia, renovación sin reiniciar sesión, liquidación del reloj, historial/fechas, estadísticas, repetición, rechazo por cupo, edición de perfil, contraseña, revocación y autoexclusión. Usó una cuenta ficticia identificada por prefijo `sprint2-smoke-`, que terminó autoexcluida. No sustituye las pruebas de las pantallas nuevas.
