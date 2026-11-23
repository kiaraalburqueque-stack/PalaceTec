# Base de datos de PalaceTec

PalaceTec usa **una sola base de datos PostgreSQL** compartida por los dos backends:

- `administracion/backend` (Django): dueño del esquema; lo crea y modifica con sus migraciones.
- `usuario/backend` (Kotlin + Spring Boot, por construir): se conecta a la misma base y usa las tablas existentes.

## schema.sql

`schema.sql` es la estructura completa (tablas, índices, restricciones y la vista `wallet_balances`)
después de aplicar todas las migraciones de Django. **No contiene datos**: se generó con
`pg_dump --schema-only` sobre una base vacía recién migrada, así que no incluye usuarios, DNI,
billeteras ni ningún otro dato personal.

Sirve como referencia del modelo de datos (por ejemplo, para mapear las entidades en Spring Boot) y para
crear una base vacía sin Python.

## Restaurar el esquema

Requiere PostgreSQL 18 (el archivo usa `\restrict`, que entienden `psql` 18 y las versiones 17.6 o posteriores).
En PowerShell, con `bin` de PostgreSQL en el PATH (por ejemplo `D:\Postgresql\bin`):

```powershell
createdb -U postgres palacetec
psql -U postgres -d palacetec -v ON_ERROR_STOP=1 -f database/schema.sql
```

La base restaurada queda con la tabla `django_migrations` vacía. Si después vas a usar Django sobre ella, es
más simple crear la base vacía y dejar que Django la construya:

```powershell
createdb -U postgres palacetec
cd administracion/backend
.\.venv\Scripts\Activate.ps1
python manage.py migrate
```

`migrate` también carga los datos iniciales (catálogo de juegos, salas y grupo de auditores) que `schema.sql` no trae.

## Regenerar schema.sql

Cuando cambien los modelos, regenera el archivo desde una base temporal para no exportar datos reales:

```powershell
createdb -U postgres palacetec_schema_tmp
cd administracion/backend
$env:DB_NAME = "palacetec_schema_tmp"
.\.venv\Scripts\python.exe manage.py migrate --noinput
Remove-Item Env:DB_NAME
cd ../..
pg_dump -U postgres -d palacetec_schema_tmp --schema-only --no-owner --no-privileges --encoding=UTF8 -f database/schema.sql
dropdb -U postgres palacetec_schema_tmp
```
