# Deploy + validación staging — live in-process (2026-09-15)

WAR `1.0.3+d30d7c8` → `tomcat-staging` `/ispadmin-staging/` HTTP 200. Un solo WAR (core+traffic+acs+oltgateway). Prod no se tocó.

## Smoke STOMP (browser contract)

Start: `{ "subscriptionId": 5 }` solamente. Topic `/topic/subscription-traffic/5`.

| Check | Resultado |
|-------|-----------|
| Login | OK |
| `#5` | `STATIC_IP` / `192.168.250.20` |
| `traffic/latest` | 200, `clientIp=192.168.250.20`, host 8 |
| Ticks | 3 en ~4 s, `queueFound=true` |
| Mbps | 0/0 (lab idle; la cola existe) |

Causa previa: `StompTrafficStreamTransport` loopback al mismo `/ws` sin `SubscriptionTrafficWebSocket`. Fix: `SubscriptionTrafficLiveMonitor` in-process.
