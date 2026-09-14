# Backend: release en runtime, registro de deploys y filtros por release

Implementación de la **Parte 1** (backend) del plan _UX comparación observability_ más la parte
backend de `nplusone-route`. Solo se validó compilación (`./mvnw -q -o compile` → OK); el testing
end-to-end lo hace un flujo posterior.

## Parte 1.1 — Propiedad de release en runtime

- `ObservabilityProperties`: nuevo campo raíz `var release: String = ""`.
- `application.properties`: `observability.release=${APP_RELEASE:}` (bind del env `APP_RELEASE`).
- `TraceContextFilter`:
  - `TraceScope.release = properties.release.takeIf { it.isNotBlank() }` (antes `null`).
  - Span `SERVER` de `enqueueServerSpan` ahora incluye `release = properties.release.takeIf { it.isNotBlank() }`.
- `ObsTracer` ya propagaba `scope.release` a los spans `INTERNAL`/`DB` (sin cambios).

Resultado: cuando el backend arranca con `APP_RELEASE=1.4.2+abc1234`, todos los spans quedan
etiquetados con ese release en la columna `obs_span.app_release`.

## Parte 1.2 — Registro de deploys

- Entidad `ObsDeployEvent` (tabla `obs_deploy_event`, autocreada por `ddl-auto=update`):
  `id` (IDENTITY), `platform` (60), `app_release` (120), `semver` (60, null), `git_sha` (40, null),
  `notes` (TEXT null), `deployed_at`. Índices `idx_obs_deploy_platform` y `idx_obs_deploy_deployed_at`.
- `ObsDeployEventRepository` con: `findByPlatformAndDeployedAtBetweenOrderByDeployedAtDesc`,
  `findByDeployedAtBetweenOrderByDeployedAtDesc`, `findTop50ByOrderByDeployedAtDesc`,
  `findTop50ByPlatformOrderByDeployedAtDesc`.
- DTOs (`dto/DeployDtos.kt`): `DeployEventDto` y `CreateDeployEventRequest` (+ `ObsDeployEvent.toDto()`).
- `ObsDeployEventService`: `list(platform, from, to)` y `create(request)` (setea `deployedAt = now`).
- `ObservabilityReleaseController` con `@RequestMapping("/observability/releases")`.

### Contrato final del API

Rutas reales bajo context-path `/ispadmin` → `/ispadmin/observability/releases`.

`GET /observability/releases`
- Query params (todos opcionales): `platform`, `from`, `to` (ISO-8601 `DATE_TIME`, ej.
  `2026-07-13T00:00:00`).
- Lógica: si vienen `from` y `to` → rango (filtrado por `platform` si se envía). Si no hay fechas
  → top 50 más recientes (filtrado por `platform` si se envía).
- Respuesta `200`: `List<DeployEventDto>`:
  `{ "release": string?, "platform": string?, "semver": string?, "gitSha": string?, "deployedAt": iso?, "notes": string? }`.
- Auth: sesión admin del dashboard (`X-Obs-Session`), igual que el resto de lecturas `/observability/*`.

`POST /observability/releases`
- Body `CreateDeployEventRequest`:
  `{ "platform": string (req), "release": string (req), "semver": string?, "gitSha": string?, "notes": string? }`.
- Respuesta `201`: `DeployEventDto`.
- Auth: **`X-Obs-Api-Key`** (se añadió `/observability/releases` a `isTelemetryIngestPath` en
  `ObservabilityApiKeyFilter`, solo para POST) → lo consume el script de deploy.

## Parte 1.3 — Filtros por release en queries existentes (opción A)

`ObsSpanRepository`:
- `searchRootSpans`: nuevo `@Param("release") release: String?` + `AND (:release IS NULL OR s.release = :release)`.
- `aggregateDbStatements`: nuevo param + filtro por `s.release`.
- `findNPlusOneCandidates`: nuevo param + filtro por `s.release` (ver nplusone-route abajo).
- `aggregateDbTimeByRoute`: nuevo param + `AND (:release IS NULL OR root.release = :release)`.

Propagación (todos opcionales, `null` = comportamiento idéntico al actual):
- `ObsTraceQueryService.searchTraces(..., release, page, size)`.
- `ObsDatabaseQueryService.topQueries(..., release)` y `nPlusOne(..., release)`.
- `ObsMetricQueryService.aggregate(from, to, release)`.
- `ObsSessionQueryService`: se actualizó la llamada a `searchRootSpans` con `release = null`.

Nombres de parámetro `release` en endpoints (todos `@RequestParam(required = false) release: String?`):
- `GET /observability/traces?...&release=`
- `GET /observability/database/queries?...&release=`
- `GET /observability/database/nplusone?...&release=`
- `GET /observability/metrics/endpoints?...&release=`

## Fix 2026-07-13 — `release` visible en el listado de trazas

El filtro `release` en `searchTraces` ya existía, pero `TraceSummaryDto` **no exponía** el
campo, así que la columna "Versión" de la tabla de trazas siempre mostraba "—" (aunque los
spans raíz sí traían `app_release`). Corregido:

- `SpanDtos.kt`: `TraceSummaryDto` gana `val release: String?`.
- Los **tres** constructores de `TraceSummaryDto` pueblan `release = root.release`:
  `ObsTraceQueryService`, `ObsQueryService` y `ObsSessionQueryService` (este último ya no queda
  desalineado). El detalle de traza (`SpanDto`) ya exponía `release`.

Verificado en prod con token de sesión admin: `GET /observability/traces?release=1.0.0+1133758`
devuelve `release` por traza; `GET /observability/traces/{id}` lo trae por span; y
`GET /observability/releases` lista las 4 versiones registradas.

## nplusone-route (parte backend)

- `findNPlusOneCandidates` ahora hace join con el span raíz (`ObsSpan root` con
  `root.parentSpanId IS NULL AND root.traceId = s.traceId`) y devuelve
  `traceId, dbStatement, count, sumDuration, MAX(root.httpRoute)`.
- `NPlusOneCandidateDto` gana `httpRoute: String?`.
- `ObsDatabaseQueryService.nPlusOne` puebla `httpRoute` desde el `best` (traza representativa por
  máximas repeticiones).

## Fix 2026-07-13 — latencia/reqs del comparador ahora respetan el release

Síntoma: en "Validar fix (comparar versiones)" las métricas Prom/p95/p99/Reqs salían
**idénticas** entre las dos versiones (0% cambio), mientras que DB% y N+1 sí diferían.

Causa raíz: `ObsMetricQueryService.aggregate(from, to, release)` calculaba el DB% desde spans
filtrados por release (`spanRepository.aggregateDbTimeByRoute(..., release)`), pero la
latencia y el conteo de requests venían de `metricRepository.aggregateByRoute(from, to)`, que
lee la tabla rollup `obs_endpoint_metric`. Esa tabla **no tiene dimensión de release**
(solo `bucket_start`, `route`, `http_method`, percentiles), así que devolvía lo mismo para
cualquier versión.

Fix: cuando se pasa `release`, la latencia/reqs se calculan desde `obs_span` (release-aware):

- `ObsSpanRepository.rootSpanMetricsByRoute(from, to, release)`: spans raíz
  (`parentSpanId IS NULL`) con `httpRoute`, `httpMethod`, `durationMs`, `status`, filtrados por
  `release`.
- `ObsMetricQueryService.aggregate` ramifica: si `release` no es nulo/blank →
  `aggregateFromSpans(...)` agrupa por (ruta, método) y calcula `totalRequests`, `totalErrors`,
  `avgMs`, `p95Ms`/`p99Ms` (percentil nearest-rank en Kotlin, mismo criterio que
  `ObsQueryService.percentile`) y `maxMs`; combina el DB time ya filtrado por release. Sin
  `release` mantiene el camino rollup (dashboard sin cambios).

Efectivo para datos históricos mientras los spans estén dentro de retención
(`retention.spanDays`, default 7).

## Notas

- No hay migración manual: `spring.jpa.hibernate.ddl-auto=update` crea `obs_deploy_event` sola.
- Se ajustó `ObsLlmContextServiceTest` (matcher extra en `topQueries`) por el nuevo param opcional.
