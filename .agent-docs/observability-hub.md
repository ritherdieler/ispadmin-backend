# Hub de Observabilidad (backend) — Fase 1, 3 (backend), 6 y 7

Módulo in-house de observabilidad (estilo Crashlytics/Dynatrace) embebido en `ispadmin-backend`
pero diseñado como un servicio independiente que casualmente corre dentro del mismo WAR.

> Regla de oro: **todo** vive bajo `com.dscorp.wispadmin.observability` (hermano de `wispadmin`,
> NO dentro), con tablas prefijo `obs_` y config bajo `observability.*`. Sin FKs al dominio.

- Base path de todos los endpoints: `/ispadmin/observability/**` (context-path `/ispadmin`).
- Autenticación de todo `/observability/**`: header **`X-Obs-Api-Key`** (excepto preflight `OPTIONS` y el webhook del tracker). Cualquier key de plataforma válida es aceptada; sin ella o inválida → `401`. El acceso restringido a administradores se aplica en el **frontend** del panel (`ispadmin-observability-web`), que exige usuario con rol `ADMIN`.
- Correlación front↔back: header **`X-Correlation-Id`** (se genera si no llega y se devuelve en la respuesta).
- Eventos en vivo: STOMP sobre el WebSocket existente `/ws` (SockJS), topic **`/topic/observability/events`**.

---

## 1. Estructura del paquete

```
com.dscorp.wispadmin.observability
├── config/       ObservabilityProperties, ObservabilityAsyncConfig (obsTaskExecutor),
│                 CorrelationIdFilter, ObservabilityApiKeyFilter,
│                 ObsMetricInterceptor, ObservabilityWebConfig
├── port/         ObservabilityReporter (interfaz) + ReportedEvent (datos planos)
├── entity/       ObsIssue, ObsEvent, ObsReplay, ObsEndpointMetric (+ enum ObsIssueStatus)
├── repository/   ObsIssueRepository, ObsEventRepository, ObsReplayRepository, ObsEndpointMetricRepository
├── port/         ObservabilityReporter, IssueTrackerPort (+ modelos neutrales)
├── adapter/      jira/JiraIssueTrackerAdapter (implementa IssueTrackerPort)
├── service/      ObsIngestionService, ObsFingerprintService, ObsLivePublisher, ObsQueryService,
│                 ObsReplayService, ObsMetricCollector, ObsMetricQueryService, IssueTrackerRegistry,
│                 ObsTicketApplicationService, ObsTrackerWebhookService, TrackerBackfillRunner,
│                 InProcessObservabilityReporter
├── controller/   ObservabilityEventController, ObservabilityIssueController, ObservabilityStatsController,
│                 ObservabilityReplayController, ObservabilityMetricController, ObservabilityJiraController,
│                 ObservabilityTrackerController, ObservabilityTrackerWebhookController
├── dto/          EventDtos, QueryDtos, MetricDtos
└── scheduled/    ObsMetricScheduler (flush por minuto), ObsRetentionScheduler (purga diaria)
```

El escaneo se habilita en `WispAdminApplication` con
`scanBasePackages`, `@EntityScan` y `@EnableJpaRepositories` apuntando a
`com.dscorp.wispadmin.wispadmin` + `com.dscorp.wispadmin.observability`.

---

## 2. CONTRATO DEL EVENTO — `POST /observability/events`

Endpoint de ingesta **por lotes** (batch). Lo consumen web (backoffice/asistencias), Android y el propio backend.

### Headers obligatorios

| Header | Obligatorio | Descripción |
|---|---|---|
| `X-Obs-Api-Key` | Sí | API key por plataforma. Sin ella o inválida → `401`. |
| `X-Correlation-Id` | No (recomendado) | ID para enlazar front↔back. Si no se envía, el backend genera uno y lo devuelve en la respuesta. |
| `Content-Type: application/json` | Sí | |

### Request body (JSON)

```json
{
  "events": [
    {
      "eventType": "error",
      "platform": "web-backoffice",
      "severity": "error",
      "message": "Cannot read properties of undefined (reading 'id')",
      "errorType": "TypeError",
      "stacktrace": "TypeError: Cannot read properties...\n  at ...",
      "environment": "prod",
      "release": "1.4.2",
      "correlationId": "b3f1c2...",
      "sessionId": "sess-abc-123",
      "url": "/subscriptions/45",
      "httpMethod": "GET",
      "httpStatus": 500,
      "durationMs": 1234,
      "userAgent": "Mozilla/5.0 ...",
      "user":   { "id": "12", "name": "Juan", "role": "ADMIN" },
      "device": { "os": "Android 14", "model": "Pixel 7", "brand": "Google" },
      "breadcrumbs": [
        { "type": "navigation", "message": "/login -> /dashboard", "ts": 1720710000000 },
        { "type": "click", "message": "button#save", "ts": 1720710005000 }
      ],
      "tags": { "feature": "billing", "tenant": "gigafiber" },
      "context": { "cualquier": "dato-extra-plano" },
      "replayId": 987,
      "timestamp": 1720710010000
    }
  ]
}
```

### Campos del evento

| Campo | Tipo | Req. | Notas |
|---|---|---|---|
| `eventType` | string | No | `error` \| `crash` \| `http_error` \| `log` \| `custom`. Default `error`. |
| `platform` | string | No* | `web-backoffice` \| `web-asistencias` \| `android` \| `backend`. Si falta, se usa la plataforma asociada a la API key. |
| `severity` | string | No | `fatal` \| `error` \| `warning` \| `info`. Default `error`. |
| `message` | string | No | Mensaje principal. |
| `errorType` | string | No | Clase de excepción / nombre del error (parte del fingerprint). |
| `stacktrace` | string | No | Stacktrace completo (parte del fingerprint, normalizado). |
| `environment` | string | No | `prod` \| `dev` \| `local`. |
| `release` | string | No | Versión de build / app. |
| `correlationId` | string | No | Debe coincidir con el header `X-Correlation-Id` cuando aplique. |
| `sessionId` | string | No | Enlaza con el Session Replay. |
| `url` | string | No | Ruta/endpoint donde ocurrió. |
| `httpMethod` | string | No | Método HTTP relacionado. |
| `httpStatus` | number | No | Status HTTP relacionado. |
| `durationMs` | number | No | Duración de la operación. |
| `userAgent` | string | No | |
| `user` | object | No | Datos **planos** del usuario (nunca relación JPA). Se guarda como JSON. |
| `device` | object | No | Datos del dispositivo. Se guarda como JSON. |
| `breadcrumbs` | array | No | Migas de pan (navegación, clicks, requests). JSON. |
| `tags` | object | No | Etiquetas clave/valor. JSON. |
| `context` | object | No | Contexto arbitrario adicional. JSON. |
| `replayId` | number | No | ID de replay previamente subido (ver Fase 3). |
| `timestamp` | number (epoch ms) | No | Momento del evento; si falta se usa `now()`. |

\* `platform` es opcional en el JSON pero **muy recomendado**; si no llega, se deriva de la API key.

### Response `202 Accepted`

```json
{ "accepted": 1, "rejected": 0, "issueIds": [42] }
```

- `accepted`: eventos ingeridos correctamente.
- `rejected`: eventos rechazados (fallo individual o exceso de `maxEventsPerBatch`).
- `issueIds`: IDs de issues afectados (creados o incrementados).

### Códigos de error
- `401` header `X-Obs-Api-Key` ausente/ inválido.
- `429` rate limit por minuto excedido (`observability.rate-limit-per-minute`).
- `503` módulo deshabilitado (`observability.enabled=false`).

### Agrupación (fingerprint)
`fingerprint = sha256(platform + "|" + errorType + "|" + stacktraceNormalizado || message)`.
El stacktrace se normaliza (primeros ~8 frames, direcciones `0x...`→`0xADDR`, dígitos→`N`, minúsculas).
Si existe un issue con ese fingerprint se incrementa `eventCount` y se actualiza `lastSeen`; si estaba
`RESOLVED` se reabre a `OPEN`. Cada evento ingerido publica un resumen en `/topic/observability/events`.

---

## 3. API de consulta (dashboard)

Todas requieren el header `X-Obs-Api-Key` (cualquier key de plataforma válida). El panel usa la key `dashboard`.

### Issues
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/observability/issues` | Listado paginado con filtros. |
| GET | `/observability/issues/{id}` | Detalle del issue. |
| GET | `/observability/issues/{id}/latest-event` | Último evento del issue. |
| GET | `/observability/issues/{id}/occurrences` | Ocurrencias (eventos) paginadas. |
| PATCH | `/observability/issues/{id}/status` | Cambia estado. Body: `{ "status": "RESOLVED" }`. |
| POST | `/observability/issues/{id}/ticket` | Crea ticket en el issue tracker activo (ver Fase 6). |
| DELETE | `/observability/issues/{id}/ticket` | Desvincula el ticket (limpia `tracker_*`/`jiraIssueKey`). |
| POST | `/observability/issues/{id}/jira` | Alias legado de creación de ticket. |

**Filtros de `GET /observability/issues`** (query params, todos opcionales):
`platform`, `severity`, `status` (`OPEN`\|`RESOLVED`\|`IGNORED`\|`MUTED`), `environment`,
`from` / `to` (ISO-8601 `yyyy-MM-dd'T'HH:mm:ss`), `text` (busca en título/mensaje/errorType),
`page` (default 0), `size` (default 25, máx 200). Orden por `lastSeen` desc.

**Respuesta paginada** (`PagedResponse<T>`):
```json
{ "content": [ /* IssueSummaryDto */ ], "page": 0, "size": 25, "totalElements": 132, "totalPages": 6 }
```

**`IssueSummaryDto`**:
```json
{
  "id": 42, "fingerprint": "ab12...", "title": "TypeError: ...",
  "platform": "web-backoffice", "severity": "error", "status": "OPEN",
  "errorType": "TypeError", "lastMessage": "...", "lastEnvironment": "prod",
  "lastRelease": "1.4.2", "eventCount": 17,
  "firstSeen": "2026-07-01T10:00:00", "lastSeen": "2026-07-11T15:30:00",
  "jiraIssueKey": "OBS-123",
  "trackerProvider": "jira", "trackerIssueKey": "OBS-123",
  "trackerBrowseUrl": "https://tu.atlassian.net/browse/OBS-123"
}
```

**`EventDto`** (ocurrencias / latest-event): incluye todos los campos del evento; los objetos
(`user`, `device`, `breadcrumbs`, `tags`, `context`) se devuelven ya parseados como JSON, más
`id`, `issueId`, `createdAt`, `eventTimestamp`, `replayId`.

### Overview / stats
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/observability/stats/overview` | KPIs del overview. |
| GET | `/observability/stats/events-timeseries?from=&to=` | Serie temporal de eventos por hora y plataforma. |

**`OverviewStatsDto`**:
```json
{
  "openIssues": 12, "resolvedIssues": 40, "ignoredIssues": 3,
  "eventsLast24h": 512, "eventsLastHour": 22,
  "issuesByPlatform": { "web-backoffice": 8, "android": 6 },
  "openIssuesBySeverity": { "error": 9, "warning": 3 },
  "eventsByFeature": { "payment": 120, "subscription": 80, "login": 15 },
  "topIssues": [ /* IssueSummaryDto (top 10 por eventCount) */ ]
}
```

**events-timeseries** → `[{ "bucket": "2026-07-11 15:00:00", "platform": "android", "count": 12 }]`
(default últimas 24h si no se pasan `from`/`to`).

---

## 4. Fase 3 (backend) — Session Replay

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/observability/replays` | Sube el buffer rrweb comprimido (gzip). |
| GET | `/observability/replays/{id}` | Descarga el blob para el reproductor. |

### Subida — `POST /observability/replays`
El SDK web comprime el buffer rrweb (gzip) y lo sube. Se aceptan **dos formas**:

1. **Multipart** (`multipart/form-data`): campo `file` con el blob gzip, más params
   `sessionId`, `eventId`, `issueId`, `platform`, `format`, `durationMs`.
2. **Body crudo**: el cuerpo de la request ES el gzip. Metadatos por query params
   (`sessionId`, `eventId`, `format`, ...) y `Content-Encoding: gzip`.

Param `format` (opcional): `"rrweb"` (default) | `"frames"` (replay Android por
fotogramas). Determina la extensión del archivo (`.rrweb.gz` vs `.frames.gz`) y se
persiste en `obs_replay.format`.

Límite: `observability.replay.max-upload-bytes` (default 15 MB) → `413` si se excede.
El blob se guarda en disco bajo `observability.replay.storage-dir/yyyy/MM/dd/<uuid>.<format>.gz`
y los metadatos en `obs_replay`.

**Response `201 Created`**:
```json
{ "id": 987, "sessionId": "sess-abc-123", "format": "rrweb", "sizeBytes": 34567, "contentEncoding": "gzip" }
```

`ReplaySummaryDto` y `EventDto` exponen `format: String?` (el formato del replay
enlazado por `replayId`; `null` en registros legacy → tratar como `rrweb`).
Ver `observability-replay-format.md`.

Luego el cliente puede referenciar ese `replayId` al enviar el evento en `POST /observability/events`.

### Descarga — `GET /observability/replays/{id}`
Devuelve el blob con `Content-Type: application/json` y, si aplica, `Content-Encoding: gzip`
(el navegador lo descomprime). `404` si no existe el registro o el archivo en disco.

---

## 5. Fase 6 — Issue tracker (creación asistida + webhooks entrantes)

Abstracción agnóstica de proveedor (`IssueTrackerPort`), con Jira como primer adaptador. Detalle
completo en `observability-tracker-abstraction.md`. Config bajo `observability.tracker.*` con
fallback a `observability.jira.*` (legado/deprecado). Credenciales **solo** por env en producción.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/observability/tracker/info` | `{ provider, label, configured }` del proveedor activo. |
| POST | `/observability/tracker/{provider}/webhook` | Webhook entrante (público, secreto). |
| GET | `/observability/jira/status` | `{ "configured": true|false }`. |
| POST | `/observability/jira/test` | Test de conexión (llama a `/rest/api/3/myself`). |
| POST | `/observability/issues/{id}/ticket` | Crea el ticket y guarda `tracker_*` + `jiraIssueKey`. |
| DELETE | `/observability/issues/{id}/ticket` | Desvincula (no borra en el proveedor). |

**`POST /observability/issues/{id}/ticket`** — body opcional (para el modal pre-rellenado):
```json
{ "summary": "Texto editable del resumen", "description": "Notas adicionales del reporter" }
```
El adaptador Jira crea el issue en Jira Cloud (`POST /rest/api/3/issue`, auth básica email+API token,
descripción en ADF con stacktrace + contexto + enlace al dashboard) y persiste `tracker_provider`,
`tracker_issue_key`, `tracker_browse_url` (y `jiraIssueKey` por compatibilidad). Si el issue ya tiene
ticket, no duplica.

**Response `CreateTicketResult`**:
```json
{ "ok": true, "issueKey": "OBS-123", "browseUrl": "https://tu.atlassian.net/browse/OBS-123", "error": null }
```

**Test** → `{ "ok": true, "message": "Conexión correcta", "accountName": "Bot ISP" }`.

**Webhook entrante** — `POST /observability/tracker/jira/webhook` (excluido del filtro de API key,
protegido por `X-Obs-Tracker-Secret`). Aplica borrado (limpia `tracker_*`/`jiraIssueKey`) y
transición (mapea el estado del proveedor a `OPEN/RESOLVED/IGNORED/MUTED` según
`observability.tracker.jira.status-mapping`). Ver la guía de Jira Automation en el doc de abstracción.

---

## 6. Fase 7 — APM de endpoints

Un `HandlerInterceptor` (`ObsMetricInterceptor`) registra latencia y status de **todas** las requests.
`ObsMetricCollector` acumula en memoria por (minuto, método, ruta plantilla) y `ObsMetricScheduler`
vuelca cada minuto a `obs_endpoint_metric` (p50/p95/p99, avg, max, throughput, errorCount). Retención corta
(`observability.retention.metric-days`).

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/observability/metrics/endpoints?from=&to=` | Agregado por ruta (top lentos/errores). |
| GET | `/observability/metrics/timeseries?from=&to=&route=` | Serie temporal por minuto. |
| POST | `/observability/rum` | Ingesta de Web Vitals (Fase 11, ver abajo). Responde `202`. |
| GET | `/observability/metrics/web-vitals?from=&to=` | Agregado por página+métrica (p75, distribución). |
| GET | `/observability/metrics/web-vitals/timeseries?from=&to=&page=&metric=` | Serie temporal RUM por bucket. |

**Fase 11 — RUM Web Vitals:** el SDK web envía LCP/INP/CLS/FCP/TTFB por lotes a
`POST /observability/rum` (`{ "vitals": [{ metricName, value, rating?, page, navigationType?, timestamp? }] }`).
`ObsRumCollector` acumula por (minuto, página, plataforma, métrica) y `ObsRumScheduler` vuelca cada
minuto a `obs_rum_metric` (percentiles + distribución good/needs-improvement/poor). Retención
`observability.retention.rum-metric-days`. Detalle en `observability-fase11-rum.md`.

**`EndpointMetricAggregateDto`**:
```json
{ "route": "/subscriptions/{id}", "httpMethod": "GET", "totalRequests": 1200, "totalErrors": 8,
  "errorRate": 0.0066, "avgMs": 42.5, "p95Ms": 120, "p99Ms": 380, "maxMs": 900 }
```

**`EndpointMetricPointDto`** (timeseries): `bucketStart`, `httpMethod`, `route`, `sampleCount`,
`errorCount`, `p50Ms`, `p95Ms`, `p99Ms`, `avgMs`, `maxMs`, `throughputPerMin`.

Default de rango: última hora si no se pasan `from`/`to`.

---

## 7. Eventos en vivo (STOMP)

- Endpoint WebSocket: `/ispadmin/ws` (SockJS) — el mismo de `config/WebSocketConfig.kt`.
- Topic: **`/topic/observability/events`**.
- Mensaje publicado en cada ingesta:
```json
{
  "type": "OBS_EVENT",
  "data": {
    "issueId": 42, "eventId": 1001, "fingerprint": "ab12...", "title": "TypeError: ...",
    "platform": "android", "severity": "error", "eventType": "crash",
    "message": "...", "environment": "prod", "release": "1.4.2",
    "correlationId": "b3f1...", "url": "/x", "httpStatus": 500,
    "eventCount": 18, "createdAt": "2026-07-11T15:30:00"
  }
}
```
El mismo topic transporta otros discriminadores `type` sobre la misma suscripción, p.ej.
`OBS_WEB_VITAL` (Fase 11, RUM) y `OBS_SYSTEM_METRIC` (Fase 14, infra/JVM); el cliente filtra por `type`.

El dashboard se suscribe con una URL de WS configurable aparte (`VITE_OBS_WS_URL`).

---

## 8. Properties `observability.*`

| Property | Default | Descripción |
|---|---|---|
| `observability.enabled` | `true` | Activa/desactiva el módulo entero. |
| `observability.internal-report-enabled` | `true` | Captura interna del backend (exceptions / HTTP failures). |
| `observability.max-events-per-batch` | `100` | Máx eventos por request de ingesta. |
| `observability.max-payload-bytes` | `2000000` | Límite lógico de payload. |
| `observability.rate-limit-per-minute` | `6000` | Rate limit por API key/plataforma. |
| `observability.api-keys.<plataforma>` | — | Keys por plataforma (`web-backoffice`, `web-asistencias`, `android`, `dashboard`). |
| `observability.replay.storage-dir` | `./data/observability/replays` | Carpeta de blobs de replay. |
| `observability.replay.max-upload-bytes` | `15000000` | Límite de subida de replay. |
| `observability.retention.event-days` | `30` | Retención de eventos. |
| `observability.retention.replay-days` | `14` | Retención de replays (borra archivo + registro). |
| `observability.retention.metric-days` | `7` | Retención de métricas APM. |
| `observability.retention.resolved-issue-days` | `90` | Purga de issues `RESOLVED`/`IGNORED` antiguos. |
| `observability.metrics.enabled` | `true` | Activa APM. |
| `observability.metrics.max-samples-per-bucket` | `4000` | Muestras por bucket para percentiles. |
| `observability.jira.enabled` | `false` | Activa Jira. |
| `observability.jira.base-url` | env `OBS_JIRA_BASE_URL` | URL Jira Cloud. |
| `observability.jira.email` | env `OBS_JIRA_EMAIL` | Email de la cuenta/token. |
| `observability.jira.api-token` | env `OBS_JIRA_API_TOKEN` | API token. |
| `observability.jira.project-key` | env `OBS_JIRA_PROJECT_KEY` | Project key. |
| `observability.jira.issue-type` | `Bug` | Tipo de issue. |
| `observability.jira.dashboard-base-url` | env `OBS_DASHBOARD_BASE_URL` | Para enlazar el ticket con el dashboard. |
| `observability.jira.priority-by-severity.<sev>` | — | Mapeo severidad→prioridad Jira. |
| `observability.tracker.provider` | env `OBS_TRACKER_PROVIDER` (`jira`) | Proveedor de issue tracker activo. |
| `observability.tracker.webhook.secret` | env `OBS_TRACKER_WEBHOOK_SECRET` | Secreto del webhook entrante (`X-Obs-Tracker-Secret`). |
| `observability.tracker.jira.*` | fallback a `observability.jira.*` | Config Jira en namespace neutral (preferente). |
| `observability.tracker.jira.status-mapping[Estado]` | — | Mapeo estado del proveedor → `OPEN/RESOLVED/IGNORED/MUTED`. |

Keys por entorno:
- **dev/local** (`application-dev.properties`): keys de desarrollo `dev-obs-*-key`.
- **prod** (`application-prod.properties`): via env `OBS_API_KEY_BACKOFFICE`, `OBS_API_KEY_ASISTENCIAS`,
  `OBS_API_KEY_ANDROID`, `OBS_API_KEY_DASHBOARD`, `OBS_REPLAY_DIR`, `OBS_JIRA_*`.

---

## 9. Tablas MySQL (creadas por `ddl-auto=update`, sin Flyway)

- `obs_issue` — issues agrupados por `fingerprint` (único). Sin FKs al dominio.
- `obs_event` — cada ocurrencia; `issue_id` es columna plana (no relación JPA).
- `obs_replay` — metadatos de replays; el blob vive en disco.
- `obs_endpoint_metric` — métricas APM agregadas por minuto/ruta.
- `obs_rum_metric` — Web Vitals agregados por minuto/página/plataforma/métrica (Fase 11).

Todas con prefijo `obs_` e índices propios. **Ninguna** referencia a tablas del dominio ispadmin.

---

## 10. Captura interna vía puerto `ObservabilityReporter`

El backend es **un cliente más**. `logging/GlobalExceptionHandler.kt` y
`config/RequestLoggingInterceptor.kt` NO llaman a los servicios de ingesta: reportan a través de la
interfaz `port.ObservabilityReporter` (inyectada como opcional/nullable). Hoy la implementación es
`InProcessObservabilityReporter` (guarda in-process, async con `obsTaskExecutor`). Para evitar duplicados,
el `GlobalExceptionHandler` marca el request (`obsEventReported`) y el interceptor no vuelve a reportarlo.

---

## 11. Camino de extracción del hub a un servicio independiente

Cuando el hub deje de vivir dentro de `ispadmin-backend`:

1. **Mover el paquete** `com.dscorp.wispadmin.observability` completo al nuevo servicio.
2. **Migrar los datos**: `mysqldump` de las tablas `obs_issue`, `obs_event`, `obs_replay`,
   `obs_endpoint_metric` a la base del nuevo servicio (no hay FKs al dominio, es un dump limpio).
3. **Mover la carpeta de replays** (`observability.replay.storage-dir`) al nuevo host/volumen.
4. **Mover las properties** `observability.*` a la config del nuevo servicio.
5. **En el backend original** (que sigue siendo cliente): dejar una copia mínima de la interfaz
   `ObservabilityReporter` + `ReportedEvent` y sustituir `InProcessObservabilityReporter` por una
   **implementación HTTP** que haga `POST /observability/events` al nuevo servicio. `GlobalExceptionHandler`
   y `RequestLoggingInterceptor` **no cambian** (siguen usando el puerto).
6. **En los clientes** (web/Android/dashboard): cambiar **solo** la variable de URL base
   (`VITE_OBS_BASE_URL`, `VITE_OBS_WS_URL`, `OBS_BASE_URL` en Android). Las API keys y el contrato
   de evento **no cambian**.
7. **STOMP**: el dashboard apunta su `VITE_OBS_WS_URL` al broker del nuevo servicio; el topic sigue
   siendo `/topic/observability/events`.

Nada de estado estático compartido con ispadmin; el scheduler y el async usan beans propios
(`obsTaskExecutor`) que se mueven junto con el módulo.
