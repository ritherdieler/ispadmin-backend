# Gateway staff inventory APIs — 2026-09-02

Tras el WAR propio, el CRM (`WispOltGatewayHttpClient`) llamaba rutas que el servicio ya tenía pero **no** estaban expuestas en `OltGatewayController`. Smoke staging: catalog/detail → 404; status/history → 400 (colisión con `/onus/{slot}/{port}/{ontId}`).

## Añadido

| Método | Ruta | Fuente |
|--------|------|--------|
| GET | `/api/olt-gateway/onus/configured/{externalId}` | `OltInventorySyncService.getConfiguredByExternalId` |
| GET | `/api/olt-gateway/onus/configured/{externalId}/status` | `getLiveStatusByExternalId` |
| GET | `/api/olt-gateway/onus/configured/{externalId}/history` | `getHistoryByExternalId` |
| GET | `/api/olt-gateway/onus/catalog` | `listCatalogs` |
| GET | `/api/olt-gateway/onus/catalog/boards-ports` | `listBoardsPorts` |

404 → `OnuNotFoundException` / `onu_not_found`.

Tests: `OltGatewayControllerTest` (6 casos nuevos). Catálogos HTTP/CLI actualizados.

## Staging

Redeploy de `ispadmin-staging.war` + `ispadmin-staging-oltgateway.war`. Smoke:

| Ruta | HTTP |
|------|------|
| `/onus/catalog` | 200 |
| `/onus/catalog/boards-ports?oltId=2` | 200 |
| `/onus/configured/{externalId}` | 200 |
| `/onus/configured/{externalId}/history` | 200 |
| core `/ispadmin-staging/` | 200 |
| prod `/ispadmin/` | 200 |
