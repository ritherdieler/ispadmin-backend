# Deploy staging — stack 360 unificado (2026-09-16)

Prod no se tocó. Recolección lab on.

| Pieza | Ref | Destino | Health |
|-------|-----|---------|--------|
| Core WAR | `1.0.3+f0f9075` `cursor/staging-unify-360-d0ce` | `tomcat-staging` `:8081` `/ispadmin-staging/` | HTTP 200 |
| SPA | `ac586b9` `cursor/staging-unify-360-d0ce` | `/var/www/gigafiber/backoffice-staging/` | vhost `backoffice-staging.gigafiberperu.cloud` HTTP 200 |

## Backend incluido (ya apilado en `f0f9075`)

live-readings, live-socket in-process, accessMode XOR, CSV TR-069 retirado, place SRID, e2e collection always on, gate `pilot_enabled` eliminado.

## SPA incluido (`develop` +)

- `a6b8f14` panel 360 host ∧ (IP ∨ PPPoE)
- `bdff666` sin banner «Recolección desactivada»
- `0ad068a` sin importador CSV TR-069
- `ac586b9` tsc hostDevice `{ id }`

Bundle remoto: `ServiceHealthPage-CKxF_11U.js` sin el banner; `SubscriptionTrafficPanel-C4Q1lrWn.js` con `PPPOE_DYNAMIC`; chunk `Tr069Profiles-*` ausente.

## Comandos

```bash
./scripts/deploy-disabled-modules-preflight.sh --env staging
# unpacked Gradle 8.13
gradle :core:war :core:tomcatLibs -Pdjl.linux
cp core/build/libs/ispadmin.war target/ispadmin-staging.war
./scripts/deploy.sh --war-only --env staging --with oltgateway,traffic,acs,servicehealth

npm run build:staging
# rsync --delete dist/ → VPS /var/www/gigafiber/backoffice-staging/
```

Primer WAR desde worktree limpio falló al boot: faltaba `firebase_service_account_prod.json` (gitignore). Se copió solo al árbol de build, no se commiteó. Redeploy OK.

API: `https://api.gigafiberperu.cloud/ispadmin-staging/`
SPA: host nginx `backoffice-staging.gigafiberperu.cloud` (desde la Mac el path `/backoffice-staging/` en `gigafiberperu.cloud` sirve la landing).
