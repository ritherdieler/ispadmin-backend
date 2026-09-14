# NetDiag — Targets OLT / PON (punto 2)

Construcción: **2026-07-31**.

## Objetivo

Registrar en `net_diag_target` la OLT y los **32 puertos GPON** (`gpon 0|1 / 0–15`) para correlación de alertas por logs/alarmas. El poll MikroTik **no** debe tocar estos targets.

## Comportamiento

| Pieza | Detalle |
|-------|---------|
| `OltNetDiagTargetSyncService` | `ApplicationRunner` al arrancar si `net.diag.enabled=true`; upsert por `name` |
| Target OLT | `OLT-{oltId}` · `monitor_config.kind=olt` · `deviceRefId=olt_mgr_olt.id` |
| Targets PON | `PON-{oltId}-gpon-{board}/{port}` · `kind=pon` · `parentTargetId` = OLT |
| Boards / ports | Boards `0,1` × `olt.gateway.inventory.default-ports-per-gpon-board` (16) |
| Poll filter | `NetDiagPollService` solo polla `kind` ausente o `mikrotik` |

Ejemplo `monitor_config`:

```json
{"kind":"olt","oltId":"gigafiber-ma5608t","mgmtIp":"10.11.104.2"}
{"kind":"pon","oltId":"gigafiber-ma5608t","board":0,"port":5}
```

## Pruebas

```bash
./mvnw -Dtest=NetDiagMonitorConfigSupportTest,OltNetDiagTargetSyncServiceTest,NetDiagPollServiceTest test
```

## Relación con el plan NOC por logs

Cableado alarmas: `OltAlarmPollService` + `OltAlarmIngestService` → `AlertEvaluator` (ver `olt-ma5608t-alarms-syslog.md`).  
Consulta UI/API de todos los eventos: `GET /api/netdiag/olt/logs` / `/noc/olt-logs` (ver `netdiag-olt-logs-api.md`).
