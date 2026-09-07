# Secretos en el VPS (producción GigaFiber)

Guía de **dónde viven las credenciales**, cómo llegan al backend Tomcat y qué no debe versionarse en git.

Host de referencia: `212.85.13.47` (`srv1043610`), stack en `/opt/gigafiber/`.

**Regla Cursor (obligatoria):** `.cursor/rules/documentar-secretos-env.mdc` — todo key/secreto nuevo en prod o dev debe quedar en este catálogo (**solo nombres y ubicación**, nunca valores).

---

## Resumen

| Ubicación | Qué guarda | Consumido por |
|-----------|------------|---------------|
| **`/opt/gigafiber/.env`** | Secretos del backend (WhatsApp, observabilidad, NetDiag, OLT gateway, Mapbox, Meili host/key, release) | Contenedores **`tomcat9027`** y **`tomcat-staging`** vía `env_file` |
| **`/opt/gigafiber/docker-compose.yml`** | Algunos secretos **inline** (MySQL root, Meilisearch master key) + override JDBC Tomcat | `mysql8033`, `meilisearch`, `tomcat9027`, `tomcat-staging` (host **8081**) |
| **`ispadmin.war`** | Perfil `prod` embebido; **evitar** nuevos secretos en claro en `application-prod.properties` | Tomcat (preferir `${ENV}`) |
| **Máquina del desarrollador** | `scripts/deploy.config.local`, `application-local.properties` | Deploy SSH y dev local (**gitignore**) |
| **Build frontends** | `.env.production` (backoffice, asistencias, observability-web) | Variables `VITE_*` **embebidas en JS** (tratar como expuestas al navegador) |

No usar `/etc/wispadmin/whatsapp.env` en el VPS actual: WhatsApp va en **`/opt/gigafiber/.env`** (plantilla histórica: `deploy-prod-whatsapp.sh`).

---

## Archivo principal: `/opt/gigafiber/.env`

- Propietario: `root`, permisos típicos `644`.
- **No está en git.** Es la fuente de verdad para el backend Spring en Docker.
- Copias de seguridad automáticas al editar vía deploy o `sed -i.bak`:  
  `/opt/gigafiber/.env.bak.YYYYMMDDHHMMSS`

### Cómo entra en Tomcat

En `docker-compose.yml`, el servicio `tomcat`:

```yaml
env_file:
  - /opt/gigafiber/.env
environment:
  SPRING_DATASOURCE_USERNAME: root
  SPRING_DATASOURCE_PASSWORD: "..."
  APP_RELEASE: ${APP_RELEASE:-}
  CATALINA_OPTS: "..."
```

**No** exportar `SPRING_PROFILES_ACTIVE` ni `SPRING_DATASOURCE_URL` a nivel de contenedor. Prod (`tomcat9027`) y staging (`tomcat-staging`) son JVMs distintas; cada WAR hornea su JDBC. Usuario/clave pueden seguir en `environment` / `.env` compartido.

Spring Boot enlaza el resto de variables de `.env` a propiedades (`NET_DIAG_API_KEY` → `net.diag.api-key`, etc.). No definir `GIGAFIBER_SCHEDULING_ENABLED` en `.env` (staging lo apaga en `application-staging.properties`). No definir `GIGAFIBER_ENVIRONMENT_TAG` ni `GIGAFIBER_SUBSYSTEMS_*` en `.env` compartido: el tag y los toggles van horneados en el WAR (`application-staging.properties` / `scripts/subsystems.sh`).

**Importante:** si cambias `.env` a mano, hay que **recrear el Tomcat que deba recargar** `env_file`:

```bash
cd /opt/gigafiber && docker compose up -d tomcat
# staging, si también debe recargar .env:
cd /opt/gigafiber && docker compose up -d tomcat-staging
```

`docker compose up -d tomcat` **no** debe usarse en un deploy staging: vacía webapps de prod. Staging se despliega con `--env staging` contra `tomcat-staging`. Tras recrear prod a mano, restaurar `ispadmin.war` con `--war-only --env prod`.

## Variables habituales en `.env` (solo nombres)

Agrupadas por función; valores **nunca** en este documento.

| Grupo | Variables |
|-------|-----------|
| Release | `APP_RELEASE` |
| Smart Map | `MAPBOX_ACCESS_TOKEN` |
| Buscador | `MEILI_HOST`, `MEILI_MASTER_KEY` |
| WhatsApp Cloud API | `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_BUSINESS_ACCOUNT_ID`, `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_WEBHOOK_VERIFY_TOKEN`, `WHATSAPP_APP_SECRET`, `WHATSAPP_MEDIA_STORAGE_DIR` (path en contenedor; volumen host `/opt/gigafiber/data/whatsapp/media`), `WHATSAPP_MEDIA_BASE_PATH` (ruta/URL base para armar `PaymentDto.proofImagePath`; default prod `/usr/local/tomcat/../data/whatsapp/media`) |
| CRM / OpenAI | `CRM_SECRETS_MASTER_KEY` → `crm.llm.master-key`; `OPENAI_API_KEY` bootstrap opcional → `crm.llm.bootstrap-api-key` |
| RouterOS REST | `ROUTER_OS_CLIENT_ADAPTER` (prod: `rest`); `ROUTER_OS_REST_TRUSTSTORE_PASSWORD` → truststore JKS embebido |
| Observabilidad (ingest + dashboard) | `OBS_API_KEY_BACKOFFICE`, `OBS_API_KEY_ASISTENCIAS`, `OBS_API_KEY_ANDROID`, `OBS_API_KEY_DASHBOARD`, `OBS_SESSION_SECRET`, `OBS_DASHBOARD_BASE_URL`, … |
| NetDiag NOC | `NET_DIAG_ENABLED`, `NET_DIAG_API_KEY`, `NET_DIAG_WHATSAPP_NOC_PHONE` (opcional), `NET_DIAG_SNMP_*`, `NET_DIAG_SYSLOG_*`, `NET_DIAG_LLM_*` |
| OLT Gateway (SSH) | `OLT_GATEWAY_ENABLED` (en el **WAR gateway**; el core staging lo deja en `false`), `OLT_GATEWAY_API_KEY`, `OLT_GATEWAY_PASSWORD`, `OLT_GATEWAY_HOST`, `OLT_GATEWAY_USERNAME`, `OLT_GATEWAY_WRITES_ENABLED` (prod: env, default `false`; **staging WAR gateway reemplaza la línea en `application-prod.properties` a `true`** — no poner `false` en `.env` compartido o el env gana al WAR). |
| OLT Gateway WAR (split) | `OLT_GATEWAY_INTERNAL_BASE_URL` y `OLT_GATEWAY_DATASOURCE_URL` **no** van en `.env` compartido: van horneadas (`olt.gateway.internal-base-url`, JDBC `stg_oltgateway` / `prod_oltgateway`). Staging core → `http://127.0.0.1:8080/ispadmin-staging-oltgateway`. `olt_mgr_*` vive en el schema del WAR gateway; `olt_mgr_onu_optical_sample` permanece en el schema del core. `OLT_GATEWAY_OPERATION_SECRET` → `olt.gateway.operation-secret` (AES-GCM del journal de activación; VPS `/opt/gigafiber/.env`; fallback `${OLT_GATEWAY_API_KEY}` si falta). Rotar implica re-cifrar u operaciones en curso ilegibles. |
| SmartOLT cloud (toggle de escrituras en Gateway) | `OLT_SERVICE_BASE_URL` → `olt.service.base-url` (default `https://gigafiberperu.smartolt.com/api/`); `OLT_SERVICE_API_KEY` (secreto; header `X-Token`) → `olt.service.api-key`; `OLT_SERVICE_CONNECT_TIMEOUT_MS` / `OLT_SERVICE_READ_TIMEOUT_MS`. Toggle por operación: `OLT_PROVIDER_AUTHORIZE`, `OLT_PROVIDER_DELETE`, `OLT_PROVIDER_REBOOT`, `OLT_PROVIDER_MOVE` (`SMARTOLT` \| `GATEWAY`, default `GATEWAY`) → `olt.provider.*`. Homologación SSH↔SmartOLT: `OLT_GATEWAY_CUSTOM_PROFILE_BINDINGS` → `olt.gateway.writes.custom-profile-bindings` (default `Generic_1:1=3:2,Generic_1:100=6:13`); `OLT_GATEWAY_INBOUND_TRAFFIC_TABLE` / `OLT_GATEWAY_OUTBOUND_TRAFFIC_TABLE` → traffic-table 8/9; `OLT_GATEWAY_SMARTOLT_OLT_ID` → `olt.gateway.smartolt-olt-id` (default `2`). El WAR Gateway las hornea en `application-oltgateway.properties` (el overlay no hereda `application-dev/prod`). Core las usa solo si `olt.gateway.client-enabled=false`. Viven en `/opt/gigafiber/.env`. |
| ACS WAR (TR-069) | `ACS_API_KEY` (secreto; header `X-Acs-Key`; misma pareja Gateway ↔ ACS WAR → `acs.api-key` / `olt.gateway.acs.api-key`). Perfiles TR-069 (`tr069_model_profile`) viven en JDBC `stg_acs` / `prod_acs` (Flyway ACS `db/acs`). No hay `ACS_PROFILES_CATALOG`. `OLT_GATEWAY_ACS_BASE_URL` **no** va en `.env` compartido: horneada en el WAR gateway (`olt.gateway.acs.internal-base-url`). Staging: `http://127.0.0.1:8080/ispadmin-staging-acs`. Core no habla con ACS WAR ni GenieACS: backoffice → Core `/admin/tr069-profiles` → Gateway `/api/olt-gateway/acs/profiles`. |
| OLT Gateway SNMP RO | `OLT_GATEWAY_SNMP_ENABLED`, `OLT_GATEWAY_SNMP_RO_COMMUNITY` (secreto), `OLT_GATEWAY_SNMP_PORT`, `OLT_GATEWAY_SNMP_TIMEOUT_MS`, `OLT_GATEWAY_SNMP_RETRIES`, `OLT_GATEWAY_SNMP_MAX_REPETITIONS`, `OLT_GATEWAY_SNMP_ALLOW_SSH_FALLBACK` (inventario SSH deprecado; default false), `OLT_GATEWAY_SNMP_ALLOW_SSH_SIGNAL_FALLBACK` (óptica SSH deprecada; default false), `OLT_GATEWAY_SNMP_OPTICAL_PARALLEL_COLUMNS` (default true; threads de columnas; la red se serializa por OLT según modelo), `OLT_GATEWAY_SNMP_OPTICAL_PER_PORT` (default false; walk por puerto más lento), `OLT_GATEWAY_SNMP_OPTICAL_PARALLEL_PORTS` (default 3; solo si PER_PORT), `OLT_GATEWAY_SNMP_OPTICAL_ONLINE_ONLY` (default true; solo si PER_PORT), `OLT_GATEWAY_SNMP_ACQUIRE_TIMEOUT_MS` (default 300000; espera de permit del bus de esa OLT), `OLT_GATEWAY_SNMP_TRAP_ENABLED` (default false; receptor ASN.1 Huawei), `OLT_GATEWAY_SNMP_TRAP_LISTEN_PORT` (default 1162; ≠ NetDiag 1620), `OLT_GATEWAY_SNMP_TRAP_BIND_ADDRESS`, `OLT_GATEWAY_SNMP_TRAP_COMMUNITY` (opcional; secreto si se usa), `OLT_GATEWAY_SNMP_TRAP_BUFFER_SIZE`, `OLT_GATEWAY_SNMP_TRAP_DISPATCHER_THREADS`, `OLT_GATEWAY_SYNC_SIGNAL_INTERVAL_MS` (default 300000 = 5 min), `OLT_GATEWAY_SYNC_LAB_OPTICAL_SSH_ENABLED` (default `false`; el SSH lab vive en el WAR gateway, no en el overlay del core), `OLT_GATEWAY_SYNC_LAB_OPTICAL_SSH_INTERVAL_MS` (default `900000`), `OLT_GATEWAY_SYNC_LAB_OPTICAL_SSH_INITIAL_DELAY_MS` (default `120000`) → `olt.gateway.snmp.*` / `olt.gateway.sync.*`. Capacidad SNMP GET: `OltSnmpModelLimits` / `max_concurrent_snmp_walks` (MA5608T=1). |
| GenieACS NBI (TR-069) | `GENIEACS_ENABLED`, `GENIEACS_NBI_BASE_URL` (prod típico `http://127.0.0.1:7557`), `GENIEACS_WAIT_TIMEOUT_MS`, `GENIEACS_OFFLINE_WAIT_TIMEOUT_MS`, `GENIEACS_POLL_INTERVAL_MS`, `GENIEACS_TASK_TIMEOUT_MS`, `GENIEACS_CONNECT_TIMEOUT_MS`, `GENIEACS_DEFAULT_DNS`, `GENIEACS_WAN_VLAN_ID`, `GENIEACS_STAGING_WAN_INDEX` (default `1`, WCD TR-069 intocable), `GENIEACS_CLIENT_WAN_INDEX` (default `2`, Internet abonado), `GENIEACS_CLIENT_WAN_NAME_PATTERN` (default `2_INTERNET_R_VID_{vlan}`) → `genieacs.*` en el **WAR ACS** (`application-acs.properties`). El Core no carga `genieacs.*`. Credenciales ACS/CPE (`ACS_CPE_*`) viven en `/opt/gigafiber/genieacs/.env`, no en Tomcat. |
| Ambiente / subsistemas WAR | `GIGAFIBER_ENVIRONMENT_TAG` → `gigafiber.environment.tag` (staging `stg`; vacío en prod). `GIGAFIBER_SUBSYSTEMS_OBSERVABILITY_ENABLED`, `GIGAFIBER_SUBSYSTEMS_OLTGATEWAY_ENABLED`, `GIGAFIBER_SUBSYSTEMS_NETDIAG_ENABLED`, `GIGAFIBER_SUBSYSTEMS_TRAFFIC_ENABLED`, `GIGAFIBER_SUBSYSTEMS_SERVICEHEALTH_ENABLED` → `gigafiber.subsystems.<key>.enabled`. No ponerlas en `/opt/gigafiber/.env`: el WAR de staging las hornea. |
| Diagnóstico 360 / service-health | `SERVICE_HEALTH_ENABLED`, `SERVICE_HEALTH_OPTICAL_ENABLED`, `SERVICE_HEALTH_ACS_ENABLED`, `SERVICE_HEALTH_CORRELATION_ENABLED`, `SERVICE_HEALTH_SHARED_INCIDENTS_ENABLED`, `SERVICE_HEALTH_SHARED_INCIDENT_NOTIFICATIONS_ENABLED`, `SERVICE_HEALTH_ACTIONS_ENABLED`, `SERVICE_HEALTH_CONFIG_ENABLED`, `SERVICE_HEALTH_PILOT_SUBSCRIPTION_IDS` (opcional; vacío = todas las suscripciones no-lab en prod), `SERVICE_HEALTH_PILOT_ACS_DEVICE_IDS`, `SERVICE_HEALTH_STATION_HMAC_KEY` (secreto ≥32 bytes; exigido al activar ACS/acciones; no rotar sin plan de `station_key`), `SERVICE_HEALTH_ACS_GPV_COOLDOWN_SECONDS` (default prod `1800`; cooldown entre GPV WLAN automáticos del watcher), `SERVICE_HEALTH_ACS_WIFI_SAMPLE_TARGET_SECONDS` (default prod `1800`; cadencia objetivo de muestras Wi‑Fi), `SERVICE_HEALTH_STATION_HOURLY_RETENTION_DAYS` (default `90`), `SERVICE_HEALTH_STATION_SERIES_RAW_MAX_DAYS` (default `7`; series ≤7 d en raw, más largas en hourly), `SERVICE_HEALTH_SNAPSHOT_FRESH_SECONDS` (default `60`; frescura de `service_health_current` en el GET 360) → `service.health.*`. Default todo `false`. **Staging:** `application-staging.properties` fija `service.health.acs-wifi-sample-target-seconds=180` y `acs-gpv-cooldown-seconds=180` (3 min; no poner esos `SERVICE_HEALTH_ACS_*_SECONDS` en `/opt/gigafiber/.env` o ganarían al WAR). Detalle: `staging-acs-wifi-sample-cadence-3min.md`. As-built: `01-implementacion-analitica-consumo-ancho-banda.md` (tráfico) y `03-implementacion-diagnostico-tecnico-convergente.md`. |
| Redis interno (cache 360 + Streams) | `REDIS_ENABLED` (staging core: default `true` vía `application-staging`; prod default `false` vía `${REDIS_ENABLED:false}`; **WAR oltgateway** fuerza `gigafiber.redis.enabled=true` en bake para publicar `cpe.provisioning`). `REDIS_HOST` (VPS: hostname compose `redis`; no exponer a internet), `REDIS_PORT` (default `6379`), `REDIS_PASSWORD` (secreto; `openssl rand -hex 16`). Enlazan `gigafiber.redis.*`. Compose VPS: servicio `redis` en `/opt/gigafiber/docker-compose.yml` (`maxmemory 128mb`, AOF). Local: `docker-compose.redis.yml` + `scripts/redis-local.sh`. Stream `gigafiber.events`, grupos `snapshot-core` (360) y `cpe-provision-core` (cierre `tr069` en core). No poner la password en git. |
| Tráfico WAR (collector) | `TRAFFIC_API_KEY` (secreto; header `X-Traffic-Key`; misma key core↔traffic). `TRAFFIC_INTERNAL_BASE_URL` y `TRAFFIC_CORE_BASE_URL` **no** van en `.env` compartido: van horneadas en el WAR (`traffic.internal-base-url` / `traffic.core-base-url`). Staging: core → `http://127.0.0.1:8080/ispadmin-staging-traffic`; traffic → `http://127.0.0.1:8080/ispadmin-staging`. JDBC schema propio `stg_traffic` / `prod_traffic` (migrate SQL, no ejecutar sin OK). |

### Frontends

| Proyecto | Variable | Entorno | Dónde vive | Notas |
|---|---|---|---|---|
| Backoffice | `VITE_TRAFFIC_WS_URL` | build (`staging`, opcional `prod`) | `ispadmin-backoffice/.env.staging`, `.env.example` | URL WebSocket dedicada del Traffic WAR. Fallback: si falta, usa `VITE_API_BASE_URL + /ws` del core. |

Contrato completo de propiedades: `src/main/resources/application-prod.properties`.

Generar claves nuevas (ejemplo):

```bash
openssl rand -hex 24   # NET_DIAG_API_KEY, tokens internos
openssl rand -hex 16   # OLT_GATEWAY_API_KEY
```

### Editar una variable sin romper el archivo

Patrón **upsert** (SSH en el VPS):

```bash
upsert() {
  local k="$1" v="$2"
  ENV=/opt/gigafiber/.env
  if grep -q "^${k}=" "$ENV"; then
    sed -i.bak "s|^${k}=.*|${k}=${v}|" "$ENV"
  else
    echo "${k}=${v}" >> "$ENV"
  fi
}
# upsert NET_DIAG_ENABLED true
```

Evitar comillas sin escapar en valores con `$` o `;` — si hace falta, usar comillas simples en el valor y probar con `docker exec tomcat9027 printenv NOMBRE_VAR`.

---

## Qué actualiza `scripts/deploy.sh` automáticamente

En cada `--deploy` y `--setup` (prod y staging), vía SSH:

1. **`APP_RELEASE`** → `1.0.3+<git-sha>` en `/opt/gigafiber/.env` (backup `.bak` si cambia). Solo `--deploy` / `--war-only` / `--full` en prod; staging no toca `APP_RELEASE`.
2. **`MAPBOX_ACCESS_TOKEN`** → solo si está definido en **`scripts/deploy.config.local`** o exportado al ejecutar deploy (opcional).
3. Si cambió `APP_RELEASE` o Mapbox → **`docker compose up -d tomcat`** (recrea contenedor).
4. **Modelos faciales** (no es secreto): rsync `src/main/resources/models/` → `/opt/gigafiber/models/` (`DOCKER_FACE_MODELS_HOST_DIR`). Volumen Tomcat `:ro` al mismo path. Ver [wars-adelgazados-models-fs.md](./wars-adelgazados-models-fs.md).

**No** escribe WhatsApp, NetDiag, OBS ni OLT: esos se mantienen a mano en el VPS.

Registro de deploy en observabilidad usa credenciales **locales** del desarrollador (no van al VPS):

- `OBS_BASE_URL`, `OBS_API_KEY` en `deploy.config.local` → POST `/observability/releases` desde la Mac.

---

## Secretos en Docker Compose (duplicados a vigilar)

Además de `.env`, el `docker-compose.yml` del VPS puede definir:

| Servicio | Secretos inline |
|----------|-----------------|
| `mysql` | `MYSQL_ROOT_PASSWORD` |
| `meilisearch` | `MEILI_MASTER_KEY`, `MEILI_ENV=production` |
| `tomcat` | `SPRING_DATASOURCE_PASSWORD` (debe coincidir con MySQL) |

El Tomcat también lee `MEILI_*` desde `.env` para el cliente de búsqueda del backend. **La misma master key** debe ser coherente entre Meilisearch y `MEILI_MASTER_KEY` en `.env`.

Rotación MySQL: actualizar **compose + `.env` si aplica + `application-prod` legacy** y reiniciar servicios.

---

## WAR y `application-prod.properties`

- Perfil activo de prod: **`prod`** (horneado en el WAR con `-Pprod-war`). Staging: **`staging`** (`-Pstaging-war`).
- Pagos Micuentaweb, SmartOLT API key y password MySQL **históricos** pueden seguir en el JAR; el contenedor **no** debe fijar `SPRING_DATASOURCE_URL`.
- Patrón deseado para features nuevas: **`propiedad=${NOMBRE_ENV:}`** en `application-prod.properties` + valor solo en `/opt/gigafiber/.env`.
- Firebase prod: `classpath:firebase_service_account_prod.json` (credencial de servicio en el WAR, no en `.env`).

---

## Secretos en la máquina de desarrollo

| Archivo | Uso |
|---------|-----|
| `scripts/deploy.config.local` | `VPS_HOST`, `DEPLOY_SSH_PASSWORD` o clave SSH, `MAPBOX_ACCESS_TOKEN`, `OBS_API_KEY` para deploy |
| `src/main/resources/application-local.properties` | MySQL local, OLT lab, etc. (**gitignore**) |

Copiar plantilla: `scripts/deploy.config.example`. **Nunca commitear** `deploy.config.local`.

---

## Frontends (backoffice, asistencias, observability-web)

| Nombre | Entorno | Dónde vive el valor | Enlaza | Rotación / pareja |
|--------|---------|---------------------|--------|-------------------|
| `VITE_API_BASE_URL` | prod / build | `.env.production` (local/CI, no secretos) | Axios del backoffice | Prod: `https://api.gigafiberperu.cloud/ispadmin` |
| `VITE_API_BASE_URL` | staging / build | `.env.staging` | Axios del backoffice | Staging: `https://api.gigafiberperu.cloud/ispadmin-staging` (`npm run build:staging`) |
| `VITE_MAPBOX_ACCESS_TOKEN` | build | `.env` / `.env.production` | Mapbox GL | Token `pk.` público |
| `VITE_OBS_API_KEY` | build | `.env.production` | ingest observabilidad | Par con `OBS_API_KEY_BACKOFFICE` |
| `VITE_NETDIAG_API_KEY` | build | `.env.production` | header NOC | Par con `NET_DIAG_API_KEY` |

- Tras `npm run build`, las claves van **dentro del bundle JS** servido por Nginx (`/var/www/gigafiber/backoffice/`, etc.).
- `VITE_NETDIAG_API_KEY` debe coincidir con `NET_DIAG_API_KEY` del backend, pero asumir que **cualquier usuario autenticado en NOC puede verla** en DevTools.

Deploy backoffice típico: build local + `rsync` al VPS. Staging: `npm run build:staging` (no apunta el bundle de prod a `/ispadmin-staging`). Observability-web y asistencias: misma idea en una fase posterior. Runbook: [ambientes-local-staging-prod.md](./ambientes-local-staging-prod.md).

---

## Otros servicios en el mismo VPS

| Ruta | Notas |
|------|--------|
| `/opt/gigafiber/genieacs/` | `.env` propio GenieACS (Mongo, JWT, `ACS_CPE_*`); ver `genieacs-despliegue-gigafiber.md` |
| `/opt/gigafiber/models/` | Modelos faciales DJL/ONNX (`face_feature.zip`, `ultranet.zip`, `arcface_w600k_mbf.onnx`). **No es secreto.** Rsync desde `src/main/resources/models/` en `--deploy`/`--setup`. Core prod/staging: `face.login.*` / `face.embedding.model-path`. Plantilla: `DOCKER_FACE_MODELS_HOST_DIR` en `scripts/deploy.config.example`. |
| Tomcat `/opt/gigafiber/.env` | Flags NBI del backend: `GENIEACS_ENABLED`, `GENIEACS_NBI_BASE_URL`, … (sin secretos ACS) |
| Nginx | Certificados Let's Encrypt; sin secretos de app en repo |
| `/var/lib/wispadmin/observability/` | Replays y símbolos en disco (datos, no credenciales) |

---

## Checklist operativo

1. Añadir o rotar secreto → editar `/opt/gigafiber/.env` (backup `.bak` automático si usas `sed -i.bak`).
2. `cd /opt/gigafiber && docker compose up -d tomcat`
3. `./scripts/deploy.sh --war-only --env prod` desde Mac (restaura `ispadmin.war`; si también hay staging, el script reinyecta ambos WAR tras recrear Tomcat).
4. Verificar: `curl -s https://api.gigafiberperu.cloud/ispadmin/` → 200; endpoints que usen la clave (NetDiag health, WhatsApp webhook, etc.).
5. Frontends: si cambió una `VITE_*`, **rebuild + rsync**.

---

## Seguridad

- No pegar secretos en chat, tickets ni commits.
- Preferir **SSH por clave** en lugar de password root en `deploy.config.local`.
- MySQL expuesto en `:3306` (ver `vps-mysql-access.md`): contraseña fuerte obligatoria.
- Revisar permisos: `.env` solo root; limitar quién tiene SSH root.
- Tras filtración: rotar token afectado en Meta/WhatsApp, OBS keys, `NET_DIAG_API_KEY`, `OLT_GATEWAY_*`, MySQL si aplica.

---

## Referencias

- [deploy-flow.md](./deploy-flow.md) — deploy WAR y DJL
- [wars-adelgazados-models-fs.md](./wars-adelgazados-models-fs.md) — modelos en `/opt/gigafiber/models/`
- [observability-release-versioning-deploy.md](./observability-release-versioning-deploy.md) — `APP_RELEASE`
- [whatsapp-production-https-webhook.md](./whatsapp-production-https-webhook.md) — Meta / webhook
- [netdiag-fase1.md](./netdiag-fase1.md) — `NET_DIAG_*`
- [vps-mysql-access.md](./vps-mysql-access.md) — acceso BD

Última revisión operativa: 2026-09-05 (modelos faciales en `/opt/gigafiber/models/`; cuatro WAR adelgazados).

## Architecture recovery bindings (2026-09-05)

| Variable / property | Environment | Value location | Binding / contract |
|---|---|---|---|
| `OLT_GATEWAY_OPERATION_SECRET` | Gateway dev/staging/prod | Deployment environment, never Git | `olt.gateway.operation-secret`; encrypts durable activation requests. Falls back to `olt.gateway.api-key` for compatibility. Keep stable; rotate only after draining pending operations and migrating encrypted records. |
| `gigafiber.redis.namespace` | All WARs | Profile properties | `dev`, `prod`, `stg`; all WARs in one environment must match. Prefixes streams and caches; no secret. Existing unprefixed events must be drained before rollout; activation outbox retries independently. |
| `traffic.directory-max-stale-seconds` | Traffic | Runtime properties | Default 300; maximum age accepted during a directory outage. |
| `traffic.router-directory-interval-ms` | Traffic | Runtime properties | Default 60000; HTTP router synchronization interval. No cross-schema SQL is used. |
| `olt.gateway.activation-recovery-ms` | Gateway | Runtime properties | Default 30000; retries journal recovery and pending event publication. |
| `gigafiber.redis.pending-idle-ms` / `max-deliveries` | Consumers | Runtime properties | Defaults 60000 / 5; reclaim timeout and quarantine threshold. |
