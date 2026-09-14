# Auth, permisos, sesión — SmartOLT → IspManager

## 1. Tres credenciales distintas

| Canal | Mecanismo | Dónde vive | Uso |
|-------|-----------|------------|-----|
| UI browser | Cookie de sesión HTTP (`{subdomain}_session`, HttpOnly, Secure, SameSite=Lax, Max-Age 7200) | login web | HTML + AJAX UI |
| UI AJAX | Header `X-Token: config.X_TOKEN` | JS inyectado en página | Lecturas `/api/onu/...` desde UI |
| API pública | Header `X-Token: <API key>` | Settings → API key | Integraciones (WispAdmin, scripts) |

**Importante:** el `X_TOKEN` de sesión UI **no** es la API key de Settings. En docs siempre `***`.

Set-Cookie observado en respuestas API (también crea sesión efímera):

```text
Set-Cookie: gigafiberperu_session=***; Max-Age=7200; path=/; secure; HttpOnly; SameSite=Lax
```

## 2. Roles / permisos UI

Modelo inferido (DB pack + UI):

| Rol | Capacidades típicas |
|-----|---------------------|
| `admin` | todo + General/Users/API key |
| `installer` | authorize, ver ONUs recientes (setting `installer_visibility_days`) |
| `readonly` | listados / view / graphs |

Flags / UI observados (2026-07-17):

- Batch actions: modal “**Permission Required** — You do not have permission to perform batch actions on ONUs.” (usuario `admins` sin flag batch).
- Groups + **restriction groups** (data access) en `/auth`.
- 2F Auth por usuario (enable OTP path `/auth/enable_otp/{id}`).
- Escrituras destructivas (delete/reboot/resync) restringidas por rol.
- API key rotation solo admin (`/general/listing/api_key`).

IspManager: tabla `app_user.role` + opcional `user_permission` granular (batch, export, mismatch auto-fix).

## 3. localStorage / preferencias cliente

| Key | Efecto |
|-----|--------|
| `smartolt-wide-tables` | `1` = tablas anchas |
| `smartolt-night` | `1` = night mode |
| `smartolt-theme-local` | `night` \| `light` \| `system` (override DB `SMARTOLT_DB_THEME`) |

Deep links: `#batch-actions`, `#more-filters`.

## 4. API Logs y rate limiting

- UI: General → API Logs (`/api_stats`).
- Límites mostrados en General: 1000/h, 10/s, burst &gt;15/s; heavy OLT detail 30/10min/OLT.
- Headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.
- 429 + `Retry-After`.
- Modelo DB: `api_key`, `api_request_log`.

## 5. Allowed IPs / General settings

Settings General (UI capturada):

| Setting | Valor gigafiberperu |
|---------|---------------------|
| Title | SMARTOLT |
| Timezone | America/Lima |
| IPs allowed | Allowed from anywhere |
| Installer ONU visibility (days) | 5 |
| Login language | English |

Umbrales señal (default docs): Warning -30 / Critical -32 dBm Rx OLT.

## 6. Implicaciones IspManager

1. Separar session JWT/cookie de API keys hasheadas.
2. No reutilizar API key como token de UI embebido en JS si se puede evitar (SmartOLT lo hace con `config.X_TOKEN` — riesgo XSS).
3. Permiso batch explícito.
4. Auditoría: user_id + ip + source `ui|api|system`.
5. Enmascarar secretos OLT/PPPoE/VoIP en UI.
