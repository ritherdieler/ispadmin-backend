# NetDiag — Mikrotik RouterOS 7 (REST)

**Última actualización:** 2026-08-01

## Optical en `probe_run`

Flujo: `NetDiagPollService` → `MikrotikPollAdapter.collectSnapshot` → `MikrotikOpticalAdapter.collect` → REST `POST /rest/interface/ethernet/monitor` con cuerpo `{"numbers":"<iface>","once":""}`.

Campos mapeados desde la respuesta JSON (claves con guiones, valores string):

| RouterOS | `OpticalSnapshot` |
|----------|-------------------|
| `name` | `interfaceName` |
| `sfp-rx-power` / `rx-power` | `rxPowerDbm` |
| `sfp-tx-power` / `tx-power` | `txPowerDbm` |
| `sfp-temperature` / `temperature` | `temperatureC` |
| `sfp-module-present` / `sfp-present` | `sfpPresent` |
| `sfp-connector-type` | `sfpConnectorType` |
| (derivado) | `opticalDdmAvailable` |

## DAC / cobre sin DDM (MK2)

En MK2 (`38.224.231.4`), `sfp-sfpplus1`–`3` usan **FT-SFP-DAC1M** (`sfp-connector-type=copper-pigtail`). RouterOS reporta `sfp-module-present=true` pero **no** incluye `sfp-rx-power`, `sfp-tx-power` ni `sfp-temperature` (no hay DDM óptico en DAC). Los `null` en `probe_run` son esperados, no fallo de parseo.

`opticalDdmAvailable=false` evita alertas `OPTICAL_*` falsas en esos puertos.

## Comandos de verificación (MK2)

CLI:

```
/interface ethernet monitor sfp-sfpplus1 once
```

REST (equivalente al poll):

```bash
curl -sk -u 'USER:PASS' -H 'Content-Type: application/json' \
  -X POST 'https://38.224.231.4/rest/interface/ethernet/monitor' \
  -d '{"numbers":"sfp-sfpplus1","once":""}'
```

Potencias ópticas solo aparecen en transceivers **de fibra** con DDM; no en DAC/cobre.
