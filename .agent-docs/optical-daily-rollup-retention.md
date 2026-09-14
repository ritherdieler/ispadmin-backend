# Óptica diaria — roll-up y retención

## Capas

| Capa | Tabla | Retención | Notas |
|------|-------|-----------|-------|
| Raw | `olt_mgr_onu_optical_sample` | **90 días** | Samples del push SNMP (`onu.optical-batch`) |
| Daily | `olt_mgr_onu_optical_daily` | **730 días** | min/avg/max de Rx, Tx y OLT Rx por ONU y día UTC |

Flyway `V51__optical_daily_rollup.sql`. Watermark layer `OPTICAL_DAILY` en `acs_wifi_aggregation_watermark` (misma tabla que WiFi hourly). Lock cursor `optical-daily-rollup`.

Gateway **sin cambios**: solo last snapshot / batch; el roll-up vive en Core service-health.

## Roll-up

`OpticalDailyRollupService` (espejo de `WifiStationRollupService`):

- Bucket: día UTC cerrado (`ChronoUnit.DAYS`)
- Agregados: `onu_rx_*`, `onu_tx_*`, `olt_rx_*` min/avg/max + `sample_count`
- Schedule `0 25 4 * * *` y catch-up en purge 04:15 Lima
- Chunks de hasta 7 días cerrados

## Series API

`GET /subscription/{id}/service-health/series` y `GET /onu/configured/{externalId}/optical-series`

| Ventana | Fuente | `optical_resolution` | `onu_rx_dbm` |
|---------|--------|----------------------|--------------|
| ≤ 90 días | raw | `raw` | muestra |
| > 90 días | daily | `daily` | `onu_rx_avg` (+ min/max en campos `*_min` / `*_max`) |

Ventana máxima de series/timeline: `opticalDailyRetentionDays` (730).

## Propiedades

| Propiedad | Default | Env |
|-----------|---------|-----|
| `service.health.optical-retention-days` | 90 | `SERVICE_HEALTH_OPTICAL_RETENTION_DAYS` |
| `service.health.optical-daily-retention-days` | 730 | `SERVICE_HEALTH_OPTICAL_DAILY_RETENTION_DAYS` |
| `service.health.optical-series-raw-max-days` | 90 | `SERVICE_HEALTH_OPTICAL_SERIES_RAW_MAX_DAYS` |

Push canónico: [optical-push-gateway-core.md](./optical-push-gateway-core.md).
