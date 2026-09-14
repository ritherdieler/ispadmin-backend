# Build — retención backups Drive 10 GiB (2026-09-02)

## Problema

Drive lleno (~15 GiB, ~72 MiB libres). Dumps MySQL horarios (~440–460 MiB) con retención remota de 3 días (~31 GiB teóricos) no cabían. Además, con `set -e`, si `rclone copyto` fallaba por cuota el job salía **antes** del `rclone delete`, así que no se podaba nunca. MySQL en Drive congelado desde ~2026-08-19 (~69 archivos).

## Cambio

| Ítem | Antes | Después |
|------|-------|---------|
| MySQL → Drive | Cada hora, ret. 3 d | Cada **6 h** (00/06/12/18 Lima), ret. **7 d** + budget |
| MK / OLT → Drive | Ret. 3 d | Ret. **14 d** |
| Orden remoto | upload → prune | **prune (edad+budget) → upload** |
| Presupuesto | (ninguno) | **10 GiB** (`rclone about`) + margen 1 GiB |
| Scripts ispadmin/mikrotik | Solo en VPS | Versionados en `scripts/` + `lib/backup_gdrive_common.sh` |
| Secretos scripts | Hardcode en `.sh` | `/opt/gigafiber/scripts/backup.env` |

## Aplicado en VPS

1. Backup de scripts previos en `/opt/gigafiber/scripts/backup-script-bak/`.
2. Creado `backup.env` (chmod 600) con passwords extraídos de los scripts viejos.
3. Desplegados scripts nuevos + `lib/`.
4. Borrados 69 dumps MySQL remotos (`rclone delete … --drive-use-trash=false`).
5. Verificación: `BACKUP_FORCE_UPLOAD=1` → upload OK `ispadmin_2026-09-02_12-28-11.sql.gz` (~442 MiB).
6. Post-check: `rclone about` → Used ~466 MiB, Free ~14.5 GiB; 1 archivo MySQL remoto.

## Archivos

- `scripts/lib/backup_gdrive_common.sh`
- `scripts/backup_ispadmin_to_gdrive.sh`
- `scripts/backup_mikrotik_to_gdrive.sh`
- `scripts/backup_olt_to_gdrive.sh`
- `scripts/backup.env.example`
- `.agent-docs/vps-backups-gdrive.md`

Cron MySQL superseded 2026-09-12: `30 3 * * *` (una vez al día, America/Lima). Ver [mysql-backup-cron-diario-0330-2026-09-12.md](./mysql-backup-cron-diario-0330-2026-09-12.md).
