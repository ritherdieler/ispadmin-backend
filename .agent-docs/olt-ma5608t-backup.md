# Backup nativo — Huawei MA5608T (10.11.104.2)

Procedimiento validado **2026-07-19** para respaldar configuración y base de datos usando los comandos propios de la OLT (no `display current-configuration`).

## Archivos generados (último backup exitoso)

| Archivo | Tamaño | Contenido |
|---------|--------|-----------|
| `backups/olt/ma5608t-20260719-210255.cfg` | 247 899 B | Configuración Huawei (texto, restore con `load configuration`) |
| `backups/olt/ma5608t-20260719-210556.dat` | 360 206 B | Base de datos OLT (binario TTComp, restore con `load data`) |

**No commitear** estos archivos: contienen VLANs, passwords hash y datos de clientes.

## Flujo oficial Huawei

1. `save configuration` — persiste config en flash (`cfm.efs`). Tarda minutos; la OLT responde `System is busy` hasta terminar.
2. `save data` — persiste DB en flash (`data.dat`).
3. `backup configuration tftp {IP} {nombre.cfg}` — exporta config al servidor TFTP.
4. `backup data tftp {IP} {nombre.dat}` — exporta DB al servidor TFTP.

Modos alternativos: `ftp`, `sftp`. **XMODEM por SSH no funciona** en esta OLT:

> Failure: Executing Xmodem loading or backup command on telnet terminal or serial port terminal of the standby board is prohibited

## Infraestructura usada

| Componente | Valor |
|------------|-------|
| OLT | `10.11.104.2`, usuario `oltadmin` |
| Servidor TFTP | VPS `212.85.13.47`, directorio `/tmp/olt-backup` |
| Daemon | `in.tftpd --listen --create --secure /tmp/olt-backup` |
| Firewall VPS | `ufw allow 69/udp` (obligatorio) |
| SSH desde VPS | GRE → `oltadmin@10.11.104.2:22` (MK1 solo enruta) |

La OLT **alcanza** el VPS (ping ~85 ms). No alcanza la IP local del Mac (`192.168.1.4`).

Desde el VPS, tras GRE (`vps-olt-gre.service`):

- Host SSH: `10.11.104.2`
- Puerto: `22`
- Usuario: `oltadmin`
- Scripts: `setup-vps-olt-gre.sh`, `mikrotik-mk1-olt-vps-gre.rsc`
- Detalle: [olt-vps-ssh-nat-build.md](./olt-vps-ssh-nat-build.md)

## Backup periódico en VPS (producción)

Validado **2026-07-20** desde `212.85.13.47` vía GRE a `10.11.104.2`.

| Ítem | Valor |
|------|-------|
| Cron | `30 0 * * *` (America/Lima), tras Mikrotik `0 0 * * *` |
| Script | `/opt/gigafiber/scripts/backup_olt_to_gdrive.sh` |
| Local | `/opt/gigafiber/oltBackups/` |
| Log | `/var/log/olt-backup.log` |
| Drive | `gdrive:oltBackups` (rclone) |
| Retención | 12 h local; 14 días remoto (+ presupuesto Drive 10 GiB) |
| GRE | `setup-vps-olt-gre.sh` + `vps-olt-gre.service` (idempotente si ya está sano) |

Flujo del job:

1. Asegurar GRE / reachability OLT (reintentos).
2. `olt-tftp-save-cfg.expect` → `save configuration` (tolera disconnect).
3. Espera `OLT_BACKUP_SAVE_WAIT_SECS` (default 180).
4. `olt-tftp-backup-cfg.expect` → `backup configuration tftp`.
5. Poll TFTP hasta `.cfg` estable (≥50 KB).
6. `olt-tftp-backup-data.expect` → `save data` + `backup data tftp`.
7. Poll TFTP hasta `.dat` estable (≥100 KB).
8. Copiar a `oltBackups/` y `rclone copyto` a Drive.

Última corrida exitosa:

| Archivo | Tamaño |
|---------|--------|
| `ma5608t_2026-07-20_11-45-07.cfg` | 248 360 B |
| `ma5608t_2026-07-20_11-45-07.dat` | 360 343 B |

Detalle de construcción: [olt-backup-periodico-vps-build.md](./olt-backup-periodico-vps-build.md).

## Script manual (desde repo)

```bash
export OLT_GATEWAY_PASSWORD='...'
# DEPLOY_SSH_PASSWORD se lee de scripts/deploy.config.local si existe
./scripts/olt-backup.sh
```

Scripts expect (VPS / job):

- `scripts/olt-tftp-save-cfg.expect` — solo dispara `save configuration`
- `scripts/olt-tftp-backup-cfg.expect` — `backup configuration tftp`
- `scripts/olt-tftp-backup-data.expect` — `save data` + `backup data tftp`

## Restore (referencia)

En modo privilegiado, con TFTP/FTP accesible:

```
load configuration tftp {IP} {archivo.cfg}
load data tftp {IP} {archivo.dat}
```

Confirmar prompts. Reinicio puede ser necesario según versión.

## Lecciones / errores frecuentes

| Síntoma | Causa | Solución |
|---------|-------|----------|
| `Failed to transfer the file` | UDP 69 bloqueado en VPS | `ufw allow 69/udp` |
| TFTP sin archivo en servidor | `in.tftpd` sin `--create` | Reiniciar con `--create` |
| `System is busy` | `save` anterior en curso | Esperar; sondear con `display file cfm.efs` |
| Sesión SSH cerrada durante backup | Timeout consola OLT (~60–90 s inactivo) o OLT cierra SSH tras save/TFTP | Normal en este equipo; el job espera y confirma por tamaño TFTP |
| Expect cuelga con `displayclock` | Poll `display clock` sobre prompt incompleto Huawei | No enviar comandos durante espera TFTP; solo expect success/eof |
| `OLT unreachable (GRE/route?)` | `setup-vps-olt-gre.sh` recreaba el túnel y rompía ping un instante | Script GRE idempotente si peer+OLT responden |
| Cron `another OLT backup is running` todos los días | `in.tftpd` heredaba fd 9 del `flock` y dejaba el lock vivo al salir el job | Lanzar tftpd con `9>&-` y `pkill` tftpd al `EXIT` (solo el dueño del lock) |
| Dump CLI truncado | Paginación `-- More --` | No usar como backup; usar TFTP nativo |

## Relacionado

- [olt-backup-periodico-vps-build.md](./olt-backup-periodico-vps-build.md) — job cron VPS + Drive
- [olt-vps-ssh-nat-build.md](./olt-vps-ssh-nat-build.md) — GRE VPS↔MK1
- [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md) — filas `save` / `backup` / `load`
- [olt-ma5608t-gpon-guide.md](./olt-ma5608t-gpon-guide.md) — operación GPON
