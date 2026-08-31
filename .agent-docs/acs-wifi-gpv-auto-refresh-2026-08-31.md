# ACS Wi-Fi GPV automático — 2026-08-31

## Problema

El watcher ACS leía GenieACS cada ~2 min, pero con inform fresco y hojas WLAN con `_timestamp` anterior al inform no persistía (`PARAMETERS_NOT_REFRESHED_FOR_INFORM`). Runs: `written≈0`, `missing` alto. Conteos/RSSI quedaban STALE/MISSING en la UI 360.

## Fix

Cuando el parse falla por params stale:

1. Fuera de la TX del watcher, encolar `getParameterValues` + Connection Request (mismas paths que el refresh manual).
2. Cooldown por device (`service_health_cursor` key `acs-gpv:{deviceId}`), default **900 s** (`SERVICE_HEALTH_ACS_GPV_COOLDOWN_SECONDS`).
3. Respeta `crConcurrency` frente a acciones `RUNNING` WIFI_REFRESH/CONFIG/REBOOT_ACS.
4. El siguiente poll lee el cache ya refrescado y persiste si `shouldPersist`.

## Código

- `WifiTelemetry.gpvPaths` (compartido con `RemoteActionService.refresh`)
- `AcsWifiRefreshPlanner`
- `AcsTelemetryService.enqueueStaleParameterRefresh`

## Tests

`AcsWifiRefreshPlannerTest`, `WifiTelemetryTest`, `HealthWiringTest`.
