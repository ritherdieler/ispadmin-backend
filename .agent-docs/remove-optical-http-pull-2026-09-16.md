# Quitar pull HTTP de óptica 360 — 2026-09-16

El camino vivo es Gateway SNMP → Redis `onu.optical-batch` → `OpticalBatchPersistService`.
`HealthOltOpticalPullService` era el fallback HTTP, siempre apagado (`optical-pull-enabled=false`).

## Eliminado

| Superficie | Por qué |
|---|---|
| `HealthOltOpticalPullService` + test | Scheduler que salía al toque |
| `opticalPullEnabled` / `service.health.optical-pull-enabled` / `SERVICE_HEALTH_OPTICAL_PULL_ENABLED` | Flag del fallback |
| `HealthOltGatewayHttpClient.pullOptical()` / `pullStates()` / `pullStateObservations()` / `configuredItems()` | Solo los usaba el pull |
| `HealthStateObservation` | DTO del pull de estado |
| `HealthOpticalPaginationTest` | Cubría paginación del GET `/onus/configured` del pull |

Se mantiene el cliente HTTP para lookup ONU, refresh lab y comandos CPE. La óptica 360 sigue con `service.health.optical-enabled` y el consumer Redis.

## Tests

```bash
./gradlew :core:test --tests "com.dscorp.wispadmin.servicehealth.config.ServiceHealthDeadSurfaceTest" \
  --tests "com.dscorp.wispadmin.servicehealth.client.HealthOltGatewayHttpClientTest" \
  --tests "com.dscorp.wispadmin.servicehealth.OltHistoryServiceTest" \
  --tests "com.dscorp.wispadmin.wispadmin.config.ServiceHealthEnvironmentWiringTest" \
  --tests "com.dscorp.wispadmin.wispadmin.config.StagingEnvironmentPropertiesTest"
```
