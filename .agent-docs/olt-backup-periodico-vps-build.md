# Construcción — backup periódico OLT → Google Drive (VPS)

Fecha: **2026-07-20**. Patrón alineado al backup Mikrotik del mismo VPS.

## Objetivo

Respaldar diariamente configuración (`.cfg`) y base de datos (`.dat`) de la MA5608T, guardar en disco del VPS y subir a Google Drive. Retención vigente: 12 h local / 14 días remoto (ver [vps-backups-gdrive.md](./vps-backups-gdrive.md); este build histórico mencionaba 3 días).

## Acceso

- VPS `212.85.13.47` → GRE `gre-mk1` (`10.255.255.2/30`) → MK1 `10.255.255.1` → OLT `10.11.104.2:22`
- TFTP destino en la OLT: IP pública del VPS `212.85.13.47:69/udp` → `/tmp/olt-backup`
- Persistencia GRE: `scripts/setup-vps-olt-gre.sh`, unit `vps-olt-gre.service`

## Artefactos en el VPS

| Ruta | Rol |
|------|-----|
| `/opt/gigafiber/scripts/backup_olt_to_gdrive.sh` | Job principal |
| `/opt/gigafiber/scripts/olt-tftp-save-cfg.expect` | `save configuration` |
| `/opt/gigafiber/scripts/olt-tftp-backup-cfg.expect` | `backup configuration tftp` |
| `/opt/gigafiber/scripts/olt-tftp-backup-data.expect` | `save data` + `backup data tftp` |
| `/opt/gigafiber/scripts/setup-vps-olt-gre.sh` | GRE idempotente |
| `/opt/gigafiber/oltBackups/` | Copias locales |
| `/var/log/olt-backup.log` | Log del job |
| `/var/run/olt-backup.lock` | flock anti-solape |

## Cron

```cron
0 0 * * * /opt/gigafiber/scripts/backup_mikrotik_to_gdrive.sh
30 0 * * * /opt/gigafiber/scripts/backup_olt_to_gdrive.sh
```

Zona horaria del VPS: America/Lima.

## Variables opcionales

| Variable | Default | Uso |
|----------|---------|-----|
| `OLT_BACKUP_SAVE_WAIT_SECS` | `180` | Espera tras disparar `save configuration` |
| `OLT_BACKUP_SESSION_WAIT_SECS` | `30` | Pausa entre sesiones SSH |

## Validación 2026-07-20

Corrida manual exitosa:

```
Local files created: ma5608t_2026-07-20_11-45-07.cfg (248360 B), ma5608t_2026-07-20_11-45-07.dat (360343 B)
Uploaded to Google Drive: oltBackups/...
OLT backup job finished
```

Comprobaciones:

```bash
ls -la /opt/gigafiber/oltBackups/ma5608t_*.cfg /opt/gigafiber/oltBackups/ma5608t_*.dat
rclone ls gdrive:oltBackups
tail -50 /var/log/olt-backup.log
```

## Decisiones técnicas

1. **Sesiones separadas** para save-cfg / cfg-TFTP / data: la OLT cierra SSH durante saves y transfers largos.
2. **Éxito por tamaño TFTP** (poll estable), no solo por mensaje CLI: el disconnect deja el UDP en curso.
3. **Sin poll `display clock`** en expect: en prompts incompletos Huawei concatena `displayclock` y cuelga la sesión.
4. **GRE no se recrea** si peer y OLT ya responden (evita falso `unreachable` al inicio del job).
5. **`in.tftpd` no hereda el flock** (`9>&-`) y se mata en `EXIT`. Sin eso, un tftpd huérfano deja `/var/run/olt-backup.lock` tomado y el cron sale con `another OLT backup is running` (incidente 2026-09-02..10).

## Operación manual

```bash
/opt/gigafiber/scripts/backup_olt_to_gdrive.sh
# o con espera más corta tras un save reciente:
OLT_BACKUP_SAVE_WAIT_SECS=120 OLT_BACKUP_SESSION_WAIT_SECS=20 \
  /opt/gigafiber/scripts/backup_olt_to_gdrive.sh
```

No usar `pkill -f backup_olt_to_gdrive` desde una sesión cuyo comando contenga esa cadena (se auto-mata). Preferir:

```bash
ps aux | awk '/\/opt\/gigafiber\/scripts\/backup_olt_to_gdrive\.sh/ && !/awk/ {print $2}' | xargs -r kill
```

## Relacionado

- [olt-ma5608t-backup.md](./olt-ma5608t-backup.md)
- [olt-vps-ssh-nat-build.md](./olt-vps-ssh-nat-build.md)
