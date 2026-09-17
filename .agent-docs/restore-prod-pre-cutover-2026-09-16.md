# Restore prod pre-cutover (83de8fe + Hibernate)

**Fecha backup:** 2026-09-16 23:08–23:22 America/Lima  
**Sergio abre:** store `docs/restore-prod-pre-cutover.md` (este archivo es la copia en repo).

Backup **OK**. Dump en caliente (`--single-transaction`). `tomcat9027` no se detuvo. No hubo `deploy.sh --env prod`. No DML/DDL en `ispadmin`. Lab `#35`/`#36` intactos. `traffic.poll.enabled=true`.

## Pin

| Campo | Valor |
|---|---|
| `APP_RELEASE` | `1.0.3+83de8fe` (env contenedor + `/opt/gigafiber/.env`) |
| WAR host = contenedor | sha256 `986f736bb789d2d7393fc9bec51fe7cbe32df242e685b26e242ab282eb29da71` · 252 430 389 B |
| Overlay WAR | `spring.jpa.hibernate.ddl-auto=update` · **sin** Flyway · `traffic.poll.enabled=true` |
| `ispadmin` | 123 tablas · 12.9 GiB · `access_mode` 0 cols · sin `flyway_schema_history` |
| Conteos | 1349 subscriptions · 27415 payments |
| Health | `/ispadmin/` 200 · actuator UP (durante y después del dump) |

## Artefactos

**VPS:** `/opt/gigafiber/backups/pre-cutover-2026-09-16_23-08/`

| Archivo | Bytes | sha256 |
|---|---:|---|
| `war/ispadmin.war` | 252430389 | `986f736bb789d2d7393fc9bec51fe7cbe32df242e685b26e242ab282eb29da71` |
| `mysql/ispadmin.sql.gz` | 499131449 | `ec97b714a15c5b3c2c7fe79c788a02602743e92b8a5d5c66afb7e5eef8260e8a` |
| `mysql/ispadmin_telemetry.sql.gz` | 490 | `1cb257aceb68858a45246335cd5f60603fcd1979daf2451b2e40614b17cbd9bc` |
| `redis/dump.rdb` | 1964108 | `3eb9178a84472dff56a4a757c0b52ab8ffe0ca357a648f661f4c89c901e4fe54` |

También: `config/dot-env` (600), `config/docker-compose.yml`, nginx API/ACS/landing + snippets, `config/genieacs-docker-compose.yml`, `MANIFEST.md`, `SHA256SUMS-ALL`.

**Local (gitignore `/backups/`):**  
`ispadmin-backend/backups/pre-cutover-2026-09-16_23-08/` — mismos SHA.

Dump header: `Database: ispadmin`, MySQL 8.0.33. Primeras 200k líneas: 0 `access_mode`, 0 `flyway_schema_history`. `gzip -t` OK.

## Restaurar (WAR → env → DB → contenedor → health)

1. `cp` WAR respaldado → `/opt/gigafiber/ispadmin.war` y verificar sha `986f736b…`.
2. `APP_RELEASE=1.0.3+83de8fe` en `/opt/gigafiber/.env` (o restaurar `dot-env` si el cutover lo reescribió).
3. `docker compose stop tomcat` (**solo** `tomcat9027`). Importar `ispadmin.sql.gz` y telemetry (vacío) por `docker exec -i mysql8033 mysql …`.
4. `docker cp` WAR a `tomcat9027:/usr/local/tomcat/webapps/ispadmin.war` · `docker compose up -d tomcat`. Si el exploded Flyway queda pegado: borrar `webapps/ispadmin/` y redeploy.
5. Health: `/ispadmin/` 200, `APP_RELEASE=1.0.3+83de8fe`, `access_mode` ausente, sin `flyway_schema_history`, search deuda, `#35/#36` vivas.

Pasos literales: store `docs/restore-prod-pre-cutover.md`.

## No restaurar

`ispadmin_staging`, lab `#35`/`#36`, `stg_*`, `prod_*` (no existían), leftover `ispadmin-staging*` en `tomcat9027`, emergency WAR, Mongo GenieACS, SmartOLT, MikroTik, Redis (Core 83de8fe sin `REDIS_ENABLED`), Meilisearch.

## Inventario externos

| Pieza | Restore |
|---|---|
| Redis | RDB copiado; no necesario para 83de8fe |
| GenieACS+Mongo | Vivo compartido. Sin dump. Dejar. |
| SmartOLT | Cloud. `OLT_PROVIDER_*=GATEWAY`. Sin snapshot. |
| MikroTik | Externo. Sin snapshot inventado. |

## Qué no se hizo en este turno

`deploy.sh --env prod`, reemplazo de WAR vivo, DML/DDL, stop largo de `tomcat9027`, corte de migración, delete `#35/#36`, apagar `traffic.poll.enabled`, push.
