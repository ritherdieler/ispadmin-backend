# Observabilidad — Integración Fase 1 (Backend: contexto y agregaciones)

Parte del plan "Observability Feature Integration". Esta fase habilita que el **contexto viaje** (release, ruta, rango de tiempo) hasta las consultas y añade dos endpoints agregados tipo "hub": detalle de Endpoint y summary de Release. Todo se sigue exponiendo mediante DTOs (nunca entidades). Desarrollo con TDD (JUnit5 + MockK/Mockito, MockMvc `standaloneSetup`).

Estos contratos serán consumidos por el frontend (`ispadmin-observability-web`) en fases posteriores.

## 1. Filtros de contexto añadidos a endpoints existentes

### 1.1 Issues — filtro `release`
- `GET /observability/issues` acepta ahora `release` (opcional).
- Propagación: `ObservabilityIssueController.list` → `ObsQueryService.searchIssues(..., release, ...)` → `ObsIssueRepository.search(..., release, ...)` con `AND (:release IS NULL OR i.lastRelease = :release)`.

### 1.2 Web Vitals — filtro `release`
- `GET /observability/metrics/web-vitals?release=` y `GET /observability/metrics/web-vitals/timeseries?release=`.
- Se añadió la **dimensión release** a RUM de extremo a extremo:
  - Entidad `ObsRumMetric`: nueva columna `app_release` (`release`, nullable, len 120).
  - DTO de ingesta `RumVitalRequest`: nuevo campo opcional `release`.
  - `ObservabilityRumController.ingest` pasa `release` a `ObsRumCollector.record(..., release)`; `BucketKey` incluye `release` y `flush()` lo persiste.
  - `ObsRumMetricRepository.aggregateByPageAndMetric(from, to, release)` filtra por `release` (`AND (:release IS NULL OR m.release = :release)`).
  - `ObsRumQueryService.aggregate/timeSeries` aceptan `release` (timeSeries filtra en memoria).
  - `RumMetricAggregateDto` y `RumMetricPointDto` incluyen ahora `release` (nullable).
- Compatibilidad: sin `release` el comportamiento es idéntico al anterior (agrega todos los releases).

### 1.3 Sessions — filtro `release`
- `GET /observability/sessions?release=`.
- Propagación: `ObservabilitySessionController.list` → `ObsSessionQueryService.listSessions(from, to, release, page, size)` → `ObsEventRepository.aggregateRecentSessions(from, to, release, pageable)` (filtro en query principal y en countQuery).

### 1.4 Database — filtro `route`
- `GET /observability/database/queries?route=` y `GET /observability/database/nplusone?route=` (además del `release` ya existente).
- Propagación: `ObservabilityDatabaseController` → `ObsDatabaseQueryService.topQueries(..., release, route)` / `nPlusOne(..., release, route)`.
- Repositorio:
  - `ObsSpanRepository.aggregateDbStatementsByRoute(...)`: nueva query que hace join con el root span (`root.parentSpanId IS NULL AND root.traceId = s.traceId AND root.httpRoute = :route`). Se usa solo cuando `route` viene informado; si no, se mantiene la query original `aggregateDbStatements`.
  - `findNPlusOneCandidates(..., route)`: se añadió `AND (:route IS NULL OR root.httpRoute = :route)`.

### 1.5 Overview — rango `from`/`to`
- `GET /observability/stats/overview?from=&to=` (ISO date-time). Defaults: `to = now`, `from = to - 24h` (compatibilidad).
- `ObsQueryService.overview(from, to)` respeta el rango en: `eventsByFeature`, `eventsLast24h` (eventos en el rango), `eventsLastHour` (última hora del rango), trazas del rango (`tracesLastHour`, `errorTraceRate`, `p95TraceDurationMs`, `slowTraces`).
- Los conteos por estado (open/resolved/ignored) y por plataforma/severidad siguen siendo totales globales.
- Nuevos métodos de repositorio: `ObsEventRepository.countBetween`, `countGroupedByFeatureBetween`; `ObsSpanRepository.rootSpanDurationsBetween`, `findSlowestRootSpansBetween` (se reutiliza `aggregateRootSpansBetween`).

## 2. Nuevo endpoint agregado — Detalle de Endpoint

`GET /observability/metrics/endpoints/detail?route=&from=&to=&release=`

- `route` es **obligatorio**. `from`/`to` opcionales (default `to=now`, `from=to-1h`). `release` opcional.
- Servicio: `ObsEndpointDetailService.detail(route, from, to, release)` que compone: métricas desde spans root de la ruta, serie temporal (`ObsMetricQueryService.timeSeries`), top queries y N+1 de la ruta (`ObsDatabaseQueryService`), issues relacionados (`ObsIssueRepository.findIssuesByRoute` por `url LIKE %route%`) y trazas de ejemplo.

### DTO de respuesta: `EndpointDetailDto`
| Campo | Tipo | Descripción |
|---|---|---|
| `route` | `String?` | Ruta consultada |
| `release` | `String?` | Release filtrado (o null) |
| `from` | `LocalDateTime` | Inicio del rango |
| `to` | `LocalDateTime` | Fin del rango |
| `metrics` | `EndpointDetailMetricsDto` | Métricas agregadas de la ruta |
| `timeSeries` | `List<EndpointMetricPointDto>` | Serie temporal por bucket |
| `topQueries` | `List<DbQueryAggregateDto>` | Top consultas SQL de la ruta |
| `nPlusOne` | `List<NPlusOneCandidateDto>` | Candidatos N+1 de la ruta |
| `issues` | `List<IssueSummaryDto>` | Issues relacionados con la ruta |
| `sampleTraces` | `List<TraceSummaryDto>` | Trazas de ejemplo (id + duración) |

### `EndpointDetailMetricsDto`
`route: String?`, `httpMethod: String?`, `totalRequests: Long`, `totalErrors: Long`, `errorRate: Double`, `avgMs: Double`, `p50Ms: Long`, `p95Ms: Long`, `p99Ms: Long`, `maxMs: Long`, `dbTimeMs: Long?`, `dbTimeRatio: Double?`.

## 3. Nuevo endpoint agregado — Summary de Release

`GET /observability/releases/{version}/summary`

- Servicio: `ObsReleaseSummaryService.summary(version)`.
- Ventana de tiempo del release: `from = deployedAt(version)`, `to = deployedAt(siguiente release del mismo platform) ?: now`. Si el release no está registrado como deploy event → default últimas 24h y `previousVersion = null`.
- `previousVersion`: deploy anterior del mismo platform (o global si platform null).
- Regresión de latencia: reutiliza `ObsMetricQueryService.aggregate` para el release objetivo y el anterior (misma lógica que FixValidation), join por `route`+`httpMethod`, delta de p95 en %. `regressed = deltaP95Pct > 10%`.
- Web Vitals del release: `ObsRumQueryService.aggregate(from, to, version)`.
- Adopción: sesiones/eventos (`ObsEventRepository.countDistinctSessionsByReleaseBetween`, `countByReleaseBetween`) y trazas (`ObsSpanRepository.countRootSpansByReleaseBetween`).

### DTO de respuesta: `ReleaseSummaryDto`
| Campo | Tipo | Descripción |
|---|---|---|
| `version` | `String` | Release consultado |
| `previousVersion` | `String?` | Release anterior comparado |
| `platform` | `String?` | Plataforma del release |
| `from` | `LocalDateTime` | Inicio de la ventana del release |
| `to` | `LocalDateTime` | Fin de la ventana del release |
| `newIssues` | `List<IssueSummaryDto>` | Issues nuevos del release (máx. 25) |
| `newIssuesCount` | `Long` | Conteo total de issues nuevos |
| `latencyComparison` | `List<ReleaseRouteLatencyDto>` | Comparación de latencia por ruta vs release anterior |
| `webVitals` | `List<RumMetricAggregateDto>` | Web vitals del release |
| `adoption` | `ReleaseAdoptionDto` | Adopción del release |

### `ReleaseRouteLatencyDto`
`route: String?`, `httpMethod: String?`, `baseAvgMs: Double?`, `targetAvgMs: Double?`, `baseP95Ms: Long?`, `targetP95Ms: Long?`, `deltaP95Pct: Double?`, `regressed: Boolean`.

### `ReleaseAdoptionDto`
`sessionCount: Long`, `eventCount: Long`, `traceCount: Long`.

## 4. Archivos

### Producción (modificados)
- `controller/ObservabilityIssueController.kt`
- `controller/ObservabilitySessionController.kt`
- `controller/ObservabilityDatabaseController.kt`
- `controller/ObservabilityStatsController.kt`
- `controller/ObservabilityMetricController.kt`
- `controller/ObservabilityRumController.kt`
- `controller/ObservabilityReleaseController.kt`
- `service/ObsQueryService.kt`
- `service/ObsSessionQueryService.kt`
- `service/ObsRumQueryService.kt`
- `service/ObsRumCollector.kt`
- `service/ObsDatabaseQueryService.kt`
- `service/ObsLivePublisher.kt`
- `repository/ObsIssueRepository.kt`
- `repository/ObsEventRepository.kt`
- `repository/ObsRumMetricRepository.kt`
- `repository/ObsSpanRepository.kt`
- `repository/ObsDeployEventRepository.kt`
- `entity/ObsRumMetric.kt`
- `dto/RumDtos.kt`
- `dto/MetricDtos.kt`
- `dto/DeployDtos.kt`

### Producción (nuevos)
- `service/ObsEndpointDetailService.kt`
- `service/ObsReleaseSummaryService.kt`

### Pruebas (nuevas)
- `controller/ObservabilityIssueControllerTest.kt`
- `controller/ObservabilitySessionControllerTest.kt`
- `controller/ObservabilityDatabaseControllerTest.kt`
- `controller/ObservabilityStatsControllerTest.kt`
- `controller/ObservabilityMetricControllerTest.kt`
- `controller/ObservabilityReleaseControllerTest.kt`
- `service/ObsEndpointDetailServiceTest.kt`
- `service/ObsReleaseSummaryServiceTest.kt`

### Pruebas (ajustadas)
- `service/ObsLlmContextServiceTest.kt`: el stub de `topQueries` pasó a 5 matchers por el nuevo parámetro `route`.

## 5. Notas de esquema (MySQL)
- Nueva columna `obs_rum_metric.app_release VARCHAR(120) NULL`. Con `ddl-auto=update` se crea automáticamente; en prod aplicar `ALTER TABLE obs_rum_metric ADD COLUMN app_release VARCHAR(120) NULL;` si el DDL automático está deshabilitado.

## 6. Verificación
- `./mvnw -o test -Dtest='Observability*Test,ObsEndpointDetailServiceTest,ObsReleaseSummaryServiceTest,ObsTrackerWebhookDeleteTest,ObsLlmContextServiceTest'`
- Resultado: `BUILD SUCCESS`, `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`.
