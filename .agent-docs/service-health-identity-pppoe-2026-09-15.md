# Identidad 360 PPPoE (2026-09-15)

`PPPOE_DYNAMIC` no tiene IP estable. El snapshot 360 ahora expone el usuario PPPoE para que el backoffice muestre consumo sin exigir IP.

| Kind | Fuente | Cuándo |
|------|--------|--------|
| `IP` | `subscription.ip` | `STATIC_IP` / `PPPOE_FIXED` |
| `PPPOE` | `subscription.pppoeUsername` | `PPPOE_DYNAMIC` (y cualquier alta con username) |
| `ROUTER` | `hostDevice.id` | Poller MikroTik |

Código: `SubscriptionHealthRef.pppoeUsername` ← `SubscriptionDirectoryAdapter`; `IdentityService.snapshot` pone `PPPOE`. Tests: `IdentityServiceTest`, `SubscriptionHealthAdaptersTest`.

UI: `ispadmin-backoffice/.agent-docs/traffic-360-pppoe-sin-ip-2026-09-15.md`.
