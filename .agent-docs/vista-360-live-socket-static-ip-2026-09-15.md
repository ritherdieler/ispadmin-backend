# Vista 360 — actividad en vivo por socket (STATIC_IP, 2026-09-15)

La pestaña **Ahora** sigue siendo STOMP (`/topic/subscription-traffic/{id}` + `/app/subscription-traffic/start`). No usa `GET /subscription/{id}/live-readings`.

## Problema

Tras pasar #5 a `STATIC_IP` / cola `[stg] id:5` / `192.168.250.20`, el gráfico quedaba vacío. El start solo mandaba `{subscriptionId}`. Traffic resolvía la identidad con su directorio HTTP cacheado (TTL 60 s): si quedaba `pppoe:gf5` o no había IP, no emparejaba la cola simple y no publicaba ticks.

## Contrato del start

El browser solo manda `{ subscriptionId }`. Core arma el resto desde `TrafficDirectoryService` (XOR por `accessMode`):

- `STATIC_IP` / `PPPOE_FIXED`: `{ subscriptionId, ip, routerHint }`
- `PPPOE_DYNAMIC`: `{ subscriptionId, pppoeUsername, routerHint }`

No existe un comando con IP y username. El directorio no emite los dos.

Flujo: backoffice `{subscriptionId}` → Core (directorio) → Traffic → `/queue/simple` cada 2 s → ticks.

## Tests

Core: `CoreTrafficStreamRelayTest`, `TrafficStreamActivityTest`. Traffic: `SubscriptionLiveMonitorTargetTest`, `SubscriptionTrafficLiveTickBuilderTest`. Backoffice: `subscriptionTrafficWebsocketService.test.ts`, `useSubscriptionTrafficLive.test.ts`.
