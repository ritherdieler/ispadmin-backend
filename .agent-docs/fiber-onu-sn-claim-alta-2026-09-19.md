# Claim de `fiber_onu_sn` en el alta (2026-09-19)

El duplicado no salía del Gateway (una ONU por SN). El Core copiaba el serial al `POST /subscription` sin soltar la fila anterior.

## Comportamiento

`FiberOnuSnClaimService.claim` corre **antes** de guardar el SN (alta FIBER y `migrateToFiber`):

- Match exacto o sufijo hex de 6 (`VSOL00` / `HWTC15` / serial TR-069).
- Si otra suscripción **ACTIVE / CUT_OFF / SUSPENDED** lo tiene → `IllegalStateException` (hay que cancelar primero).
- Si solo hay **CANCELLED** → `fiber_onu_sn` y `tr069_device_id` a null + `SubscriptionChangedEvent`.
- No borra la ONU en OLT (el Gateway sigue haciendo delete+reauth).

`CancelledOnuReuseService` sigue siendo solo el fallback SmartOLT.

## Tests

```bash
./gradlew :core:test \
  --tests "com.dscorp.wispadmin.wispadmin.service.FiberOnuSnClaimServiceTest" \
  --tests "com.dscorp.wispadmin.wispadmin.service.SubscriptionServiceTest" \
  --tests "com.dscorp.wispadmin.wispadmin.service.SubscriptionServiceIdempotencyTest"
```
