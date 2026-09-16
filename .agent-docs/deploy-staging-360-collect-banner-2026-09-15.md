# Deploy staging — banner 360 recolección on (2026-09-15)

```bash
# hook de Cursor intercepta ./gradlew; mismo comando que deploy.sh
gradle :core:war :core:tomcatLibs -Pdjl.linux
./scripts/deploy.sh --war-only --env staging --with oltgateway,traffic,acs,servicehealth
```

Release `1.0.3+61fdeef`. Tomcat-staging `:8081` `/ispadmin-staging/` HTTP 200. Prod no se tocó.

Preflight: cadena FIBER/TR-069 completa (sin `--yes`).

## Validación API

`GET /subscription/12/service-health` (VSOL PPPoE, e2e Espresso, cleanup skip):

| Campo | Valor |
|-------|--------|
| `pilot_enabled` | `true` |
| `actions_enabled` | `true` |
| `evaluated_at` | `2026-09-16T03:29:27Z` (post-restart) |
| `identity.lab` | `false` (el scope `stg` gana igual) |
| `identity.ONU` | `VSOL0031C0B6` |
| `identity.PPPOE` | `gf12` |
| `identity.ACS` | `B46415-V2804AX15T-12345B4641531C0B6` |

Un segundo GET sigue en `true` (decorate pisa snapshot cacheado).

Tráfico STALE / «sin IP» en PPPoE es XOR de directorio, no este banner.

Detalle del bug: [staging-e2e-collection-banner-false-2026-09-15.md](./staging-e2e-collection-banner-false-2026-09-15.md).
