# Backend del jugador (Spring Boot)

API del jugador de PalaceTec en Java 21 y Spring Boot 3.5 (Web, Data JPA, Security, Validation, PostgreSQL, jjwt,
webp-imageio, Apache POI y OpenPDF). La usan el frontend React, con cookies, y la app móvil, con Bearer.
Usa **la misma base PostgreSQL que Django** (`administracion/backend`): no crea ni modifica tablas
(`ddl-auto=validate`). Créditos virtuales, sin dinero real.

Spring no abre, cierra ni liquida rondas, ni crea o liquida los encuentros de deportes, ni cierra las manos de blackjack
abandonadas: eso lo hace `python manage.py runroulette`, que debe estar corriendo con la misma base para poder apostar.

## Requisitos

- JDK 21. `JAVA_HOME` debe apuntar a él. Maven no hace falta: se usa el wrapper `mvnw`.
- PostgreSQL con **todas las migraciones de Django aplicadas**. Si falta alguna, Hibernate no valida el esquema y la
  aplicación no arranca (`Schema-validation: missing column/table`).
- Django con `DB_ENGINE=postgresql` (o sin definir). Con `DB_ENGINE=sqlite` trabajan sobre bases distintas.

## Configuración

Copia `application-example.yml` como `application-local.yml` en esta carpeta (está en `.gitignore`) y completa los
valores, o define las mismas claves como variables de entorno, que tienen prioridad.

| Variable | Obligatoria | Uso |
| --- | --- | --- |
| `DB_URL` | Sí | `jdbc:postgresql://localhost:5432/palacetec` |
| `DB_USER`, `DB_PASSWORD` | Sí | Credenciales locales de PostgreSQL |
| `JWT_SECRET` | Sí | 32 caracteres o más. No reutilizar la `SECRET_KEY` de Django |
| `EXCLUSION_HMAC_KEY` | Sí | **La misma de Django** (`EXCLUSION_HMAC_KEY` o, si no existe, su `SECRET_KEY`). Con otra clave no se reconoce a las personas excluidas |
| `JWT_EXPIRATION` | No | Duración del JWT, por defecto `8h` |
| `MEDIA_BASE_URL` | No | Servidor de las portadas, por defecto `http://localhost:8000` |
| `CORS_ALLOWED_ORIGINS` | No | Orígenes del frontend (CORS con cookies y CSRF). Por defecto `http://localhost:5173,http://127.0.0.1:5173` |
| `COOKIE_SECURE` | No | `true` si el sitio se sirve por HTTPS (en Django, `DEBUG=False`). Por defecto `false` |
| `LOGIN_RATE_LIMIT` | No | Solicitudes de autenticación por minuto y por IP. Por defecto `30` |
| `FRONTEND_URL` | No | Adonde apuntan los enlaces de los correos. Por defecto `http://localhost:5173` |
| `ASSISTANT_PROVIDER` | No | `faq` (por defecto), `gemini` u `openai`. Los mismos nombres que en Django |
| `ASSISTANT_API_KEY` | No | Clave del proveedor de IA. Sin ella el asistente responde con preguntas frecuentes |
| `ASSISTANT_MODEL` | No | Modelo. Por defecto `gemini-3.6-flash` o `gpt-4o-mini` |
| `ASSISTANT_BASE_URL`, `ASSISTANT_TIMEOUT` | No | Servidor compatible con OpenAI y segundos de espera, por defecto `10` |
| `MEDIA_ROOT` | No | Carpeta de fotos, la misma de Django. Por defecto `../../administracion/backend/media` (relativa a esta carpeta) |

## Ejecutar

En PowerShell, desde `usuario/backend`:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.12.1"   # ajusta a tu JDK 21
.\mvnw.cmd spring-boot:run
```

Queda en `http://localhost:8080`. En otra terminal, desde `administracion/backend` y con la misma base:
`python manage.py runroulette`.

## Endpoints

Base `/api/v1`, sin barra final. Rutas, campos JSON y mensajes son los de Django; el dinero viaja como texto
(`"100.00"`). Salvo las rutas públicas de `/auth`, todos exigen una sesión, que puede ser de dos tipos:

- **Web (React):** JWT en cookies HttpOnly, como en Django: `access_token` (15 minutos) y `refresh_token` (8 horas, solo
  viaja a `/api/v1/auth/`). Toda escritura exige `X-CSRFToken`, que se pide en `/auth/csrf`.
- **App móvil:** `Authorization: Bearer <access_token>` (8 horas), sin cookies ni CSRF.

En login y registro se considera app móvil a quien no envía ninguna cabecera propia de un navegador (`Origin`,
`Referer`, `Cookie`, `Sec-Fetch-Site`, `X-CSRFToken`). A ese cliente nunca se le ponen cookies.

React usa este backend por defecto (`http://localhost:8080`): `usuario/frontend-web` ya apunta aquí para la API, los
WebSockets, las cookies, el CSRF y las fotos `/media/`. Con `npm run dev` desde `usuario/frontend-web` basta. Para usar
Django en su lugar: `$env:VITE_API_BASE_URL = "http://localhost:8000"; npm run dev`.

## WebSockets

`ws://localhost:8080/ws/roulette/<sala>/` y `ws://localhost:8080/ws/notifications/`, con las mismas rutas, mensajes
`{"event": ..., "data": ...}`, códigos de cierre (4401 sesión, 4403 cuenta suspendida, 4404 sala no disponible, 1009
mensaje demasiado largo) y cookie `access_token` que Django Channels. El handshake exige un `Origin` de
`CORS_ALLOWED_ORIGINS`; sin sesión válida el socket se abre y se cierra con 4401, para que React renueve la sesión.

**Eventos.** `runroulette` (Django) abre y liquida las rondas y Spring no recibe sus mensajes de Channels, así que
`RealtimePoller` lee la base cada segundo (`REALTIME_POLL_MS`, por defecto `1000`) y envía solo lo que cambió. Solo
trabaja si hay sockets abiertos. Lo que hace Spring mismo se publica al confirmar la transacción, sin esperar al
sondeo: `session:revoked` (autoexclusión, logout, cambio o recuperación de contraseña, "No fui yo") y `wallet:update`
(apuestas y premios de blackjack).

| Evento | Lo detecta |
| --- | --- |
| `room:state`, `RECONNECT_SUCCESS`, `pong`, `error`, `room:user_joined`, `room:user_left`, `notification:count` | El socket, al conectarse o al recibir `room:sync`, `room:resume` y `ping` |
| `timer:start`, `timer:tick`, `bets:closed`, `timer:stop`, `round:result` | Sondeo de `game_rounds` |
| `round:winnings` | Sondeo, al liquidarse una ronda: para quien apostó en ella |
| `wallet:update` | Sondeo de `wallet_balances`, o al instante si el saldo lo movió Spring |
| `notification:new` | Sondeo de `notifications` (con `unread_count`) |
| `responsible:risk-alert` | Sondeo de `responsible_riskincident` |
| `room:limits`, `room:closed` | Sondeo de `rooms` y `roulette_game` |
| `session:revoked`, `FORCE_DISCONNECT` | Sondeo de `accounts_authsession` y `users`, además del aviso inmediato de Spring |

Un evento de Django llega con hasta ~1 s de retraso. Diferencias con Django:

- Al desactivarse el juego de una sala, Django corta con 4401 sin enviar `room:closed` (su consumer revisa la sala
  antes de reenviar), así que React nunca muestra el aviso. Spring envía `room:closed` y cierra con 4404.
- `room:user_joined` y `room:user_left` solo se reparten entre los sockets de Spring. Las conexiones se registran en
  `room_connections` (con `channel_name` que empieza por `spring.`), así que sí cuentan en `players` de `/rooms`.
- Una sesión sin cookie válida se cierra con 4401 después de abrirse; Django rechaza el handshake.


| Método y ruta | Entrada | Respuesta |
| --- | --- | --- |
| GET `/auth/csrf` | | `{csrfToken}` y cookie `csrftoken` |
| POST `/auth/register` | `{email, password, dni, birth_date}` | 201 `{id, email}`. Crea la billetera con el bono de 1000 |
| POST `/auth/login` | `{email, password}` | Web: 200 `{id, email}` y las cookies. Móvil: 200 `{id, email, access_token, token_type, expires_in}` |
| GET `/auth/me` | | id, email, role (`USER` o `ADMIN`) |
| POST `/auth/refresh` | cookie `refresh_token` | `{detail, session_started_at}` y otra cookie de acceso |
| POST `/auth/logout` | cookies o Bearer | `{detail}`. Revoca esa sesión y borra las cookies |
| POST `/auth/forgot-password` | `{email}` | `{detail}`, igual exista o no la cuenta. El enlace sale por correo y vale 15 minutos |
| POST `/auth/reset-password` | `{token, new_password}` | `{detail}`. Cierra todas las sesiones |
| POST `/auth/not-me` | `{token}` | `{detail, locked_until}`. Botón "No fui yo" del correo de acceso nuevo: bloquea la cuenta 24 horas y cierra todas las sesiones. El enlace vale 24 horas; repetirlo no vuelve a bloquear |
| GET y PUT `/users/me`, `/users/profile` | `{nickname, avatar_url, first_name, last_name, phone}`, todos opcionales | id, email, dni, nickname, avatar_url, first_name, last_name, phone |
| POST `/users/avatar` | multipart, campo `avatar`: JPG, PNG o WEBP de hasta 2 MB | El perfil, con la foto en WEBP de 300 x 300 |
| PUT `/users/change-password` | `{current_password, new_password}` | `{detail}`. Cierra todas las sesiones |
| GET y PUT `/users/notification-preferences` | `{promotions, security, game_alerts}`, todos opcionales | Los tres valores |
| GET `/wallet/balance` | | balance, low_balance_threshold, is_low_balance |
| GET `/wallet/summary` | | balance, bonus_balance, winnings_balance, low_balance_threshold, is_low_balance |
| POST `/wallet/pre-bet-check` | `{amount}` | balance, can_bet |
| GET `/notifications` | | `{unread_count, results}` con las 30 últimas |
| POST `/notifications/{id}/read` | | La notificación y unread_count |
| POST `/notifications/read-all` | | `{updated, unread_count}` |
| GET `/responsible-gaming/daily-limit` | | configured, limit, spent, remaining, pending_limit, effective_at, date, timezone |
| POST `/responsible-gaming/daily-limit` | `{amount}` | Igual que el GET. Primer límite o reducción: inmediatos; aumento: en 24 horas |
| POST `/responsible-gaming/self-exclude` | `{days}`: 7, 30 o 90 | `{self_excluded_until, detail}`. Cierra todas las sesiones y no se puede cancelar |
| GET `/responsible-gaming/session-summary` | | Lo apostado, pagado y pendiente en la sesión actual, y su duración |
| GET `/responsible-gaming/risk-alerts` | | `{pause_until, results}` con las 20 últimas alertas |
| POST `/responsible-gaming/pause` | `{incident_id}` | `{pause_until}`. Pausa de 10 minutos; cada alerta da una sola |
| GET `/games` | `?category=ruleta\|cartas\|deportes\|otros&search=` | Catálogo con límites, portada e is_favorite |
| GET `/games/categories` | `?search=` | `{total, categories, favorites}`: cantidad de juegos por categoría |
| GET `/games/favorites` | `?search=` | Favoritos disponibles, del más reciente al más antiguo |
| POST `/games/{id}/favorite` | | `{game_id, is_favorite}`. Lo marca o lo quita |
| GET `/games/featured` | | Hasta 5 destacados, con play_room_id |
| GET `/games/recommendations` | | `{results, blocked, personalized}`: hasta 4 juegos con el motivo |
| GET `/games/{id}/rules` | | Reglas, tabla de pagos, RTP y ventaja de la casa |
| GET `/rooms` | `?game=ID` | id, name, min_bet, max_bet, players |
| GET `/rooms/{id}/state` | | Ronda actual, fichas, diez últimos resultados y números |
| POST `/rooms/{id}/bets` | `{round_id, kind, selection, chips, reference}` | 201 al aceptar; 200 si se repite la misma referencia |
| GET `/rooms/{id}/bets` | | Las últimas 100 apuestas propias en la sala |
| GET `/rooms/{id}/bets/previous` | | `{source_round_id, bets}`: apuestas propias de la última ronda liquidada |
| POST `/rooms/{id}/bets/repeat` | `{round_id, reference, source_round_id}`, el último opcional | 201 con las apuestas repetidas, todas o ninguna; 200 si se repite la misma referencia |
| GET `/users/bets/{id}` | | Detalle de una apuesta propia y de su ronda |
| GET `/users/bets-history/export` | `?format=xlsx\|pdf&startDate=&endDate=` | Archivo con las apuestas propias |
| GET `/stats/summary`, `/stats/win-rate`, `/stats/top-games` | `?startDate=&endDate=` | Totales, porcentaje de apuestas ganadas y los 5 juegos más jugados |
| GET `/stats/biggest-win` | | `{record}` con la mayor ganancia neta, o null |
| GET `/stats/balance-history` | `?period=7\|30` | Saldo al cierre de cada día de Lima |
| GET `/users/bets-history` | `?startDate=&endDate=&page=&page_size=` | `{count, next, previous, results}` (ruleta y casino) |
| GET `/casino/blackjack` | | `{hand}`: la última mano propia, o null |
| POST `/casino/blackjack` | `{amount, reference}`, de 10 a 500 | 201 con la mano nueva; 200 si se repite la misma referencia |
| POST `/casino/blackjack/{id}/action` | `{action: hit\|stand, revision}` | La mano. Con otra revisión es un reintento y no reparte ni paga |
| GET `/casino/sports/events` | | `{simulation, server_time, events}` con los 12 últimos encuentros |
| GET `/casino/sports/tickets` | | Los 50 últimos boletos propios |
| POST `/casino/sports/tickets` | `{event_id, selection, amount, quoted_odds, reference}` | 201 con el boleto; 200 si se repite la misma referencia |
| GET `/assistant/chat` | | `{messages, reminder}` con los 30 últimos mensajes |
| POST `/assistant/chat` | `{message}` | 201 `{question, reply, reminder}`. Hasta 20 por minuto y por jugador |
| GET `/assistant/odds` | `?bet=straight&number=17` o `?bet=color&selection=rojo` | Probabilidad, pago y ventaja de la casa |

Antes de la primera apuesta hay que fijar el límite diario.

`GET /media/**` sirve las fotos de avatar y las portadas, sin sesión. Los correos se escriben en la consola, como en
Django: ahí están los enlaces para restablecer la contraseña.

### Errores

| Estado | Cuándo |
| --- | --- |
| 400 `{campo: ["mensaje"]}` | Datos inválidos |
| 400 `{code, detail}` | `invalid_token` (enlace del correo), `bets_closed`, `insufficient_balance`, `daily_limit_required`, `daily_limit_exceeded`, `pause_active` |
| 400 `{amount: "mensaje"}` | Fuera del mínimo o del máximo acumulado de la sala |
| 400 `{detail}` | Mano de blackjack sin terminar, mercado cerrado o cuota distinta |
| 400 `{source_round_id: "mensaje"}` | No hay una ronda anterior que repetir, o no es esa |
| 401 `{detail}` | Sin sesión, vencida o revocada; credenciales inválidas. Una cookie inválida agrega `code` y `messages`, como SimpleJWT |
| 403 `{code, detail}` | `access_not_allowed` (excluido o autoexcluido), `account_suspended` |
| 403 `{detail: "CSRF: ..."}` | Escritura de la web sin un token CSRF válido o desde otro origen |
| 404 `{detail}` | Sala, juego, apuesta, alerta o página inexistente, o de otro jugador |
| 409 | Correo o DNI repetidos; `reference_conflict` si la referencia ya es de otra apuesta, jugada, boleto o repetición |
| 429 `{detail, retry_after}` | Cuenta bloqueada: 15 minutos tras 5 intentos fallidos o 24 horas con "No fui yo" |
| 429 `{code, detail}` | `assistant_rate_limited`: más de 20 mensajes al asistente por minuto |
| 429 `{detail}` | Más de 30 solicitudes de autenticación por minuto desde una IP |

## Cómo se comparte con Django

- **Contraseñas:** formato `bcrypt_sha256$$2b$12$...`. Una cuenta creada en un backend entra en el otro. También se
  aceptan los hashes `pbkdf2_sha256` antiguos, que se actualizan al entrar.
- **Sesiones:** cada login guarda una fila en `accounts_authsession`. Si Django la revoca, cambia la contraseña,
  suspende o autoexcluye al jugador, el JWT de Spring deja de servir. El JWT de la web tiene las claims de SimpleJWT,
  pero se firma con `JWT_SECRET`: una sesión abierta en un backend no sirve en el otro.
- **Cookies:** se llaman igual en los dos backends y no distinguen puertos. En un mismo navegador, entrar en uno
  reemplaza la sesión del otro en `localhost` (por ejemplo, la del panel de administración).
- **Fotos:** `avatar_url` y la carpeta `media` son comunes.
- **Repetir apuesta:** la referencia es común. Si se repite en un backend y se reintenta en el otro, responde la misma
  operación sin un segundo cargo.
- **Casino:** una mano o un boleto empezado en un backend se puede seguir o reintentar en el otro. Spring paga el
  blackjack; los deportes los paga Django al liquidar el encuentro.
- **Asistente:** la conversación es común. La clave de IA nunca se escribe en el log ni sale en una respuesta.
- **Apuestas:** los candados siguen el orden de Django (sala, ronda, usuario, billetera) y la referencia, el cupo diario
  y el saldo son comunes a los dos backends.

## Pruebas

Corren con JUnit 5 y MockMvc sobre una base PostgreSQL **exclusiva**, `palacetec_spring_test`. Borran datos, así que se
niegan a ejecutarse si el nombre de la base no termina en `_test`.

Crear la base una sola vez (variables solo en esa terminal, sin tocar el `.env` de Django):

```powershell
createdb -U postgres palacetec_spring_test
cd administracion/backend
$env:DB_ENGINE = "postgresql"; $env:DB_NAME = "palacetec_spring_test"
.\.venv\Scripts\python.exe manage.py migrate --noinput
Remove-Item Env:DB_ENGINE, Env:DB_NAME
```

Ejecutarlas desde `usuario/backend`, con `runroulette` detenido en esa base:

```powershell
$env:DB_USER = "postgres"; $env:DB_PASSWORD = "tu_clave_local"
.\mvnw.cmd test
```

Ninguna prueba llama a una IA real: el asistente usa un proveedor simulado, y los clientes de Gemini y OpenAI se prueban
contra un servidor local.

`TEST_DB_URL`, `TEST_DB_USER` y `TEST_DB_PASSWORD` permiten usar otra base de pruebas. Si Django agrega migraciones,
vuelve a correr `migrate` sobre ella.

## Pendiente y diferencias con Django

- Todas las rutas REST del jugador están migradas. `POST /responsible-gaming/excluded-people` es del administrador y
  sigue en Django, igual que el panel (`/admin`) y `healthcheck`.
- No hay WebSockets: `ws/roulette/{sala}/` y `ws/notifications/` siguen solo en Django, y aquí no se emiten
  `wallet:update`, `notification:new`, `session:revoked` ni `responsible:risk-alert`. La web ve los cambios al volver a
  consultar.
- Las alertas de riesgo de ruleta y deportes las crea Django al liquidar; las de blackjack, el backend que termina la mano.
- Casino: en una pausa, reintentar una jugada ya aceptada devuelve la misma jugada (200), como en la ruleta. Django
  responde 400 `pause_active`. Ninguno cobra dos veces.
- Asistente, comparado con Django:
  - el modelo de Gemini por defecto es `gemini-3.6-flash`; el de Django, `gemini-2.0-flash`, fue retirado por Google;
  - las preguntas y respuestas sobre el saldo o el límite no se envían a la IA dentro del historial;
  - los signos no ocultan una palabra ("¿algún truco?" se bloquea, "¡Hola!" se entiende), "me preocupo" no se confunde
    con "cupo" y una docena o columna que no existe no se responde como la primera;
  - cualquier respuesta inesperada de la IA cae en las preguntas frecuentes; en Django dos casos dan un error 500;
  - el límite por minuto se cuenta en la memoria de este proceso, aparte del de Django.
- Exportar el historial: con más de 5000 apuestas se conservan las más recientes (Django, las más antiguas). El correo y
  el título del juego salen siempre como texto y sin caracteres de control; en Django, un correo con `<` da un error 500
  en el PDF, un carácter de control da un error 500 en el Excel y un título que empieza con `=` queda como fórmula.
- `win-rate` y `biggest-win` cuentan apuestas, igual que Django, aunque la web las llame rondas: dos apuestas en la misma
  ronda son dos. `top-games` y las recomendaciones sí cuentan rondas.
- Recomendaciones: si dos juegos de la misma categoría empatan en rondas, "Porque jugaste..." nombra el de menor id, con
  los de ruleta primero. En Django depende del orden en que responda la base.
- `GET /games` y `GET /users/bets-history` conservan los mensajes de validación en español de la app móvil, y el segundo
  rechaza una fecha vacía. Las demás rutas usan los de Django, que ignora las fechas vacías.
- Repetir el enlace de "No fui yo" después de restablecer la contraseña responde 200 con `locked_until` nulo; Django
  da un error 500.
- No se escribe la auditoría (`admin_audit_logs`) de lo que un administrador haga en estas rutas.
- El límite por IP se cuenta en la memoria de este proceso, aparte del de Django.
- La app móvil no renueva su Bearer: al vencer se vuelve a iniciar sesión.
- Solo se aceptan cuerpos JSON (y multipart en el avatar); Django admite también formularios.
- Un WEBP animado se rechaza como avatar; Django usa su primer fotograma.
