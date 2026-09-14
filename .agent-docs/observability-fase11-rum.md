> Actualización (Integración Fase 1): RUM ganó la dimensión `release`. `ObsRumMetric` tiene la
> columna `app_release`, la ingesta acepta `release` en `RumVitalRequest`, el `BucketKey` incluye
> release y los endpoints `web-vitals`/`web-vitals/timeseries` aceptan `release=`. Ver
> `observabilidad-integracion-fase1-backend.md`.

# Fase 11 — RUM Web Vitals (rendimiento percibido)

Captura de Web Vitals (LCP, INP, CLS, FCP, TTFB) en el SDK web, ingesta por un endpoint
dedicado con agregación por bucket/página/plataforma en el backend de observabilidad,
exposición por API + STOMP en vivo y visualización en el dashboard (`ispadmin-observability-web`)
con umbrales Good/Needs-Improvement/Poor.

## Enfoque

Replica el patrón casero del resto del módulo (colector en memoria por bucket → entidad → repo →
query service → controller), el mismo de APM (Fase 7) e infra/JVM (Fase 14). El SDK web envía
mediciones individuales; el backend acumula por minuto y calcula percentiles + distribución de
rating. Aislamiento total bajo `obs_*` / `observability.*`, sin FKs al dominio y sin migraciones
(Hibernate `ddl-auto=update` crea la tabla al arrancar).

Decisiones:
- **Endpoint dedicado** `POST /observability/rum` (no se reusa `/observability/events`), coherente
  con el pipeline de agregación.
- **Modelo "long"**: una fila por `(bucket, page, platform, metricName)` con percentiles como
  `Double` (soporta ms y el valor fraccional de CLS) + conteos de rating.
- **Rating** calculado en el cliente por la librería `web-vitals`; si no llega, el backend lo deriva
  con los umbrales estándar.

## Componentes

- **Entidad** `entity/ObsRumMetric` (tabla `obs_rum_metric`): `id`, `bucketStart`, `page` (len 300),
  `platform` (len 40), `metricName` (len 16), `sampleCount`, `p50/p75/p95/p99` (Double),
  `min/max/avg` (Double, columnas `min_value`/`max_value`/`avg_value`), `goodCount`,
  `needsImprovementCount`, `poorCount`. Índices en `bucket_start`, `page` y compuesto
  `(bucket_start, page, platform, metric_name)`.
- **Repositorio** `repository/ObsRumMetricRepository`:
  - `findByBucketStartBetweenOrderByBucketStartAsc(from, to)` (serie temporal).
  - `aggregateByPageAndMetric(from, to)` (`@Query` con `GROUP BY page, metricName`,
    `SUM(sampleCount/good/ni/poor)`, `AVG(avg)`, `MAX(p75/p95/p99/max)`).
  - `deleteOlderThan(threshold)` (`@Modifying`, retención).
- **Colector** `service/ObsRumCollector`: `ConcurrentHashMap<BucketKey, Accumulator>` con clave
  `(minuto, page, platform, metricName)`. `record(page, platform, metricName, value, rating)`
  acumula valor (cap `maxSamplesPerBucket`) y rating (resuelto del cliente o derivado por umbral).
  `flush()` vuelca **solo los buckets cerrados** (`minute < currentMinute`), calcula percentiles por
  rango (p50/p75/p95/p99), persiste vía `saveAll` y, si `publishLive`, publica cada fila en vivo.
- **Scheduler** `scheduled/ObsRumScheduler`: `@Scheduled(fixedRate = 60000)` → `collector.flush()`.
- **DTO request** `dto/RumDtos`: `RumIngestBatchRequest(vitals: List<RumVitalRequest>)` y
  `RumVitalRequest(metricName, value, rating?, page, navigationType?, timestamp?)` con
  `@JsonIgnoreProperties(ignoreUnknown = true)`. Respuesta `RumIngestResponse(accepted, rejected)`.
- **Controller ingesta** `controller/ObservabilityRumController` (`@RequestMapping("/observability")`):
  `POST /rum`. Lee la plataforma del atributo `ObservabilityApiKeyFilter.PLATFORM_ATTRIBUTE`, aplica
  `ingestionService.allowRequest(rateKey)`, trunca a `maxEventsPerBatch`, valida `metricName` contra
  `observability.rum.allowed-metrics` (LCP/INP/CLS/FCP/TTFB, normalizado a mayúsculas) y que
  `value`/`page` no sean nulos; los inválidos suman a `rejected`. Responde `202` con el conteo.
- **Query service** `service/ObsRumQueryService`: `timeSeries(from, to, page?, metricName?)` y
  `aggregate(from, to)` (mapeo manual de `List<Array<Any>>`; deriva `goodRate/needsImprovementRate/
  poorRate` y el `rating` global por umbral sobre el p75).
- **DTOs consulta** `dto/MetricDtos`: `RumMetricPointDto` (serie) y `RumMetricAggregateDto` (agregado
  con p75/p95/p99, distribución y rating global).
- **Controller consulta** `controller/ObservabilityMetricController` (`@RequestMapping("/observability/metrics")`):
  - `GET /web-vitals?from&to` → agregado por página+métrica (default última hora).
  - `GET /web-vitals/timeseries?from&to&page?&metric?` → serie temporal por bucket.
- **Live** `service/ObsLivePublisher.publishRumMetric(metric)`: reutiliza el topic
  `/topic/observability/events` con discriminador `"type": "OBS_WEB_VITAL"` y `data` con la fila
  agregada (bucket, page, platform, metricName, percentiles, distribución). El cliente ya escucha ese
  único topic y filtra por `type`.
- **Retención** `scheduled/ObsRetentionScheduler`: purga diaria de `obs_rum_metric` con
  `deleteOlderThan(now.minusDays(retention.rumMetricDays))`.

## Contrato — `POST /observability/rum`

Headers: `X-Obs-Api-Key` (obligatorio), `Content-Type: application/json`.

```json
{
  "vitals": [
    { "metricName": "LCP", "value": 2100, "rating": "good", "page": "/dashboard", "navigationType": "navigate" },
    { "metricName": "CLS", "value": 0.05, "page": "/dashboard" }
  ]
}
```

- `metricName` ∈ {LCP, INP, CLS, FCP, TTFB} (case-insensitive). Otros valores se ignoran (cuentan como `rejected`).
- `rating` opcional (`good` | `needs-improvement` | `poor`); si falta, se deriva por umbral.
- Respuesta `202 Accepted`: `{ "accepted": n, "rejected": m }`.
- `401` si falta/es inválida la API key; `429` si se excede el rate limit; `503` si el módulo o
  `observability.rum.enabled` están apagados.

Umbrales estándar (good ≤ / poor >): LCP 2500/4000 ms, INP 200/500 ms, CLS 0.1/0.25,
FCP 1800/3000 ms, TTFB 800/1800 ms.

## Configuración por entorno

- `ObservabilityProperties.RumProperties` accesible como `observability.rum.*`:
  `enabled=true`, `maxSamplesPerBucket=4000`, `publishLive=true`,
  `allowedMetrics=[LCP, INP, CLS, FCP, TTFB]`.
- `RetentionProperties.rumMetricDays = 7` (`observability.retention.rum-metric-days`).
- `application.properties`: defaults `observability.rum.*` y `observability.retention.rum-metric-days`.
- `application-dev.properties`: habilitado.
- `application-prod.properties`: por variables `OBS_RUM_ENABLED`, `OBS_RUM_PUBLISH_LIVE`,
  `OBS_RUM_RETENTION_DAYS`; documentadas en `scripts/docker/env.prod.example`.

## Validación de integración (2026-07-12)

Backend reiniciado con `./mvnw spring-boot:run` (context-path `/ispadmin`, `ddl-auto=update` crea
`obs_rum_metric` al arrancar). Resultados:

- **Ingesta válida** (`POST /observability/rum`, key `dev-obs-android-key`, LCP/INP/CLS/FCP/TTFB +
  un LCP "poor", page `/dashboard`): `HTTP 202`, `{"accepted":6,"rejected":0}`.
- **Ingesta con `metricName` inválido** (`BOGUS` + un `LCP` válido): `HTTP 202`,
  `{"accepted":1,"rejected":1}` — el inválido se descarta sin romper el lote.
- **Sin API key**: `HTTP 401`.
- **Persistencia + consulta**: tras el flush (cada 60s sobre buckets del minuto anterior),
  `GET /observability/metrics/web-vitals?from&to` devuelve filas por página+métrica con `p75`,
  distribución `good/needs-improvement/poor` y `rating` global. `web-vitals/timeseries` devuelve la
  serie por bucket.
- **Live STOMP**: un cliente suscrito a `/topic/observability/events` recibe, en el flush, mensajes
  con `"type":"OBS_WEB_VITAL"` (junto a los `OBS_SYSTEM_METRIC` de la Fase 14).

## Notas

- El flush persiste únicamente buckets cerrados (`minute < currentMinute`), así que hay una latencia
  de hasta ~2 min entre la ingesta y su aparición en la consulta/dashboard.
- Los percentiles se calculan sobre las muestras retenidas por bucket (cap `maxSamplesPerBucket`).
- El agregado deriva `p75/p95/p99/max` como `MAX` entre buckets del rango (no recomputa percentiles
  globales) y el `rating` global se decide por el p75, coherente con el criterio estándar de RUM.
