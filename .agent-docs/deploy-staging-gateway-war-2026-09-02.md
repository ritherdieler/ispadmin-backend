# Deploy staging: WAR Gateway aparte — 2026-09-02

Release local: `1.0.3+0d0af94`. Comando:

```bash
./scripts/deploy.sh --env staging --with oltgateway,servicehealth,netdiag,traffic
```

## Schema

`RENAME TABLE` de 15 `olt_mgr_*` desde `ispadmin_staging` → `stg_oltgateway` (script `migrate-olt-mgr-to-stg_oltgateway.sql`). Quedaron en core:

- `olt_mgr_onu_optical_sample` (Health)
- `olt_mgr_onu_autofind` (huérfana; no es tabla Gateway)

Inventario migrado: 804 ONU, 1 OLT.

## Artefactos

| WAR | Context | HTTP |
|-----|---------|------|
| `ispadmin-staging.war` | `/ispadmin-staging` | 200 |
| `ispadmin-staging-oltgateway.war` | `/ispadmin-staging-oltgateway` | health 200, `status=UP`, OLT reachable (~451 ms) |

Perfiles Gateway: `prod,staging,oltgateway`. Arranque `OltGatewayApplication` ~17.5 s.

Smoke: `POST /onus/reboot` sin key → 401; `GET /descriptor` con key → 200 (`gigafiber-ma5608t`).

Prod no se tocó (`prod_oltgateway` no existe aún; `olt_mgr_*` siguen en `ispadmin`).
