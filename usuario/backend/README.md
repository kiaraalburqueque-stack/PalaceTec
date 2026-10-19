# Backend de PalaceTec

Django REST Framework, sesiones JWT revocables, billetera y ruleta europea con WebSockets. El Sprint 2 agrega juego responsable, catálogo de juegos, historial, estadísticas y perfil ampliado. Consulta [SPRINT2_API.md](SPRINT2_API.md) para instalar las migraciones y conectar todas las funciones nuevas. Los contratos ampliados de esa guía prevalecen sobre los ejemplos históricos del Sprint 1 que siguen abajo.

## Ejecutar y probar

Desde `usuario/backend`, con el entorno virtual activado:

```powershell
python -m pip install -r requirements.txt
$env:DB_ENGINE = "sqlite"
python manage.py test accounts wallet roulette --settings=config.test_settings
python manage.py makemigrations --check --dry-run --settings=config.test_settings
```

Las pruebas preparan y destruyen una base temporal, sin aplicar cambios a la base de desarrollo. `config/test_settings.py` reemplaza Redis por memoria solo durante las pruebas. Ejecutar también con PostgreSQL: SQLite omite las pruebas que requieren bloqueo por fila.

Antes de ejecutar el sistema, aplicar las migraciones existentes con `python manage.py migrate` en la base seleccionada. No generar migraciones adicionales para instalar el proyecto.

Redis es compartido por Channels, el reloj, la cache y el limitador de intentos. Si tienes Docker, puedes iniciarlo con:

```powershell
docker compose -f compose.redis.yml up -d
```

Si Redis ya está instalado, basta usar ese servicio. La URL predeterminada es `redis://127.0.0.1:6379/0`; puede cambiarse con la variable `REDIS_URL`. Las pruebas aisladas usan memoria; la ejecución integrada necesita Redis compartido.

Despues de preparar la base y Redis, abre dos terminales con el mismo entorno:

```powershell
# Terminal 1: HTTP y WebSockets mediante Daphne
python manage.py runserver
```

```powershell
# Terminal 2: reloj central, independiente de los navegadores
python manage.py runroulette
```

`runroulette --once` procesa un tick y termina. Las rondas tienen 15 segundos para apostar, pasan a CLOSED y se liquidan en el siguiente tick; despues comienza una ronda nueva. Si el proceso se interrumpe, retoma la ronda persistida sin reiniciar su plazo. Las salas desactivadas terminan sus rondas pendientes y no abren nuevas.

Para la validación concurrente, activa PostgreSQL y usa DB_ENGINE distinto de sqlite con las variables DB_NAME, DB_USER, DB_PASSWORD, DB_HOST y DB_PORT habituales. El ejecutor necesita permiso para crear una base de pruebas. Ejecuta las pruebas con `--settings=config.test_settings`; las carreras del Sprint 2 se verifican en PostgreSQL.

SQLite ignora select_for_update. Sirve para desarrollo y pruebas funcionales, pero no demuestra que operaciones simultaneas respeten el bloqueo por fila. Los CHECK siguen impidiendo saldos negativos. Las pruebas con hilos usan skipIf y requieren PostgreSQL.

## Contrato HTTP para conectar el frontend

Todas las rutas se usan sin barra final. Las protegidas reciben exclusivamente la cookie JWT, con `credentials: "include"`. El frontend debe usar el mismo host del backend: no mezclar localhost y 127.0.0.1 para las cookies.

| Metodo y ruta | Funcion |
| --- | --- |
| GET /api/v1/auth/csrf | Entrega csrfToken y establece la cookie CSRF. |
| POST /api/v1/auth/register | Registro con bono de 1000.00. |
| POST /api/v1/auth/login | Login y cookie JWT HttpOnly. |
| GET /api/v1/auth/me | Conserva la respuesta original id y email. |
| GET /api/v1/users/me | Perfil para el header: id, email, nickname y avatar_url. |
| PUT /api/v1/users/profile | Actualiza nickname y avatar_url. |
| GET /api/v1/wallet/balance | Saldo total, por ejemplo {"balance":"1000.00"}. |
| GET /api/v1/wallet/summary | balance, bonus_balance y winnings_balance. |
| POST /api/v1/wallet/pre-bet-check | Recibe amount; devuelve balance y can_bet. Es orientativo: apostar vuelve a comprobar el saldo con bloqueo. |
| GET /api/v1/rooms | Salas activas, limites y jugadores distintos conectados. |
| GET /api/v1/rooms/{id}/state | Ronda actual, hora del servidor, plazo, 37 casillas y fichas autorizadas. |
| POST /api/v1/rooms/{id}/bets | Valida, registra y debita una apuesta atomica. |
| GET /api/v1/rooms/{id}/bets | Las ultimas 100 apuestas del usuario autenticado en esa sala, incluyendo sus pagos. |

Antes de registro/login y de cualquier POST o PUT, obtiene el CSRF y envialo en X-CSRFToken. Ejemplo:

```javascript
const base = "http://localhost:8000/api/v1";
const { csrfToken } = await fetch(`${base}/auth/csrf`, {
  credentials: "include"
}).then(response => response.json());

const login = await fetch(`${base}/auth/login`, {
  method: "POST",
  credentials: "include",
  headers: { "Content-Type": "application/json", "X-CSRFToken": csrfToken },
  body: JSON.stringify({ email: "kiara@example.com", password: "Palace123!" })
});
```

Ejemplo de apuesta:

```json
{
  "round_id": 1,
  "kind": "STRAIGHT_UP",
  "selection": "17",
  "chips": [10, 50],
  "reference": "550e8400-e29b-41d4-a716-446655440000"
}
```

Tipos: STRAIGHT_UP con seleccion "0" a "36"; COLOR con "rojo" o "negro"; PARITY con "par" o "impar". Solo se aceptan fichas de 10, 50, 100 y 500, hasta 100 fichas por solicitud. El monto es la suma calculada por el servidor. Debe alcanzar el minimo de la sala; el total de apuestas de ese usuario en la ronda no puede superar el maximo.

Usa `crypto.randomUUID()` para una nueva apuesta. Si reintentas la misma solicitud por un problema de red, conserva su referencia y datos. La primera respuesta es 201; el reintento identico devuelve 200 sin descontar de nuevo, incluso si la ronda ya termino. Cambiar los datos reutilizando la referencia devuelve 409. No reutilices esa referencia para una apuesta nueva.

Los errores de saldo insuficiente y cierre devuelven 400 con `code: "insufficient_balance"` o `code: "bets_closed"`. No tener sesion devuelve 401; fallar CSRF devuelve 403. Las selecciones, fichas y limites invalidos devuelven 400. El login limita por IP y bloquea temporalmente la cuenta tras cinco intentos fallidos. En conexion directa se ignora X-Forwarded-For para impedir que el cliente simule otra IP; si se despliega detras de un proxy de confianza, configura NUM_PROXIES segun ese despliegue.

Perfil: nickname acepta de 3 a 30 letras ASCII, numeros o guion bajo, se guarda en minusculas y es unico. avatar_url debe ser uno de `/avatars/avatar-1.svg` a `/avatars/avatar-6.svg`. El frontend debe proporcionar esas seis imagenes; el backend almacena y valida su identificador.

## Contrato WebSocket

Con una sesion iniciada:

```javascript
const socket = new WebSocket("ws://localhost:8000/ws/roulette/1/");
socket.onmessage = ({ data }) => {
  const { event, data: payload } = JSON.parse(data);
  console.log(event, payload);
};
// Para resincronizar despues de reconectar:
socket.onopen = () => socket.send(JSON.stringify({ event: "room:sync" }));
```

La cookie se envia automaticamente. No se envian tokens en la URL. El origen permitido por defecto es http://localhost:5173; ajusta CORS_ALLOWED_ORIGINS, CSRF_TRUSTED_ORIGINS y WEBSOCKET_ALLOWED_ORIGINS si cambia el frontend. La conexion se cierra al vencer el JWT.

| Evento del servidor | Datos y uso |
| --- | --- |
| room:state | Estado completo para conectar o resincronizar. |
| room:user_joined / room:user_left | user_id de la conexion que ingresa o sale; vuelve a consultar el catalogo para el conteo de usuarios distintos. |
| timer:start | id de ronda, opened_at, closes_at y remaining_seconds. |
| timer:tick | round_id, closes_at y remaining_seconds. |
| timer:stop / bets:closed | round_id; bloquear el tapete. |
| round:result | round_id, winning_number y settled_at; visible en la sala. |
| wallet:update | balance, bonus_balance y winnings_balance; solo para su propietario. |
| round:winnings | round_id y payout total de ese usuario; solo para su propietario. |
| pong | Respuesta a {"event":"ping"}. |
| error | Mensaje ante JSON invalido o evento no permitido. |

Los clientes solo pueden enviar ping y room:sync. Las apuestas se envian por HTTP con CSRF. Los clientes no pueden iniciar/cerrar rondas, escoger resultados ni acreditar dinero. Las conexiones tienen una presencia con vencimiento que el reloj limpia si desaparecen sin desconectarse correctamente.

Los eventos se publican despues del commit. Si Redis falla al publicar, el dinero ya confirmado no se revierte; queda un error registrado. No hay cola durable de reenvio de eventos: al reconectar, consulta estado, saldo y apuestas propias para recuperar el resultado desde la base.

## Trazabilidad del backend del Sprint 1

| HU | Implementacion de backend |
| --- | --- |
| HU01 | Registro, Bcrypt coste 12, email/DNI unicos y CHECK de mayoria de edad al registrarse. |
| HU02 | JWT en cookie, bloqueo por cuenta, limite por IP y CSRF. |
| HU03 | Validacion de longitud, mayuscula y simbolo en el registro. |
| HU04 | Perfil publico en /users/me, con clave primaria indexada. |
| HU05 | Usuario, billetera y movimiento WELCOME_BONUS de 1000.00 en una transaccion. |
| HU06 | Consulta, desglose por vista SQL y evento wallet:update. |
| HU07 | pre_bet_check y consumo del bono antes de ganancias. |
| HU08 | Error HTTP 400 por saldo insuficiente; el frontend usa can_bet para su boton. |
| HU09 | Catalogo y conteo SQL de jugadores distintos con presencia vigente. |
| HU10 | Channels, rutas de sala, eventos de conexion, cache Redis y prueba de 12 clientes. |
| HU11 | Matriz de 37 casillas con color desde el backend. |
| HU12 | Validacion de fichas autorizadas en el DTO/serializer. |
| HU13 | Pleno, limites, registro STRAIGHT_UP y pago total 36x. |
| HU14 | Mapeo rojo/negro, pago total 2x y cero perdedor. |
| HU15 | Paridad, pago total 2x y cero perdedor. |
| HU16 | Reloj central con ventana de 15 segundos y eventos sincronizados. |
| HU17 | Cierre automatico y rechazo por hora del servidor, aun si se retrasa el reloj. |
| HU18 | RNG criptografico, numero y fecha persistidos, evaluacion y round:result. |
| HU19 | Debito/premio atomicos, movimientos, idempotencia, rollback y aviso privado. |
| HU20 | Actualizacion de apodo/avatar y unicidad del apodo en BD. |

El documento del laboratorio usa Node.js, Zod, Socket.io y Jest. Este proyecto adopta Django/DRF, serializers, Channels y unittest/APITestCase para las mismas responsabilidades. HU05 mantiene la creacion atomica explicita autorizada en la tarea inicial, en lugar de un trigger SQL; cubre el registro HU01, no inserciones SQL ajenas al servicio. Las tareas de componentes React, animaciones, assets, deshabilitar botones y pruebas de clicks pertenecen a la integracion del frontend y no se implementan aqui.

## Explicacion de los archivos para sustentacion

| Archivo | Explicacion sencilla |
| --- | --- |
| accounts/models.py | Define al usuario; agrega apodo/avatar y restricciones de edad y unicidad. |
| accounts/serializers.py | Valida el registro y el perfil; crea usuario, billetera y bono juntos. |
| accounts/views.py | Recibe solicitudes de registro, login, perfil y CSRF, y devuelve respuestas. |
| accounts/authentication.py | Lee la cookie JWT; exige CSRF cuando una sesion modifica datos. |
| accounts/throttles.py | Cuenta intentos por IP en cache y limita solicitudes de login. |
| accounts/urls.py y profile_urls.py | Asocian las direcciones con las vistas de autenticacion y perfil. |
| accounts/migrations/0003_*.py | Agrega las columnas de apodo y avatar. |
| accounts/migrations/0004_*.py | Agrega las restricciones de apodo y edad en la base. |
| accounts/test_profile.py y test_security.py | Prueban perfil, unicidad, edad, limite por IP y CSRF real. |
| wallet/models.py | Guarda saldo total y bono; calcula ganancias y representa la vista SQL. |
| wallet/services.py | Comprueba saldo, descuenta bono primero y registra debitos/premios con bloqueo e idempotencia. |
| wallet/views.py y urls.py | Exponen saldo, desglose y comprobacion previa del usuario conectado. |
| wallet/migrations/0002_*.py | Agrega saldo de bono y reconstruye el bono restante con los movimientos existentes. |
| wallet/migrations/0003_*.py | Crea la vista SQL wallet_balances para obtener el desglose. |
| wallet/test_bonus.py | Comprueba consumo del bono, desglose y restricciones. |
| roulette/models.py | Define salas, rondas, apuestas y conexiones, con restricciones de consistencia. |
| roulette/migrations/0001_initial.py | Describe las nuevas tablas y restricciones. |
| roulette/migrations/0002_default_rooms.py | Prepara tres salas iniciales. |
| roulette/serializers.py | Valida el JSON de una apuesta y da formato a la respuesta. |
| roulette/services.py | Coordina apuestas, reloj, cierre, RNG y pago total de la ronda. |
| roulette/state.py | Construye el estado de sala y guarda una copia temporal en Redis. La BD decide saldos y plazos. |
| roulette/views.py y urls.py | Exponen catalogo, estado y apuestas propias. |
| roulette/consumers.py | Maneja cada socket: ingreso, salida, vencimiento, resincronizacion y eventos privados/publicos. |
| roulette/routing.py | Asocia /ws/roulette/{id}/ con el consumidor. |
| roulette/management/commands/runroulette.py | Ejecuta el reloj una vez por segundo, sin depender de un navegador. |
| roulette/test_rooms.py | Comprueba catalogo, conteo de usuarios y matriz/fichas. |
| roulette/test_game.py | Prueba apuestas, limites, cierre, premios, reintentos y rollback completo. |
| roulette/test_websocket.py | Prueba autenticacion, origen, eventos privados, ronda completa, 12 clientes y comando. |
| roulette/test_concurrency.py | Usa hilos para probar cobros/premios unicos y saldo seguro en PostgreSQL. |
| realtime/authentication.py | Autentica el socket con la misma cookie JWT del login. |
| realtime/events.py | Publica eventos solo despues de confirmar la transaccion. Registra fallos de envio. |
| config/settings.py | Configura apps, Redis, Channels, CSRF, origen y limite de login. |
| config/test_settings.py | Aisla pruebas de Redis sin cambiar la base elegida. |
| config/asgi.py | Dirige HTTP a Django y WebSockets a Channels. |
| config/urls.py | Conecta las rutas de todas las apps. |
| requirements.txt | Incluye las dependencias para instalar el backend y los sockets. |
| compose.redis.yml | Permite iniciar Redis local con Docker. |
| Archivos __init__.py | Identifican carpetas como paquetes Python. |
| README.md y guias de wallet/roulette | Documentan decisiones, ejemplos, pruebas y sustentacion. |

Los archivos puros rng.py, constants.py y payout.py conservan la explicacion en roulette/README.md. El frontend puede usar esta guia para conectar sus formularios, salas, reloj, tapete y saldo.
