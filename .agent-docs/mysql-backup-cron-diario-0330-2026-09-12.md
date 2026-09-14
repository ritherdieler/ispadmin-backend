# Cron MySQL diario 3:30 America/Lima (2026-09-12)

## Por qué

El dump `mysqldump` + gzip del job `backup_ispadmin_to_gdrive.sh` saturaba CPU del VPS al correr **cada hora**. Se dejó **una vez al día** en madrugada (poca carga).

## Cambio (crontab root)

`CRON_TZ=America/Lima` (sin mover de sitio). TZ del sistema: `America/Lima`.

| | Valor |
|--|--------|
| Antes | `0 * * * * /opt/gigafiber/scripts/backup_ispadmin_to_gdrive.sh` |
| Después | `30 3 * * * /opt/gigafiber/scripts/backup_ispadmin_to_gdrive.sh` |

Jobs no tocados: MikroTik `0 0 * * *`, OLT `30 0 * * *`.

No se añadió `flock` / `nice` / `ionice`. No se cambió el password en argv. No había dump en curso al aplicar (2026-09-12 ~18:08 Lima).

## Drive upload

El script **no** se modificó. `is_mysql_drive_upload_hour` sigue subiendo solo en `00/06/12/18` Lima (o `BACKUP_FORCE_UPLOAD=1`). A las **3:30** el dump es **local**; retención local 12 h.

Para forzar subida:

```bash
BACKUP_FORCE_UPLOAD=1 /opt/gigafiber/scripts/backup_ispadmin_to_gdrive.sh
```

## Repo

No hay crontab versionado ni script de install que reinstale `0 * * * *`. Fuente de verdad del horario: crontab root del VPS + este doc / [vps-backups-gdrive.md](./vps-backups-gdrive.md).
