# OLT Gateway WAR (staging) — 2026-09-03

**Histórico.** `deploy.sh` ya no publica el sibling. El camino vigente es un solo dueño SSH: [olt-gateway-compartido-fase0-2026-09-21.md](./olt-gateway-compartido-fase0-2026-09-21.md).

Split del paquete `oltgateway` a un WAR propio, copiando el patrón de traffic.

## Artefactos

| WAR | Perfil Maven | Context | JDBC |
|-----|--------------|---------|------|
| `ispadmin-staging.war` | `staging-war` | `/ispadmin-staging` | `ispadmin_staging` |
| `ispadmin-staging-oltgateway.war` | `oltgateway-staging-war` | `/ispadmin-staging-oltgateway` | `stg_oltgateway` |
| `ispadmin-oltgateway.war` | `oltgateway-war` | `/ispadmin-oltgateway` | `prod_oltgateway` (Maven listo; `deploy.sh --env prod` no lo sube) |

`./scripts/deploy.sh --env staging` construye y copia el WAR gateway junto al de traffic.

## Transporte

Clientes externos → core (`/onu`, JWT). Core / Health / NetDiag → gateway con `X-Olt-Gateway-Key`.

`--with oltgateway` hornea `olt.gateway.client-enabled=true` y `olt.gateway.enabled=false` en el **core**. El SSH/SNMP corre solo en el WAR gateway (`application-oltgateway.properties`: `olt.gateway.enabled=true`).

URL interna horneada: `http://127.0.0.1:8080/ispadmin-staging-oltgateway`. No añadir `OLT_GATEWAY_INTERNAL_BASE_URL` a `/opt/gigafiber/.env`.

## Schema

Hibernate `ddl-auto=update` crea `olt_mgr_*` en `stg_oltgateway`. Copia opcional desde `ispadmin_staging`: `scripts/sql/migrate-olt-mgr-to-stg_oltgateway.sql` (no ejecutar en prod).

`olt_mgr_onu_optical_sample` queda en el core; Health hace pull periódico de `/api/olt-gateway/onus/configured`.

## Fuera de este ciclo

- Desplegar `ispadmin-oltgateway.war` en producción.
- Migrar writes de alta FIBER (reboot/move CLI) de SmartOLT al gateway.
- Nginx público al context del gateway.
