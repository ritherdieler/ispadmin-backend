# Fases 8 y 9 — Diseño de implementación

Detalle técnico para construir:
- **Fase 8 — Dashboard de diagnóstico** como **módulo dentro del backoffice** (`ispadmin-backoffice`, React 19 + Vite).
- **Fase 9 — Motor de alertas + notificaciones** (backend `observability` + UI de configuración en el mismo módulo).

Referencias: `observability-roadmap.md`, `observability-hub.md` (contrato y endpoints ya existentes),
`observability-tracing-spans.md`, `observabilidad-database-insights-session-view.md`.

> Toda la API de consulta y de ingesta **ya existe** en el backend (secciones 3, 4, 6, 7 de
> `observability-hub.md`). La Fase 8 es casi 100% frontend. La Fase 9 sí agrega backend nuevo.

---

# FASE 8 — Dashboard de diagnóstico (backoffice)

## 8.1 Decisiones de arquitectura

- Vive como **feature aislada** en `src/features/observability/` para poder extraerse el día que el hub
  sea servicio propio (coherente con la portabilidad del hub).
- **Cliente HTTP propio** (NO reusar `src/lib/api.ts`): necesita `baseURL = VITE_OBS_BASE_URL` y header
  `X-Obs-Api-Key` con la **key de dashboard**, distinta de la API del negocio.
- **STOMP**: reusar el patrón de `websocketService`, pero con instancia propia apuntando a
  `VITE_OBS_WS_URL` y topic `/topic/observability/events` (no mezclar con el socket de tickets/tráfico).
- Acceso restringido: `ProtectedRoute` con un `requiredResource` nuevo (p.ej. `observability`), visible
  solo para admin/rol de plataforma.

## 8.2 Variables de entorno (frontend)

Ya existen `VITE_OBS_BASE_URL` y `VITE_OBS_WS_URL` (usados por el SDK). Agregar:

| Variable | Dev | Prod | Uso |
|---|---|---|---|
| `VITE_OBS_DASHBOARD_KEY` | `dev-obs-dashboard-key` | env en build | `X-Obs-Api-Key` para la API de consulta |

En el backend ya está contemplada la key `observability.api-keys.dashboard` (sección 8 de `observability-hub.md`).

## 8.3 Estructura de archivos (nueva)

```
src/features/observability/
├── obsApi.ts                 # axios propio (baseURL OBS + X-Obs-Api-Key dashboard)
├── obsSocket.ts              # cliente STOMP a VITE_OBS_WS_URL, topic /topic/observability/events
├── types.ts                  # DTOs espejo del backend (IssueSummary, EventDto, Trace*, Metric*, ...)
├── services/
│   ├── issueService.ts       # /observability/issues, /issues/{id}, occurrences, status, ticket
│   ├── statsService.ts       # /observability/stats/overview, events-timeseries
│   ├── traceService.ts       # /observability/traces, /traces/{traceId}
│   ├── metricService.ts      # /observability/metrics/endpoints, timeseries
│   ├── databaseService.ts    # /observability/database/queries, nplusone
│   ├── sessionService.ts     # /observability/sessions/... (ver 8.7)
│   └── replayService.ts      # /observability/replays/{id} (descarga blob)
├── hooks/
│   ├── useOverview.ts        # polling + merge con feed STOMP en vivo
│   ├── useIssues.ts          # lista paginada + filtros
│   ├── useIssueDetail.ts     # detalle + ocurrencias + latest-event
│   ├── useTraces.ts / useTraceDetail.ts
│   ├── useEndpointMetrics.ts
│   ├── useDatabaseInsights.ts
│   └── useLiveEvents.ts      # suscripción STOMP -> stream de OBS_EVENT
├── components/
│   ├── ObsLayout.tsx         # sub-nav de tabs (Overview/Issues/Traces/APM/DB/Sesiones)
│   ├── overview/OverviewCards.tsx, EventsTimeSeriesChart.tsx, LiveFeed.tsx, TopIssuesList.tsx
│   ├── issues/IssueTable.tsx, IssueFilters.tsx, IssueDetailDrawer.tsx, OccurrenceList.tsx,
│   │          StacktraceView.tsx, BreadcrumbsView.tsx, StatusBadge.tsx, TicketButton.tsx
│   ├── traces/TraceTable.tsx, TraceWaterfall.tsx, SpanRow.tsx
│   ├── apm/EndpointTable.tsx, LatencyChart.tsx
│   ├── database/TopQueriesTable.tsx, NPlusOneTable.tsx
│   └── replay/ReplayPlayer.tsx   # rrweb-player (web) / frames (android)
└── pages/
    ├── ObservabilityOverview.tsx
    ├── ObservabilityIssues.tsx
    ├── ObservabilityTraces.tsx
    ├── ObservabilityApm.tsx
    ├── ObservabilityDatabase.tsx
    └── ObservabilitySessions.tsx
```

## 8.4 Cliente HTTP (obsApi.ts) — diseño

- `axios.create({ baseURL: import.meta.env.VITE_OBS_BASE_URL, timeout, headers: { 'X-Obs-Api-Key': VITE_OBS_DASHBOARD_KEY } })`.
- Interceptor de request que adjunta `X-Correlation-Id` (reusar el generador del SDK en `src/lib/observability/ids.ts`).
- Sin los `console.log` ruidosos de `src/lib/api.ts`.
- Degradación: si falta `VITE_OBS_BASE_URL` o `VITE_OBS_DASHBOARD_KEY`, el módulo muestra un estado
  "no configurado" en vez de romper.

## 8.5 Routing y navegación

En `src/App.tsx`, agregar rutas lazy protegidas:

```
/observability            -> ObservabilityOverview
/observability/issues     -> ObservabilityIssues (drawer de detalle por ?issueId=)
/observability/traces     -> ObservabilityTraces (?traceId= para waterfall)
/observability/apm        -> ObservabilityApm
/observability/database   -> ObservabilityDatabase
/observability/sessions   -> ObservabilitySessions (?sessionId=)
/observability/alerts     -> ObservabilityAlerts (Fase 9, ver abajo)
```

En `src/components/layout/Sidebar.tsx` agregar una categoría **"Observabilidad"** (visible con
`hasAccess('observability')`) con ítems Overview, Issues, Trazas, APM, Base de datos, Alertas.
Extender el tipo de `hasAccess` y el mapeo de recursos en `useAuth`.

## 8.6 Endpoints consumidos (ya existentes) → vistas

| Vista | Endpoints |
|---|---|
| Overview | `GET /stats/overview`, `GET /stats/events-timeseries`, STOMP `/topic/observability/events` |
| Issues | `GET /issues` (filtros), `GET /issues/{id}`, `/issues/{id}/occurrences`, `/issues/{id}/latest-event`, `PATCH /issues/{id}/status`, `POST/DELETE /issues/{id}/ticket`, `GET /tracker/info` |
| Trazas | `GET /traces` (route/minDurationMs/status/platform/sessionId), `GET /traces/{traceId}` |
| APM | `GET /metrics/endpoints`, `GET /metrics/timeseries?route=` |
| Base de datos | `GET /database/queries`, `GET /database/nplusone` |
| Sesiones/Replay | `GET /observability/sessions`, `GET /observability/sessions/{sessionId}`, `GET /observability/replays/{id}` |

## 8.7 Backend: nada nuevo requerido en Fase 8

La vista de sesión unificada **ya existe**:
- `GET /observability/sessions` (listado paginado, `SessionSummaryDto`).
- `GET /observability/sessions/{sessionId}` (timeline, `SessionDetailDto`) — ver
  `ObservabilitySessionController` y `observabilidad-database-insights-session-view.md`.

Por lo tanto la **Fase 8 es 100% frontend**: solo consume endpoints existentes. No se toca el backend
(salvo, si acaso, ajustar CORS en `ObservabilityWebConfig` para el origen del backoffice).

## 8.8 Componentes clave — notas de implementación

- **TraceWaterfall**: recibir `TraceDetailDto` (lista de spans con `startEpochMs`, `durationMs`,
  `parentSpanId`); calcular offset relativo al root y renderizar barras anidadas por profundidad.
  Colorear `status=ERROR` en rojo; mostrar `db_statement` en tooltip.
- **LiveFeed**: buffer en memoria (máx ~100) alimentado por `useLiveEvents`; badges por severidad;
  clic navega al issue.
- **ReplayPlayer**: dependencia nueva `rrweb-player` (ya está `rrweb`); para `format=frames` (Android)
  renderizar secuencia de imágenes. Descargar blob gzip de `/replays/{id}` (el navegador descomprime).
- **StacktraceView**: colapsable, resalta frames de app vs librería. (La simbolicación real es Fase 10).
- Reusar `Pagination`, `card`, `LoadingSpinner`, `skeleton`, `ConfirmDialog` existentes.

## 8.9 Dependencias nuevas (frontend)

- `rrweb-player` (reproductor de replay). `recharts`/`chart.js` ya están para gráficos.

## 8.10 Tareas Fase 8

1. `.env.development` / `.env.production`: agregar `VITE_OBS_DASHBOARD_KEY`.
2. `obsApi.ts` + `obsSocket.ts` + `types.ts`.
3. Services (7 archivos) mapeando los endpoints existentes.
4. Hooks con estados loading/error y polling configurable.
5. Páginas + componentes (Overview → Issues → Traces → APM → DB → Sessions, en ese orden).
6. Routing en `App.tsx` + entrada en `Sidebar.tsx` + recurso `observability` en `useAuth`/ProtectedRoute.
7. (Opcional) endpoint de sesión unificada en backend (8.7).
8. `npm run build` verde.

## 8.11 Criterios de aceptación Fase 8

- Overview muestra KPIs reales y feed en vivo al ocurrir un error.
- Desde un issue se llega a su última ocurrencia, su trace (waterfall), sus queries y su replay.
- Filtros de issues y trazas funcionan y pagina correctamente.
- Todo bajo `X-Obs-Api-Key` de dashboard; sin errores de CORS.

---

# FASE 9 — Motor de alertas + notificaciones (backend + UI)

## 9.1 Objetivo

Evaluar reglas contra los datos ya ingeridos y **notificar** por Slack/Telegram/email/webhook cuando se
cruzan umbrales, con baseline para picos y cooldown para evitar spam. Hoy `ObsLivePublisher` solo empuja
a STOMP; esto añade la capa proactiva.

## 9.2 Modelo de datos (tablas `obs_*`, ddl-auto=update, sin FKs al dominio)

### `obs_alert_rule`
| Columna | Tipo | Notas |
|---|---|---|
| `id` | bigint PK | |
| `name` | varchar(200) | |
| `enabled` | boolean | default true |
| `type` | varchar(40) | ver enum `ObsAlertType` |
| `platform` | varchar(60) null | filtro opcional |
| `environment` | varchar(60) null | filtro opcional |
| `route` | varchar(300) null | para reglas de endpoint |
| `severity` | varchar(30) null | filtro opcional |
| `threshold` | double | valor a comparar |
| `comparator` | varchar(5) | `GT`/`GTE`/`LT`/`LTE` |
| `window_minutes` | int | ventana de evaluación |
| `baseline_multiplier` | double null | para `ERROR_SPIKE` (ej. 3x baseline) |
| `cooldown_minutes` | int | silencio tras disparo |
| `channel_ids_json` | text | ids de canales destino |
| `last_triggered_at` | datetime null | para cooldown |
| `created_at` | datetime | índice |

Índices: `idx_obs_alert_rule_enabled(enabled)`, `idx_obs_alert_rule_type(type)`.

### `obs_alert_channel`
| Columna | Tipo | Notas |
|---|---|---|
| `id` | bigint PK | |
| `type` | varchar(20) | `SLACK`/`TELEGRAM`/`EMAIL`/`WEBHOOK` |
| `name` | varchar(200) | |
| `config_json` | text | secretos/targets (webhook url, chat id, emails) |
| `enabled` | boolean | |
| `created_at` | datetime | |

> En prod, los secretos sensibles (tokens de bot, webhooks) deben venir por env y `config_json`
> guardar solo referencias/targets no sensibles cuando sea posible.

### `obs_alert_event`
| Columna | Tipo | Notas |
|---|---|---|
| `id` | bigint PK | |
| `rule_id` | bigint | plano, sin FK |
| `triggered_at` | datetime | índice |
| `value` | double | valor observado |
| `threshold` | double | umbral en el momento |
| `context_json` | text | issueId/route/platform/muestra |
| `notified` | boolean | resultado del envío |
| `resolved_at` | datetime null | si la condición se normaliza |

Retención: nueva `observability.retention.alert-event-days` (default 30) en `ObsRetentionScheduler`.

### Enum `ObsAlertType`
`NEW_ISSUE`, `ISSUE_REGRESSION`, `ERROR_SPIKE`, `EVENT_THRESHOLD`, `ENDPOINT_ERROR_RATE`,
`ENDPOINT_LATENCY_P95`, `TRACE_ERROR_RATE`.

## 9.3 Estructura de paquetes (backend)

```
com.dscorp.wispadmin.observability
├── entity/     ObsAlertRule, ObsAlertChannel, ObsAlertEvent (+ enums ObsAlertType, ObsAlertChannelType, ObsComparator)
├── repository/ ObsAlertRuleRepository, ObsAlertChannelRepository, ObsAlertEventRepository
├── dto/        AlertDtos (rule/channel request+response, history)
├── port/       AlertChannelPort (send(notification)) + AlertNotification (modelo neutral)
├── adapter/alert/  SlackAlertChannel, TelegramAlertChannel, EmailAlertChannel, WebhookAlertChannel
├── service/    ObsAlertEvaluator, ObsAlertService (CRUD), ObsAlertNotifier, ObsAlertChannelRegistry
├── controller/ ObservabilityAlertController
└── scheduled/  ObsAlertScheduler (@Scheduled cada observability.alerts.eval-interval-ms)
```

Patrón espejo del issue tracker (puerto + adaptadores + registry), coherente con
`observability-tracker-abstraction.md`.

## 9.4 Lógica de evaluación (`ObsAlertEvaluator`)

Por cada regla `enabled`, si fuera de cooldown:

- **NEW_ISSUE / ISSUE_REGRESSION**: evaluación **event-driven** desde `ObsIngestionService.persistEvent`
  (al crear issue nuevo o reabrir `RESOLVED`), no por scheduler. Requiere para regresión guardar
  `resolved_in_release` en `obs_issue` (ligado a Fase 15) o, mínimamente, disparar en toda reapertura.
- **EVENT_THRESHOLD**: `count(obs_event)` en `window_minutes` (con filtros platform/severity/environment)
  vs `threshold`.
- **ERROR_SPIKE**: comparar conteo de la ventana actual vs media de N ventanas previas ×
  `baseline_multiplier`.
- **ENDPOINT_ERROR_RATE / ENDPOINT_LATENCY_P95**: leer `obs_endpoint_metric` agregado en la ventana para
  `route`; comparar `errorRate` o `p95Ms`.
- **TRACE_ERROR_RATE**: reusar `spanRepository.aggregateRootSpansSince` (ya usado en `ObsQueryService.overview`).

Al disparar: crear `obs_alert_event`, invocar `ObsAlertNotifier` con los canales de la regla, setear
`last_triggered_at`. Todo en try/catch: un fallo de canal no rompe la evaluación (degradación silenciosa).

## 9.5 Notificación (`ObsAlertNotifier` + adaptadores)

- `AlertNotification`: `title`, `body`, `severity`, `dashboardUrl` (deep-link a
  `observability.jira.dashboard-base-url` + `/observability/issues?issueId=` o la ruta correspondiente),
  `value`, `threshold`, `ruleName`.
- **Slack/Telegram/Webhook**: `RestTemplate`/`WebClient` POST al target del `config_json`.
- **Email**: reusar el `JavaMailSender` del proyecto si existe; si no, adaptador webhook a un relay.
- Formato con enlace directo al issue/endpoint en el dashboard (cierra el loop con Fase 8).

## 9.6 Endpoints (`ObservabilityAlertController`, bajo `X-Obs-Api-Key`)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/observability/alerts/rules` | Lista reglas |
| POST | `/observability/alerts/rules` | Crea regla |
| PUT | `/observability/alerts/rules/{id}` | Edita regla |
| DELETE | `/observability/alerts/rules/{id}` | Borra regla |
| PATCH | `/observability/alerts/rules/{id}/toggle` | Activa/desactiva |
| GET | `/observability/alerts/channels` | Lista canales |
| POST | `/observability/alerts/channels` | Crea canal |
| PUT | `/observability/alerts/channels/{id}` | Edita canal |
| DELETE | `/observability/alerts/channels/{id}` | Borra canal |
| POST | `/observability/alerts/channels/{id}/test` | Envía notificación de prueba |
| GET | `/observability/alerts/history?from=&to=&ruleId=` | Histórico de disparos |

## 9.7 Properties nuevas (`observability.alerts.*`)

| Property | Default | Descripción |
|---|---|---|
| `observability.alerts.enabled` | `true` | Activa el motor |
| `observability.alerts.eval-interval-ms` | `60000` | Frecuencia del scheduler |
| `observability.alerts.default-cooldown-minutes` | `15` | Cooldown por defecto |
| `observability.retention.alert-event-days` | `30` | Retención de histórico |
| `observability.alerts.slack.*` / `.telegram.*` / `.email.*` | env | Secretos por canal |

## 9.8 UI de alertas (dentro del módulo observability — Fase 8)

- `pages/ObservabilityAlerts.tsx` con dos tabs: **Reglas** y **Canales**, más panel de **Histórico**.
- `components/alerts/RuleTable.tsx`, `RuleFormModal.tsx` (tipo, filtros, umbral, ventana, cooldown,
  selección de canales), `ChannelTable.tsx`, `ChannelFormModal.tsx` (+ botón "Probar"), `AlertHistory.tsx`.
- `services/alertService.ts` sobre `obsApi.ts`.

## 9.9 Tareas Fase 9

1. Entidades + enums + repositorios + DTOs.
2. Puerto `AlertChannelPort` + 4 adaptadores + `ObsAlertChannelRegistry`.
3. `ObsAlertService` (CRUD), `ObsAlertNotifier`, `ObsAlertEvaluator`, `ObsAlertScheduler`.
4. Enganche event-driven en `ObsIngestionService` (NEW_ISSUE / regresión).
5. `ObservabilityAlertController` + properties + retención.
6. UI de reglas/canales/histórico en el módulo del dashboard.
7. Tests: evaluación de umbral, cooldown, y envío de canal (mock).

## 9.10 Criterios de aceptación Fase 9

- Crear una regla `ERROR_SPIKE` y provocar un pico dispara notificación a Slack/Telegram con deep-link.
- Cooldown evita reenvíos dentro de la ventana.
- "Probar canal" entrega un mensaje real.
- El histórico registra cada disparo; la retención purga lo viejo.
- Un fallo de canal no afecta la ingesta ni la evaluación de otras reglas.

---

## Orden de construcción sugerido

1. **Fase 8** primero (desbloquea visibilidad y provee el deep-link para las alertas).
2. **Fase 9 backend** (entidades → evaluador → notifier → controller).
3. **Fase 9 UI** integrada en el mismo módulo.

Al terminar la construcción, documentar en `.agent-docs` (regla del proyecto) y actualizar
`observability-hub.md` (nuevos endpoints/tablas/properties) y `observability-roadmap.md` (marcar fases).
