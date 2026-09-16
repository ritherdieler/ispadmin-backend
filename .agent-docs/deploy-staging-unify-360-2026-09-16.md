# Deploy staging — stack 360 unificado (2026-09-16)

Prod no se tocó. Recolección lab on.

| Pieza | Ref | Destino | Health |
|-------|-----|---------|--------|
| Core WAR | `1.0.3+f0f9075` `cursor/staging-unify-360-d0ce` | `tomcat-staging` `:8081` `/ispadmin-staging/` | HTTP 200 |
| Backoffice | `ispadmin-backoffice` `--mode staging` | **Solo Vite en la Mac.** No se publica en el VPS | — |

## Backend incluido (ya apilado en `f0f9075`)

live-readings, live-socket in-process, accessMode XOR, CSV TR-069 retirado, place SRID, e2e collection always on, gate `pilot_enabled` eliminado.

## Backoffice

El front `ispadmin-backoffice` staging **no se despliega**. Un rsync a `/var/www/gigafiber/backoffice-staging/` en este turno fue un error de procedimiento. Regla: `gigafiber/.cursor/rules/backoffice-staging-solo-local.mdc`.

Árbol unificado local: `cursor/staging-unify-360-d0ce` (`a6b8f14` accessMode, `bdff666` sin banner, `0ad068a` sin CSV TR-069).

## Comandos

```bash
./scripts/deploy-disabled-modules-preflight.sh --env staging
# unpacked Gradle 8.13
gradle :core:war :core:tomcatLibs -Pdjl.linux
cp core/build/libs/ispadmin.war target/ispadmin-staging.war
./scripts/deploy.sh --war-only --env staging --with oltgateway,traffic,acs,servicehealth

# backoffice: Vite local, no rsync
npx vite --mode staging --host 127.0.0.1 --port 3000 --strictPort
```

Primer WAR desde worktree limpio falló al boot: faltaba `firebase_service_account_prod.json` (gitignore). Se copió solo al árbol de build, no se commiteó. Redeploy OK.

API: `https://api.gigafiberperu.cloud/ispadmin-staging/`
