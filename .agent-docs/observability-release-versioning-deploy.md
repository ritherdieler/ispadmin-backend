# Versionado por release en deploy (semver + git)

Implementación de la **Parte 2** del plan _UX comparación observability_: versionado
automático `{semver}+{shortSha}` (ej. `1.4.2+abc1234`) generado en cada deploy, escrito en el
`.env` del backend como `APP_RELEASE` y registrado como deploy event en el hub de
observabilidad. No se ejecutaron deploys reales en esta entrega (solo scripts + validación de
sintaxis con `bash -n`).

## Cómo se genera la versión

`ispadmin-backend/scripts/version.sh` (source-able desde cualquier cwd):

- `RELEASE_SEMVER`: último tag git sin la `v` (`git describe --tags --abbrev=0`); `0.0.0` si no
  hay tags o no es repo git.
- `RELEASE_SHA`: `git rev-parse --short=7 HEAD`; `unknown` si no aplica.
- `RELEASE_VERSION`: `${RELEASE_SEMVER}+${RELEASE_SHA}`.

Robusto sin `git` instalado ni tags. El `git` se evalúa en el cwd del que hace `source`, de modo
que cada repo produce su propia versión (el backend la suya, observability-web la suya aunque
reuse el mismo `version.sh`).

## Backend (`ispadmin-backend/scripts/deploy.sh`)

Nuevas funciones y orden en los modos `deploy`/`war-only`/`full`:

1. `load_release_version` → `source scripts/version.sh`.
2. `update_release_env` → escribe/actualiza `APP_RELEASE=$RELEASE_VERSION` en el `.env` remoto
   (`BACKEND_ENV_FILE`, default `/opt/gigafiber/.env`) con el patrón idempotente de
   `update_backend_env()` de observability-web (`grep -q '^APP_RELEASE='`, backup `.bak.<ts>`,
   `sed -i` o append) y recrea Tomcat (`docker compose up -d tomcat`).
3. `deploy_war` (existente) copia el WAR **después** de recrear Tomcat.
4. `register_deploy` → `POST $OBS_BASE_URL/observability/releases` con
   `X-Obs-Api-Key: $OBS_API_KEY` y body `{platform:"backend", release, semver, gitSha, notes:null}`.
   No fatal: si el curl falla, warn y continúa.

> **Orden crítico:** el servicio `tomcat` no monta `webapps` como volumen (ver
> `observabilidad-prod-deploy-runbook.md` §7/§9), así que recrear el contenedor descarta el WAR.
> Por eso `update_release_env` (que recrea Tomcat para cargar `APP_RELEASE`) va **antes** de
> `deploy_war`, no después. El modo `setup` no despliega WAR y no registra release.

## Observability-web (`ispadmin-observability-web/scripts/deploy.sh`)

1. `resolve_release_version` → `source ../ispadmin-backend/scripts/version.sh`; si no existe,
   fallback inline (`0.0.0+sha`).
2. `build_spa` exporta `VITE_APP_VERSION=$RELEASE_VERSION` antes de `npm run build`.
3. Tras `upload_spa`, `register_deploy` → `POST $OBS_BASE_URL/observability/releases` con
   `platform:"observability-web"`. No fatal.

## Variables de config nuevas

Añadidas a ambos `scripts/deploy.config.example` (definir el valor real en el `.local`):

| Variable | Dónde | Valor sugerido |
|---|---|---|
| `OBS_BASE_URL` | backend + observability-web | `https://api.gigafiberperu.cloud/ispadmin` |
| `OBS_API_KEY` | backend + observability-web | valor de `OBS_API_KEY_DASHBOARD` del backend |
| `BACKEND_ENV_FILE` | backend | `/opt/gigafiber/.env` |

Si `OBS_BASE_URL`/`OBS_API_KEY` quedan vacíos, el deploy **no falla**: solo omite el registro
del deploy event (warn).

## Contrato del API (implementado en Parte 1 — ver `observability-release-comparacion-backend.md`)

`POST {OBS_BASE_URL}/observability/releases`, header `X-Obs-Api-Key: <key>`, body
`{ "platform": "backend"|"observability-web", "release": "1.4.2+abc1234", "semver": "1.4.2",
"gitSha": "abc1234", "notes": null }` → **201**.

## Deploy real y verificación en prod (2026-07-13)

Primer deploy real usando estos scripts (ya no solo `bash -n`):

- Tags `v1.0.0` creados en backend y observability-web → `RELEASE_VERSION` pasó de `0.0.0+sha`
  a `1.0.0+sha`.
- **observability-web**: `1.0.0+b99097d` (build + rsync + deploy event 201).
- **backend**: `1.0.0+1133758`. El primer intento falló al compilar
  (`TraceSummaryDto` sin `release` rompía `ObsQueryService`/`ObsSessionQueryService`); tras el fix
  (ver `observability-release-comparacion-backend.md` §Fix) compiló, recreó Tomcat, redeployó el
  WAR y registró el deploy event.
- `obs_deploy_event` contiene 4 filas (backend/web × 0.0.0/1.0.0).
- Spans backend nuevos etiquetados con `app_release=1.0.0+1133758` (confirmado en
  `obs_span` y vía API de trazas/detalle/releases con token de sesión admin emitido con
  `OBS_SESSION_SECRET`).

> El `.env` remoto ya mapea `APP_RELEASE` en `docker-compose.yml` (servicio `tomcat`), añadido de
> forma idempotente en un deploy previo; sin ese mapeo el contenedor no toma la variable aunque
> esté en `.env`.

## Guardas anti-duplicado en deploy (2026-07-13)

Para evitar desplegar/registrar una versión ya existente (p. ej. re-deploy sin cambios o
con cambios sin commitear, donde el SHA no cambia), el deploy backend valida **antes** de
compilar/subir el WAR:

1. **Árbol de trabajo limpio** (`scripts/version.sh` exporta `RELEASE_DIRTY`): calculado con
   `git status --porcelain`. En `load_release_version()`, si `RELEASE_DIRTY=1` el deploy
   **aborta** pidiendo commit (el SHA no representaría el código real). Se mantiene el
   cálculo basado en `cwd` para que observability-web siga generando su propia versión.
2. **Versión no registrada** (`check_version_not_registered()` en `scripts/deploy.sh`):
   consulta `GET {OBS_BASE_URL}/observability/releases?platform=backend` con
   `X-Obs-Api-Key` y, si `RELEASE_VERSION` ya aparece, **aborta**. Si `OBS_BASE_URL`/
   `OBS_API_KEY` faltan o la consulta falla, solo warn y continúa (no bloquea).

Ambas guardas corren al inicio de los modos `deploy`, `war-only` y `full`, antes del build.

### Acceso de lectura para la guarda

`GET /observability/releases` requería token de sesión admin (`X-Obs-Session`), que el
script no tiene. Se relajó `ObservabilityApiKeyFilter` para aceptar además la **API key de
ingesta** (`X-Obs-Api-Key`) en el **GET** de `/observability/releases` (método
`isReleasesReadPath`), con fallback a sesión para el dashboard. El POST de registro sigue
igual (ingest path). La guarda de colisión solo es efectiva a partir del release que
incluye este cambio de filtro; en deploys con backend anterior, el GET devuelve 401 y la
guarda continúa con warning.

## Pendiente para clientes (backoffice / asistencias)

Sus deploys **no** se tocaron. Para alinear la versión de sus trazas/eventos frontend, deberían
pasar `VITE_OBS_RELEASE=$RELEASE_VERSION` en el build (haciendo `source` del mismo `version.sh`).
