# Deploy prod persist-only `wifi-inform-core` — 2026-09-18

## Qué

`develop` @ `c963852` (`1.0.3+c963852`). `CpeInformEventConsumer` solo persiste; no llama `reevaluate`. Preflight prod OK (cadena FIBER/TR-069 completa). `deploy.sh --env prod` → HTTP 200 `/ispadmin/`.

Causa y fix: [wifi-inform-reevaluate-stall-2026-09-18.md](./wifi-inform-reevaluate-stall-2026-09-18.md).

## Post-deploy (CR + front #2373)

| Check | Resultado |
|-------|-----------|
| HTTP `/ispadmin/` | 200 |
| `wifi-inform-core` pending | 49 → 0 |
| `acs_wifi_count_sample` MAX flota | avanza (`06:21` atascado → `06:58` UTC y sigue) |
| #2373 count samples | 0 → 7 (`06:26`–`06:43` UTC, total 5 / 3+2, FRESH) |
| CR `POST …/acs/wifi-refresh` | 202 `actionId=9` PENDING; ACS `last_inform_at` 02:39:11 Lima |
| Vite `:3000` `--mode production` | WifiCharts: **5** dispositivos, 2.4=3 · 5=2, 5 estaciones RSSI |

El consumer sigue el backlog del stream (`last-delivered` ~46 min detrás de `snapshot-core`). El Inform del CR (~07:39 UTC) entra cuando el catch-up lo alcanza; el persist es idempotente. `WifiCharts` ya pinta con las muestras del catch-up.

## Front verificado

`http://localhost:3000/subscriptions/2373/service-health` (JEISON AQUINO CUARINO):

- Dispositivos asociados: total **5**, **2.4 GHz 3 · 5 GHz 2**
- Señal: HUAWEI_Y6, A20s-de-Jeyson, ZTE-Blade-A35e, HONOR-X7c, Redmi-12
- Estaciones última lectura con RSSI (−50 / −56 / −65 / −52 / −55 dBm)
