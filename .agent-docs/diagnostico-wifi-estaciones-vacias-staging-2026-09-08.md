# Diagnóstico: dispositivos Wi-Fi asociados vacíos en staging 360 (2026-09-08)

**Estado:** diagnóstico del hueco **antes** del push WiFi-on-Inform. El camino activo que rellena `acs_wifi_*` es [wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md) (validado staging el mismo día). Conservar esta nota solo como evidencia del problema del stub poll.

## Veredicto (histórico 2026-09-08 mañana)

La UI 360 no mostraba estaciones/conteos porque **Core ya no persistía** `acs_wifi_count_sample` / `acs_wifi_station_sample`. El watcher GenieACS se sustituyó (desacople multi-WAR, `7e799ed`) por un stub `gateway-cpe` que solo actualiza `acs_wifi_status_current` con lastInform vía Gateway→ACS telemetry.

No es un bug de lab-filter ni de la UI: las series leen tablas vacías.

## Camino API (lectura)

```text
Backoffice 360
  GET /subscription/{id}/service-health/series
    → wifi_counts  (acs_wifi_count_sample)
    → wifi_signal  (acs_wifi_station_sample | hourly)
HealthEvidenceReader
  → associated_device_count / wifi_signal desde WifiCurrent + samples
```

Manual refresh: `POST /subscription/{id}/acs/wifi-refresh` → Core → Gateway → ACS `refreshObject(WLANConfiguration)` (no escribe samples).

## Evidencia staging (2026-09-08)

| Señal | Valor |
|-------|--------|
| Labs | `#2392` ZTEGDC47BFFD F6600R; `#2389` VSOL0031C0B6 V2804AX15T; `subscription_acs.lab=1` |
| `acs_wifi_count_sample` | **0 filas** (Auto_increment 3422; Update_time 2026-09-05) |
| `acs_wifi_station_sample` | **0 filas** |
| `acs_wifi_status_current` 2392/2389 | `quality=FRESH`, `associated_device_count=NULL`, `count_sample_id=NULL`, `device_id` = id OLT Gateway |
| `telemetry_source_run` ACS | Último `genieacs`: 2026-09-04 17:34; desde ~2026-09-07 solo `gateway-cpe` (written=2 labs, sin samples) |
| GenieACS ZTE | Inform fresco; `TotalAssociations` 1/5 = **0** (timestamp 2026-09-07) |
| GenieACS VSOL | `TotalAssociations` 1+1 y MACs en caché; **no llegan a Core** |

## Causa raíz (código)

`AcsTelemetryService` actual (~71 líneas): poll scope lab → `HealthCpePort.telemetry(sn)` → guarda solo inform/model en `WifiCurrent`.

Antes (`937362b`): `GenieAcsClient.readDeviceCache` + `WifiTelemetry.parse` + `upsertAtomic` + GPV (`AcsWifiRefreshPlanner`) para `PARAMETERS_NOT_REFRESHED_FOR_INFORM` y estaciones incompletas.

`WifiTelemetry` / planner / upsert siguen en el repo **sin caller de producción**. ACS WAR solo expone telemetry ligera + `wifi-refresh`; no hay collector de estaciones.

## Fix recomendado (no trivial; no re-acoplar Core→GenieACS)

> **Resuelto el mismo día** vía push Inform (ACS last-state → Gateway XADD → Core persist), no vía restaurar el poll GenieACS en Core. Ver [wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md).

1. **ACS WAR**: servicio que lea caché GenieACS (`countProjection` / `stationProjection`), parseé con lógica equivalente a `WifiTelemetry`, encole GPV/CR con cooldown, y exponga HTTP interno (p. ej. batch wifi reading por deviceId/SN).
2. **Core**: `AcsTelemetryService` consuma ese HTTP (como Gateway), haga upsert en tablas Core y rellene `WifiCurrent.associatedDeviceCount` / stations.
3. TDD: RED con test de contrato ACS + test Core que persiste samples al recibir payload; no restaurar `import …acs.genieacs` en service-health (rompe `SubsystemDependencyRulesTest`).
4. Tras deploy ACS+Core: verificar `#2389` (ya tiene asociaciones en GenieACS) y `#2392` (puede necesitar GPV si TotalAssociations sigue en 0).

Workaround operativo temporal (histórico): no había datos en BD; un `wifi-refresh` manual refresca GenieACS pero **no** rellenaba la UI hasta el ingest Inform.
