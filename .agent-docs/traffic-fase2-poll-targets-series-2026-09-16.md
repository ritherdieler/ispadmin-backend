# Traffic fase 2 — poll key, target por id, LIMIT series (2026-09-16)

`POST /traffic/poll` y `POST /traffic/aggregation/catch-up` piden `X-Traffic-Key` (mismo filtro que `/api/traffic/*`). El canónico sigue siendo `POST /api/traffic/v1/admin/poll`.

`GET /internal/traffic/targets/{id}` resuelve un abonado con el XOR IP/PPPoE. Live-monitor y Core relay dejan de hacer `directory.list()` para un id.

`getSeries` (sample/hourly/daily y sample-by-ip) usa `findTop500…OrderByBucketStartDesc` en SQL y revierte a ASC. `getDay` / `getSummary` no cambian.

La doc de 360 (`vista-360-live-readings-2026-09-15.md`) refleja que Ahora es GET, no STOMP.

`traffic.poll.enabled` sigue en `true`.
