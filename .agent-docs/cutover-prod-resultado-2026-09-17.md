# Cutover prod ← develop — resultado 2026-09-17

**FAIL.** Se restauró a `1.0.3+83de8fe`. `ispadmin` no recibió Flyway. Staging / lab intactos.

Detalle operativo (hashes, HTTP, restore): store `docs/cutover-prod-resultado.md`.

## Qué se desplegó

- Pin: `56991bd` (`APP_RELEASE` intentado `1.0.3+56991bd`).
- `deploy.sh --env prod` subió el WAR (sha `28523a30…`, 135 829 840 B) y recreó `tomcat9027`.
- Readiness: `/ispadmin/` **404** (timeout 90×5 s).

## Causa

Hibernate validate: `assistance_ticket.priority` es `varchar` en prod y la entidad espera `integer`.

Flyway Core **no** corrió sobre `ispadmin` (sin `flyway_schema_history`, sin `access_mode`). Sí corrieron `acsFlyway` (`prod_acs` V1–V5) y `oltGatewayFlyway` (`prod_oltgateway` V1). No hay bean `coreFlyway`; `CoreJpaConfig` valida el schema antes de cualquier migrate de `classpath:db/migration`. V45 habría hecho el `ALTER` de `priority` si ese bean existiera.

El clon SQL `ispadmin_flyway_clone` (V39–V54 PASS) no arrancó Spring; no cazó este hueco.

## Restore

WAR + `APP_RELEASE=1.0.3+83de8fe` + se quitó `SPRING_PROFILES_ACTIVE: prod` del servicio `tomcat`. Dump MySQL **no** restaurado: cero DDL en `ispadmin` (123 tablas, 1349 subs, 27415 payments).

Post-restore: `/ispadmin/` 200, actuator UP, login+search deuda 200, `#35`/`#36` vivos, `traffic.poll.enabled=true`.

## No reintentar sin

Bean Core Flyway + ensayo Hibernate validate contra el clon (no solo SQL).
