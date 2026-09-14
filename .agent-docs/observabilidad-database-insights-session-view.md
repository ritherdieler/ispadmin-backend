# Database Insights y Session View (backend)

Capa de consulta que explota los datos ya persistidos por el trazado distribuido
(`obs_span`, `obs_event`, `obs_replay`) para alimentar dos features nuevas del dashboard
(`/database` y `/sessions`) y mejorar Overview/Performance. Plan: "Database Insights y
Session View". Doc web equivalente:
[`ispadmin-observability-web/.agent-docs/observabilidad-database-insights-session-view.md`](../../ispadmin-observability-web/.agent-docs/observabilidad-database-insights-session-view.md).

Todo vive en `src/main/kotlin/com/dscorp/wispadmin/observability/`. Las rutas cuelgan del
context-path `/ispadmin` y requieren el header `X-Obs-Api-Key` (filtro
`ObservabilityApiKeyFilter`, aplica a **todas** las rutas `/observability/**`, también los
GET de consulta). Keys de dev en `application-dev.properties`
(`observability.api-keys.dashboard=dev-obs-dashboard-key`). Zona horaria `America/Lima`
(`app.timezone`).

## Database Insights

### `controller/ObservabilityDatabaseController.kt` (`/observability/database`)

- `GET /queries?from&to&limit` — `from`/`to` como `LocalDateTime` ISO (default última hora),
  convertidos a epoch ms con `ZoneId.of(app.timezone)`; `limit` default 50 (coerce 1..500).
- `GET /nplusone?from&to&threshold` — `threshold` default 5 (coerce ≥1).

### `service/ObsDatabaseQueryService.kt`

- `topQueries` → `List<DbQueryAggregateDto>`. Usa `ObsSpanRepository.aggregateDbStatements`
  (spans `kind='DB'`, `GROUP BY s.dbStatement ORDER BY SUM(durationMs) DESC`). Calcula
  `pct = totalMs del statement / gran total`.
- `nPlusOne` → `List<NPlusOneCandidateDto>`. Usa `findNPlusOneCandidates`
  (`GROUP BY traceId, dbStatement HAVING COUNT >= threshold`) y **consolida por statement en
  memoria**: `maxRepetitions` = máximo COUNT entre trazas, `exampleTraceId` = traza con más
  repeticiones, `totalMs` = suma, `affectedTraces` = nº de trazas distintas. Orden desc por
  `maxRepetitions`.

### DTOs (`dto/QueryDtos.kt`) — nombres REALES

```kotlin
data class DbQueryAggregateDto(
    val statement: String?, val calls: Long, val totalMs: Long,
    val avgMs: Double, val maxMs: Long, val pct: Double
)
data class NPlusOneCandidateDto(
    val statement: String?, val exampleTraceId: String?,
    val maxRepetitions: Long, val totalMs: Long, val affectedTraces: Long
)
```

> No se añade hash/normalización de `dbStatement`: datasource-proxy ya guarda sentencias
> preparadas con `?` y con retención de 7 días el `GROUP BY` sobre TEXT es aceptable.

## Session View

### `controller/ObservabilitySessionController.kt` (`/observability/sessions`)

- `GET /observability/sessions?from&to&page&size` — default 24h, `page` 0, `size` 25
  (coerce 1..200) → `PagedResponse<SessionSummaryDto>`.
- `GET /observability/sessions/{sessionId}?feature&action` → `SessionDetailDto` (o 404).
  `feature`/`action` son opcionales y filtran los eventos de la sesión por dominio
  (reconstrucción del camino de un dominio previo al error).

### `service/ObsSessionQueryService.kt`

- `listSessions`: la lista se **deriva de `obs_event`** (`ObsEventRepository.aggregateRecentSessions`,
  `GROUP BY sessionId, platform`). Enriquecimiento por sesión:
  `ObsSpanRepository.countRootSpansBySession` (traceCount),
  `ObsReplayRepository.findSessionIdsWithReplay` (hasReplay), y `user` del último evento
  (`findFirstBySessionIdOrderByCreatedAtDesc`).
- `getSession`: une los tres orígenes — eventos (`findBySessionIdOrderByCreatedAtDesc`),
  root spans por sesión (`searchRootSpans(..., sessionId, …)` enriquecidos con
  `aggregateByTraceIds`) y replays (`findBySessionIdOrderByCreatedAtAsc`). Devuelve `null`
  (→404) solo si los tres están vacíos.

### DTOs (`dto/QueryDtos.kt`)

```kotlin
data class SessionSummaryDto(
    val sessionId: String?, val platform: String?, val eventCount: Long,
    val traceCount: Long, val hasReplay: Boolean,
    val firstSeen: LocalDateTime?, val lastSeen: LocalDateTime?, val user: Any?
)
data class ReplaySummaryDto(
    val id: Long?, val durationMs: Long?, val sizeBytes: Long?, val createdAt: LocalDateTime?
)
data class SessionDetailDto(
    val summary: SessionSummaryDto, val events: List<EventDto>,
    val traces: List<TraceSummaryDto>, val replays: List<ReplaySummaryDto>
)
```

> **Limitación conocida**: sesiones "solo trazas" (sin `obs_event`) no aparecen en la lista
> (derivada de eventos), pero sí son accesibles por `GET /sessions/{sessionId}`.

## Mejoras a features existentes

### Overview (`service/ObsQueryService.kt` → `OverviewStatsDto`)

Campos nuevos, calculados sobre root spans de la última hora:

```kotlin
val tracesLastHour: Long        // aggregateRootSpansSince(from) -> COUNT
val errorTraceRate: Double      // root spans status=ERROR / total
val p95TraceDurationMs: Long    // rootSpanDurationsSince(from) -> percentil 95
val slowTraces: List<TraceSummaryDto>  // findSlowestRootSpansSince(from, top 5)
```

### Performance (`metrics/endpoints` → `EndpointMetricAggregateDto`)

```kotlin
val dbTimeMs: Long? = null      // SUM(durationMs) spans kind=DB por ruta
val dbTimeRatio: Double? = null // dbTimeMs / tiempo total de la ruta
```

`ObsSpanRepository.aggregateDbTimeByRoute` une spans `kind='DB'` con su root span por
`traceId` y agrupa por `root.httpRoute`. Campos nulos cuando la ruta no tiene spans DB.

## Repositorio (`repository/ObsSpanRepository.kt`) — queries nuevas

`aggregateDbStatements`, `findNPlusOneCandidates`, `countRootSpansBySession`,
`aggregateRootSpansSince`, `rootSpanDurationsSince`, `findSlowestRootSpansSince`,
`aggregateDbTimeByRoute`. En `ObsEventRepository`/`ObsReplayRepository` se añadieron
`aggregateRecentSessions`, `findFirstBySessionIdOrderByCreatedAtDesc`,
`findBySessionIdOrderByCreatedAtDesc`, `findBySessionIdOrderByCreatedAtAsc`,
`findSessionIdsWithReplay`.

## Validación E2E (2026-07-11)

- Arranque: `./mvnw spring-boot:run` (profiles `dev,local`, MySQL local, puerto 8080).
  Un WAR viejo ocupaba 8080 y devolvía **404** en los endpoints nuevos; se detuvo y se
  arrancó el build fresco (los nuevos endpoints pasaron a **200**).
- Endpoints (con `X-Obs-Api-Key: dev-obs-dashboard-key`): los 6 responden **200** con la
  forma esperada. `stats/overview` incluye `tracesLastHour=47`, `errorTraceRate≈0.54`,
  `p95TraceDurationMs=7536`, `slowTraces[5]`. `metrics/endpoints` trae `dbTimeMs`/`dbTimeRatio`
  poblados. `database/queries` (14 filas con `pct`), `database/nplusone`
  (`maxRepetitions`/`exampleTraceId`/`affectedTraces`).
- Tráfico real: flavor dev en dispositivo físico recorriendo **Registrar Suscripción**;
  generó ~10 trazas nuevas con spans `kind=DB` en la sesión Android `c11cdfc8-…`
  (`GET /napbox/near` con 28 spans, `place/findByLocation`, `onu/unconfigured_onus`, …).

### Advertencia de arranque no bloqueante (preexistente)

En `ddl-auto=update`, MySQL rechaza recrear índices sobre columnas TEXT del
`obs_endpoint_metric` (`BLOB/TEXT column 'route' used in key specification without a key
length`). Es un `WARN` de Hibernate, la tabla ya existe y **no impide el arranque** ni afecta
a los endpoints.
