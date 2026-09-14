# Acceso a MySQL en producción (VPS)

## Acceso directo por IP

MySQL está expuesto en `212.85.13.47:3306` (acceso público).

| Campo | Valor |
|---|---|
| Host | `212.85.13.47` |
| Port | `3306` |
| User | `root` |
| Database | `ispadmin` |
| Password | ver `application-prod.properties` en el VPS |

Compatible con DBeaver, MySQL Workbench, DataGrip, etc.

## Acceso via túnel SSH (alternativa más segura)

Si en algún momento se prefiere cerrar el puerto público, se puede usar el túnel SSH incluido:

```bash
cd ispadmin-backend
./scripts/db-tunnel.sh start
```

Conectar a `127.0.0.1:13306` con las mismas credenciales.

```bash
./scripts/db-tunnel.sh stop     # cerrar túnel
./scripts/db-tunnel.sh status   # verificar
```

## SSH integrado en el cliente SQL

Alternativa sin instalar nada extra. En DBeaver:

- Pestaña **SSH**: host `212.85.13.47`, user `root`, port `22`
- Pestaña **Main**: host `127.0.0.1`, port `3306`

## Historial de configuración

| Fecha | Configuración | Motivo |
|---|---|---|
| Pre 2026-07-04 | `0.0.0.0:3306` público | Acceso directo por IP |
| 2026-07-04 (hardening HTTPS) | `127.0.0.1:3306` solo localhost | Hardening VPS al activar HTTPS |
| 2026-07-04 (restaurado) | `0.0.0.0:3306` público | Requisito operativo del equipo |

## Nota de seguridad

MySQL expuesto en internet requiere contraseña fuerte (ya configurada). Si se detectan intentos de acceso no autorizados, considerar volver al acceso por túnel SSH o restringir por IP en UFW:

```bash
# Restringir a una IP fija:
ufw delete allow 3306/tcp
ufw allow from TU_IP to any port 3306
```
