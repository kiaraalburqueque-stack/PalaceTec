# Blackjack y deportes virtuales

Ampliación funcional posterior al Sprint 2. No cambia la numeración de las historias HU21 a HU40. Todos los importes son créditos virtuales; los eventos, cuotas y resultados deportivos son simulados, no se consultan proveedores ni partidos reales.

## Ejecutar

Aplicar las migraciones existentes con `DB_ENGINE=postgresql python manage.py migrate` en Git Bash (en PowerShell: `$env:DB_ENGINE='postgresql'` y luego `python manage.py migrate`). Mantener Redis, `runserver` y `runroulette` activos. El mismo reloj procesa ruleta, calendario deportivo y manos de blackjack abandonadas. No abrir un segundo reloj para estos juegos.

Rutas de la web: `/blackjack` y `/deportes`. La portada tiene accesos a los dos juegos y a la ruleta. Requieren login y límite diario configurado; comparten saldo, consumo de bono, cupo, autoexclusión y pausa. No requieren recarga ni dinero real.

## Blackjack

Una baraja de 52 cartas, mezclada de nuevo al comenzar cada mano mediante el generador seguro del servidor. As vale 1 u 11, figuras 10. El jugador pide carta o se planta; la banca se planta en 17, incluido 17 suave. No incluye dividir, doblar, rendirse ni seguro.

Apuesta mínima 10 y máxima 500 CRD. Devoluciones totales: victoria ×2, blackjack natural ×2.5, empate ×1 y derrota ×0. El blackjack de la banca se comprueba al repartir. Dos naturales empatan. Al llegar a 21 con más de dos cartas no se paga como natural.

Solo una mano activa por usuario. GET no expone la baraja ni la segunda carta ni la puntuación total de la banca mientras la mano siga abierta. Cada acción incluye la revisión recibida; repetir una acción antigua no vuelve a repartir. A los diez minutos sin actividad el servidor planta la mano automáticamente. Esto permite liquidar apuestas ya aceptadas incluso si luego la cuenta se autoexcluye; no se cobra una nueva apuesta.

## Deportes simulados

Mercado simple de fútbol 1/X/2: HOME, DRAW, AWAY. No hay combinadas, cash-out ni apuestas durante el encuentro. Cada ciclo prepara tres encuentros ficticios; inician a los 90, 150 y 210 segundos y terminan 60 segundos después de su inicio. El marcador se genera en el servidor al terminar y el pago se confirma en la misma transacción, sin duplicarlo si se reintenta el reloj.

Cuotas decimales de demostración: local 1.90, empate 3.20, visitante 2.40. Son constantes del simulador, no cuotas de mercado real. El boleto conserva una copia de la cuota aceptada y rechaza una confirmación con cuota distinta. El pago total es importe por cuota, redondeado a dos decimales. Apostar 10 a cuota 1.90 devuelve 19 si acierta, con ganancia neta 9. No se garantizan ganancias.

## API

Usa las mismas cookies, renovación y CSRF documentados en `SPRINT2_API.md`.

| Método y ruta bajo `/api/v1/casino` | Entrada o respuesta |
| --- | --- |
| GET `/blackjack` | `{hand}`: última mano propia, activa o liquidada. |
| POST `/blackjack` | `{amount: "10.00", reference: UUID}`. Primera respuesta 201; reintento idéntico 200. |
| POST `/blackjack/{id}/action` | `{action: "hit" o "stand", revision: 0}`. Nueva revisión y estado. Solo mano propia. |
| GET `/sports/events` | simulation=true, server_time y los doce eventos más recientes con cuotas, fase y resultado publicado. |
| GET `/sports/tickets` | Últimos cincuenta boletos del usuario. |
| POST `/sports/tickets` | `{event_id, selection, amount, quoted_odds, reference}`. 201 o 200 en reintento idéntico. |

Una referencia no puede reutilizarse para otra jugada. Usar UUID nueva al apostar y conservarla cuando haya un error de red. Los bloqueos por usuario serializan apuestas de todos los juegos con el mismo cupo diario y billetera. Los pagos no devuelven cupo consumido.

Historial paginado y estadísticas combinan los tres productos. Las filas incluyen `source` (`roulette` o `casino`); usar `source:id` como clave de interfaz, porque son tablas distintas. Blackjack y deportes no tienen room_id/round_id. Un empate tiene status=PUSH. El resumen de sesión incluye también sus resultados y apuestas pendientes.

Las alertas de escalamiento continúan en ruleta y se evalúan también por producto nuevo sobre sus últimas cuatro jugadas liquidadas. La alerta es informativa; el cupo y la pausa se imponen en el servidor. Si el reloj o Redis se interrumpen, el dinero confirmado permanece en PostgreSQL y el reloj reanuda las liquidaciones pendientes al arrancar.

## Verificación

Pruebas en `casino/tests.py` y `casino/test_concurrency.py`: naturales, ases, empate, carta oculta, límite, exclusión, reversión por saldo insuficiente, cuota y cierre del mercado, liquidación única, privacidad, resumen de sesión y carreras entre eventos. Frontend: `NewGames.test.jsx` cubre cartas, boleto y reutilización de referencia después de error de red.
