# Vista 360 — lecturas en vivo (2026-09-15)

`GET /subscription/{id}/live-readings` es la fachada pública en Core. Core no abre MikroTik: llama Traffic `GET /api/traffic/v1/by-subscription/{id}/live-readings` (`TrafficHttpClient`). Traffic lee `/queue/simple` (cache 2 s por router), y leftover `/ppp/active` + `monitor-traffic` solo si no hay fila de cola. El gráfico **Ahora** de la 360 usa este GET cada 2 s (`useSubscriptionTrafficLive`). No auto-arranca STOMP ni `/app/traffic/start`. El WS de suscripción sigue huérfano de UI.

Staging y prod comparten MK2. La cola `id:6` (`*9`, Cintia Escobal, `192.168.30.23`) no es el lab. El lab #6 es `PPPOE_DYNAMIC` `gf6`: interfaz `pppoe-in` `<pppoe-gf6>` (y cola dinámica `*89C` solo como objeto residual).

## Contrato

Path desplegado: `/ispadmin-staging/subscription/{id}/live-readings` (staging) o `/ispadmin/subscription/{id}/live-readings` (local/prod). Auth igual que el resto de `/subscription/**`. Siempre 200: sin host/sesión → `available: false`, `source: NONE`.

| source | Cuándo | Cómo | bps | bytes | pppoe |
|--------|--------|------|-----|-------|-------|
| `PPPOE` | `PPPOE_DYNAMIC` | `/interface` type `pppoe-in` + `/interface/monitor-traffic once` | `tx-bits-per-second` = download, `rx-bits-per-second` = upload | `tx-byte` = rxBytes (hacia el cliente), `rx-byte` = txBytes | user extraído |
| `QUEUE` | `STATIC_IP` / `PPPOE_FIXED` (y leftover solo si no hay fila) | `/queue/simple` por IP, `pppoeLastIp`, nombre `<pppoe-{user}>` o `id:{subscriptionId}` con tag de ambiente | `rate` upload/download | `bytes` upload/download | `null` |
| `NONE` | nada de lo anterior | — | 0 | 0 | `null` |

`PPPOE_DYNAMIC` no lee `rate` de simple queue. En `pppoe-in` el RX del router es upload del cliente y el TX es download.

Core `SubscriptionLiveReadingService` (`repository` + `TrafficHttpClient` + env + `ObjectMapper`) arma query `accessMode`, `ip`, `pppoeLastIp`, `pppoeUsername`, `hostDeviceId`, `envTag`. Traffic `SubscriptionLiveReadingService` abre `trafficPollMikrotikClient`. Escrituras MK (colas, secret, cortes) también van por Traffic (`[mikrotik-only-via-traffic-2026-09-16.md](./mikrotik-only-via-traffic-2026-09-16.md)`). WS de tráfico por suscripción ya vivía en Traffic.

## Tests

JUnit/MockK: `:core` `SubscriptionLiveReadingServiceTest` (proxy, sin RouterOS), `SubscriptionLiveReadingControllerTest`. `:traffic` `SubscriptionLiveReadingServiceTest` (RouterOS), `TrafficSubscriptionLiveReadingControllerTest`.
