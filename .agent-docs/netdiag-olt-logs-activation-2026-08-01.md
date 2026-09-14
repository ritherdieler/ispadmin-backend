# Activación logs OLT en BD — 2026-08-01

## Problema

El backend en `:8080` estaba arrancado **antes** del código de alarmas → sin tabla `net_diag_olt_log_event`, endpoint `/api/netdiag/olt/logs` 404, poll inexistente.

Tras reinicio, `display alarm active all` hacía timeout en `---- More ----` (el espacio no avanzaba la página vía Apache SSHD).

## Fix aplicado

1. Reinicio con `NET_DIAG_ENABLED=true` y alarm poll on.
2. Confirmación CLI `{ <cr>}` en `HuaweiCliPromptDetector` / `HuaweiCliSession`.
3. Paginación `More`: reintento space / `\r` / `f`; stall >12s con contenido ALARM → `q` y dump parcial.
4. Prefacio CLI: `screen-length 0 temporary` + PTY sensible (`setupSensibleDefaultPty`, 512×9999).
5. Timeout comando alarma: 300s.

## Resultado verificado

| Check | Valor |
|-------|-------|
| Tabla | `net_diag_olt_log_event` existe |
| Poll | `persisted=203` en ~7.5 s |
| API | `GET /api/netdiag/olt/logs` → 203 |
| UI | `/noc/olt-logs` |

## Cómo repetir

```bash
OLT_GATEWAY_WRITES_ENABLED=false NET_DIAG_ENABLED=true \
OLT_GATEWAY_SYNC_ALARM_ENABLED=true ./run-dev.sh
```
