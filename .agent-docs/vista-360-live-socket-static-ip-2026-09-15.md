# Vista 360 — actividad en vivo por socket (STATIC_IP, 2026-09-15)

La pestaña **Ahora** sigue siendo STOMP (`/topic/subscription-traffic/{id}` + `/app/subscription-traffic/start`). No usa `GET /subscription/{id}/live-readings`.

## Problema

Tras pasar #5 a `STATIC_IP` / cola `[stg] id:5` / `192.168.250.20`, el gráfico quedaba vacío. El start solo mandaba `{subscriptionId}`. Traffic resolvía la identidad con su directorio HTTP cacheado (TTL 60 s): si quedaba `pppoe:gf5` o no había IP, no emparejaba la cola simple y no publicaba ticks.

## Contrato del start

`{ subscriptionId, ip?, pppoeUsername?, routerHint? }`

- `STATIC_IP` / `PPPOE_FIXED`: solo `ip` (cola `target=IP/32`).
- `PPPOE_DYNAMIC`: solo `pppoeUsername`.
- Un `accessMode` a la vez. El start lleva una sola identidad; si llegan las dos, Core/Traffic usan el directorio (ya XOR por `accessMode`).

Flujo: backoffice (IP del 360) → Core `CoreTrafficStreamRelay` (rellena desde `TrafficDirectoryService`) → Traffic `SubscriptionTrafficWebSocket` (`SubscriptionLiveMonitorTarget`) → `/queue/simple` cada 2 s → ticks.

## Tests

Core: `CoreTrafficStreamRelayTest`, `TrafficStreamActivityTest`. Traffic: `SubscriptionLiveMonitorTargetTest`, `SubscriptionTrafficLiveTickBuilderTest`. Backoffice: `subscriptionTrafficWebsocketService.test.ts`, `useSubscriptionTrafficLive.test.ts`.
