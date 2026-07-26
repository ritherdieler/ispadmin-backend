# NetDiag Fase 1.5 — Construcción ROS7 avanzada

Construcción del plan `netdiag_fases_mk1` — entrega ROS7 (netwatch, optical DOM, SNMP traps, syslog).

## Entregables

| Pieza | Archivos clave |
|---|---|
| Netwatch | `MikrotikNetwatchAdapter.kt` |
| Optical DOM | `MikrotikOpticalAdapter.kt` + `MikrotikSession.call` |
| SNMP | `NetDiagSnmpTrapIngestService.kt`, `NetDiagTrapEvent`, UDP listener |
| Syslog | `SyslogIngestAdapter.kt`, UDP listener |
| Correlación | `CorrelationEngine` suprime hijos bajo `UPSTREAM_PROBE_FAIL` |
| LLM | snapshots health/netwatch/optical + traps recientes |
| Docs | `netdiag-mikrotik-ros7.md`, `netdiag-mikrotik-seed.rsc`, runbooks actualizados |

## Gate

- Suite unitaria mock verde (sin red a MK1)
- Controllers devuelven DTOs
- Seed `.rsc` solo documentado (sin auto-apply)

## Fuera de alcance

- R4–R5 (wispadmin `adapter=rest`)
- Auto-aplicar netwatch/SNMP en routers prod
