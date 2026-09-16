# Eliminar fallbacks SSH inventario/óptica — 2026-09-16

El par deprecado era inventario CLI (`display ont info` / `ParallelOnuInventoryReader`) y óptica CLI (`display ont optical-info` / `captureViaSshDeprecated`), gated por `allow-ssh-inventory-fallback` y `allow-ssh-signal-fallback`.

## Qué se quitó

- Flags `olt.gateway.snmp.allow-ssh-inventory-fallback` / `allow-ssh-signal-fallback` y env `OLT_GATEWAY_SNMP_ALLOW_SSH_FALLBACK` / `OLT_GATEWAY_SNMP_ALLOW_SSH_SIGNAL_FALLBACK`
- `ParallelOnuInventoryReader`, `InventoryJobPlanner`, `OltGponTopologyDiscovery`, `GponBoardClassifier`, `InventoryCliJob`
- Paths SSH de `OltGatewayQueryService.listOnusParsed` / `optical` y `OltSignalPollService.captureViaSshDeprecated`
- Parsers `BoardParser` / `OpticalInfoParser` como dependencias de `OltSignalPollService`

Inventario y óptica de flota: solo SNMP GETBULK. Si SNMP no está habilitado, `snmp_required`.

## Qué se dejó (no es ese par)

- SSH lab on-demand (`lab-optical-ssh-enabled`, `HealthLabOpticalPort.refreshBySn`, `LabOpticalSshPollService`)
- SSH de writes / autofind / by-sn / detalle ONU
- `OpticalInfoParser` para lab y mock
- Enums `CliJobType.INVENTORY` / `SIGNAL_POLL` (tests del bus)

## VPS

Quitadas de `/opt/gigafiber/.env` (backup `.env.bak.20260916161600`):

- `OLT_GATEWAY_SNMP_ALLOW_SSH_FALLBACK`
- `OLT_GATEWAY_SNMP_ALLOW_SSH_SIGNAL_FALLBACK`

Sin recrear Tomcat: ya no hay binding.

## Tests

`OltGatewaySshFallbackDeadSurfaceTest` + suite `:oltgateway:test` (BUILD SUCCESSFUL).
