# Catálogo HTTP — Traffic WAR

Auth: header `X-Traffic-Key` (`TRAFFIC_API_KEY`). Base interna staging: `http://127.0.0.1:8080/ispadmin-staging-traffic`.

| Método | Ruta | Qué hace |
|--------|------|----------|
| GET | `/api/traffic/v1/by-subscription/{id}/latest` | Última muestra etiquetada al abonado |
| GET | `/api/traffic/v1/by-subscription/{id}/series` | Serie por `subscription_id` (histórico sobrevive reasignación de IP) |
| GET | `/api/traffic/v1/by-subscription/{id}/summary` | Resumen mensual; si no hay rollup, DTO con ceros (nunca cuerpo vacío) |
| GET | `/api/traffic/v1/by-subscription/{id}/today` | Vista del día |
| GET | `/api/traffic/v1/by-subscription/{id}/day` | Vista de un `date` |
| GET | `/api/traffic/v1/by-ip/{ip}/latest` | Última muestra de una cola |
| GET | `/api/traffic/v1/by-ip/{ip}/series` | Serie física por IP |
| GET | `/api/traffic/v1/network` | Agregado de red |
| GET | `/api/traffic/v1/overview` | Overview de red |
| GET | `/api/traffic/v1/series` | Serie de red |
| GET | `/api/traffic/v1/sources` | Salud del collector |
| GET | `/api/traffic/v1/anomalies` | Anomalías |
| GET | `/api/traffic/v1/anomalies/changes` | Cursor de cambios para Health |
| GET | `/api/traffic/v1/routers/{id}/latest-run` | Último poll de un router |
| GET | `/api/traffic/v1/config` | `minimumCoveragePct` |
| POST | `/api/traffic/v1/admin/poll` | Poll inmediato |
| GET | `/internal/traffic/targets` (core) | Directorio `{subscriptionId, ip, routerHint, plan*}`. Auth `X-Traffic-Key`; `PlatformAuthFilter` no exige JWT. |

BFF público (core, `--with traffic`): mismas URLs de siempre (`/subscription/{id}/traffic*`, `/traffic/bandwidth/v1/*`).
