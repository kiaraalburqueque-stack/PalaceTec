# Sprint 4 de PalaceTec (HU61 a HU75)

**LABORATORIO N° 07 (CONTINUACIÓN): PLANIFICACIÓN DEL SPRINT 4 EN SCRUM**

**Curso:** Construcción y Pruebas de Software | **Ciclo / Semestre:** IV Ciclo - Semestre 2026-I | **Docente:** Jaime Farfán / Jaime Gómez

**Proyecto Integrador:** PalaceTec - Sistema de Gestión de Casino con Juego Responsable (créditos virtuales, sin dinero real)

**Integrantes del Equipo Scrum (2):** Alburqueque Arzapalo Kiara Paola (Líder / Scrum Master / Backend Dev) y Espíritu Canteño Sebastián Italo (Frontend Dev / Tester). En este sprint Sebastián desarrolla las 15 historias y Kiara facilita los eventos Scrum y revisa los Pull Requests.

## 1. Objetivo del Sprint 4

**Sprint Goal:** al terminar el Sprint 4, el jugador encuentra más rápido sus juegos (filtros, búsqueda, favoritos y destacados), cuenta con un asistente virtual que le explica reglas y probabilidades, recibe recomendaciones y exporta su historial; el administrador obtiene los reportes de negocio restantes y el sistema suma un monitor de salud y la limpieza automática de notificaciones.

Este documento define las 15 Historias de Usuario del Sprint 4 (HU61 a HU75), las últimas del Product Backlog de 75 historias. Las prioriza según el valor para el cliente, las desglosa en 5 a 8 tareas técnicas cada una y ajusta la capacidad a 20.0 horas. El sprint se construye sobre el mismo proyecto de los Sprints 2 y 3 (Django REST Framework, Django Channels, React y PostgreSQL); la reorganización del proyecto se hará después de cerrarlo.

### Criterio de priorización

- **Asistente virtual y recomendaciones (HU68 a HU70):** cubren la Épica 13, la parte de inteligencia que promete el título del proyecto y que aún no se ha entregado.
- **Catálogo y experiencia del jugador (HU61 a HU67):** filtros, búsqueda, favoritos, destacados, foto de avatar, exportación del historial y juegos más jugados. Reutilizan el catálogo (HU34, HU60), el historial (HU31, HU32) y las estadísticas (HU51 a HU53).
- **Reportes de administración (HU71 a HU73):** ranking de juegos, retención DAU/MAU e informe consolidado en Excel. Completan la Épica 14 sobre el panel del Sprint 3.
- **Infraestructura (HU74 y HU75):** monitor de salud y limpieza de notificaciones, de menor riesgo.

### Decisiones y ajustes

- **Un solo desarrollador:** Sebastián asume las 15 historias (backend, frontend y pruebas) con 20.0 h. Kiara, como Scrum Master, dirige el Planning, los Daily y la Review, y aprueba cada Pull Request antes del merge a main.
- **Misma tecnología:** Django REST Framework y Django Channels en el backend, React en los frontends del jugador y de administración, y PostgreSQL (SQLite solo para desarrollo local). La reorganización del repositorio y de la arquitectura se hace después del Sprint 4 para no mezclar historias nuevas con movimientos de carpetas.
- **Asistente virtual (HU68 y HU69):** usa un proveedor de IA configurable desde el .env (por ejemplo Gemini u OpenAI) con las reglas y tablas de pago del catálogo como contexto. Las probabilidades las calcula el backend con fórmulas exactas, no el modelo. Sin clave configurada, responde con preguntas frecuentes por palabras clave, así las pruebas no dependen de un servicio externo.
- **Recomendador (HU70) por reglas:** puntaje por categorías jugadas, favoritos y popularidad, sin modelo entrenado, igual que la HU29 del Sprint 2. No recomienda juegos a jugadores autoexcluidos ni con el límite diario alcanzado.
- **App móvil (Épica 10, HU10_01 a HU10_05) fuera del Sprint 4:** se desarrolla en Kotlin después de la reorganización, consumiendo la misma API. También quedan fuera HU04_05 (logs de juegos, ya cubiertos por la auditoría HU50), HU13_05, HU15_02, HU15_04 y HU15_05.
- **Backlog total:** se mantiene en 75 historias (Sprint 1: 20, Sprint 2: 20, Sprint 3: 20, Sprint 4: 15).

## 2. Historias de Usuario del Sprint 4 (HU61 a HU75)

Suman 49 Story Points estimados con Planning Poker: tres historias de 5 SP, once de 3 SP y una de 1 SP.

| Código | ID Lab 06 | Historia de Usuario | Criterio de aceptación | Prioridad | SP |
| --- | --- | --- | --- | --- | --- |
| HU61 | HU03_02 | Filtro del catálogo por categoría | Pestañas por categoría que filtran sin recargar la página; cada pestaña muestra su contador de juegos; la categoría elegida queda en la URL. | Media | 3 |
| HU62 | HU03_03 | Búsqueda de juegos por nombre | Búsqueda con espera de 350 ms al escribir; no distingue tildes ni mayúsculas; sin resultados muestra el mensaje No se encontraron juegos. | Media | 3 |
| HU63 | HU03_05 | Juegos favoritos | El corazón de la tarjeta marca o desmarca el juego; el favorito se guarda en la base de datos y sigue al volver a iniciar sesión; pestaña Favoritos. | Media | 3 |
| HU64 | HU04_04 | Juegos destacados en el banner principal | El administrador marca un juego como destacado (máximo 5); el inicio del jugador los muestra en un carrusel; un juego desactivado deja de aparecer. | Baja | 3 |
| HU65 | HU02_03 | Subida de foto de avatar | Acepta JPG, PNG o WEBP de hasta 2 MB; la imagen se recorta y guarda a 300 x 300 px; otro formato o tamaño se rechaza con mensaje claro. | Baja | 3 |
| HU66 | HU08_04 | Exportación del historial en PDF y Excel | Botones PDF y Excel que respetan el filtro de fechas; el archivo trae fecha, juego, apuesta, resultado y saldo; solo exporta las apuestas propias. | Media | 5 |
| HU67 | HU09_04 | Juegos más jugados del jugador | Ranking de los 5 juegos con más rondas y créditos apostados en la pantalla Estadísticas; estado vacío si aún no juega. | Media | 3 |
| HU68 | HU13_01 | Asistente virtual para dudas sobre los juegos | Chat flotante que responde sobre reglas, pagos, límites y la cuenta; no da estrategias para ganar y recuerda el juego responsable; funciona sin clave de IA con preguntas frecuentes. | Alta | 5 |
| HU69 | HU13_04 | Explicación de probabilidades de una apuesta | Ante una apuesta (por ejemplo pleno al 17) el asistente indica probabilidad, pago y ventaja de la casa calculados por el backend (pleno: 1/37 = 2.70 %, paga 35:1). | Alta | 3 |
| HU70 | HU13_02 | Recomendaciones personalizadas de juegos | Sección Para ti con hasta 4 juegos según categorías jugadas, favoritos y popularidad; vacía para autoexcluidos o con el límite diario alcanzado. | Media | 5 |
| HU71 | HU14_04 | Ranking de popularidad de juegos | Tabla del administrador con rondas, jugadores únicos y créditos apostados por juego en 7 o 30 días; solo para administradores. | Media | 3 |
| HU72 | HU14_03 | Retención de jugadores (DAU/MAU) | Gráfico de usuarios activos por día de los últimos 30 días y el índice DAU/MAU; un usuario activo es el que jugó al menos una ronda. | Baja | 3 |
| HU73 | HU14_05 | Informe consolidado de negocio en Excel | Descarga un .xlsx con hojas KPIs, Flujo diario, Ranking de juegos y Retención para el rango elegido; la acción queda en la auditoría (HU50). | Baja | 3 |
| HU74 | HU15_03 | Monitor de salud del sistema | GET /healthcheck revisa la base de datos, Redis y la capa de WebSockets; responde 200 si todo está bien y 503 indicando qué servicio falló. | Media | 1 |
| HU75 | HU11_05 | Limpieza automática de notificaciones antiguas | Un comando programado borra las notificaciones leídas de más de 30 días; nunca borra las no leídas; registra cuántas eliminó. | Baja | 3 |

## 3. Desglose de tareas técnicas del Sprint 4

Cada historia tiene de 5 a 8 tareas técnicas, 90 en total. Todas son de Sebastián Espíritu y las horas son proporcionales a los Story Points (1 SP = 0.40 h, 3 SP = 1.10 h, 5 SP = 2.50 h).

### HU61: Filtro del catálogo por categoría (1.10 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.61.1 [Backend] | GET /api/v1/games?category={slug} con conteo de juegos por categoría. | Sebastián Espíritu | 0.25 h |
| T4.61.2 [Database] | Índice compuesto (is_published, category) en el modelo Game. | Sebastián Espíritu | 0.10 h |
| T4.61.3 [Frontend] | Pestañas de categoría con ícono y contador en el catálogo. | Sebastián Espíritu | 0.25 h |
| T4.61.4 [State] | Sincronizar la categoría elegida con la URL mediante searchParams. | Sebastián Espíritu | 0.15 h |
| T4.61.5 [UI/UX] | Transición suave al cambiar de pestaña, sin recargar la página. | Sebastián Espíritu | 0.10 h |
| T4.61.6 [Testing] | Prueba del endpoint filtrado y prueba en React del filtro por pestaña. | Sebastián Espíritu | 0.25 h |

### HU62: Búsqueda de juegos por nombre (1.10 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.62.1 [Backend] | Parámetro search en GET /api/v1/games con búsqueda sin tildes (unaccent + icontains). | Sebastián Espíritu | 0.25 h |
| T4.62.2 [Frontend] | Componente SearchInput con botón para limpiar. | Sebastián Espíritu | 0.20 h |
| T4.62.3 [Performance] | Hook useDebounce de 350 ms antes de llamar a la API. | Sebastián Espíritu | 0.15 h |
| T4.62.4 [UI/UX] | Estado vacío No se encontraron juegos con sugerencias de categorías. | Sebastián Espíritu | 0.15 h |
| T4.62.5 [Testing] | Búsquedas con ñ, tildes y mayúsculas devuelven el mismo resultado. | Sebastián Espíritu | 0.20 h |
| T4.62.6 [Testing] | Prueba del hook useDebounce con temporizadores simulados. | Sebastián Espíritu | 0.15 h |

### HU63: Juegos favoritos (1.10 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.63.1 [Database] | Modelo FavoriteGame con restricción única (usuario, juego) y su migración. | Sebastián Espíritu | 0.15 h |
| T4.63.2 [Backend] | POST /api/v1/games/{id}/favorite que alterna el favorito y GET /api/v1/games/favorites. | Sebastián Espíritu | 0.25 h |
| T4.63.3 [Frontend] | Ícono de corazón en la tarjeta con estado activo e inactivo. | Sebastián Espíritu | 0.20 h |
| T4.63.4 [Frontend] | Pestaña Favoritos en el catálogo que consume la lista guardada. | Sebastián Espíritu | 0.20 h |
| T4.63.5 [Testing] | El favorito persiste tras cerrar e iniciar sesión. | Sebastián Espíritu | 0.15 h |
| T4.63.6 [Testing] | Un jugador no ve ni modifica los favoritos de otro. | Sebastián Espíritu | 0.15 h |

### HU64: Juegos destacados en el banner principal (1.10 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.64.1 [Database] | Campo is_featured en Game y su migración. | Sebastián Espíritu | 0.10 h |
| T4.64.2 [Backend] | PATCH /api/v1/admin/games/{id}/featured con tope de 5 destacados y registro en auditoría. | Sebastián Espíritu | 0.25 h |
| T4.64.3 [Backend] | GET /api/v1/games/featured que excluye juegos desactivados. | Sebastián Espíritu | 0.15 h |
| T4.64.4 [Frontend] | Interruptor Destacado en la tabla de juegos del panel de administración. | Sebastián Espíritu | 0.20 h |
| T4.64.5 [Frontend] | Carrusel de destacados en el inicio del jugador. | Sebastián Espíritu | 0.25 h |
| T4.64.6 [Testing] | Se rechaza un sexto destacado y un juego desactivado deja de mostrarse. | Sebastián Espíritu | 0.15 h |

### HU65: Subida de foto de avatar (1.10 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.65.1 [Backend] | POST /api/v1/users/avatar con multipart/form-data y filtro de tipo MIME. | Sebastián Espíritu | 0.25 h |
| T4.65.2 [Service] | Recorte y compresión a WEBP de 300 x 300 px con Pillow. | Sebastián Espíritu | 0.20 h |
| T4.65.3 [Database] | Guardar la ruta en avatar_url y borrar la foto anterior. | Sebastián Espíritu | 0.10 h |
| T4.65.4 [Frontend] | Selector de archivo con vista previa en Mi perfil, junto a los avatares predeterminados. | Sebastián Espíritu | 0.25 h |
| T4.65.5 [Security] | Rechazar archivos mayores a 2 MB o con extensión falsa. | Sebastián Espíritu | 0.10 h |
| T4.65.6 [Testing] | Pruebas de subida válida, formato inválido y tamaño excedido. | Sebastián Espíritu | 0.20 h |

### HU66: Exportación del historial en PDF y Excel (2.50 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.66.1 [Backend] | GET /api/v1/users/bets-history/export?format=xlsx con openpyxl. | Sebastián Espíritu | 0.45 h |
| T4.66.2 [Backend] | Exportación en PDF con ReportLab: encabezado, tabla y totales. | Sebastián Espíritu | 0.50 h |
| T4.66.3 [Backend] | Reutilizar el filtro de fechas de la HU32 y limitar a las apuestas propias. | Sebastián Espíritu | 0.25 h |
| T4.66.4 [Frontend] | Botones Exportar PDF y Exportar Excel en Mi historial con indicador de carga. | Sebastián Espíritu | 0.35 h |
| T4.66.5 [Frontend] | Descarga del archivo con nombre historial_palacetec_{fechas}. | Sebastián Espíritu | 0.20 h |
| T4.66.6 [Testing] | El archivo trae las columnas y filas del rango elegido. | Sebastián Espíritu | 0.30 h |
| T4.66.7 [Testing] | Un jugador no puede exportar el historial de otro usuario. | Sebastián Espíritu | 0.25 h |
| T4.66.8 [Testing] | Prueba en React de los botones y del estado de carga. | Sebastián Espíritu | 0.20 h |

### HU67: Juegos más jugados del jugador (1.10 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.67.1 [Backend] | GET /api/v1/stats/top-games con los 5 juegos de más rondas y créditos apostados. | Sebastián Espíritu | 0.25 h |
| T4.67.2 [Database] | Agregación sobre Bet y Play agrupada por juego. | Sebastián Espíritu | 0.20 h |
| T4.67.3 [Frontend] | Componente TopGames con barras horizontales en Estadísticas. | Sebastián Espíritu | 0.25 h |
| T4.67.4 [UI/UX] | Estado vacío Aún no tienes juegos jugados. | Sebastián Espíritu | 0.10 h |
| T4.67.5 [Testing] | El ranking ordena bien y solo cuenta las apuestas del jugador. | Sebastián Espíritu | 0.30 h |

### HU68: Asistente virtual para dudas sobre los juegos (2.50 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.68.1 [Backend] | App assistant con POST /api/v1/assistant/chat protegido con JWT. | Sebastián Espíritu | 0.40 h |
| T4.68.2 [Service] | Cliente del proveedor de IA configurable desde .env con las reglas y pagos como contexto. | Sebastián Espíritu | 0.45 h |
| T4.68.3 [Service] | Modo sin clave: respuestas de preguntas frecuentes por palabras clave. | Sebastián Espíritu | 0.30 h |
| T4.68.4 [Security] | Límite de 20 mensajes por minuto y filtro que evita consejos para ganar o apostar más. | Sebastián Espíritu | 0.25 h |
| T4.68.5 [Database] | Modelo AssistantMessage para el historial de la conversación. | Sebastián Espíritu | 0.15 h |
| T4.68.6 [Frontend] | Burbuja flotante de chat con historial y estado Escribiendo. | Sebastián Espíritu | 0.45 h |
| T4.68.7 [Testing] | Pruebas del endpoint con el proveedor simulado y del modo sin clave. | Sebastián Espíritu | 0.30 h |
| T4.68.8 [Testing] | Prueba en React del envío y la visualización de mensajes. | Sebastián Espíritu | 0.20 h |

### HU69: Explicación de probabilidades de una apuesta (1.10 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.69.1 [Backend] | Módulo odds.py que calcula probabilidad, pago y ventaja de la casa por tipo de apuesta. | Sebastián Espíritu | 0.25 h |
| T4.69.2 [Backend] | GET /api/v1/assistant/odds?bet=straight&number=17 reutilizando la tabla de pagos. | Sebastián Espíritu | 0.20 h |
| T4.69.3 [Service] | El asistente detecta preguntas de probabilidad y responde con los datos de odds.py. | Sebastián Espíritu | 0.20 h |
| T4.69.4 [Frontend] | Respuesta del chat con tarjeta de probabilidad, pago y ventaja de la casa. | Sebastián Espíritu | 0.20 h |
| T4.69.5 [Testing] | Pleno 1/37, color 18/37, docena 12/37 y ventaja de la casa de 2.70 %. | Sebastián Espíritu | 0.25 h |

### HU70: Recomendaciones personalizadas de juegos (2.50 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.70.1 [Backend] | Servicio de recomendación con puntaje por categorías jugadas, favoritos y popularidad. | Sebastián Espíritu | 0.50 h |
| T4.70.2 [Backend] | GET /api/v1/games/recommendations con hasta 4 juegos y el motivo de cada uno. | Sebastián Espíritu | 0.30 h |
| T4.70.3 [Security] | Lista vacía para autoexcluidos y jugadores con el límite diario alcanzado. | Sebastián Espíritu | 0.25 h |
| T4.70.4 [Database] | Consulta agregada de rondas por categoría de los últimos 30 días. | Sebastián Espíritu | 0.25 h |
| T4.70.5 [Frontend] | Sección Para ti en el inicio con tarjetas y el texto Porque jugaste... | Sebastián Espíritu | 0.40 h |
| T4.70.6 [UI/UX] | Jugador nuevo sin historial: se muestran los juegos más populares. | Sebastián Espíritu | 0.20 h |
| T4.70.7 [Testing] | El puntaje prioriza la categoría más jugada y excluye juegos desactivados. | Sebastián Espíritu | 0.35 h |
| T4.70.8 [Testing] | Un autoexcluido no recibe recomendaciones. | Sebastián Espíritu | 0.25 h |

### HU71: Ranking de popularidad de juegos (1.10 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.71.1 [Backend] | GET /api/v1/admin/reports/games-ranking?days=7 (o 30) solo para administradores. | Sebastián Espíritu | 0.25 h |
| T4.71.2 [Database] | Agregación de rondas, jugadores únicos y créditos apostados por juego. | Sebastián Espíritu | 0.20 h |
| T4.71.3 [Frontend] | Página Ranking de juegos en el panel con selector de 7 o 30 días. | Sebastián Espíritu | 0.30 h |
| T4.71.4 [UI/UX] | Orden por créditos apostados con barra proporcional en cada fila. | Sebastián Espíritu | 0.10 h |
| T4.71.5 [Testing] | Un jugador recibe 403 y el ranking suma bien los créditos. | Sebastián Espíritu | 0.25 h |

### HU72: Retención de jugadores (DAU/MAU) (1.10 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.72.1 [Backend] | GET /api/v1/admin/reports/retention con DAU de 30 días y el índice DAU/MAU. | Sebastián Espíritu | 0.25 h |
| T4.72.2 [Database] | Conteo de jugadores distintos con al menos una ronda por día. | Sebastián Espíritu | 0.20 h |
| T4.72.3 [Frontend] | Gráfico de líneas de DAU con el índice DAU/MAU destacado. | Sebastián Espíritu | 0.30 h |
| T4.72.4 [Performance] | Caché de 60 segundos, igual que los KPIs de la HU47. | Sebastián Espíritu | 0.10 h |
| T4.72.5 [Testing] | Datos sembrados con jugadores activos conocidos dan el DAU esperado. | Sebastián Espíritu | 0.25 h |

### HU73: Informe consolidado de negocio en Excel (1.10 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.73.1 [Backend] | GET /api/v1/admin/reports/business.xlsx con rango de fechas. | Sebastián Espíritu | 0.20 h |
| T4.73.2 [Service] | Libro openpyxl con hojas KPIs, Flujo diario, Ranking de juegos y Retención. | Sebastián Espíritu | 0.30 h |
| T4.73.3 [Security] | Solo administradores y registro de la descarga en la auditoría (HU50). | Sebastián Espíritu | 0.10 h |
| T4.73.4 [Frontend] | Botón Descargar informe en el Dashboard con selector de fechas. | Sebastián Espíritu | 0.20 h |
| T4.73.5 [Testing] | El archivo tiene las 4 hojas y sus totales coinciden con los KPIs. | Sebastián Espíritu | 0.30 h |

### HU74: Monitor de salud del sistema (0.40 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.74.1 [Backend] | GET /healthcheck que consulta la base de datos con SELECT 1. | Sebastián Espíritu | 0.10 h |
| T4.74.2 [Service] | Comprobar Redis y la capa de canales de Django Channels. | Sebastián Espíritu | 0.10 h |
| T4.74.3 [Backend] | Respuesta 200 o 503 con el estado y la latencia de cada servicio. | Sebastián Espíritu | 0.05 h |
| T4.74.4 [Security] | El detalle técnico solo se muestra a administradores. | Sebastián Espíritu | 0.05 h |
| T4.74.5 [Testing] | Con Redis caído el endpoint responde 503 y lo indica. | Sebastián Espíritu | 0.10 h |

### HU75: Limpieza automática de notificaciones antiguas (1.10 h)

| Código / Categoría | Descripción técnica de la tarea | Responsable | Horas |
| --- | --- | --- | --- |
| T4.75.1 [Backend] | Comando cleanup_notifications que borra las leídas de más de 30 días. | Sebastián Espíritu | 0.25 h |
| T4.75.2 [Database] | Índice (is_read, created_at) en Notification para el borrado. | Sebastián Espíritu | 0.10 h |
| T4.75.3 [Service] | Programación diaria del comando (cron o Programador de tareas de Windows). | Sebastián Espíritu | 0.20 h |
| T4.75.4 [Backend] | Registro en el log de cuántas notificaciones se eliminaron. | Sebastián Espíritu | 0.10 h |
| T4.75.5 [Testing] | Las no leídas y las recientes no se borran. | Sebastián Espíritu | 0.25 h |
| T4.75.6 [Testing] | Opción --dry-run que solo cuenta sin borrar. | Sebastián Espíritu | 0.20 h |

## 4. Resumen de capacidad y distribución de trabajo

La capacidad del sprint es exactamente 20.0 h de desarrollo, todas de Sebastián; Kiara aporta el rol de Scrum Master y la revisión de código.

| Integrante | Rol en el Sprint 4 | Historias y responsabilidades | Horas de desarrollo |
| --- | --- | --- | --- |
| Sebastián Italo Espíritu Canteño | Desarrollador full stack / Tester | HU61 a HU75 (backend, frontend del jugador y de administración, y pruebas) | 20.0 h |
| Kiara Paola Alburqueque Arzapalo | Líder / Scrum Master | Sprint Planning, Daily, Review y Retrospectiva; revisión y aprobación de cada Pull Request | 0.0 h |
| **TOTAL EQUIPO SCRUM** | 15 historias, 90 tareas técnicas, 49 SP | Desarrollo y pruebas integrados | **20.0 h** |

Horas por tamaño: tres historias de 5 SP (7.5 h), once de 3 SP (12.1 h) y una de 1 SP (0.4 h).

## 5. Trazabilidad: de la historia al código

Convención: Sebastián trabaja en la rama Sebastian, actualizada desde main antes de empezar; cada commit lleva el ID de la historia (por ejemplo feat(HU68): ...) y cada prueba incluye el ID en su nombre. Las rutas son las planificadas y se corrigen al cerrar cada historia. El backend está en usuario/backend/, el frontend del jugador en usuario/frontend-web/ y el panel en administracion/frontend-web/.

| HU | Backend (usuario/backend/) | Frontend | Pruebas | Estado |
| --- | --- | --- | --- | --- |
| HU61 | roulette/catalog.py, views.py (games?category, games/categories), migraciones 0011 y 0012 (blackjack y deportes-virtuales) | usuario/frontend-web/src/components/CategoryTabs.jsx, GameCatalog.jsx | roulette/test_hu61.py, GameCatalog.test.jsx | Hecha |
| HU62 | roulette/catalog.py (normalize, apply_search), views.py (games?search) | usuario/frontend-web/src/components/SearchInput.jsx, hooks/useDebounce.js, GameCatalog.jsx | roulette/test_hu62.py, GameCatalog.test.jsx, useDebounce.test.js | Hecha (sin tildes en Python en lugar de unaccent, aprobado) |
| HU63 | roulette/models.py (FavoriteGame, migración 0013), views.py (games/{id}/favorite, games/favorites) | usuario/frontend-web/src/components/FavoriteButton.jsx, GameCatalog.jsx (pestaña Favoritos) | roulette/test_hu63.py, FavoriteButton.test.jsx, GameCatalog.test.jsx | Hecha |
| HU64 | admin_api/views.py (AdminGameFeaturedView), roulette/views.py (games/featured), migración 0014 | administracion/frontend-web/src/pages/Games.jsx, components/FeaturedToggle.jsx, usuario/frontend-web/src/components/FeaturedCarousel.jsx | admin_api/tests/test_hu64_featured.py, FeaturedToggle.test.jsx, FeaturedCarousel.test.jsx | Hecha |
| HU65 | accounts/views.py (AvatarUploadView), accounts/avatar.py, admin_api/serializers.py (validate_image_upload) | usuario/frontend-web/src/pages/ProfilePage.jsx, components/AvatarUpload.jsx, utils/avatar.js | accounts/test_hu65.py, AvatarUpload.test.jsx | Hecha |
| HU66 | roulette/history.py (BetHistoryExportView), history_export.py | usuario/frontend-web/src/pages/HistoryPage.jsx, components/ExportButtons.jsx | roulette/test_hu66.py, ExportButtons.test.jsx | Hecha |
| HU67 | stats/views.py (TopGamesView), roulette/game_activity.py | usuario/frontend-web/src/components/TopGames.jsx, pages/StatsPage.jsx | stats/tests/test_hu67_topgames.py, TopGames.test.jsx | Hecha |
| HU68 | assistant/views.py, providers.py, faq.py, guard.py, services.py, models.py (AssistantMessage) | usuario/frontend-web/src/components/AssistantChat.jsx | assistant/tests/test_hu68_chat.py, AssistantChat.test.jsx | Hecha (modo FAQ por defecto; Gemini u OpenAI con ASSISTANT_API_KEY) |
| HU69 | assistant/odds.py, views.py (assistant/odds), services.py | usuario/frontend-web/src/components/OddsCard.jsx, AssistantChat.jsx | assistant/tests/test_hu69_odds.py, OddsCard.test.jsx, AssistantChat.test.jsx | Hecha |
| HU70 | roulette/recommendations.py, game_activity.py, views.py (games/recommendations) | usuario/frontend-web/src/components/ForYou.jsx, pages/HomePage.jsx | roulette/test_hu70.py, ForYou.test.jsx | Hecha (también vacía durante una pausa activa) |
| HU71 | admin_api/reports.py (games_ranking, GamesRankingView) | administracion/frontend-web/src/pages/GamesRanking.jsx | admin_api/tests/test_hu71_ranking.py, GamesRanking.test.jsx | Hecha |
| HU72 | admin_api/reports.py (retention, RetentionView) | administracion/frontend-web/src/components/RetentionChart.jsx, pages/Dashboard.jsx | admin_api/tests/test_hu72_retention.py, RetentionChart.test.jsx | Hecha |
| HU73 | admin_api/business_report.py, reports.py (financial_flow_range) | administracion/frontend-web/src/pages/Dashboard.jsx, components/BusinessReport.jsx | admin_api/tests/test_hu73_report.py, BusinessReport.test.jsx | Hecha |
| HU74 | config/health.py, config/urls.py | (sin pantalla propia) | config/test_hu74_health.py | Hecha |
| HU75 | notifications/management/commands/cleanup_notifications.py, models.py (índice, migración 0003), scripts/register_cleanup_task.ps1 | (sin pantalla propia) | notifications/tests/test_hu75_cleanup.py | Hecha (script de programación verificado en sintaxis, no registrado en este equipo) |

## 6. Riesgos y Definición de Terminado

### Riesgos identificados

- **Un solo desarrollador:** si Sebastián se atrasa no hay quién reparta la carga. Si la capacidad no alcanza, se recorta en este orden: HU72, HU73 y HU75; el asistente (HU68 y HU69) se protege porque es la pieza de IA que se muestra en la Review.
- **Servicio de IA externo (HU68):** la clave puede no estar disponible o el servicio puede fallar. El modo sin clave con preguntas frecuentes asegura la demo y las pruebas usan un proveedor simulado.
- **Nuevas dependencias:** openpyxl, ReportLab y Pillow se agregan a requirements.txt; la búsqueda sin tildes necesita la extensión unaccent de PostgreSQL (en SQLite se normaliza el texto en Python).
- **Dependencia de sprints anteriores:** las historias usan el catálogo (HU34, HU60), el historial (HU31, HU32), las estadísticas (HU51 a HU53), los límites (HU26, HU27), el panel de administración (HU41 a HU50) y las notificaciones (HU57). Todo eso ya está en main.

### Definición de terminado

- La historia está en la rama Sebastian, con commits que llevan su ID.
- Sus pruebas pasan con python manage.py test y, si corresponde, con las pruebas del frontend.
- Kiara revisó y aprobó el Pull Request antes del merge a main.
- La columna Estado de la matriz se actualiza como Hecha, Parcial o Movida.

## 7. Seguimiento y conclusiones

- **Granularidad:** cada una de las 15 historias se desglosó en 5 a 8 tareas técnicas, para un total de 90 tareas.
- **Capacidad:** la suma de horas es exactamente 20.0 h, todas de Sebastián, con Kiara como Scrum Master y revisora.
- **Prioridad al cliente:** el Sprint 4 entrega la parte de inteligencia del proyecto (asistente, probabilidades y recomendaciones), mejora la exploración del catálogo y cierra los reportes de negocio.
- **Backlog total:** con el Sprint 4 se completan las 75 historias (Sprint 1: 20, Sprint 2: 20, Sprint 3: 20, Sprint 4: 15). La app móvil en Kotlin y las historias restantes de infraestructura pasan a la siguiente etapa, después de reorganizar el proyecto.
