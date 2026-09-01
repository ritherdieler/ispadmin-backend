# ACS Wi-Fi GPV automático — 2026-08-31

## Problema

El watcher ACS leía GenieACS cada ~2 min, pero con inform fresco y hojas WLAN con `_timestamp` anterior al inform no persistía (`PARAMETERS_NOT_REFRESHED_FOR_INFORM`). Runs: `written≈0`, `missing` alto. Conteos/RSSI quedaban STALE/MISSING en la UI 360.

## Fix inicial

Cuando el parse falla por params stale:

1. Fuera de la TX del watcher, encolar `getParameterValues` + Connection Request.
2. Cooldown por device (`service_health_cursor` key `acs-gpv:{deviceId}`), default **900 s** (`SERVICE_HEALTH_ACS_GPV_COOLDOWN_SECONDS`).
3. Respeta `crConcurrency` frente a acciones `RUNNING` WIFI_REFRESH/CONFIG/REBOOT_ACS.
4. El siguiente poll lee el cache ya refrescado y persiste si `shouldPersist`.

## Ajuste (mismo día, post 1.0.3+2b15333)

En prod el GPV **sí encolaba**, pero `written` seguía 0. Causa: `gpvPaths` incluía `AssociatedDevice.N` fantasma del cache (índices 2–6 de clientes ya ausentes). Un 9005 en una hoja tumba todo el GPV, incluido `TotalAssociations`. Además `readDeviceCache(projection())` pide 32 estaciones × 2 radios (~30 GET NBI) y dispara `ACS_CACHE_READ_FAILED` / sesiones mezcladas.

Cambio:

- `gpvPaths` → solo `WLANConfiguration.{1,5}.TotalAssociations`
- Watcher lee `countProjection()` (un chunk NBI, sin hojas de estación)
- `opticalFreshSeconds` default **2400** (el collector óptico ~17 min; 600 s marcaba STALE en la 360 con muestras válidas)

## Fase 2 — RSSI por estación (`1..N`)

Tras persistir conteos frescos:

1. Leer `stationProjection(associated2g, associated5g)` (solo índices `1..N`, no 32).
2. Si hay menos estaciones parseadas que el conteo, GPV `AssociatedDevice.1..N` (MAC/RSSI/SNR) con cursor aparte `acs-gpv-sta:{deviceId}` (mismo cooldown 900 s).
3. Persistir estaciones **en el count sample nuevo**. No borrar estaciones si el parse de RSSI vino vacío (evita perder el histórico mientras el GPV termina).
4. Refresh manual (`WIFI_REFRESH`) usa `gpvRefreshPaths`: totales siempre; hojas de estación solo si los conteos del cache ya están alineados con el inform.

## Código

- `WifiTelemetry.gpvPaths` / `countProjection` / `stationProjection` / `gpvStationPaths` / `gpvRefreshPaths`
- `AcsWifiRefreshPlanner` (`acs-gpv:*` y `acs-gpv-sta:*`)
- `AcsTelemetryService.enqueueStaleParameterRefresh`
- `RemoteActionService.refresh`

## Persistencia post-GPV (staging lab, 2026-09-01)

Tras un GPV alineado el upsert nativo escribía la fila, pero `findId…ObservedAt` no la veía: `datetime` sin fsp redondea millis y el catch del watcher marcaba `ACS_CACHE_READ_FAILED` con `written=0`. `HealthSqlTime.timestamp` trunca a segundos; `AcsWifiSampleLookup` cae al último sample del device. Validación: [acs-wifi-lab-staging-2026-08-31.md](./acs-wifi-lab-staging-2026-08-31.md).

## Tests

`AcsWifiRefreshPlannerTest`, `WifiTelemetryTest`, `HealthWiringTest`, `HealthSqlTimeTest`.
