
## RouterOS (Mikrotik) — consumo por suscripción

| Operación | Descripción | Usado en | Notas |
|-----------|-------------|----------|-------|
| REST `GET /queue/simple` proplist `target,name,bytes,rate` | Cola simple por IP del abonado | `SubscriptionTrafficPollService`, `SubscriptionTrafficWebSocket` | Poll batch cada 5 min + tick WS ~1 s mientras hay viewers |
| REST `POST /queue/simple/print` proplist `.id,name,target,max-limit,comment` | Lista colas para purga/reconcile | `scripts/mk2-purge-staging-queues.mjs`, `scripts/mk2-reconcile-queues.mjs` | Staging se selecciona por `env=stg` o prefijo `[stg]` |
| REST `DELETE /queue/simple/{id}` | Borra una cola simple | `scripts/mk2-purge-staging-queues.mjs --apply` | Solo con `--tag` explícito; dry-run por defecto |
