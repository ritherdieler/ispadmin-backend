# `wifi-inform-core` se vuelve a atascar — 2026-09-18

Canónico: [wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md).

## Síntoma

Tras el carril `wifi-inform-core`, ACS last-state fresco y `WifiCharts` vacío. #2373 (`VSOL00323AF6` / `12345B46415323AF6`): 5 asociados FRESH en `prod_acs.cpe_record`, 0 filas en `acs_wifi_*` de Core.

| Grupo | last-delivered | pending |
|-------|----------------|---------|
| `snapshot-core` | ~07:22Z | 0 |
| `wifi-inform-core` | ~06:21Z | 22 → 49 |

`MAX(inform_at)` de flota se detuvo en `06:21`. Stream `MAXLEN≈20000`.

## Causa

El ACK de `RedisStreamPump` corre **después** del handler. `CpeInformEventConsumer` persistía y luego llamaba `HealthSummaryQueryService.reevaluate`:

- `HealthEvidenceReader.read` pega HTTP a Gateway (`telemetry`), Traffic y NetDiag
- 400–2300 ms (o cuelga si el HTTP no vuelve)
- un hilo, batch 50, `scheduleWithFixedDelay` no vuelve a hacer `XREADGROUP` hasta que `poll()` termina

El carril nuevo evitó que óptica/tráfico bloquearan Inform, pero **copió el mismo `reevaluate` al hilo de Inform**. Flota ~1.7 Inform/s; el consumer no converge; MAXLEN recorta.

`WifiCharts` lee `GET …/series` (`acs_wifi_count_sample` / `acs_wifi_station_sample`). No usa el snapshot 360. El `reevaluate` no aporta al chart y sí atasca el persist.

## Fix

`CpeInformEventConsumer` solo persiste. GET 360 ya recalcula si la foto tiene más de 60 s.

## Tests

`CpeInformEventConsumerTest.persists cpe inform without reevaluating snapshot` (RED → GREEN).
