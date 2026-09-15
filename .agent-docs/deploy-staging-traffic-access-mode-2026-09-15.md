# Deploy staging — tráfico accessMode + BFF `/api/traffic` (2026-09-15)

```bash
FORCE_WAR_REBUILD=1 ./scripts/deploy.sh --deploy --env staging --with oltgateway,traffic,acs,servicehealth
```

Tomcat-staging `:8081` `/ispadmin-staging/` HTTP 200. Prod no se tocó.

## Qué entra

- Directorio Core: `PPPOE_DYNAMIC` → solo username; `STATIC_IP` / `PPPOE_FIXED` → solo IP.
- `PlatformAuthFilter` excluye `/api/traffic` (el BFF usa `X-Traffic-Key`; sin esto `…/traffic/latest` daba 502 `UPSTREAM_AUTHENTICATION_FAILED`).

## Verificación

| Caso | Id | Resultado |
|------|----|-----------|
| FIBER `PPPOE_DYNAMIC` | `#6` `gf6` | `traffic/latest` 200, `polledAt` y mbps |
| WIRELESS `STATIC_IP` | `#7` MK2 id 8 | Directorio solo IP; cola `COMPLETE`; `polledAt` 12:14, `avgMbpsDown=0` (idle) |

Alta lab: `./scripts/staging-static-ip-traffic-lab.sh all`.
