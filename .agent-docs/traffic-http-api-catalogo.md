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
| GET | `/api/traffic/v1/by-subscription/{id}/live-readings` | Lectura viva 360 (RouterOS) |
| POST | `/api/traffic/v1/admin/poll` | Poll inmediato |
| POST | `/api/traffic/v1/routeros/{hostDeviceId}/print` | Gateway RouterOS print |
| POST | `/api/traffic/v1/routeros/{hostDeviceId}/add` | Gateway RouterOS add |
| POST | `/api/traffic/v1/routeros/{hostDeviceId}/set` | Gateway RouterOS set |
| POST | `/api/traffic/v1/routeros/{hostDeviceId}/remove` | Gateway RouterOS remove |
| POST | `/api/traffic/v1/routeros/{hostDeviceId}/call` | Gateway RouterOS call |
| GET | `/internal/traffic/targets` (core) | Directorio `{subscriptionId, ip, routerHint, plan*}`. Auth `X-Traffic-Key`; `PlatformAuthFilter` no exige JWT. |
| GET | `/internal/traffic/targets/{id}` (core) | Un target. 404 si no entra al XOR IP/PPPoE. |
| GET | `/internal/traffic/targets/page` (core) | Página cursor del directorio. |
| POST | `/traffic/poll` | Legacy admin poll. Ahora exige `X-Traffic-Key`. Canónico: `/api/traffic/v1/admin/poll`. |
| POST | `/traffic/aggregation/catch-up` | Legacy catch-up. Ahora exige `X-Traffic-Key`. |

BFF público (core, `--with traffic`): mismas URLs de siempre (`/subscription/{id}/traffic*`, `/traffic/bandwidth/v1/*`).
