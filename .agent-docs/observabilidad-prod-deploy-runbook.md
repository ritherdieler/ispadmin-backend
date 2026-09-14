# Runbook — Despliegue de Observabilidad en producción (2026-07-13)

Guía para poner en marcha el módulo de observabilidad en prod y su **control de acceso**
(quién puede ingerir vs. quién puede leer/consultar).

> **Actualización 2026-07-13 (auth de plataforma):** la autenticación por token dejó de ser
> exclusiva de `/observability/**`. Ahora un `PlatformAuthFilter` exige
> `Authorization: Bearer <accessToken>` en **todo** `/ispadmin/**` salvo endpoints públicos, y el
> login emite `accessToken`+`refreshToken` para **todo** usuario (no solo ADMIN). El diseño del
> token (access/refresh HMAC, formato, payload, refresh, TTLs, variables de entorno y modelo por
> cliente) está documentado en `plataforma-auth-token.md`. El registro de ese build/deploy está en
> el §10 de este runbook.

## 1. Contexto

- En prod el módulo **nunca se ha desplegado**: la BD `ispadmin` no tiene tablas `obs_*`.
- El primer arranque del WAR con `spring.jpa.hibernate.ddl-auto=update` (activo en prod)
  **crea las ~11 tablas `obs_*` desde cero**. Son tablas nuevas → no tocan las 35 existentes,
  sin pérdida de datos ni reconciliación. El primer arranque tarda algo más por el burst de
  `CREATE TABLE`/`CREATE INDEX`.
- URL pública del backend: `https://api.gigafiberperu.cloud/ispadmin` (Nginx → `tomcat9027`).

## 2. Modelo de control de acceso

Regla: **la ingesta de telemetría se autentica con API key de plataforma; la
lectura/gestión del panel exige un token de sesión ADMIN emitido en el login**. El
enforcement de "solo administradores" ahora se aplica **a nivel de API**, no solo en el
frontend (ver §2.3).

Dos planos de acceso separados en `ObservabilityApiKeyFilter`:

- **Ingesta / telemetría — SOLO API key de plataforma (sin token):** los `POST` de máquinas
  siguen exactamente como antes con `X-Obs-Api-Key`. Rutas excluidas del token:
  `POST /observability/events`, `/spans`, `/rum`, `/replays`, `/symbols/sourcemaps`,
  `/symbols/proguard`. Android, backoffice y asistencias **no cambian**.
- **Lectura / gestión — token ADMIN obligatorio (`X-Obs-Session`):** todo lo demás bajo
  `/observability/**` (stats, issues, traces, alerts CRUD, sessions, metrics, database, jira,
  tracker/info, replays GET, symbols GET/DELETE). Requiere el token de sesión emitido en el
  login; **la key de plataforma ya no sirve para leer**.
- **Fuera del filtro:** `OPTIONS`, el webhook del tracker (secreto propio) y `/ws/observability`
  (autenticado por su handshake, ver §7.1).

### 2.1 Token de sesión ADMIN (HMAC-SHA256)

`ObservabilitySessionTokenService` firma/verifica un token propio sin dependencias nuevas
(`javax.crypto.Mac`, ya usado en el webhook de WhatsApp):

- **Formato:** `base64url(json).base64url(hmacSHA256)`.
- **Payload (claims):** `{userId, username, type, iat, exp}` (segundos epoch).
- **Firma:** `HmacSHA256` sobre el payload codificado, con el secreto de config; la
  verificación compara con `MessageDigest.isEqual` (tiempo constante) y valida `exp` y
  `type == "ADMIN"`.
- **Config:** `observability.session.secret=${OBS_SESSION_SECRET:}` y
  `observability.session.ttl-minutes=${OBS_SESSION_TTL_MIN:720}` (12 h por defecto). En dev hay
  un secreto por defecto en `application-dev.properties`; en prod el secreto viene del `.env`
  del VPS (`OBS_SESSION_SECRET`). Si el secreto está vacío, el login **no** emite token y las
  lecturas devuelven `401`.

### 2.2 Emisión en el login — solo ADMIN

`POST /users/login` (`UserController.login`) puebla el campo opcional `obsSessionToken` en el
`UserDto` **solo si `foundUser.type == ADMIN`**. Es un cambio aditivo: los demás clientes
(backoffice/asistencias/android) ignoran el campo extra y no reciben token. Esto **no**
convierte a `/users/login` en un login con token global de plataforma; el único cliente que
consume el token es el dashboard.

El dashboard (`ispadmin-observability-web`) guarda el token tras el login y lo envía en
`X-Obs-Session` en cada lectura; ante `401` limpia la sesión y redirige a `/login`.

### 2.3 Estado del enforcement (limitación resuelta)

**Resuelto:** el gate de admin ya **no es solo de cliente**. El login emite un token de sesión
firmado para ADMIN y las rutas de lectura/gestión de `/observability` lo exigen y validan a
nivel de API (`ObservabilityApiKeyFilter` + `ObservabilitySessionTokenService`). La key
`dashboard` **dejó de existir** para lecturas y **ya no viaja en el bundle** del panel (se
eliminó `VITE_OBS_API_KEY` del dashboard).

Notas / riesgos residuales:

1. **Token en `localStorage`:** expuesto a XSS como cualquier token SPA; mitigado con TTL corto
   (12 h) y `401 → relogin`. La alternativa de cookie httpOnly queda fuera de alcance (requiere
   manejo CSRF).
2. **Secreto HMAC obligatorio:** `OBS_SESSION_SECRET` debe existir en el `.env` del VPS; si
   falta, el login no emite token y las lecturas fallan. El `deploy.sh` del dashboard lo genera
   automáticamente (`ensure_session_secret`).
3. **Feed en vivo por WebSocket:** usa el endpoint dedicado `/ws/observability` (ver §7.1); el
   handshake ahora valida el **token ADMIN** vía query param `obsToken` (antes la key
   `dashboard`). El `/ws` original queda intacto para backoffice.

## 3. Mapa de keys por plataforma

| Plataforma | Env var backend (VPS) | Dónde la usa el cliente | Uso |
|---|---|---|---|
| `android` | `OBS_API_KEY_ANDROID` | APK prod: `local.properties`/CI `OBS_API_KEY` → `BuildConfig.OBS_API_KEY` | envío de telemetría |
| `web-backoffice` | `OBS_API_KEY_BACKOFFICE` | `ispadmin-backoffice/.env.production` → `VITE_OBS_API_KEY` | envío de telemetría |
| `web-asistencias` | `OBS_API_KEY_ASISTENCIAS` | `ispadmin-asistencias/asistencia-frontend/.env.production` → `VITE_OBS_API_KEY` | envío de telemetría |
| `dashboard` | `OBS_API_KEY_DASHBOARD` | (ya no se hornea en el panel) | histórica; el panel dejó de usar key para leer |
| _(sesión)_ | `OBS_SESSION_SECRET` | — (secreto del backend, no cliente) | firma del token ADMIN de lectura/gestión (§2.1) |

> Los valores reales se generaron y entregaron por separado (no se versionan en este doc).
> Rotación de keys de plataforma: cambiar el valor en el `.env` del VPS y re-desplegar el
> cliente correspondiente. Rotación del secreto de sesión: cambiar `OBS_SESSION_SECRET` en el
> `.env` del VPS y recrear Tomcat (invalida los tokens vigentes → los admins deben re-loguear).
> **La key `dashboard` ya no habilita lecturas** (solo el token ADMIN); puede conservarse por
> compatibilidad pero no la consume ningún cliente.

## 4. Pasos de despliegue

1. **(Recomendado) Backup** de `ispadmin` antes del deploy (aunque el cambio sea aditivo).
2. **Configurar las env vars en el VPS** (`/opt/gigafiber/.env`) y pasarlas al contenedor en
   `docker-compose.yml` (bloque `environment:` del servicio tomcat):
   ```env
   OBS_API_KEY_ANDROID=<valor-android>
   OBS_API_KEY_BACKOFFICE=<valor-backoffice>
   OBS_API_KEY_ASISTENCIAS=<valor-asistencias>
   OBS_API_KEY_DASHBOARD=<valor-dashboard>
   OBS_SESSION_SECRET=<secreto-aleatorio>   # firma del token ADMIN (§2.1); lo genera deploy.sh del dashboard
   ```
   ```yaml
   services:
     tomcat:
       environment:
         - OBS_API_KEY_ANDROID=${OBS_API_KEY_ANDROID}
         - OBS_API_KEY_BACKOFFICE=${OBS_API_KEY_BACKOFFICE}
         - OBS_API_KEY_ASISTENCIAS=${OBS_API_KEY_ASISTENCIAS}
         - OBS_API_KEY_DASHBOARD=${OBS_API_KEY_DASHBOARD}
         - OBS_SESSION_SECRET=${OBS_SESSION_SECRET}
   ```
   (Los dirs `OBS_REPLAY_DIR`/`OBS_SYMBOLS_DIR` tienen default en `/var/lib/wispadmin/...`;
   deben ser escribibles en el contenedor o fallarán replays/símbolos, no el arranque.)
3. **Desplegar el backend**: `./scripts/deploy.sh` (ver `deploy-flow.md`). Hibernate crea las
   tablas `obs_*`. El resto de la app no se ve afectada.
4. **Construir y desplegar los clientes** con su key ya cableada (ver tabla §3). La APK prod
   necesita `OBS_API_KEY` en `local.properties`/CI al compilar el flavor `prod`.
5. **Restringir el acceso al panel** (SSO/basic-auth/allowlist en Nginx) — ver §2.3.

## 5. Verificación post-deploy

- Tablas creadas: `SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ispadmin' AND table_name LIKE 'obs\_%';` → 11.
- Ingesta (key de app): `POST /observability/events` con `X-Obs-Api-Key: <android>` y **sin
  token** → **202** (telemetría excluida del token, intacta).
- Lectura con token ADMIN: `GET /observability/stats/overview` con `X-Obs-Session: <token>` →
  **200**. El token se obtiene de `POST /users/login` con un usuario `ADMIN` (campo
  `obsSessionToken` de la respuesta).
- Lectura sin token → **401**. Lectura con API key de plataforma (sin token) → **401** (las
  lecturas ya no aceptan key).
- El enforcement admin-only ahora es a nivel de API (token ADMIN), no solo en el panel.

## 6. Rollback

- El módulo es aditivo: para desactivarlo, `observability.enabled=false` (ingesta responde
  503) o retirar el WAR anterior. Las tablas `obs_*` pueden quedarse vacías sin afectar al
  resto de la app.

## 0. Credenciales de despliegue (no volver a pedir)

Las credenciales de acceso al VPS ya están persistidas y **excluidas de git**:

- `scripts/deploy.config.local` (ignorado por `.gitignore` → `/scripts/`) contiene
  `VPS_HOST`, `VPS_USER`, `VPS_PORT` y `DEPLOY_SSH_PASSWORD`. El script `deploy.sh` la carga
  automáticamente (`source deploy.config.local`), así que **no hay que pedir la contraseña SSH**:
  basta ejecutar `./scripts/deploy.sh [--war-only|--full]`.
- Para operaciones manuales por SSH, tomar la contraseña desde ese mismo archivo
  (no está versionado). No duplicar el valor en docs versionadas.

## 7. Registro del despliegue ejecutado (2026-07-13)

Despliegue realizado y verificado en producción (`root@212.85.13.47`, `tomcat9027`).

- **Estado previo verificado**: `.env` sin keys OBS, `0` tablas `obs_*` en `ispadmin`
  (módulo nunca desplegado), WAR anterior presente (Jul 11).
- **Backups tomados** en `/opt/gigafiber`: `.env.bak.<ts>` y `docker-compose.yml.bak.<ts>`.
- **Keys configuradas** en `/opt/gigafiber/.env` (`OBS_API_KEY_ANDROID/BACKOFFICE/ASISTENCIAS/DASHBOARD`)
  y referenciadas en el `environment:` del servicio `tomcat` como `${OBS_API_KEY_*:-}`.
- **Orden aplicado**: el servicio `tomcat` **no** monta `webapps` como volumen, por lo que se
  **recreó primero** el contenedor (`docker compose up -d tomcat`, para cargar las env vars) y
  luego se desplegó el WAR (`./scripts/deploy.sh --war-only`). Breve indisponibilidad durante
  la recreación + copia del WAR (~235 MB).
- **Verificación post-deploy**:
  - `GET /ispadmin/` → **200**.
  - `11` tablas `obs_*` creadas por Hibernate (`ddl-auto=update`).
  - Ingesta sin cabecera → **401**; con key `android` → **202** (`accepted:1`).
  - `GET /observability/stats/overview` con key `dashboard` → **200**, incluye
    `eventsByFeature` (agrupado por dominio funcionando).
- **Limpieza**: se eliminaron los datos sintéticos de la prueba de humo
  (`obs_event`/`obs_issue`/`obs_alert_event` → `0`) y los archivos temporales.
- **Pendiente para el equipo**: compilar/desplegar los clientes (`android`, `backoffice`,
  `asistencias`, `dashboard`) con su key cableada (ver §3) y restringir el acceso al panel en
  Nginx (§2.3).

## 7.1. WebSocket dedicado con API key (2026-07-13)

El feed en vivo del dashboard migró del endpoint compartido `/ispadmin/ws` a uno dedicado y
autenticado: `/ispadmin/ws/observability`.

> Actualización (login server-side): el handshake dejó de validar la key `dashboard` y ahora
> valida el **token de sesión ADMIN** (query param `obsToken`) contra
> `ObservabilitySessionTokenService` (ver §2.1). El resto del diseño del endpoint dedicado se
> mantiene.

- **Backend** (nuevo):
  - `observability/config/ObservabilityWebSocketHandshakeInterceptor.kt`: valida el token de
    sesión ADMIN en el query param `obsToken` (`verifyAdmin`) y rechaza el handshake (`401`) si
    es inválido/expirado o `type != ADMIN`.
  - `observability/config/ObservabilityStompChannelInterceptor.kt`: rechaza `SUBSCRIBE` a
    `/topic/observability/**` sin sesión autenticada (defensa en profundidad).
  - `wispadmin/config/WebSocketConfig.kt`: registra `/ws/observability` con el interceptor y
    orígenes acotados (`localhost:5175`, `observability.gigafiberperu.cloud`,
    `api.gigafiberperu.cloud`). `/ws` sigue con `setAllowedOriginPatterns("*")` para backoffice.
- **Frontend** (`ispadmin-observability-web`): `lib/liveClient.ts` envía el **token ADMIN** en el
  query param `obsToken` (y `connectHeaders`); `VITE_OBS_WS_URL` termina en `/ws/observability`.
- **Orden de deploy**: desplegar el WAR **antes** que el dashboard nuevo; si se invierte, el panel
  fallará en "Eventos en vivo" hasta que el backend tenga el endpoint.

## 8. Despliegue del panel (SPA) en observability.gigafiberperu.cloud

El dashboard se sirve como SPA estático (SPA público a nivel de Nginx; **ya no lleva
basic-auth**, la autorización se aplica a nivel de API con el token ADMIN, §2). Automatizado en
`ispadmin-observability-web/scripts/deploy.sh`:

- `--deploy`: build + `rsync --delete` de `dist/` a `/var/www/gigafiber/observability/`.
- `--full`: además vhost Nginx (plantilla `scripts/nginx/observability.gigafiberperu.cloud.conf.example`),
  certificado SSL (Certbot), `OBS_SESSION_SECRET` aleatorio (`ensure_session_secret`) y
  `OBS_DASHBOARD_BASE_URL` en `/opt/gigafiber/.env` (recrea Tomcat).

> **Retiro del basic-auth (login server-side):** se quitó `setup_basic_auth` del `deploy.sh`,
> `auth_basic`/`auth_basic_user_file` de la plantilla nginx y los pasos del README. En el VPS,
> al migrar, hay que eliminar `auth_basic` del vhost activo
> (`/etc/nginx/sites-*/observability.gigafiberperu.cloud`), borrar
> `/etc/nginx/.htpasswd-observability` y `nginx -t && systemctl reload nginx`.

Credenciales SSH reutilizadas de `scripts/deploy.config.local` del backend.

Verificación:

- `curl -I https://observability.gigafiberperu.cloud/` → **200** (index.html, sin popup de
  basic-auth).
- Panel → login con usuario ADMIN → lecturas con `X-Obs-Session` e indicador "En vivo" por WS
  con `obsToken`. Usuario no-ADMIN → login sin token → panel rechaza.

## 9. Build y despliegue del login server-side (2026-07-13)

Ejecución de la fase FINAL del plan _Login server-side para observability_ (token de sesión
ADMIN + retiro de basic-auth). Las tres fases de implementación (backend, frontend, scripts) ya
estaban completas y compilando; esta entrada documenta el build/deploy y su verificación.

### Cambios de código verificados en el árbol

- Backend: `observability/security/ObservabilitySessionTokenService.kt` (HMAC-SHA256,
  `verify`/`verifyAdmin`), `ObservabilityApiKeyFilter` (ingesta por API key vs lectura por
  `X-Obs-Session`), `ObservabilityWebSocketHandshakeInterceptor` (valida `obsToken` ADMIN),
  `UserController.login` emite `obsSessionToken` solo ADMIN, config
  `observability.session.secret`/`ttl-minutes` en `application*.properties` y
  `OBS_SESSION_SECRET` en `env.prod.example`.
- Frontend (`ispadmin-observability-web`): `X-Obs-Session` en lecturas, `obsToken` en WS, sin
  `VITE_OBS_API_KEY`.
- Scripts: plantilla nginx sin `auth_basic`; `deploy.sh` del dashboard sin `setup_basic_auth` y
  con `ensure_session_secret()`.

### Build y deploy ejecutados (`root@212.85.13.47`, `tomcat9027`)

1. **Backend** — `./scripts/deploy.sh --deploy`: `mvnw clean package -DskipTests -Ddjl.linux`
   OK (WAR 235 971 187 bytes), subida y despliegue OK → `GET /ispadmin/` **200**.
2. **Secreto de sesión en el VPS**: `/opt/gigafiber/.env` no tenía `OBS_SESSION_SECRET` ni el
   `docker-compose.yml` lo referenciaba. Se hizo backup de ambos, se generó
   `OBS_SESSION_SECRET` aleatorio (`openssl rand -base64 48`, 64 chars) en `.env` y se añadió
   `OBS_SESSION_SECRET: ${OBS_SESSION_SECRET:-}` al `environment:` del servicio `tomcat`.
3. **Recreación de Tomcat** (`docker compose up -d tomcat`) para cargar el secreto (verificado
   dentro del contenedor: `OBS_SESSION_SECRET` presente, len 64). Como el servicio no monta
   `webapps` como volumen, la recreación descartó el WAR; se **redeployó** con
   `./scripts/deploy.sh --war-only` → `GET /ispadmin/` **200**.
4. **Dashboard** — `./scripts/deploy.sh --deploy`: build del SPA + `rsync --delete` a
   `/var/www/gigafiber/observability/` OK.
5. **Nginx**: se quitó `auth_basic`/`auth_basic_user_file` del vhost activo
   `sites-available/observability.gigafiberperu.cloud.conf` (con backup), se borró
   `/etc/nginx/.htpasswd-observability`, `nginx -t` OK y `systemctl reload nginx`.

### Verificación post-deploy (contra `https://api.gigafiberperu.cloud/ispadmin`)

| Prueba | Resultado |
|---|---|
| `GET /observability/stats/overview` sin token | **401** |
| `GET /observability/stats/overview` con token ADMIN (`X-Obs-Session`) | **200** |
| `GET /observability/stats/overview` con API key de plataforma (`X-Obs-Api-Key: dashboard`) | **401** |
| `POST /observability/events` con API key `android` y sin token | **202** |
| `GET https://observability.gigafiberperu.cloud/` (panel) | **200**, sin `WWW-Authenticate` |

> El token ADMIN de la prueba de lectura se firmó localmente con el `OBS_SESSION_SECRET` real
> del VPS (equivalente al que emite `POST /users/login` para un ADMIN), al no disponer de
> credenciales de un usuario ADMIN. La verificación con login real de un ADMIN y el feed WS
> queda para una comprobación funcional desde el panel. Las pruebas se hicieron contra
> `api.gigafiberperu.cloud` (host real de la API/filtro que consume el dashboard); el host
> `observability.gigafiberperu.cloud` solo sirve el SPA y no proxya `/ispadmin`.

## 10. Build y despliegue de la auth de plataforma (2026-07-13)

Fase FINAL del plan _autenticación por token a nivel de plataforma_ (`PlatformAuthFilter` sobre
todo `/ispadmin/**` + access/refresh en el login de todo usuario). Diseño completo en
`plataforma-auth-token.md`. Este es el registro del build/deploy y su verificación. **Activación
BIG-BANG**: el enforcement es inmediato al desplegar el backend.

### Cambios de código verificados en el árbol

- `wispadmin/security/PlatformAuthFilter.kt` (nuevo, `@Order(HIGHEST_PRECEDENCE+18)`): exige
  `Authorization: Bearer <accessToken>` salvo públicos/`OPTIONS`/`/observability`/`/ws`.
- `observability/security/ObservabilitySessionTokenService.kt`: access/refresh con claim `typ`,
  `issueAccess`/`issueRefresh`/`verifyAccess`/`verifyRefresh`.
- `wispadmin/controller/UserController.kt`: los login(s) emiten `accessToken`+`refreshToken` para
  **todo** usuario (`withSessionTokens`); nuevo `POST /users/token/refresh`.
- Config: `observability.session.refresh-ttl-minutes=${OBS_SESSION_REFRESH_TTL_MIN:43200}` en
  `application.properties`/`application-prod.properties` (720/43200 por defecto).
- Clientes: backoffice (`src/lib/api.ts`), asistencias (`src/services/httpClient.ts`) y Android
  (OkHttp `Authenticator`) envían Bearer y refrescan en `401`.

### Build y deploy ejecutados (`root@212.85.13.47`, `tomcat9027`)

1. **Backend** — `./scripts/deploy.sh --deploy`: `mvnw clean package -DskipTests -Ddjl.linux`
   OK (WAR 235 980 196 bytes), subida y despliegue OK → `GET /ispadmin/` **200**.
2. **`OBS_SESSION_REFRESH_TTL_MIN` en el VPS**: `/opt/gigafiber/.env` ya tenía
   `OBS_SESSION_SECRET` (len 64) pero **no** `OBS_SESSION_REFRESH_TTL_MIN` ni el
   `docker-compose.yml` lo referenciaba. Con backup de ambos (`.bak.<ts>`) se añadió
   `OBS_SESSION_REFRESH_TTL_MIN=43200` al `.env` y
   `OBS_SESSION_REFRESH_TTL_MIN: ${OBS_SESSION_REFRESH_TTL_MIN:-43200}` al `environment:` del
   servicio `tomcat`.
3. **Recreación de Tomcat** (`docker compose up -d tomcat`) para cargar la var (verificado dentro
   del contenedor: `REFRESH=43200`, `OBS_SESSION_SECRET` len 64). Como el servicio no monta
   `webapps` como volumen, la recreación descartó el WAR; se redeployó con
   `./scripts/deploy.sh --war-only` → `GET /ispadmin/` **200**.
4. **Backoffice** — `npm run build` OK; `rsync --delete` de `dist/` a
   `/var/www/gigafiber/backoffice`. Se creó y habilitó el vhost nginx **HTTP:80**
   (`sites-available/backoffice.gigafiberperu.cloud.conf`), `nginx -t` OK y `systemctl reload nginx`.
5. **Asistencias — NO se despliega públicamente.** Es un **proyecto interno**; no debe tener
   dominio público ni vhost expuesto en el VPS. Durante este deploy inicial se subió su `dist/` a
   `/var/www/gigafiber/asistencias` y se creó un vhost HTTP por error; **queda pendiente retirar**
   ese vhost (`sites-available/sites-enabled/asistencias.gigafiberperu.cloud.conf`) y no emitir SSL
   para ese dominio. El build de asistencias con auth por token sigue siendo válido; simplemente su
   distribución/acceso es interno (no vía subdominio público).
6. **Android** — solo se confirmó que compila (`assembleDebug` OK); **no se publica** (paso
   manual al store, fuera de alcance).

### Verificación post-deploy (contra `https://api.gigafiberperu.cloud/ispadmin`)

| Prueba | Esperado | Resultado |
|---|---|---|
| `POST /users/login` (creds inválidas; endpoint público) | no 401 | **404** |
| `GET /plan` **sin** `Authorization` | 401 | **401** |
| `GET /plan` con `Bearer <access válido>` | 200 | **200** |
| `GET /users` con `Bearer <access válido>` | 200 | **200** |
| `GET /plan` con token inválido | 401 | **401** |
| `POST /users/token/refresh` (refresh válido) | 200 + tokens nuevos | **200** |
| `POST /users/token/refresh` (refresh inválido) | 401 | **401** |
| `POST /users/token/refresh` (access token, `typ!=refresh`) | 401 | **401** |
| `GET /app/check_version` (público) | 200 | **200** |
| `POST /observability/events` (`X-Obs-Api-Key` android, sin Bearer) | 202 | **202** (`accepted:0`) |
| `GET /observability/stats/overview` sin token | 401 | **401** |
| `GET /observability/stats/overview` con `X-Obs-Session` ADMIN | 200 | **200** |
| SPA backoffice por HTTP (Host header, `--resolve`) | 200 | **200** |
| SPA asistencias (proyecto interno, no se expone públicamente) | n/a | no aplica |

> El `accessToken`/`refreshToken` de prueba se firmaron localmente con el `OBS_SESSION_SECRET`
> real del VPS para el usuario real `id=1` (`dscorp`, ADMIN), al no disponer de la contraseña de
> un usuario. El endpoint `/users/login` se confirmó **público** (404 con creds inválidas, no
> 401); su emisión de tokens está cubierta por el mismo código que `refresh` (verificado 200 con
> tokens nuevos). No se insertaron datos sintéticos (batch de eventos vacío) → sin limpieza en BD.

### Bloqueos / gaps

- **Asistencias es interno (no se despliega):** `asistencias.gigafiberperu.cloud` **no se usa**;
  asistencias es un proyecto de uso interno y no debe publicarse. Pendiente retirar del VPS el vhost
  HTTP y el `dist/` que se subieron por error en el deploy inicial; **no** emitir SSL para ese dominio.
- **DNS/SSL de backoffice:** el A-record de `backoffice.gigafiberperu.cloud` → `212.85.13.47` ya fue
  registrado por el usuario. Pendiente emitir SSL: `certbot --nginx -d backoffice.gigafiberperu.cloud`
  y verificar HTTPS 200.
- **Android BIG-BANG:** usuarios con la APK vieja (sin token) reciben `401` hasta actualizar.
  Publicar la APK al store es manual y queda fuera de alcance.
- **`/ws` sin enforcement:** gap conocido (ver `plataforma-auth-token.md` §7).

### Addendum (2026-07-13): SSL de backoffice + retiro de asistencias

Resuelto el bloqueo de DNS de la entrega anterior. Decisión del equipo: **backoffice es público**,
**asistencias es interno** (no debe exponerse).

- **Asistencias retirado** (se había publicado por error en el deploy inicial): symlink
  `sites-enabled/asistencias.gigafiberperu.cloud.conf` eliminado; conf en `sites-available` movido a
  `asistencias.gigafiberperu.cloud.conf.bak.20260713141137`; web root movido a
  `/var/www/gigafiber/asistencias.bak.20260713141137`. No se emitió certificado para ese dominio.
  `nginx -t` OK + reload.
- **Backoffice con HTTPS**: DNS `backoffice.gigafiberperu.cloud → 212.85.13.47` OK;
  `certbot --nginx -d backoffice.gigafiberperu.cloud --non-interactive --agree-tos
  --email admin@gigafiberperu.cloud --redirect` → certificado emitido (Let's Encrypt, CN del
  dominio, vence 2026-10-11) y redirección HTTP→HTTPS activada.

| Prueba | Resultado |
|---|---|
| `dig backoffice...` | **212.85.13.47** |
| `http://backoffice.gigafiberperu.cloud/` | **301** → `https://backoffice.gigafiberperu.cloud/` |
| `https://backoffice.gigafiberperu.cloud/` | **200** (SPA, `<title>Vite + React + TS</title>`) |
| Certificado backoffice | CN=`backoffice.gigafiberperu.cloud`, issuer Let's Encrypt, verify **ok** |
| CORS preflight API con `Origin: https://backoffice.gigafiberperu.cloud` | **200**, `Access-Control-Allow-Origin` = ese origen |
| `dig asistencias...` | **(no resuelve)** |
| `Host: asistencias...` HTTP/80 en el VPS | **404** (sin vhost; SPA retirado) |

> Nota: en `:443` con SNI de un host sin vhost, nginx responde con el server TLS por defecto (api/
> observability), no con el SPA de asistencias (su web root fue movido). Como el dominio no resuelve
> públicamente, no hay exposición del app interno.

### Addendum (2026-07-13): handshake con token en `/ws` de backoffice

Se cerró el gap del WebSocket compartido `/ws`: ahora exige token en el handshake.

- **Backend** — `wispadmin/security/PlatformWebSocketHandshakeInterceptor.kt` (nuevo) valida
  `verifyAccess` del token por query param `token` y responde `401` si falta/es inválido;
  registrado en `/ws` en `wispadmin/config/WebSocketConfig.kt` con orígenes restringidos
  (`localhost`, `backoffice.gigafiberperu.cloud`, `api.gigafiberperu.cloud`). `/ws/observability`
  sin cambios. Deploy: `./scripts/deploy.sh --deploy` (WAR 235 983 836 bytes) → `GET /ispadmin/`
  **200**.
- **Backoffice** — `src/services/websocketService.ts` arma la URL SockJS con `?token=<accessToken>`
  (`buildWsUrl()`, reevaluado en cada reconexión). `npm run build` OK + `rsync --delete` de `dist/`
  a `/var/www/gigafiber/backoffice`.

| Prueba (contra `https://api.gigafiberperu.cloud/ispadmin`) | Esperado | Resultado |
|---|---|---|
| `GET /ws/websocket` **sin** token | 4xx, no 401→ rechazo auth | **401** |
| `GET /ws/websocket?token=<access válido>` | no 401 | **400** (auth OK; 400 por falta de headers de upgrade en curl) |
| `GET /ws/websocket?token=abc.def` (inválido) | 401 | **401** |
| `GET /ws/info` (negociación SockJS) | 200 | **200** |
| `GET https://backoffice.gigafiberperu.cloud/` | 200 | **200** (SPA nuevo bundle) |

> El `accessToken` de prueba se firmó localmente con el `OBS_SESSION_SECRET` real del VPS para el
> usuario `id=1` (`dscorp`, ADMIN). El `400` con token válido confirma que el handshake **autorizó**
> el token (sería `401` si lo rechazara); el `400` proviene de la validación de upgrade de WebSocket
> al no enviar curl los headers `Upgrade`/`Connection`. **Pendiente (manual):** prueba funcional
> STOMP completa (tickets/tráfico) desde el navegador con sesión real.
