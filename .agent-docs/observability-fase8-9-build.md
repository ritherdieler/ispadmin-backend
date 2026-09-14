# Observabilidad — Construcción Fases 8 y 9

Registro de la construcción del **Dashboard de diagnóstico (Fase 8)** y el **Motor de alertas (Fase 9)**.
Diseño previo: [observability-fase8-9-implementation.md](./observability-fase8-9-implementation.md).

Estado: **implementado y compilando** (obs-web `npm run build` ✓, backend `mvnw compile` ✓).

> **Actualización de ubicación de la UI.** El dashboard vive en el proyecto dedicado
> **`ispadmin-observability-web`** (Vite + React + ECharts + React Query), que ya tenía las vistas
> de la Fase 8. La UI de la Fase 9 (alertas) se **portó a ese proyecto** (`src/features/alerts/`).
> El **backoffice** conserva únicamente el **SDK de captura** (`src/lib/observability/*`,
> `src/observability.ts`); el módulo de dashboard que se había construido en el backoffice
> (`src/features/observability/`) fue **retirado** para evitar duplicación. La sección "Fase 8 —
> Dashboard (frontend, backoffice)" de abajo queda como registro histórico del enfoque inicial.

---

## Fase 8 — Dashboard (frontend, backoffice)

Módulo aislado en `ispadmin-backoffice/src/features/observability/` (portable para cuando el hub sea servicio propio).

### Configuración / infraestructura
- `obsConfig.ts`: lee `VITE_OBS_BASE_URL`, `VITE_OBS_WS_URL`, `VITE_OBS_DASHBOARD_KEY` (fallback a `VITE_OBS_API_KEY`).
- `obsApi.ts`: cliente axios propio con header `X-Obs-Api-Key` (key `dashboard`) y `X-Correlation-Id`.
- `obsSocket.ts`: cliente STOMP+SockJS propio suscrito a `/topic/observability/events` (independiente del socket de tickets/tráfico).
- `.env.development` / `.env.production`: nueva variable `VITE_OBS_DASHBOARD_KEY`.

### Datos
- `types.ts`: tipos espejo de los DTO del backend (issues, eventos, trazas, spans, métricas, DB, sesiones, replays, alertas).
- `format.ts`: helpers de fecha/duración/porcentaje/bytes + tonos de severidad/estado/HTTP/plataforma.
- `services/`: `issueService`, `statsService`, `traceService`, `metricService`, `databaseService`, `sessionService`, `alertService`.
- `hooks/useObsQuery.ts` (fetch + polling + refetch) y `hooks/useLiveEvents.ts` (feed STOMP).

### UI compartida
- `components/ui.tsx`: `ObsCard`, `StatCard`, `Badge`, `SectionTitle`, `Spinner`, `EmptyState`, `ErrorState`.
- `components/badges.tsx`: `SeverityBadge`, `StatusBadge`, `PlatformBadge`, `HttpStatusBadge`.
- `components/RangeSelector.tsx`, `components/ObsLayout.tsx` (header + tabs + guard de configuración).

### Vistas (`pages/`)
- **Overview**: 6 KPIs, área de eventos 24h (recharts), feed en vivo, top issues y trazas lentas.
- **Issues**: filtros (texto/estado/severidad/plataforma) + tabla + `IssueDetailDrawer` (metadatos, cambio de estado, crear/ver ticket, último evento, stacktrace, breadcrumbs, deep-links a sesión/trazas).
- **Trazas**: filtros (rango/ruta/duración/errores/sesión) + tabla + `TraceDetailDrawer` con **waterfall** de spans (`TraceWaterfall`).
- **APM**: tabla de endpoints ordenada por p95 + gráfico de latencia p50/p95 por ruta.
- **Base de datos**: top queries + candidatos N+1 con deep-link a la traza.
- **Sesiones**: listado + `SessionDetailDrawer` con **Session Replay** (`ReplayPlayer` usando `rrweb-player`), timeline de eventos y trazas.
- **Alertas**: ver Fase 9 UI.

### Integración
- `App.tsx`: rutas anidadas `('/observability/*')` con `ObsLayout` (Outlet) y páginas lazy.
- `Sidebar.tsx`: categoría "Plataforma" → "Observabilidad" (solo ADMIN).
- `useAuth.ts` / `ProtectedRoute.tsx`: nuevo recurso `observability` (admin-only).
- Dependencia nueva: **`rrweb-player`** (chunk lazy ~129 kB, solo se carga al abrir un replay).

---

## Fase 9 — Motor de alertas (backend + UI)

### Backend (`ispadmin-backend`)
Entidades (`entity/`): `ObsAlertRule` (+ enums `ObsAlertType`, `ObsAlertComparator`), `ObsAlertChannel` (+ `ObsAlertChannelType`), `ObsAlertEvent`.

Repos (`repository/`): `ObsAlertRuleRepository`, `ObsAlertChannelRepository`, `ObsAlertEventRepository` (+ dedup por `dedupKey`).
Consultas añadidas: `ObsEventRepository.countInWindow(...)`, `ObsSpanRepository.aggregateRootSpansBetween(...)`.

Puerto + adaptadores (patrón igual al issue tracker):
- `port/AlertChannelPort` (+ `AlertNotification`, `AlertChannelSendResult`) y `port/AlertChannelRegistry`.
- `adapter/alert/`: `SlackAlertChannelAdapter`, `TelegramAlertChannelAdapter`, `WebhookAlertChannelAdapter` (todos vía `RestTemplate`).
- **Email queda pendiente** (no hay `spring-boot-starter-mail` en el classpath); documentado como futuro.

Servicios (`service/`):
- `ObsAlertService`: CRUD de reglas/canales, prueba de canal, histórico paginado.
- `ObsAlertDispatcher`: dedup por ventana, registro de `ObsAlertEvent`, envío a canales y `lastTriggeredAt`.
- `ObsIssueAlertHandler` (`@Async obsTaskExecutor`): reglas `NEW_ISSUE` / `ISSUE_REGRESSION`, enganchado en `ObsIngestionService.persistEvent` (detecta issue nuevo/reabierto).
- `ObsAlertEvaluator` (`@Scheduled`): reglas de métrica `EVENT_THRESHOLD`, `ERROR_SPIKE` (baseline×factor), `ENDPOINT_ERROR_RATE`, `ENDPOINT_LATENCY_P95`, `TRACE_ERROR_RATE`.

Controlador: `ObservabilityAlertController` (`/observability/alerts/{rules,channels,events}`, `channels/{id}/test`) — protegido por `X-Obs-Api-Key`.

Retención: `ObsRetentionScheduler` ahora purga también `obs_alert_event` (usa `retention.eventDays`).

Properties (`ObservabilityProperties.AlertsProperties`): `observability.alerts.{enabled, evaluation-interval-ms, dedup-window-minutes, dashboard-base-url}` en `application-dev/prod.properties`.

### UI de alertas (`ispadmin-observability-web/src/features/alerts/`)
Portada al proyecto dedicado, siguiendo sus convenciones (React Query + componentes shadcn):
- `AlertsPage.tsx`: tres pestañas (Reglas / Canales / Historial) con tablas y paginación.
- `AlertRuleDialog.tsx`: formulario con campos condicionales por tipo de alerta y selección de canales.
- `AlertChannelDialog.tsx`: Slack/Telegram/Webhook (token de Telegram por `configJson`) + botón "Probar".
- `queries.ts` (hooks) + métodos en `lib/observabilityApi.ts` + tipos en `types/observability.ts`.
- Ruta `/alerts` en `App.tsx` y entrada "Alertas" en `AppShell`.

---

## Variables de entorno nuevas

| Proyecto | Variable | Descripción |
|---|---|---|
| observability-web | `VITE_OBS_API_KEY` | API key de dashboard (`X-Obs-Api-Key`) |
| observability-web | `VITE_OBS_BASE_URL` / `VITE_OBS_WS_URL` | Base HTTP y WebSocket del hub |
| backoffice (SDK) | `VITE_OBS_BASE_URL` / `VITE_OBS_WS_URL` / `VITE_OBS_API_KEY` | Ingesta del SDK de captura (no dashboard) |
| backend (prod) | `OBS_ALERTS_ENABLED` | Habilita el motor de alertas |
| backend (prod) | `OBS_ALERTS_INTERVAL_MS` | Intervalo del evaluador |
| backend (prod) | `OBS_ALERTS_DEDUP_MIN` | Ventana de deduplicación (min) |
| backend (prod) | `OBS_DASHBOARD_BASE_URL` | Base para deep-link de notificaciones |

## Tablas nuevas
`obs_alert_rule`, `obs_alert_channel`, `obs_alert_event` (creadas por Hibernate `ddl-auto`, mismo mecanismo que el resto del hub).

## Pendientes / futuro
- Canal de **Email** (requiere `spring-boot-starter-mail` + SMTP).
- Symbolication de stacktraces y unificación de logs↔spans (Fases posteriores del roadmap).
