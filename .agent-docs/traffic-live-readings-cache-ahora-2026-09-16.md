# Traffic — un vivo 360 y cache de cola (2026-09-16)

La pestaña Ahora deja de abrir un segundo canal a MikroTik. El gráfico vive del GET público `GET /subscription/{id}/live-readings` (poll 2 s). Traffic recorta prints de `/queue/simple` con una cache en memoria de 2 s por `hostDeviceId`. `traffic.poll.enabled` sigue en `true`.

## Ahora (backoffice)

`useSubscriptionTrafficLive` solo llama `getLiveReadings`. No auto-arranca `subscribeToTraffic` → `/app/traffic/start` (WS `/interface` cada 1 s). El WS de interfaces queda opt-in (`enableInterfaceTrafficWs`). El WS de suscripción (`/app/subscription-traffic/start`) no se revive.

Android 360 no tiene cliente de live-readings ni de ese WS: no duplica.

## Misma fuente

`STATIC_IP` / `PPPOE_FIXED` (y `accessMode` vacío) leen `/queue/simple` primero. Leftover PPPoE (`/ppp/active` + `monitor-traffic` sobre `<pppoe-{user}>`) solo si no hay fila de cola. Si `/ppp/active` ya dio el user, no se printa `/interface`.

Core siempre manda `accessMode` en el GET interno.

## Cache

`SimpleQueueSnapshotCache` (TTL 2 s, por router). La comparten live-readings y live-monitor. No es Redis ni histórico. El poll de lab (60 s) no usa esta cache.

## Tests

- `:traffic:test` — verde (incluye `SubscriptionLiveReadingServiceTest`, cache hit, leftover, cola primero).
- `:core:test --tests SubscriptionLiveReadingServiceTest` — `accessMode` siempre en el query.
- Backoffice `useSubscriptionTrafficLive.test.ts` — Ahora no abre WS.
- `:traffic:compileKotlin` + `:core:compileKotlin` — OK.
