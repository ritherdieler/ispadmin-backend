# Backups VPS → Google Drive

## Jobs (cron root)

| Cron | Script | Destino Drive |
|------|--------|---------------|
| `30 3 * * *` | `/opt/gigafiber/scripts/backup_ispadmin_to_gdrive.sh` | `gdrive:gigafiberDatabaseBackups` |
| `0 0 * * *` | `/opt/gigafiber/scripts/backup_mikrotik_to_gdrive.sh` | `gdrive:mikrotikBackups` |
| `30 0 * * *` | `/opt/gigafiber/scripts/backup_olt_to_gdrive.sh` | `gdrive:oltBackups` |

`CRON_TZ=America/Lima`. El cron MySQL corre **una vez al día a las 3:30** (antes: cada hora `0 * * * *`; cambio 2026-09-12 por carga de CPU). La **subida a Drive** en el script sigue gated a horas `00/06/12/18` (o `BACKUP_FORCE_UPLOAD=1`); a las 3:30 el dump queda **local** salvo override. Detalle: [mysql-backup-cron-diario-0330-2026-09-12.md](./mysql-backup-cron-diario-0330-2026-09-12.md).

Scripts versionados en el repo: `scripts/backup_*_to_gdrive.sh` + `scripts/lib/backup_gdrive_common.sh`. Credenciales en el VPS: `/opt/gigafiber/scripts/backup.env` (plantilla `scripts/backup.env.example`). Si `BACKUP_MIKROTIK_PASSWORD` queda vacío, el cron de MK1 no corre (falló 2026-09-03..10; restaurado 2026-09-10 desde `backup-script-bak/`).

## Retención (2026-09-02)

| Destino | Qué | Retención |
|---------|-----|-----------|
| VPS local | MySQL / MK / OLT | **12 horas** (`find -mmin +720`) |
| Google Drive | MySQL (cada 6 h) | **7 días** por edad + **presupuesto 10 GiB** |
| Google Drive | MikroTik | **14 días** |
| Google Drive | OLT | **14 días** |

Presupuesto duro: `DRIVE_BUDGET_BYTES=10737418240` (10 GiB) medido con `rclone about` (`used`/`free`), margen libre `DRIVE_FREE_MARGIN_BYTES=1 GiB`. Si hace falta espacio, se borran primero los dumps MySQL más antiguos (`ispadmin_*.sql.gz`).

Orden del job (crítico): dump/local prune → **prune remoto (edad + budget)** → `rclone copyto`. Si la subida falla, el prune ya corrió (evita el fallo histórico con `set -e` que dejaba Drive lleno y sin cleanup).

## Papelera de Drive

- En `/root/.config/rclone/rclone.conf`, remote `gdrive`: `use_trash = false`
- Los deletes usan `--drive-use-trash=false`

Vaciar papelera manualmente: `rclone cleanup gdrive:` (asíncrono en Drive).

## Cuota

Cuenta Drive típica: **15 GiB** totales (compartidos con Gmail/Photos). Política operativa de backups: **no superar 10 GiB** de uso (`rclone about`).

Síntoma de cuota llena (histórico): dejan de aparecer líneas `Uploaded to Google Drive` en `/var/log/ispadmin-backup.log` y Drive acumula dumps viejos porque el prune iba *después* del upload.

## Logs

- `/var/log/ispadmin-backup.log`
- `/var/log/mikrotik-backup.log`
- `/var/log/olt-backup.log`

## Operación

Forzar subida MySQL fuera de slot 6 h:

```bash
BACKUP_FORCE_UPLOAD=1 /opt/gigafiber/scripts/backup_ispadmin_to_gdrive.sh
```

Inventario:

```bash
rclone about gdrive:
rclone lsf gdrive:gigafiberDatabaseBackups
rclone size gdrive:gigafiberDatabaseBackups
```
