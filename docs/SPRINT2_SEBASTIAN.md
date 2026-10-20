# Sprint 2 · Entrega de Sebastián

Equipo: Kiara Alburqueque (backend) y Sebastián Espíritu (frontend/tester).
Rama de trabajo: `Sebastian`. El backend HU21–HU40 se recibió mediante el merge
`c9c9eb9`; esta entrega integra las interfaces y sus pruebas sin cambiar el backend.

El laboratorio enumera HU21–HU40 con títulos genéricos. La correspondencia
funcional siguiente sigue la trazabilidad de `usuario/backend/SPRINT2_API.md`.
Se conserva un commit de implementación por historia, más la validación conjunta.

| Historia | Parte frontend / integración |
| --- | --- |
| HU21 | Reconexión del socket después de renovar, cierre por revocación y sincronización antes de habilitar apuestas. |
| HU22 | Cierre según el plazo del servidor, desfase horario y bloqueo mientras no hay conexión válida. |
| HU23 | Resultado confirmado con número, color y paridad; recuperación desde el estado persistido y deduplicación. |
| HU24 | Docenas/columnas, acumulación de fichas, limpieza de borrador entre rondas y referencias estables para reintentos. |
| HU25 | Registro interno de excluidos para administradores; el formulario borra el DNI después de guardar. |
| HU26 | Configuración obligatoria del límite, cupo disponible, reducciones y aumentos diferidos. |
| HU27 | Autoexclusión de 7/30/90 días con confirmación explícita y cierre de sesión. |
| HU28 | Aviso horario con resumen persistido de sesión; continuar conserva el próximo intervalo al recargar. |
| HU29 | Recuperación de incidentes y pausas; descanso voluntario y bloqueo hasta su vencimiento. |
| HU30 | Renovación de acceso compartida, un reintento HTTP y cierre de la sesión sin bucles. |
| HU31 | Historial privado paginado con estados, pago total y neto; pendientes diferenciados. |
| HU32 | Fechas desde/hasta, validación del rango y reinicio de paginación; días completos en Lima. |
| HU33 | Hasta diez resultados únicos de la sala, con número y color. |
| HU34 | Catálogo de juegos publicados y consulta de salas por juego. |
| HU35 | Vista previa y repetición atómica del conjunto anterior; reintentos conservan referencia y ronda. |
| HU36 | Aviso de saldo bajo según el umbral del servidor, cerrable sin repetirse por cada actualización. |
| HU37 | Perfil ampliado y guardado parcial; correo/DNI de solo lectura. |
| HU38 | Cambio de contraseña con confirmación, validación y nuevo inicio de sesión obligatorio. |
| HU39 | Rol consultado al backend; enlace administrativo y acceso protegido. |
| HU40 | Estadísticas privadas de apuestas liquidadas, filtros y distinción entre pago total y neto. |

## Validación

Resultado: 90 pruebas frontend aprobadas, dos recorridos de Edge aprobados,
lint y compilación correctos.

Desde `usuario/frontend-web`: `npm test`, `npm run lint`, `npm run build` y
`npm run test:e2e`. Los dos recorridos de Edge usan Django y Channels reales con
SQLite temporal y cache/canales en memoria; no sustituyen la prueba integrada
con Redis ni las carreras de PostgreSQL. Ver el README del frontend para requisitos.

Desde `usuario/backend`, con SQLite y `config.test_settings`, se ejecutaron las
151 pruebas existentes: 145 aprobadas y seis omitidas por requerir PostgreSQL.
`manage.py check` y `makemigrations --check --dry-run` también aprobaron.
La conexión local a PostgreSQL agotó su tiempo de espera y Redis no estaba
disponible. Por eso no se atribuye a esta entrega una nueva validación de
concurrencia con PostgreSQL/Redis. La validación anterior de Kiara se conserva
documentada en `SPRINT2_API.md`.

## Revisión antes del merge

Revisar los commits de `Sebastian`, levantar el entorno compartido con PostgreSQL
y Redis y repetir registro → límite → apuesta → liquidación → historial →
repetición → bloqueo por cupo → logout. Integrar a `main` mediante el merge que
decida el equipo. Esta entrega no realiza ese merge.

## Commits por historia

| HU | Commit en Sebastian |
| --- | --- |
| HU21 | `c484bda` |
| HU22 | `3dda4e9` |
| HU23 | `31a3efd` |
| HU24 | `1be8a9c` |
| HU25 | `cadf0b4` |
| HU26 | `c03929b` |
| HU27 | `40a336b` |
| HU28 | `22ed919` |
| HU29 | `17524b1` |
| HU30 | `c95f0a9` |
| HU31 | `30c8aea` |
| HU32 | `82ba2c4` |
| HU33 | `fa7e965` |
| HU34 | `e62f71c` |
| HU35 | `e196760` |
| HU36 | `39cded2` |
| HU37 | `c9dce1a` |
| HU38 | `8a520af` |
| HU39 | `65ce386` |
| HU40 | `217bd44` |
