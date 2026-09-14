# Política de recolección de ancho de banda

Estado: **CUMPLE** secciones 4, 15, 16 (lag/backlog), 17 (resolución por rango) y 25 de la especificación analítica GigaFiber.

As-built del Documento 1 (arquitectura, anomalías, APIs): [01-implementacion-analitica-consumo-ancho-banda.md](./01-implementacion-analitica-consumo-ancho-banda.md).

## Capas

| Capa | Resolución | Retención | Fuente | Job |
|------|------------|-----------|--------|-----|
| RAW | 1 minuto | 72 h (`traffic.retention.raw-days=3`) | MikroTik `/queue/simple` | `SubscriptionTrafficPollScheduler` cada 60 s |
| 5 min | 5 minutos | 30 d | RAW | Post-poll + cada 5 min (`TrafficAggregationJobService.catchUpFiveMinute`) |
| 1 h | 1 hora | 24 meses (730 d) | 5 min | Cada 5 min + cron `5 0 * * * *` America/Lima |
| 1 d | 1 día | 5 años (1825 d) | 1 h | Cron `0 30 3 * * *` America/Lima |

## Configuración

- `traffic.poll.interval-ms=60000`
- `traffic.poll.bucket-minutes=1`
- `traffic.poll.initial-delay-ms=30000`
- `traffic.aggregation.catch-up-chunk-hours=1` (chunks de 1 h; el rollup hace `saveAll` por lote para no hacer N+1)
- Cutover 5m→1m: watermark `ONE_MINUTE_SINCE` (primera ejecución con `bucket-minutes=1`). Buckets anteriores usan `expected_sample_count=1`.

## Backfill / catch-up

Tras arrancar, el scheduler y el hook post-poll avanzan watermarks. Si el primer backfill histórico es grande, `POST /traffic/aggregation/catch-up` (autenticado) o esperar al job cada 5 min. El watermark `FIVE_MINUTE` puede ir por detrás unas horas hasta ponerse al día; Red 24h solo muestra buckets ya consolidados en `subscription_traffic_five_minute`.

## Watermarks y purga

Tablas: `traffic_aggregation_watermark`, `traffic_aggregation_run`.

Purga solo borra datos con edad vencida **y** `bucket_start` / fecha ≤ watermark de la capa hija (RAW→5m, 5m→1h, 1h→1d). Si el watermark no avanzó, esa capa no se purga.

## Observabilidad

`GET /traffic/bandwidth/v1/sources` incluye `aggregation[]` con `layer`, `consolidatedThrough`, `lagSeconds`.

Backfill manual: `POST /traffic/aggregation/catch-up`.

## API por rango

- Red 24h → resolución `5m`
- Detalle cliente 24h → `1m` (RAW)
- 7d/30d → `1h`
- 90d → `1d`
