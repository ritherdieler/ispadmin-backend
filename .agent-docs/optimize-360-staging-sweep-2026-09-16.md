# Optimizar sweep 360 en staging — 2026-09-16

El censo de evaluación ya no recorre todas las filas de `Subscription`. El sweep salta snapshots frescos. El Inform ACS publica al bus Redis cuando está vivo y cae a HTTP si no.

Prod Redis sigue apagado (`REDIS_ENABLED` default `false`). Esta cadencia y el hop ACS→bus aplican donde el bus existe (staging / prestaging).

## Cambios

| Pieza | Antes | Ahora |
|---|---|---|
| Censo | `findAllIds()` = `SELECT s.id FROM Subscription s` | `findEvaluationIds()`: no `CANCELLED`, `FIBER`/`ONLY_TV_FIBER`, y ONU (`fiberOnuSn`) o ACS (`genieacsDeviceId`) |
| Sweep | Reevalúa cada id cada 60 s | Si `HealthCurrent.evaluatedAt` está dentro de `snapshot-fresh-seconds` (60), no corre engine |
| Stream | Inform / optical-batch → `reevaluate` | Igual. El GET no se queda viejo cuando el sweep salta |
| ACS Inform | Siempre `POST` Gateway → XADD | Si `EventBusPort` no es `NoOp`, XADD directo (`producer=acs`). Si no, HTTP |
| Staging | `evaluation-interval-ms` default 60 s | Overlay: 300000 ms (5 min), `initial-delay` 90 s |

No se saltan las reevaluaciones del optical-batch. No se agregó gate `enabled` en `evaluate()`.

## Overlay staging

```
service.health.evaluation-interval-ms=300000
service.health.evaluation-initial-delay-ms=90000
```

Sin variable nueva de secreto. `ACS_TO_GATEWAY_API_KEY` sigue siendo el fallback HTTP.

## Tests

```bash
./gradlew :core:test --tests "com.dscorp.wispadmin.servicehealth.service.HealthEvaluationServiceTest" \
  --tests "com.dscorp.wispadmin.wispadmin.adapter.SubscriptionHealthAdaptersTest" \
  --tests "com.dscorp.wispadmin.wispadmin.config.StagingEnvironmentPropertiesTest" \
  --tests "com.dscorp.wispadmin.wispadmin.config.ServiceHealthEnvironmentWiringTest" \
  :acs:test --tests "com.dscorp.wispadmin.acs.service.AcsToGatewayInformClientTest" \
  --tests "com.dscorp.wispadmin.acs.service.WifiInformNotifyServiceTest"
```
