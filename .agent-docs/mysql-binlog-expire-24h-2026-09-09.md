# MySQL: binlog desactivado (VPS)

**Fecha:** 2026-09-09  
**Contenedor:** `mysql8033` (MySQL 8.0.33)

## Decisión

No hay réplica ni PITR con binlogs. El backup operativo es `mysqldump` diario a las 3:30 America/Lima (`backup_ispadmin_to_gdrive.sh`; antes horario). El binlog solo duplicaba escrituras y llenaba el disco.

## Qué se hizo

1. Config persistente: `/opt/gigafiber/mysql/conf.d/disable-binlog.cnf` con `skip-log-bin`.
2. Mount en `docker-compose.yml`: `/opt/gigafiber/mysql/conf.d:/etc/mysql/conf.d:ro` (backup del compose en el VPS).
3. Recreate de `mysql8033`.
4. Borrado de `binlog.*` / `binlog.index` residuales **después** de confirmar `log_bin=OFF`.
5. `RESET PERSIST IF EXISTS binlog_expire_logs_seconds` (ya no aplica).

Antes se había bajado expire a 24 h y purgado; eso quedó superseded por apagar el binlog del todo.

## Resultado (tras desactivar)

| | Tras expire 24 h | Tras `skip-log-bin` |
|--|------------------|---------------------|
| Disco `/` | ~54% (~45 G libres) | **~50% (~49 G libres)** |
| Datadir MySQL | ~31 G | **~27 G** |
| `log_bin` | ON | **OFF** |

## No hacer

- Reactivar binlog sin plan de réplica/PITR y sin `expire` corto.
- Borrar `binlog.*` con `log_bin=ON`.
