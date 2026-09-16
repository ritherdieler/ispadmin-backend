# Cleanup superficie muerta 360 — 2026-09-16

Tras quitar el gate de recolección, quedaban APIs y helpers sin caller.

## Eliminado

| Superficie | Por qué |
|---|---|
| `labSubscriptionIds()` + `findByLabIsTrue()` | Nadie listaba labs para recolectar |
| `ServiceHealthScope` | Era `acs.isLab(id)` |
| `labPeriodicInformSeconds` | Nadie la leía; frescura usa `periodicInformSeconds` |
| `sharedIncidentNotificationsEnabled` / `SERVICE_HEALTH_SHARED_INCIDENT_NOTIFICATIONS_ENABLED` | Flag sin reader |
| `HealthSqlTime` | Solo su test; `AcsWifiSampleLookup` se quedó en archivo propio |
| `ReadCapabilityProfile` + repo | Entidad/repo sin inyección. Tabla `capability_profile` (V35) no se toca |

`HealthEvidenceReader` marca `identity.lab` con `AcsSubscriptionPort.isLab()`.

El pull HTTP `HealthOltOpticalPullService` se eliminó el mismo día: [remove-optical-http-pull-2026-09-16.md](./remove-optical-http-pull-2026-09-16.md).

## Tests

```bash
./gradlew :core:test --tests "com.dscorp.wispadmin.servicehealth.config.ServiceHealthDeadSurfaceTest" \
  --tests "com.dscorp.wispadmin.servicehealth.*" \
  --tests "com.dscorp.wispadmin.wispadmin.adapter.SubscriptionHealthAdaptersTest" \
  --tests "com.dscorp.wispadmin.wispadmin.config.ServiceHealthEnvironmentWiringTest" \
  --tests "com.dscorp.wispadmin.wispadmin.config.StagingEnvironmentPropertiesTest"
```
