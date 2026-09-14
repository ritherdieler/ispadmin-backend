# Bandwidth intelligence — aligerar queries (2026-08-31)

## Síntoma

En prod, `/bandwidth-intelligence` (pestaña Red) mostraba `timeout of 10000ms exceeded`.  
Medición autenticada (antes del cambio):

| Endpoint | Rango | Resolución | Tiempo |
|----------|-------|------------|--------|
| `GET /traffic/bandwidth/v1/overview` | 24h | `5m` | ~8.6 s |
| `GET /traffic/bandwidth/v1/series` | 24h | `5m` | ~7.0 s |

El front llama ambos en paralelo con Axios timeout global de 10 s → timeout aunque el backend responda 200.

## Anti-patrones detectados (no N+1 clásico de lazy load)

`findForTrafficPolling()` ya hace `JOIN FETCH` de `hostDevice` y `plan` (sin N+1 ORM).

Los cuellos reales eran **cargar todo el rango y filtrar/agregar en memoria**:

1. **`metricRows` red**: `findInBucketRange(from, to)` traía *todas* las filas 5m/1h/1d del período (todas las suscripciones) y luego `.filter { subscriptionId in ids }`. Con cientos de clientes × ~288 buckets/24h = cientos de miles de entidades hidratadas **dos veces** (overview + series).
2. **`overview` anomalías**: `findByEventStatusOrderByStartedAtDesc(OPEN)` + `count` en Kotlin.
3. **`anomalyPage`**: `findAll()` + filtros en memoria.
4. **Filtro router en 5m**: existía índice `idx_traffic_5m_router_bucket` pero no se usaba; el filtro iba por IDs de suscripción tras cargar todo.

## Cambio

| Antes | Después |
|-------|---------|
| Hidratar filas + `groupBy` en JVM | `GROUP BY bucket_start` en SQL (`aggregateNetworkBuckets*`) |
| Overview recalculaba sobre filas crudas | Overview deriva de puntos de red agregados + `COUNT(DISTINCT subscription_id)` |
| Anomalías abiertas: load + count | `countByEventStatus` / `countOpenForSubscriptions` |
| Anomaly page: `findAll` | `findFiltered(...)` |
| Ranking clientes: `findInBucketRange` global | `findInBucketRangeForSubscriptions(ids)` |

Archivos:

- `BandwidthIntelligenceService.kt`
- `SubscriptionTrafficRepositories.kt` (proyecciones + queries)
- Tests: `BandwidthIntelligenceServiceTest.kt`

## Semántica overview (red)

P95 / pico / avg de overview de red pasan a calcularse sobre **totales de red por bucket** (misma base que el chart de `series`), no sobre cada fila suscripción×bucket. Alineado con la UI “Demanda observada”.

## Verificación

```bash
./mvnw -Dtest=BandwidthIntelligenceServiceTest,TrafficAnomalyServiceTest,SubscriptionTrafficRollupServiceTest test
```

Tras deploy, revalidar latencia de overview/series 24h en prod (objetivo: muy por debajo de 10 s).

## Optimización de carga de la pestaña Red (2026-09-01)

La pestaña Red ya no solicita `overview` y `series` por separado. El endpoint
`GET /traffic/bandwidth/v1/network` calcula la serie una sola vez y devuelve
ambos bloques en una respuesta.

La resolución automática de red usa la capa consolidada adecuada al período:

- hasta 7 días: `1h` (tabla `subscription_traffic_hourly`),
- más de 7 días: `1d` (tabla `subscription_traffic_daily`).

Las resoluciones explícitas (`1m`, `5m`, `1h`, `1d`) siguen soportadas para
detalle y compatibilidad. El backoffice cancela la consulta anterior cuando
cambia el rango y bloquea **Actualizar** mientras hay una carga en curso, para
evitar solicitudes obsoletas o duplicadas.

No se añadió un índice nuevo: las tablas horaria y diaria ya tienen índices por
`bucket_start` y `(subscription_id, bucket_start)`, que son los predicados de
estas consultas agregadas. La decisión de indexación debe revisarse con un
`EXPLAIN ANALYZE` después de medir la carga real de producción.

El ranking de Clientes usa `5m` automáticamente para períodos de hasta 48 horas,
`1h` hasta 7 días y `1d` en rangos mayores, evitando consultas de ventana sobre
la tabla cruda de 1 minuto y cargas grandes de entidades. El detalle de una
suscripción conserva su resolución fina independiente.

## Deploy

Prod 2026-08-31: release `1.0.3+8e46ea4` — [deploy-prod-bandwidth-query-perf-2026-08-31.md](./deploy-prod-bandwidth-query-perf-2026-08-31.md).
