# Billetera: HU05, HU06 y HU19

## Archivos y sustentacion

- `__init__.py`: permite importar wallet como un paquete de Python.
- `apps.py`: identifica la nueva app ante Django.
- `models.py`: Wallet guarda una billetera por usuario. DecimalField conserva dos decimales sin los errores de los flotantes. El CHECK protege el saldo en la propia base de datos. WalletTransaction guarda el tipo, monto, saldo posterior, referencia unica opcional y fecha de cada movimiento. Las tablas se llaman wallets y wallet_transactions.
- `migrations/__init__.py`: identifica el paquete de migraciones.
- `migrations/0001_initial.py`: describe las tablas, relaciones y restriccion que Django creara cuando se aplique la migracion. Generar una migracion no la aplica.
- `../config/settings.py`: registra wallet en INSTALLED_APPS.
- `../accounts/serializers.py`: crea usuario, billetera con 1000.00 y movimiento WELCOME_BONUS dentro de una transaccion atomica. Si falla cualquier paso, se deshacen todos.
- `services.py`: centraliza debit y credit. BET resta y WIN suma. Devuelve el movimiento creado o el movimiento original cuando se repite una referencia identica. Reutilizar una referencia con otro usuario, tipo o monto genera ReferenceConflict. Los montos invalidos generan ValueError; el saldo insuficiente, InsufficientBalance. Una referencia omitida permite movimientos independientes. El bloqueo de la billetera dura hasta terminar la transaccion; saldo y movimiento se guardan juntos.
- `views.py`: lee exclusivamente el saldo del usuario autenticado con la cookie JWT de HU02. Devuelve balance como texto, por ejemplo {"balance": "1000.00"}, para conservar la precision decimal.
- `urls.py`: define balance sin barra final.
- `../config/urls.py`: conecta esa ruta con /api/v1/wallet/balance.
- `tests.py`: comprueba bono, reversion ante fallos, cookie, aislamiento por usuario, montos invalidos, saldo insuficiente, idempotencia, referencias incompatibles y saldo no negativo. Las clases HU05, HU06 y HU19 usan APITestCase.

## Concurrencia

La clase HU19ConcurrencyTests usa APITransactionTestCase porque los hilos necesitan conexiones independientes y datos confirmados visibles. APITestCase envuelve cada prueba en una transaccion que no confirma; por eso no sirve para esta prueba con hilos.

Cuatro hilos intentan debitar 400 de un saldo de 1000: exactamente dos deben completar, dos deben recibir saldo insuficiente y el saldo final debe ser 200. Con PostgreSQL, select_for_update bloquea la fila hasta finalizar la operacion y los demas hilos esperan.

SQLite ignora select_for_update y usa bloqueos mas amplios para escritura: no garantiza este protocolo de bloqueo por fila y puede generar errores de base bloqueada. Por eso skipIf omite la prueba alli. El CHECK sigue impidiendo saldos negativos, pero no demuestra que no se pierdan actualizaciones concurrentes. Para validar concurrencia hay que ejecutar las pruebas con PostgreSQL.

## Validacion

Desde usuario/backend: `python manage.py test accounts wallet`.
El ejecutor de pruebas prepara y destruye una base temporal; no aplica cambios a la base de desarrollo. No se ejecuto migrate ni git push para esta entrega.
