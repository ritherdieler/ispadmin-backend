# Analítica de consumo de ancho de banda — as-built (Documento 1)

Estado: **implementado y en producción**. El collector corre con poll de 1 minuto. No diagnostica causa técnica.

Spec origen: `01_Especificacion_Analitica_Consumo_Ancho_Banda_GigaFiber.docx`.  
Consumidor: [diagnóstico técnico convergente (Documento 2)](./03-implementacion-diagnostico-tecnico-convergente.md).  
Retención/capas: [traffic-recoleccion-politica.md](./traffic-recoleccion-politica.md).  
Histórico previo (números de poll 5 min obsoletos): [subscription-traffic-historico-2026-08-27.md](./subscription-traffic-historico-2026-08-27.md).

## Principio que se conservó

La analítica de tráfico es un dominio autónomo. Publica observaciones, agregados, salud del collector y anomalías **no causales**. El diagnóstico (Documento 2) las consulta; no hay un segundo collector de queues ni un P95 paralelo.

## Dónde está el código

| Capa | Ubicación |
|------|-----------|
| Backend | `src/main/kotlin/com/dscorp/wispadmin/traffic/` |
| Poll | `SubscriptionTrafficPollService` + `SubscriptionTrafficPollScheduler` |
| Rollups | `SubscriptionTrafficRollupService`, `TrafficAggregationJobService` |
| Anomalías | `TrafficAnomalyService` (`traffic-rules-v1`) |
| API red/capacidad | `BandwidthIntelligenceController` / BFF core → `/traffic/bandwidth/v1/*` (canónico WAR: `/api/traffic/v1/*`) |
| API cliente | `SubscriptionTrafficController` / BFF core → `/subscription/{id}/traffic*` |
| Desacople WAR | [traffic-war-desacople-2026-09-03.md](./traffic-war-desacople-2026-09-03.md) |
| Live (no histórico) | `SubscriptionTrafficWebSocket` STOMP `/subscription-traffic/start`; tick cada 2 s; **no se persiste** |
| Backoffice red | `/bandwidth-intelligence` (`BandwidthIntelligencePage`: Red / Clientes / Collectors / Anomalías) |
| Backoffice perfil largo | `/traffic-analytics` (hora pico 3/6/12 meses; se conservó) |
| Backoffice cliente | `SubscriptionTrafficPanel` en detalle de suscripción y embebido en la vista 360 |

## Contrato spec → tablas reales

| Nombre v1.0 (Word) | Implementación |
|--------------------|----------------|
| `traffic_observation` | `subscription_traffic_sample` (`bucket_start`, `collected_at`, `interval_seconds`, `sample_status`, `source_run_id`, `queue_id`, plan Mbps, deltas, avg Mbps) |
| `traffic_aggregate` | `subscription_traffic_five_minute` / `_hourly` / `_daily` / `_monthly` |
| `traffic_anomaly_event` | `traffic_anomaly_event` (`dedupe_key`, `confidence`, `coverage_pct`, `evidence_json`, `rule_version`) |
| `traffic_source_health` | `traffic_source_run` + `GET /traffic/bandwidth/v1/sources` |
| `sampled_at` | No existe; usar `bucket_start` + `collected_at` |

Clave histórica: `subscription_id` + `bucket_start` (`uk_traffic_sample_sub_bucket`). IP, queue y router van como atributos de la muestra / `host_device_id`.

## Recolección

- Una sesión REST por router (`/queue/simple` en batch). Nunca un GET por cliente desde este dominio ni desde diagnóstico.
- Cadencia: `traffic.poll.interval-ms=60000`, `bucket-minutes=1`, hasta 3 routers en paralelo.
- Delta con intervalo **real** entre observaciones válidas. Primer contador = `BASELINE` (no inventa bytes). Contador menor o uptime reiniciado = `RESET` (no delta negativo). Queue ausente = `MISSING` (no se convierte en cero). Contador inválido = `INVALID`. Intervalo > 3× bucket = `STALE`.
- Un cliente inválido no aborta el router: el run cuenta `expected`, `matched`, `written`, `missing`, `invalid`.
- Live WebSocket lee rate de queue para UI; no escribe filas RAW.

`TrafficSampleStatus`: `OK`, `BASELINE`, `MISSING`, `RESET`, `INVALID`, `STALE`, `UNSUPPORTED`.

## Retención (objetivo del Word, vigente)

| Capa | Resolución | Retención | Job |
|------|------------|-----------|-----|
| RAW | 1 min | 72 h | poll 60 s |
| Corto | 5 min | 30 d | post-poll + catch-up |
| Histórico | 1 h | 24 meses (730 d) | catch-up + cron `5 0 * * * *` America/Lima |
| Largo | 1 d | 5 años (1825 d) | cron `0 30 3 * * *` America/Lima |

Purga solo si la capa hija ya consolidó el watermark. Catch-up autenticado: `POST /traffic/aggregation/catch-up`.

## Métricas en rollup

avg/max/P95 RX/TX, bytes, `sample_count` / `expected_sample_count`, `coverage_pct`, `utilization_*` contra plan, `seconds_over_80/90/95`. Overview de red añade `growthPct`. Calidad de serie: `GOOD` (≥90 % cobertura), `PARTIAL` (≥80 %), `POOR`.

Resolución automática (`resolution=auto`):

| Rango | Cliente | Red |
|-------|---------|-----|
| ≤ 24 h | `1m` | `5m` |
| ≤ 48 h | `5m` | `5m` |
| ≤ 90 d | `1h` | `1h` |
| más | `1d` | `1d` |

## Anomalías (hechos, no causa)

Motor: `TrafficAnomalyService`. Abre/cierra de forma idempotente (`OPEN`/`CLOSED`). No emite etiquetas GPON/Wi-Fi/router.

| Tipo | Regla base |
|------|------------|
| `TRAFFIC_MISSING` | Cobertura del último `traffic_source_run` del router menor que `traffic.anomaly.minimum-coverage-pct` (80) |
| `NO_TRAFFIC` | Última hora con bytes 0, cobertura ≥ 90 % y historial previo con tráfico |
| `PLAN_SATURATION` | Cobertura ≥ 80 % y (utilización ≥ 80 % **o** ≥ 900 s sobre 80 %) |
| `TRAFFIC_SPIKE` / `TRAFFIC_DROP` | Mediana/MAD misma hora, ≥ 7 puntos de baseline |
| `PATTERN_DEVIATION` | Mediana/MAD mismo weekday sobre diarios, ≥ 4 puntos |

El diagnóstico **solo consume `PLAN_SATURATION`** como `diagnosis_code`. El resto queda en `/bandwidth-intelligence` y como síntoma/frescura (`TELEMETRY_GAP` si el collector de tráfico está stale/error).

Evaluación: el scheduler reutiliza `traffic.poll.interval-ms` (hoy 60 s, no 300 s del Word).

## APIs

Prefijo de contexto `/ispadmin`.

**Inteligencia de red** (`/traffic/bandwidth/v1`):

- `GET /network|overview|series|subscriptions|subscriptions/{id}|sources|anomalies`
- Filtros: `from`, `to`, `resolution`, `routerId`, `planId`, `search`, `sort`
- Performance: el endpoint combinado `/network` evita recalcular la serie para `overview` y la resolución automática usa tablas horaria/diaria para rangos amplios; ver [bandwidth-intelligence-query-perf-2026-08-31.md](./bandwidth-intelligence-query-perf-2026-08-31.md)

**Cliente**:

- `GET /subscription/{id}/traffic` (`granularity=sample\|hourly\|daily`)
- `GET /subscription/{id}/traffic/latest|today|summary|day`

**Ops**:

- `POST /traffic/poll`
- `POST /traffic/aggregation/catch-up`

## Configuración vigente

```properties
traffic.poll.enabled=true
traffic.poll.interval-ms=60000
traffic.poll.bucket-minutes=1
traffic.poll.max-parallel-routers=3
traffic.retention.raw-days=3
traffic.retention.five-minute-days=30
traffic.retention.hourly-days=730
traffic.retention.daily-days=1825
traffic.anomaly.enabled=true
traffic.anomaly.minimum-coverage-pct=80
traffic.anomaly.saturation-pct=80
traffic.anomaly.rule-version=traffic-rules-v1
```

No hay keys secretas propias de este módulo.

## Fases del Word vs código

| Fase | Entrega | Estado |
|------|---------|--------|
| 0 | Gap / inventario | Hecho (docs previos + este as-built) |
| 1 | Calidad de muestra | Hecho (`sample_status`, intervalo real, unique key, source run) |
| 2 | 1m/5m/1h/1d + purge | Hecho |
| 3 | Avg, P95, utilización, cobertura | Hecho |
| 4 | Anomalías no causales + contrato | Hecho; Documento 2 consume `PLAN_SATURATION` |
| 5 | Escala 2 000 / rankings paginados | Parcial: ranking/filtros router+plan+paginación sí; prueba de carga 2 000 no documentada aquí. Sin particionado MySQL. Filtro PON/sector no existe (solo `routerId` / `planId`). |

## Huecos deliberados o pendientes

- Timestamps de tráfico son `LocalDateTime` (zona JDBC / America/Lima), no UTC puro del Word.
- No se particionan tablas; hay índices por `subscription_id` + tiempo.
- `/traffic-analytics` (perfil 3/6/12 meses) convive con `/bandwidth-intelligence`; no se eliminó.
- Android y observability-web no consumen este contrato.
