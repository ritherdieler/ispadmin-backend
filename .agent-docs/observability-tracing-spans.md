# Observabilidad — Trazado distribuido (spans / waterfall)

Fases 1 y 2 del plan "Waterfall de trazas distribuidas". Módulo aislado en
`com.dscorp.wispadmin.observability`. Alineado a W3C Trace Context.

## Modelo

- `traceId`: 32 hex. `spanId` / `parentSpanId`: 16 hex.
- Propagación por header W3C `traceparent` (`00-<traceId>-<spanId>-<flags>`).
- Header de sesión: `X-Obs-Session-Id` (sesión → trazas → spans).
- El `X-Correlation-Id` pasa a valer el `traceId` cuando hay traza (lo fija
  `TraceContextFilter`), de modo que los `ObsEvent` de errores quedan enlazados a
  la traza vía `correlationId = traceId` sin migrar datos.

### Entidad `obs_span` (se crea sola con `ddl-auto=update`)

Columnas: `trace_id`, `span_id`, `parent_span_id`, `name`, `kind`
(SERVER/CLIENT/DB/INTERNAL), `platform` (android/backend), `session_id`,
`start_epoch_ms`, `duration_ms`, `status` (OK/ERROR), `http_method`,
`http_route`, `http_status`, `db_statement` (truncado a 2000), `tags_json`,
`environment`, `app_release`, `created_at`.
Índices: `trace_id`, `parent_span_id`, `start_epoch_ms`, `session_id`.

## Endpoints

Todos bajo `/observability/**`, protegidos por `ObservabilityApiKeyFilter`
(header `X-Obs-Api-Key`). El `TraceContextFilter` NO instrumenta rutas
`/observability` (evita auto-trazarse).

### 1) `POST /observability/spans` — ingesta batch (Android/otros)

Recibe un **array** de spans. Campos del DTO (`SpanIngestRequest`):

```json
[
  {
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
    "spanId": "00f067aa0ba902b7",
    "parentSpanId": "0000000000000000",
    "name": "GET /subscription/all",
    "kind": "CLIENT",
    "platform": "android",
    "sessionId": "b3d1c0e2-...",
    "startEpochMs": 1752283200000,
    "durationMs": 812,
    "status": "OK",
    "httpMethod": "GET",
    "httpRoute": "/subscription/all",
    "httpStatus": 200,
    "dbStatement": null,
    "tagsJson": "{\"screen\":\"SubscriptionForm\"}",
    "environment": "production",
    "release": "1.42.0"
  }
]
```

- Obligatorios: `traceId`, `spanId`. Sin ellos el span se rechaza.
- Defaults: `kind`=`INTERNAL`, `status`=`OK`, `platform`= la del API key.
- Límites: `traceId` 32, `spanId`/`parentSpanId` 16, `name`/`httpRoute` 500,
  `dbStatement` 2000, `kind` 20, `status` 10.

Respuesta `202 Accepted` (`SpanIngestResponse`):

```json
{ "accepted": 1, "rejected": 0 }
```

`503` si `observability.enabled=false` o `observability.tracing.enabled=false`.

### 2) `GET /observability/traces` — lista de trazas

Query params (todos opcionales salvo paginación con default):
`from` (epoch ms), `to` (epoch ms), `route`, `minDurationMs`, `status`
(OK/ERROR), `platform`, `sessionId`, `page` (0), `size` (25, máx 200).

Una traza = su span raíz (`parentSpanId IS NULL`). Respuesta
`PagedResponse<TraceSummaryDto>`:

```json
{
  "content": [
    {
      "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
      "rootName": "GET /subscription/all",
      "rootSpanId": "00f067aa0ba902b7",
      "platform": "android",
      "sessionId": "b3d1c0e2-...",
      "httpMethod": "GET",
      "httpRoute": "/subscription/all",
      "httpStatus": 200,
      "status": "OK",
      "startEpochMs": 1752283200000,
      "durationMs": 812,
      "spanCount": 14,
      "hasError": false
    }
  ],
  "page": 0,
  "size": 25,
  "totalElements": 1,
  "totalPages": 1
}
```

### 3) `GET /observability/traces/{traceId}` — detalle

Devuelve todos los spans ordenados por `startEpochMs` + los `ObsEvent`
vinculados por `correlationId = traceId`. Respuesta `TraceDetailDto`
(`404` si no hay spans para el `traceId`):

```json
{
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spans": [
    {
      "id": 1,
      "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
      "spanId": "00f067aa0ba902b7",
      "parentSpanId": null,
      "name": "GET /subscription/all",
      "kind": "SERVER",
      "platform": "backend",
      "sessionId": "b3d1c0e2-...",
      "startEpochMs": 1752283200010,
      "durationMs": 120,
      "status": "OK",
      "httpMethod": "GET",
      "httpRoute": "/subscription/all",
      "httpStatus": 200,
      "dbStatement": null,
      "tags": { "screen": "SubscriptionForm" },
      "environment": "production",
      "release": "1.42.0"
    }
  ],
  "events": [
    { "id": 10, "issueId": 3, "correlationId": "4bf92f35...", "sessionId": "b3d1c0e2-...", "message": "..." }
  ]
}
```

`events[]` usa el mismo `EventDto` de `/observability/issues/*` (campos JSON ya
deserializados: `user`, `device`, `breadcrumbs`, `tags`, `context`).

## Instrumentación automática (Fase 2)

- **`TraceContextFilter`** (`@Order HIGHEST_PRECEDENCE + 15`, tras
  `CorrelationIdFilter`): lee/genera `traceparent` y `X-Obs-Session-Id`, crea el
  span `SERVER` (`METHOD ruta`), guarda `TraceScope` en ThreadLocal + MDC
  (`traceId` y `correlationId`), fuerza `X-Correlation-Id = traceId`, y encola el
  span al terminar el request. Responde headers `traceparent` y
  `X-Correlation-Id`.
- **`TraceContext` / `TraceScope`**: ThreadLocal con `traceId`, `currentSpanId`
  (parent de los hijos), `sessionId`, `platform`, `environment`, `release`.
- **`ObsTracer`**: helper `span(name, kind) { ... }` (usado por `MikroTikService`
  como `mikrotik: <op>`) y `recordDbSpan(statement, elapsedMs, success)`.
- **SQL**: `datasource-proxy` (`net.ttddyy:datasource-proxy:1.10.1`) +
  `DataSourceTracingBeanPostProcessor` que envuelve el `DataSource`. Cada query → span `DB`.
  **Importante (corregido en Fase 5)**: el BPP **no** puede inyectar `ObsTracer` ni
  `ObservabilityProperties` por constructor (ni siquiera con `ObjectProvider`), porque al
  ser `BeanPostProcessor` que envuelve el `DataSource` crea el ciclo
  `obsSpanCollector → obsSpanRepository → entityManagerFactory → obsTracer → obsSpanCollector`
  y Spring 2.7 aborta el arranque (`APPLICATION FAILED TO START`, sin traza visible por el
  `logger org.springframework=OFF` de `logback-spring.xml`). Solución: el BPP implementa
  `BeanFactoryAware` y resuelve `ObsTracer`/`ObservabilityProperties` de forma perezosa vía
  `beanFactory.getBean(...)` (con try/catch de `BeansException`) solo dentro de `afterQuery`,
  quedando con cero dependencias eager. Para diagnosticar fallos de arranque futuros:
  `java -jar target/*.war --spring.profiles.active=dev,local --logging.level.org.springframework=ERROR`.
- **HTTP saliente**: `TracingClientHttpRequestInterceptor` crea span `CLIENT` e
  inyecta `traceparent` + `X-Obs-Session-Id`. Aplicado a `HttpClient` (OLT, vía
  `TracingInterceptorHolder` porque es `object`), `JiraIssueTrackerAdapter` y
  `WhatsAppService`.
- **`GlobalExceptionHandler` / `RequestLoggingInterceptor`**: ahora incluyen el
  `sessionId` (`X-Obs-Session-Id`) en el `ReportedEvent` (antes iba null).

## Configuración (`application.properties`)

```
observability.retention.span-days=7
observability.tracing.enabled=true
observability.tracing.sample-rate=1.0
observability.tracing.max-spans-per-trace=300
observability.tracing.max-buffered-spans=20000
```

## Buffer y retención

- `ObsSpanCollector`: buffer en memoria (`ConcurrentLinkedQueue`) + tope global
  (`max-buffered-spans`) y por traza (`max-spans-per-trace`). No escribe en el
  hot path.
- `ObsSpanScheduler`: flush cada 5 s (lotes de 1000).
- `ObsRetentionScheduler`: purga spans con `start_epoch_ms` anterior a
  `retention.span-days`.
