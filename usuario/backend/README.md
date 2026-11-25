# Backend del jugador (Spring Boot)

API del jugador de PalaceTec en Java 21 y Spring Boot 3.5 (Web, Data JPA, Security, Validation, PostgreSQL y jjwt).
Usa **la misma base PostgreSQL que Django** (`administracion/backend`): no crea ni modifica tablas
(`ddl-auto=validate`). Créditos virtuales, sin dinero real.

Spring no abre, cierra ni liquida rondas: eso lo hace `python manage.py runroulette`, que debe estar corriendo
con la misma base para poder apostar.

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
| `CORS_ALLOWED_ORIGINS` | No | Por defecto `http://localhost:5173` |

## Ejecutar

En PowerShell, desde `usuario/backend`:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.12.1"   # ajusta a tu JDK 21
.\mvnw.cmd spring-boot:run
```

Queda en `http://localhost:8080`. En otra terminal, desde `administracion/backend` y con la misma base:
`python manage.py runroulette`.

## Endpoints

Base `/api/v1`, sin barra final. Salvo registro y login, todos exigen `Authorization: Bearer <access_token>`.
No hay cookies ni CSRF. Rutas y campos JSON son los de Django; el dinero viaja como texto (`"100.00"`).

| Método y ruta | Entrada | Respuesta |
| --- | --- | --- |
| POST `/auth/register` | `{email, password, dni, birth_date}` | 201 `{id, email}`. Crea la billetera con el bono de 1000 |
| POST `/auth/login` | `{email, password}` | 200 `{id, email, access_token, token_type, expires_in}` |
| GET `/users/me` | | id, email, dni, nickname, avatar_url, first_name, last_name, phone |
| GET `/wallet/summary` | | balance, bonus_balance, winnings_balance, low_balance_threshold, is_low_balance |
| GET `/responsible-gaming/daily-limit` | | configured, limit, spent, remaining, pending_limit, effective_at, date, timezone |
| POST `/responsible-gaming/daily-limit` | `{amount}` | Igual que el GET. Primer límite o reducción: inmediatos; aumento: en 24 horas |
| GET `/games` | `?category=ruleta\|cartas\|deportes\|otros&search=` | Catálogo con límites, portada e is_favorite |
| GET `/rooms` | `?game=ID` | id, name, min_bet, max_bet, players |
| GET `/rooms/{id}/state` | | Ronda actual, fichas, diez últimos resultados y números |
| POST `/rooms/{id}/bets` | `{round_id, kind, selection, chips, reference}` | 201 al aceptar; 200 si se repite la misma referencia |
| GET `/users/bets-history` | `?startDate=&endDate=&page=&page_size=` | `{count, next, previous, results}` (ruleta y casino) |

Antes de la primera apuesta hay que fijar el límite diario.

### Errores

| Estado | Cuándo |
| --- | --- |
| 400 `{campo: ["mensaje"]}` | Datos inválidos |
| 400 `{code, detail}` | `bets_closed`, `insufficient_balance`, `daily_limit_required`, `daily_limit_exceeded`, `pause_active` |
| 400 `{amount: "mensaje"}` | Fuera del mínimo o del máximo acumulado de la sala |
| 401 `{detail}` | Sin token, vencido o revocado; credenciales inválidas |
| 403 `{code, detail}` | `access_not_allowed` (excluido o autoexcluido), `account_suspended` |
| 404 `{detail}` | Sala o página inexistente |
| 409 | Correo o DNI repetidos; `reference_conflict` si la referencia ya es de otra apuesta |
| 429 `{detail, retry_after}` | Cuenta bloqueada 15 minutos tras 5 intentos fallidos |

## Cómo se comparte con Django

- **Contraseñas:** formato `bcrypt_sha256$$2b$12$...`. Una cuenta creada en un backend entra en el otro.
- **Sesiones:** cada login guarda una fila en `accounts_authsession`. Si Django la revoca, cambia la contraseña,
  suspende o autoexcluye al jugador, el JWT de Spring deja de servir.
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

`TEST_DB_URL`, `TEST_DB_USER` y `TEST_DB_PASSWORD` permiten usar otra base de pruebas. Si Django agrega migraciones,
vuelve a correr `migrate` sobre ella.

## Pendiente fuera de este MVP

- No hay WebSockets: una apuesta hecha aquí no emite `wallet:update`; la web ve el saldo al resincronizar.
- No hay refresh ni logout: al vencer el JWT se vuelve a iniciar sesión. El KPI de usuarios en línea cuenta estas
  sesiones hasta que vencen (8 horas).
- No se envía la alerta de acceso nuevo (HU56) ni se escribe `login_history`, para no dar por conocido un dispositivo
  sin avisar.
- No se replica el límite de 30 logins por minuto por IP; el bloqueo por cuenta sí.
