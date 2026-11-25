# PalaceTec

Casino con juego responsable. Proyecto integrador de Construcción y Pruebas de Software. Créditos virtuales, sin dinero real.

## Arquitectura

| Módulo | Frontend | Backend |
| --- | --- | --- |
| Administración | React (`administracion/frontend-web`) | Django (`administracion/backend`) |
| Usuario (jugador) | React (`usuario/frontend-web`) y app móvil (`usuario/movil`) | Java + Spring Boot (`usuario/backend`) |

Los dos backends comparten **una sola base de datos PostgreSQL**. Su esquema está en
[database/schema.sql](database/schema.sql) y lo administran las migraciones de Django.

> **Estado actual (fase 1 de la reorganización):** solo se movieron carpetas. El backend Django completo
> (API del jugador, `admin_api`, WebSockets y juegos) vive por ahora en `administracion/backend`, y los dos
> frontends React lo siguen consumiendo en `http://localhost:8000`. `usuario/backend` tiene el
> MVP de la API del jugador en Java + Spring Boot (puerto 8080), sobre la misma base; ver su
> [README](usuario/backend/README.md).

## Estructura

```
administracion/
  backend/        Django REST Framework + Channels (hoy atiende al jugador y al administrador)
  frontend-web/   React + Vite, panel de administración (puerto 5174)
usuario/
  backend/        Java 21 + Spring Boot, API del jugador (puerto 8080)
  frontend-web/   React + Vite, jugador (puerto 5173)
  movil/          app móvil en Kotlin (más adelante)
database/         esquema PostgreSQL compartido (schema.sql) y cómo restaurarlo
docs/             planificación de sprints y guías de la API (SPRINT2_API.md a SPRINT4_API.md)
```

## Ejecutar y probar

Backend (desde `administracion/backend`):

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python manage.py test --settings=config.test_settings
python manage.py runserver
```

Frontends (desde `usuario/frontend-web` o `administracion/frontend-web`):

```powershell
npm ci
npm test
npm run dev
```

Más detalle en [administracion/backend/README.md](administracion/backend/README.md) y en las guías de
[docs/](docs/).

## Equipo

- Kiara Alburqueque (Scrum Master / Backend)
- Sebastián Espíritu (Frontend / Tester)
