# Deploy staging — WAR acumulado + logs e2e (2026-09-16)

```bash
./scripts/deploy-disabled-modules-preflight.sh --env staging
# hook de Cursor intercepta ./gradlew en el chat; el WAR se empaquetó vía script
./scripts/deploy.sh --war-only --env staging --with oltgateway,traffic,acs,servicehealth
```

Release `1.0.3+4ee17ee` (`cursor/e2e-cleanup-console-logs-3446` @ `4ee17eeb15ac45fbc8864a8ae6ad621eae016f70`).
URL pública: `https://api.gigafiberperu.cloud/ispadmin-staging/`
Tomcat-staging `:8081` `/ispadmin-staging/` HTTP 200. Prod no se tocó. Backoffice no se subió. Sin smoke ni e2e post-deploy.

Preflight: cadena FIBER/TR-069 completa (sin `--yes`). Recolección lab on (gate quitado + `service.health.enabled=true` en overlay `stg`).

Incluye el stack acumulado de la rama (WAN XOR, accessMode, 360, cleanup logs) más `scripts/e2e_console.sh` cableado en hard-cleanup y status TR-069.
