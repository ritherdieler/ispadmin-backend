# Purga datos ingest NetDiag NOC — producción (2026-08-01)

## Objetivo

Vaciar incidentes, logs de ingest (OLT/syslog/traps), historial de probes y datos derivados del NOC, **sin** borrar la configuración de monitoreo (`net_diag_target`).

## Script

`scripts/sql/netdiag-purge-ingest-data.sql`

Tablas afectadas (DELETE):

- `net_diag_incident_event`
- `net_diag_notification_log`
- `net_diag_alert_decision`
- `net_diag_incident`
- `net_diag_trap_event`
- `net_diag_olt_log_event`
- `net_diag_probe_run`
- `net_diag_audit_log`
- `net_diag_maintenance_window`

**Conservado:** `net_diag_target` (35 filas tras la operación).

## Ejecución en prod

Contenedor MySQL: `mysql8033`, base `ispadmin`. Vía SSH al VPS (ver `vps-mysql-access.md`):

```bash
cd ispadmin-backend
source scripts/deploy.config.local
# copiar SQL y ejecutar con docker exec mysql8033 mysql ...
```

## Conteos antes / después (2026-08-01)

| Métrica | Antes | Después |
|---------|-------|---------|
| `net_diag_incident` | 424 | 0 |
| `net_diag_olt_log_event` | 27 029 | 0 |
| `net_diag_probe_run` | 521 | 0 |
| `net_diag_target` | — | 35 (sin cambio) |

Verificación API (inmediata tras purga): `GET /api/netdiag/incidents/summary` → **0 / 0 / 0**; `GET /api/netdiag/olt/logs` → `totalElements: 0`.

**Re-ingesta:** en la primera purga, el poll/alertas en vivo recreó ~11 incidentes en ~15 s. Se ejecutó una segunda purga; a los 3 s el summary seguía en cero. Si la red sigue en fallo, volverán a aparecer alertas en el siguiente ciclo de poll (orden de minutos).

## Nota operativa

Tras la purga, el poll y los ingest volverán a generar incidentes y logs si las condiciones de red/OLT siguen activas. Para un NOC “en silencio” temporal, usar ventanas de mantenimiento o deshabilitar targets (`enabled=false`), no solo borrar histórico.
