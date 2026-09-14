# Autenticación por token a nivel de plataforma (2026-07-13)

Autenticación obligatoria por token en **todo** `/ispadmin/**` (salvo endpoints públicos).
Reemplaza el modelo previo en el que solo `/observability/**` exigía sesión: ahora la API
completa (usuarios, planes, pagos, etc.) requiere un `accessToken` válido. El token se emite en
el login para **todo** usuario (no solo ADMIN) y se renueva con un `refreshToken`.

## 1. Arquitectura del token (HMAC-SHA256)

El servicio `observability/security/ObservabilitySessionTokenService.kt` firma y verifica un
token propio, sin dependencias nuevas (`javax.crypto.Mac`). Se reutiliza para toda la
plataforma (no solo observability).

- **Formato:** `base64url(json).base64url(hmacSHA256)` (dos partes separadas por `.`; NO es un
  JWT estándar de 3 partes).
- **Payload (claims):** `{userId, username, type, iat, exp, typ}`.
  - `userId`: id del usuario (Int, puede ser null).
  - `username`: nombre de usuario.
  - `type`: rol del usuario (`ADMIN`, etc.), usado por `verifyAdmin`.
  - `iat` / `exp`: emisión y expiración en **segundos epoch**.
  - `typ`: discrimina el tipo de token: `"access"` o `"refresh"`.
- **Firma:** `HmacSHA256` sobre el payload codificado (`base64url`) con el secreto
  `observability.session.secret`. La verificación:
  1. separa payload y firma;
  2. recomputa el HMAC y compara con `MessageDigest.isEqual` (tiempo constante);
  3. deserializa los claims y valida `exp > now`.
- **Access vs refresh:**
  - `issueAccess` → `typ="access"`, TTL `ttl-minutes` (720 min = 12 h).
  - `issueRefresh` → `typ="refresh"`, TTL `refresh-ttl-minutes` (43200 min = 30 días).
  - `verifyAccess` acepta `typ == "access"` **o** `typ == null` (compatibilidad con tokens
    previos sin discriminador); `verifyRefresh` exige `typ == "refresh"`; `verifyAdmin` exige
    access válido **y** `type == "ADMIN"`.

## 2. `PlatformAuthFilter`

`wispadmin/security/PlatformAuthFilter.kt`, `@Order(HIGHEST_PRECEDENCE + 18)`,
`OncePerRequestFilter`.

- **Enforcement:** exige `Authorization: Bearer <accessToken>` en todo request no excluido.
  Extrae el token, llama `verifyAccess`; si es `null` responde `401` con body JSON
  `{"error":"unauthorized","message":"Missing or invalid Authorization bearer token"}`.
- **Atributos de request** poblados en éxito (para downstream): `authUserId`, `authUserType`,
  `authUsername`.
- **Autorización por rol (Fase 0 CRM, 2026-08-02):** tras un access token válido, si la ruta
  está protegida por `CrmAccessPolicy` (`/whatsapp/**` excepto webhook, y futuros `/crm/**`),
  exige `authUserType` ∈ `{SECRETARY, ADMIN}`. Si no, responde `403` JSON
  `{"error":"forbidden","message":"Requires SECRETARY or ADMIN role"}`. El webhook
  `/whatsapp/webhook` sigue público. Detalle: [`crm-omnicanal-fase0-seguridad-fundaciones.md`](./crm-omnicanal-fase0-seguridad-fundaciones.md).
- **`shouldNotFilter`** (bypass total del filtro):
  - método `OPTIONS` (CORS preflight),
  - rutas que empiezan/contienen `/observability` → las gestiona
    `ObservabilityApiKeyFilter` (ingesta por API key vs lectura por `X-Obs-Session`; ver
    `observabilidad-prod-deploy-runbook.md`),
  - rutas que contienen `/ws` → **websockets sin enforcement por ahora** (gap conocido, §7),
  - rutas públicas (`isPublicPath`).
- **CORS:** el 401 short-circuit exige que `CorsFilter` corra **antes** (registration
  `Ordered.HIGHEST_PRECEDENCE` en `CorsConfig`). Si el filtro CORS queda al final de la
  cadena, el navegador reporta fallo CORS en lugar del 401. Detalle:
  [`fix-cors-401-platform-auth-2026-07-23.md`](./fix-cors-401-platform-auth-2026-07-23.md).

## 3. Endpoints públicos (sin token)

`isPublicPath` + reglas de `shouldNotFilter`:

| Ruta | Motivo |
|---|---|
| `/` (raíz) | health/landing de Tomcat |
| `/users/login` | login por credenciales (emite tokens) |
| `/users/login/face` | login facial por descriptor (emite tokens) |
| `/users/login/face/photo` | login facial por foto (emite tokens) |
| `/users/token/refresh` | renovación de tokens (valida el refresh, no el access) |
| `/whatsapp/webhook` | webhook Meta (secreto propio) |
| `/izipay/*` | callbacks de pasarela de pago |
| `/actuator/*` | health/metrics de Spring |
| `/app/check_version` | chequeo de versión de la app Android |
| `/fcm/save-token` | registro de token FCM |
| `OPTIONS` (cualquiera) | CORS preflight |
| `/observability/*` | filtro propio (`ObservabilityApiKeyFilter`) |
| `/ws*` | websockets sin enforcement (gap conocido) |

## 4. Emisión en el login y flujo de refresh

`wispadmin/controller/UserController.kt`:

- **Login** (`/users/login`, `/users/login/face`, `/users/login/face/photo`): al autenticar,
  `withSessionTokens(user)` puebla en el `UserDto`:
  - `accessToken` = `issueAccess(user.id, user.username, user.type)`,
  - `refreshToken` = `issueRefresh(...)`,
  - `obsSessionToken` = `access` **solo si** `type == ADMIN` (compatibilidad con el dashboard de
    observability, que lo envía en `X-Obs-Session`).
  Aplica a **todo** usuario, no solo ADMIN.
- **Refresh** (`POST /users/token/refresh`, body `{refreshToken}`):
  1. `verifyRefresh(refreshToken)`; si inválido/expirado o `typ != refresh` → `401`.
  2. resuelve `userId` de los claims y recarga el usuario; si no existe → `401`.
  3. emite **nuevo** access + refresh y responde `200` con `{accessToken, refreshToken}`.
  - Es rotación de refresh (cada refresh entrega un refresh nuevo). No hay lista de revocación
    server-side: la invalidación depende del `exp` (30 días) o de rotar `OBS_SESSION_SECRET`.

## 5. TTLs y variables de entorno

Config en `application.properties` / `application-prod.properties`
(`ObservabilityProperties.SessionProperties`):

| Propiedad | Env var (VPS) | Default | Significado |
|---|---|---|---|
| `observability.session.secret` | `OBS_SESSION_SECRET` | *(vacío)* | secreto HMAC de firma. Si está vacío, el login **no** emite tokens y toda lectura da `401`. |
| `observability.session.ttl-minutes` | `OBS_SESSION_TTL_MIN` | `720` (12 h) | TTL del **access** token. |
| `observability.session.refresh-ttl-minutes` | `OBS_SESSION_REFRESH_TTL_MIN` | `43200` (30 d) | TTL del **refresh** token. |

En dev, `application-dev.properties` trae un secreto por defecto
(`dev-obs-session-secret-change-me-...`) y los mismos TTLs. En prod, las tres provienen del
`.env` del VPS (`/opt/gigafiber/.env`) y se inyectan al contenedor `tomcat` vía el bloque
`environment:` del `docker-compose.yml`.

## 6. Modelo por cliente

Todos los clientes: guardan `accessToken`+`refreshToken` tras el login, envían
`Authorization: Bearer <accessToken>` en cada request (salvo rutas públicas), y ante `401`
llaman una sola vez a `/users/token/refresh` (single-flight / cola de peticiones concurrentes),
reintentan la request original y, si el refresh falla, limpian sesión y redirigen a login.

| Cliente | Repo | Mecanismo | Almacenamiento |
|---|---|---|---|
| Android | `IpsAdmin-android app` | OkHttp `Authenticator` (refresh en `401`) + interceptor Bearer | almacenamiento local de la app |
| Backoffice | `ispadmin-backoffice` | interceptores axios (`src/lib/api.ts`); refresh con cola `pendingRequests` | `localStorage`: `access_token`, `refresh_token` |
| Asistencias (interno) | `ispadmin-asistencias/asistencia-frontend` | interceptores axios (`src/services/httpClient.ts`); refresh single-flight (`refreshPromise`) | `localStorage`: `auth_token`, `refresh_token` |

> **Asistencias es un proyecto de uso interno**: adopta la misma auth por token, pero **no se
> despliega en un subdominio público** (`asistencias.gigafiberperu.cloud` no se usa) ni lleva SSL.
> Su distribución/acceso es interno.

El login web envía `password` como hash SHA-384 (asistencias) según su contrato; el backend
compara con el hash almacenado. El campo `obsSessionToken` solo lo consume el dashboard de
observability (usuarios ADMIN).

## 7. Riesgos y gaps conocidos

- **Activación BIG-BANG (riesgo principal):** al desplegar el backend el enforcement es
  **inmediato**. Los clientes web nuevos ya envían token, pero **usuarios con app Android vieja
  (sin token) quedan bloqueados con `401`** hasta actualizar. Publicar la APK al store es un
  paso manual **fuera de alcance**; este despliegue solo cubre backend + web. Mitigación futura:
  publicar la APK y/o una ventana de gracia/feature-flag antes del enforcement.
- **WebSocket `/ws` (resuelto 2026-07-13):** el `PlatformAuthFilter` sigue excluyendo las rutas
  con `/ws` (el enforcement del token se hace en el handshake, no en el filtro HTTP). El endpoint
  compartido de backoffice `/ws` **ya exige token**: `PlatformWebSocketHandshakeInterceptor`
  (registrado en `WebSocketConfig`) valida el `accessToken` recibido por query param `token`
  (`verifyAccess`) y rechaza el handshake con `401` si falta o es inválido; además restringe
  orígenes (`localhost`, `backoffice.gigafiberperu.cloud`, `api.gigafiberperu.cloud`). El cliente
  backoffice (`src/services/websocketService.ts`) arma la URL SockJS con `?token=<accessToken>`
  (`buildWsUrl()`), reevaluado en cada (re)conexión. El feed del dashboard sigue en
  `/ws/observability` con su handshake propio (token ADMIN por `obsToken`), sin cambios.
- **Token en `localStorage` (web):** expuesto a XSS como cualquier SPA; mitigado con TTL de
  access corto (12 h) y `401 → refresh → relogin`. Cookie httpOnly queda fuera de alcance
  (requiere manejo CSRF).
- **Sin revocación server-side:** invalidar sesiones activas implica rotar `OBS_SESSION_SECRET`
  (invalida **todos** los tokens vigentes → todos deben re-loguear) o esperar el `exp`.
- **Secreto obligatorio en prod:** si `OBS_SESSION_SECRET` falta o queda vacío, el login no
  emite tokens y **toda** la API responde `401`. Debe estar presente en `/opt/gigafiber/.env`.
