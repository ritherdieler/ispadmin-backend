# Live-readings detrás de Traffic (2026-09-16)

Fachada pública sin cambio: `GET /subscription/{id}/live-readings` (mismo JSON: `subscriptionId`, `available`, `pppoe`, `timestamp`, `downloadBps`, `uploadBps`, `rxBytes`, `txBytes`, `source`).

Interno: Core → `GET /api/traffic/v1/by-subscription/{id}/live-readings` con query de identidad. Traffic abre RouterOS. Escrituras MK, NetDiag y consola ahora también van por Traffic (`[mikrotik-only-via-traffic-2026-09-16.md](./mikrotik-only-via-traffic-2026-09-16.md)`). ACS y OLT no abrían MikroTik. El WS `/app/subscription-traffic/start` ya estaba en Traffic.

Detalle de contrato: [vista-360-live-readings-2026-09-15.md](./vista-360-live-readings-2026-09-15.md).
