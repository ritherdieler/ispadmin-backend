# Secretos en el VPS (producción GigaFiber)

Guía de **dónde viven las credenciales**, cómo llegan al backend Tomcat y qué no debe versionarse en git.

Host de referencia: `212.85.13.47` (`srv1043610`), stack en `/opt/gigafiber/`.

---

## Resumen

| Ubicación | Qué guarda | Consumido por |
|-----------|------------|---------------|
| **`/opt/gigafiber/.env`** | Secretos del backend (WhatsApp, observabilidad, NetDiag, OLT gateway, Mapbox, Meili host/key, release) | Contenedor **`tomcat9027`** vía `env_file` |
| **`/opt/gigafiber/docker-compose.yml`** | Algunos secretos **inline** (MySQL root, Meilisearch master key) + override JDBC Tomcat | `mysql8033`, `meilisearch`, `tomcat9027` |
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
  SPRING_PROFILES_ACTIVE: prod
  SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/ispadmin?...
  SPRING_DATASOURCE_USERNAME: root
  SPRING_DATASOURCE_PASSWORD: "..."
  APP_RELEASE: ${APP_RELEASE:-}
  CATALINA_OPTS: "..."
```

Spring Boot enlaza variables de entorno a propiedades (`NET_DIAG_API_KEY` → `net.diag.api-key`, etc.) según `application-prod.properties`.

**Importante:** si cambias `.env` a mano, hay que **recrear Tomcat** para que el contenedor recargue el `env_file`:

```bash
cd /opt/gigafiber && docker compose up -d tomcat
```

Tras recrear el contenedor, el WAR **no** persiste en la imagen: ejecutar desde tu Mac:

```bash
cd ispadmin-backend && ./scripts/deploy.sh --war-only
```

---

## Variables habituales en `.env` (solo nombres)

Agrupadas por función; valores **nunca** en este documento.

| Grupo | Variables |
|-------|-----------|
| Release | `APP_RELEASE` |
| Smart Map | `MAPBOX_ACCESS_TOKEN` |
| Buscador | `MEILI_HOST`, `MEILI_MASTER_KEY` |
| WhatsApp Cloud API | `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_BUSINESS_ACCOUNT_ID`, `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_WEBHOOK_VERIFY_TOKEN`, `WHATSAPP_APP_SECRET` |
| Observabilidad (ingest + dashboard) | `OBS_API_KEY_BACKOFFICE`, `OBS_API_KEY_ASISTENCIAS`, `OBS_API_KEY_ANDROID`, `OBS_API_KEY_DASHBOARD`, `OBS_SESSION_SECRET`, `OBS_DASHBOARD_BASE_URL`, … |
| NetDiag NOC | `NET_DIAG_ENABLED`, `NET_DIAG_API_KEY`, `NET_DIAG_WHATSAPP_NOC_PHONE` (opcional), `NET_DIAG_SNMP_*`, `NET_DIAG_SYSLOG_*`, `NET_DIAG_LLM_*` |
| OLT Gateway (SSH) | `OLT_GATEWAY_ENABLED`, `OLT_GATEWAY_API_KEY`, `OLT_GATEWAY_PASSWORD`, `OLT_GATEWAY_HOST`, `OLT_GATEWAY_USERNAME`, … |

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

En cada deploy (`--deploy`, `--war-only`, `--full`), vía SSH:

1. **`APP_RELEASE`** → `1.0.3+<git-sha>` en `/opt/gigafiber/.env` (backup `.bak` si cambia).
2. **`MAPBOX_ACCESS_TOKEN`** → solo si está definido en **`scripts/deploy.config.local`** o exportado al ejecutar deploy (opcional).
3. Si cambió `APP_RELEASE` o Mapbox → **`docker compose up -d tomcat`** (recrea contenedor).

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

- Perfil activo: **`prod`** (`SPRING_PROFILES_ACTIVE`).
- Pagos Micuentaweb, SmartOLT API key y password MySQL **históricos** pueden seguir en el JAR; el contenedor **sobrescribe JDBC** con variables `SPRING_DATASOURCE_*`.
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

- Producción: `.env.production` / variables CI con prefijo **`VITE_`**.
- Tras `npm run build`, las claves van **dentro del bundle JS** servido por Nginx (`/var/www/gigafiber/backoffice/`, etc.).
- `VITE_NETDIAG_API_KEY` debe coincidir con `NET_DIAG_API_KEY` del backend, pero asumir que **cualquier usuario autenticado en NOC puede verla** en DevTools.
- Claves observabilidad backoffice: `VITE_OBS_API_KEY` → par con `OBS_API_KEY_BACKOFFICE` en el VPS.

Deploy backoffice típico: build local + `rsync` al VPS (ver `.agent-docs/deploy-prod-mapa-gigafiber-2026-07-23.md` en repo backoffice).

---

## Otros servicios en el mismo VPS

| Ruta | Notas |
|------|--------|
| `/opt/gigafiber/genieacs/` | `.env` propio GenieACS (Mongo, JWT); ver `genieacs-despliegue-gigafiber.md` |
| Nginx | Certificados Let's Encrypt; sin secretos de app en repo |
| `/var/lib/wispadmin/observability/` | Replays y símbolos en disco (datos, no credenciales) |

---

## Checklist operativo

1. Añadir o rotar secreto → editar `/opt/gigafiber/.env` (backup `.bak` automático si usas `sed -i.bak`).
2. `cd /opt/gigafiber && docker compose up -d tomcat`
3. `./scripts/deploy.sh --war-only` desde Mac (restaura `ispadmin.war`).
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
- [observability-release-versioning-deploy.md](./observability-release-versioning-deploy.md) — `APP_RELEASE`
- [whatsapp-production-https-webhook.md](./whatsapp-production-https-webhook.md) — Meta / webhook
- [netdiag-fase1.md](./netdiag-fase1.md) — `NET_DIAG_*`
- [vps-mysql-access.md](./vps-mysql-access.md) — acceso BD

Última revisión operativa: 2026-08-01 (NetDiag + OLT gateway en `.env`, Tomcat `env_file`).
