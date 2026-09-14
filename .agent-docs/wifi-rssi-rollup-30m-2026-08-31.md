# Wi‑Fi RSSI: cadencia 30 min + roll-up horario — 2026-08-31

## Recolección

El watcher ACS sigue midiendo cada ~2 min. Solo encola GPV+CR de estaciones si la última muestra FRESH es más vieja que **1800 s**.

| Propiedad | Default | Env |
|-----------|---------|-----|
| `service.health.acs-wifi-sample-target-seconds` | 1800 | `SERVICE_HEALTH_ACS_WIFI_SAMPLE_TARGET_SECONDS` |
| `service.health.acs-gpv-cooldown-seconds` | 1800 | `SERVICE_HEALTH_ACS_GPV_COOLDOWN_SECONDS` |

Freshness de evidence `associated_device_count` y `wifi_signal`: `target * 2` (~60 min). `last_inform` sigue atado a `periodicInform * 2`.

## Almacenamiento

| Capa | Tabla | Retención |
|------|-------|-----------|
| Raw | `acs_wifi_station_sample` | 14 días, **solo si** hay watermark `HOURLY` |
| Hourly | `acs_wifi_station_hourly` | 90 días |
| Conteos | `acs_wifi_count_sample` | 90 días |

Flyway `V38__wifi_station_hourly.sql`. Roll-up al minuto `:10` (`WifiStationRollupService`) y catch-up en el purge 04:15 Lima. Hora UTC cerrada: `rssi_min/avg/max`, `snr_min/avg`, `sample_count`, último `display_name`.

Sin watermark no se borra raw.

## Series API

`GET /subscription/{id}/service-health/series`

| Ventana | Fuente | `wifi_signal_resolution` | `rssi` |
|---------|--------|--------------------------|--------|
| ≤ 7 días | raw | `raw` | muestra |
| > 7 días | hourly | `hourly` | `rssi_min` |

Props: `station-series-raw-max-days` (7), `station-hourly-retention-days` (90).

## UI 360

Nota bajo el gráfico: «Muestras ACS (~30 min)» o «Horario · RSSI mínimo». Colores por **índice de serie** (paleta de alto contraste), no por hash de `station_key`.

## CR

Si se valida en staging, refrescar el CPE lab (2329) con GPV+CR: `./scripts/deploy.sh --env staging --with servicehealth`.
