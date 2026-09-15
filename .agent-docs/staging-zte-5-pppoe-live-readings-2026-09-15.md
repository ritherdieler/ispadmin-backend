# Staging #5 — live-readings lee PPPoE gf5, no la cola vacía (2026-09-15)

`GET /subscription/5/live-readings` devolvía `available=true` `source=QUEUE` 0/0 porque leía `*8A2` (`[stg] id:5` `target=10.64.0.11/32`).

## Causa

El ZTE (`ZTEGDC47BFFD`) sigue en sesión PPPoE `gf5` en MK2. La cola simple de STATIC_IP no ve ese tráfico: gana la dinámica `<pppoe-gf5>`.

Medido en MK2 ~20:25Z:

| Objeto | `.id` / iface | rate / monitor | bytes |
|--------|---------------|----------------|-------|
| Cola simple STATIC_IP | `*8A2` `10.64.0.11/32` | `0/0` | `0/0` |
| Cola dinámica residual | `*89A` `<pppoe-gf5>` | `20496/38640` | `14.9 MB / 210.6 MB` |
| Sesión | `/ppp/active` `gf5` | address `10.64.0.11`, MAC `58:72:C9:2F:4D:F7`, uptime 5h51m | |
| Interfaz | `<pppoe-gf5>` running | monitor rx `25096` tx `29848` bps | rx-byte `14.9 MB`, tx-byte `212.7 MB` |

ARP `10.64.0.11` vacío (vive en PPPoE, no en L2).

## Fix

Si `usesSimpleQueue()` y `/ppp/active` tiene esa IP (`ip` o `pppoeLastIp`), live-readings usa `pppoe-in` + `monitor-traffic` (`source=PPPOE`). Si no hay sesión, sigue la cola simple.

Tests: `SubscriptionLiveReadingServiceTest.static ip leftover pppoe session at same ip reads pppoe-in not empty queue`.

No se tocó recolección lab.
