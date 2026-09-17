# Deploy staging — Traffic develop + e2e ZTE (2026-09-16)

Prod no se tocó (`tomcat9027` Up, `https://api.gigafiberperu.cloud/ispadmin/` HTTP 200).
Backoffice no se subió al VPS. `traffic.poll.enabled=true` en el WAR.

## Rama

`develop` @ `a1f0dfe` (release `1.0.3+a1f0dfe`).
Worktree limpio salvo este commit de boot. Sin push a `origin`.

Incluye Traffic ya en develop:

| SHA | Commit |
|-----|--------|
| `e2849b6` | feat(traffic): execute all MikroTik I/O over Traffic HTTP |
| `5b82a19` | feat(traffic): cut live-readings RouterOS load for Ahora |
| `7936d57` | feat(traffic): lock legacy poll and limit directory and series |
| `605914d` | feat(traffic): allowlist RouterOS paths Core already uses |
| `af7c810` | feat(traffic): serve 360 live-readings over HTTP |
| `a1f0dfe` | fix(olt): open SmartOLT mgmt beans for CGLIB |

## Comando

```bash
./scripts/deploy-disabled-modules-preflight.sh --env staging
FORCE_WAR_REBUILD=1 ./scripts/deploy.sh --deploy --env staging --with oltgateway,traffic,acs,servicehealth
# primer --deploy: tests PASS, WAR 605914d; docker compose up -d tomcat-staging colgó el SSH
# tras recreate, webapps staging vacío (404). Prod intacto.
# boot del WAR 605914d falló: Cannot subclass final class SmartOltMgmtVlanPolicy
# open-class fix en develop (a1f0dfe), package + --war-only
./scripts/deploy.sh --war-only --env staging --with oltgateway,traffic,acs,servicehealth
```

Preflight: cadena FIBER/TR-069 completa. `traffic.poll.enabled=true`.

## Resultado

| Check | Resultado |
|-------|-----------|
| `GET https://api.gigafiberperu.cloud/ispadmin-staging/` | 200 `Hello World!` |
| Tomcat-staging `:8081` `/ispadmin-staging/` | HTTP 200 |
| Release | `1.0.3+a1f0dfe` |
| Prod `/ispadmin/` | 200 (no se tocó) |

WAR único Core con `oltgateway`, `traffic`, `acs`, `servicehealth` in-process.
