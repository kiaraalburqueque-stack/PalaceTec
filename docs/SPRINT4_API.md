# Backend del Sprint 4 (HU61 a HU75)

Amplía el backend del Sprint 3 con exploración del catálogo, el asistente virtual, recomendaciones, exportaciones,
reportes de negocio, monitor de salud y limpieza de notificaciones. El plan y la trazabilidad están en
[docs/SPRINT4.md](SPRINT4.md).

## Instalación

Desde `usuario/backend`, con el entorno virtual activado:

```powershell
python -m pip install -r requirements.txt   # nuevas: Pillow, openpyxl y reportlab
python manage.py migrate                    # roulette 0011-0014, assistant 0001, notifications 0003
python manage.py test --settings=config.test_settings
```

La migración `roulette 0012` agrega **Blackjack 21** (CARTAS) y **Deportes virtuales** (DEPORTES) al catálogo, enlazados
a `/blackjack` y `/deportes`. No modifica `casino.Play`: cada jugada se relaciona con su juego por `Play.kind = Game.play_kind`.
Los límites de apuesta de esos dos juegos siguen fijos en el casino (10 a 500 CRD); editarlos en el panel solo cambia lo que muestra el catálogo.

### Variables nuevas del `.env` (ver `.env.example`)

| Variable | Uso |
| --- | --- |
| `ASSISTANT_PROVIDER` | `faq` (por defecto), `gemini` u `openai`. |
| `ASSISTANT_API_KEY` | Clave del proveedor. Sin clave el asistente responde siempre con preguntas frecuentes. |
| `ASSISTANT_MODEL`, `ASSISTANT_BASE_URL`, `ASSISTANT_TIMEOUT` | Opcionales: modelo, URL compatible con OpenAI y segundos de espera (10). |

`config/test_settings.py` fuerza el modo `faq`: las pruebas nunca llaman a un servicio externo.

## Jugador (`/api/v1`, con sesión)

| Método y ruta | Historia | Contrato |
| --- | --- | --- |
| GET `/games?category=&search=` | HU61, HU62 | `category`: ruleta, cartas, deportes u otros. `search` no distingue tildes, ñ ni mayúsculas. Cada juego trae `category_slug`, `play_path` (pantalla propia o `null`), `rooms_url` e `is_favorite`. |
| GET `/games/categories?search=` | HU61, HU63 | `{total, favorites, categories: [{slug, label, count}]}`. |
| POST `/games/{id}/favorite` | HU63 | Alterna el favorito: `{game_id, is_favorite}`. Un juego inactivo se puede quitar pero no agregar (404). |
| GET `/games/favorites?search=` | HU63 | Favoritos disponibles, del más reciente al más antiguo. |
| GET `/games/featured` | HU64 | Hasta 5 destacados activos, con `play_room_id` o `play_path`. |
| GET `/games/recommendations` | HU70 | `{results, blocked, personalized}`. Hasta 4 juegos con `reason` (Porque jugaste…, Está en tus favoritos, Popular en PalaceTec). `blocked`: `self_excluded`, `pause_active` o `daily_limit_reached` y lista vacía. |
| POST `/users/avatar` | HU65 | Multipart, campo `avatar`: JPG, PNG o WEBP de hasta 2 MB. Se recorta a 300 x 300 y se guarda en WEBP en `/media/avatars/`. Devuelve el perfil. |
| GET `/users/bets-history/export?format=xlsx\|pdf&startDate=&endDate=` | HU66 | Archivo `historial_palacetec_{inicio}_{fin}`. Solo apuestas propias. |
| GET `/stats/top-games` | HU67 | `{results: [{game_id, title, category, rounds, wagered}], total_rounds}`; 5 como máximo. |
| GET, POST `/assistant/chat` | HU68 | POST `{message}` (1 a 500 caracteres) → `{question, reply: {content, source, data}, reminder}`. `source`: faq, ai, guard u odds. 20 mensajes por minuto (429 `assistant_rate_limited`). |
| GET `/assistant/odds?bet=straight&number=17` | HU69 | También `bet=color\|parity\|dozen\|column&selection=…`. Devuelve `probability` ("1/37"), `probability_pct`, `payout` ("35:1"), `total_return` y `house_edge_pct` ("2.70"). |

Una ronda de ruleta cuenta una sola vez por jugador aunque tenga varias fichas (`roulette/game_activity.py`).

## Administración (`/api/v1/admin`, solo ADMIN)

| Método y ruta | Historia | Contrato |
| --- | --- | --- |
| PATCH `/games/{id}/featured` | HU64 | `{is_featured}`. Máximo 5 (400 `featured_limit`); desactivar un juego lo quita de destacados. Queda en la auditoría. |
| GET `/reports/games-ranking?days=7\|30` | HU71 | Por juego: `rounds`, `unique_players`, `wagered`, `share_pct`; orden por créditos. |
| GET `/reports/retention` | HU72 | DAU de 30 días, `mau`, `average_dau` y `dau_mau_ratio` (DAU promedio / MAU). Caché de 60 s. |
| GET `/reports/business.xlsx?startDate=&endDate=` | HU73 | Hojas KPIs, Flujo diario, Ranking de juegos y Retención (por defecto 30 días, máximo 366). Se registra en la auditoría. |

## Infraestructura

- **HU74:** `GET /healthcheck` (sin `/api/v1`) revisa base de datos, Redis y la capa de Channels. Responde 200 o 503 con
  `failed: ["redis"]`. La latencia y el error técnico solo se muestran a administradores con sesión.
- **HU75:** `python manage.py cleanup_notifications [--days 30] [--dry-run]` borra las notificaciones leídas antiguas y
  nunca las no leídas. Para ejecutarlo a diario a las 03:00 en Windows: `.\scripts\register_cleanup_task.ps1`
  (el resultado queda en `logs/cleanup_notifications.log`). En Linux: `0 3 * * * cd /ruta/backend && .venv/bin/python manage.py cleanup_notifications`.
