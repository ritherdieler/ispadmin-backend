# Tres ambientes: local, staging, producción

Staging es **pre-producción para cualquier feature**, no un piloto de 360. El mismo commit se instala primero como `ispadmin-staging.war`; si el smoke pasa, el mismo código va a `ispadmin.war`.

## Mapa

| Ambiente | Dónde | Context path | MySQL | Cómo arrancar |
|----------|--------|--------------|-------|----------------|
| Local | Mac, perfil `dev` | `/ispadmin` | `ispadmin_dev` o túnel | `./gradlew :core:bootRun` |
| Staging | Mismo VPS, Tomcat `tomcat-staging` :8081 | `/ispadmin-staging` | `ispadmin_staging` en `mysql8033` | `./scripts/deploy.sh --env staging` |
| Producción | Tomcat `tomcat9027` | `/ispadmin` | `ispadmin` | `./scripts/deploy.sh --env prod` (default) |

URLs públicas:

- Prod API: `https://api.gigafiberperu.cloud/ispadmin/`
- Staging API: `https://api.gigafiberperu.cloud/ispadmin-staging/`
- Backoffice contra staging: **solo Vite en la Mac** (`npx vite --mode staging`). No se publica en el VPS.
- Vite en Mac: preferir **`:3000`**; si está ocupado, otro puerto y reportarlo.

## Por qué el perfil va dentro del WAR

Compose **no** debe exportar `SPRING_PROFILES_ACTIVE` ni `SPRING_DATASOURCE_URL` al contenedor: esas variables ganan a `application-staging.properties` y los dos WAR hablarían a prod.

- Prod: `spring.profiles.active=prod` y JDBC `jdbc:mysql://mysql:3306/ispadmin?...` en `application-prod.properties`
- Staging: `prod,staging` vía despliegue + `application-staging.properties` (pisa JDBC, context-path y jobs).
- Un solo WAR; no hay perfiles Maven `*-war`.

## Jobs, WhatsApp y UDP

Un segundo Spring Boot en el mismo JVM arrancaría todos los `@Scheduled` contra OLT/ACS/MikroTik reales.

Staging lleva `gigafiber.scheduling.enabled=false` (no poner `GIGAFIBER_SCHEDULING_ENABLED` en `/opt/gigafiber/.env`). Collectors service-health, OLT gateway y NetDiag van **off** en `application-staging.properties`. Listeners UDP de traps/syslog no se cargan con perfil `staging`.

El webhook de Meta debe seguir apuntando solo a **`/ispadmin/...`**, nunca a `/ispadmin-staging`.

Para probar 360/ACS en staging: ONU con tag GenieACS `lab` + `subscription_acs.lab=1`, luego `./scripts/deploy.sh --env staging --with servicehealth` (el overlay enciende scheduling/ACS solo en ese WAR). Staging solo opera sobre `lab`; prod las excluye. Detalle ACS: [acs-wifi-lab-staging-2026-08-31.md](./acs-wifi-lab-staging-2026-08-31.md). Cableado del abonado de prueba #2329 (Genie, OLT, cola MikroTik): [lab-usuario-prueba-cableado-2329.md](./lab-usuario-prueba-cableado-2329.md). El CR/GPV sigue yendo al CPE físico.

El primer `--env staging` mientras compose aún tenga `SPRING_PROFILES_ACTIVE` **hornea y deja en el host** un `ispadmin.war` de prod del mismo árbol (para que al recrear Tomcat prod no arranque con perfil `dev,local`).


```bash
# 1) Staging (no toca ispadmin.war ni APP_RELEASE)
./scripts/deploy.sh --env staging

# 2) Smoke
curl -sI https://api.gigafiberperu.cloud/ispadmin-staging/
# backoffice: Vite --mode staging en la Mac (no rsync al VPS); no disparar campañas WhatsApp

# 3) Prod (árbol git limpio; registra release)
./scripts/deploy.sh --env prod
```

Rollback staging: borrar en el contenedor `webapps/ispadmin-staging.war` y el directorio `webapps/ispadmin-staging` (no tocar `ispadmin.war`).

## Memoria

Dos aplicaciones + DJL en el mismo JVM. Vigilar heap (`CATALINA_OPTS`). Plan B (fuera de este recorte): segundo contenedor Tomcat.

## Nginx

Fragmento: `scripts/nginx-ispadmin-staging.location.conf`. Recargar nginx tras copiar el `location /ispadmin-staging/`.

## Frontends

Nombres `VITE_*`: [vps-secrets-management.md](./vps-secrets-management.md) (sección Frontends). Backoffice **staging**: Vite `--mode staging` en la Mac; **no** rsync a `/var/www/gigafiber/backoffice-staging/`. Backoffice **prod**: build + rsync a `/var/www/gigafiber/backoffice/` solo si Sergio lo pide. Regla: `gigafiber/.cursor/rules/backoffice-staging-solo-local.mdc`.

## MySQL staging

Schema `ispadmin_staging` (clon de estructura desde `ispadmin` + seed de usuarios staff). No copiar tokens WhatsApp de clientes.

Catálogo geográfico para e2e de registro: copiar **toda** `place` (y `mufa` / `nap_box`) desde prod. Las coordenadas de prueba deben caer **dentro** de un `place.area` (`ST_Contains`). Seed: [`scripts/sql/staging-e2e-place-nap.sql`](../scripts/sql/staging-e2e-place-nap.sql). Detalle: [staging-e2e-place-catalog-2026-09-01.md](./staging-e2e-place-catalog-2026-09-01.md).

## MikroTik compartido

Staging usa el **mismo MK2** que prod. Las simple queues se etiquetan:

- Nombre: prefijo `[stg] `
- Comment: `env=stg`
- Pool: único segmento `192.168.250.1/24` (ver `scripts/sql/staging-ip-pool.sql`)

Prod no reclama ni borra colas de otro tag. El recreado masivo `POST /subscription/generate-simple-queues` está bloqueado (409) en staging.

Purga tras una prueba:

```bash
node scripts/mk2-purge-staging-queues.mjs --tag stg --dry-run --insecure
node scripts/mk2-purge-staging-queues.mjs --tag stg --apply --insecure
```

Subsistemas del WAR: [subsistemas-war-toggles.md](./subsistemas-war-toggles.md). Default de staging: ninguno opcional (`--with` para pedir uno).

Combinaciones verificadas (2026-08-31, `./mvnw -Pstaging-war -DskipTests clean package` + `scripts/verify-war.sh`):

| `--with` | Resultado |
|----------|-----------|
| (ninguno) | WAR limpio: no hay paquetes opcionales; sí `LegacyOnuOperations` / `OnuOperationsPort` |
| `oltgateway` | `subsystems.sh` acepta la clave suelta |
| `netdiag` | idem |
| `traffic` | idem |
| `servicehealth` | 360 + watcher ACS/Wi‑Fi; overlay scheduling/ACS; sin oltgateway/netdiag/traffic/observability |

Tras mover clases, el empaquetado tiene que ser `clean package`. Un `package` sin `clean` puede arrastrar `.class` viejos (`wispadmin/controller/OnuController`).

## Fixtures e2e staging

Datos de prueba (place, NAP, plan, MK2, pool, **perfiles TR-069**): [staging-e2e-fixtures.md](./staging-e2e-fixtures.md). Precarga: `./scripts/sql/staging-e2e-seed-all.sh`.
