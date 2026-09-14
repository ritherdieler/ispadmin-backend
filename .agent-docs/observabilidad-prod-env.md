# Observabilidad — prod env y sesiones Android

Ver detalle operativo en `docs/observabilidad-prod-env.md`.

## Cambios backend (2026-08-01)

- `ObservabilityApiKeysStartupValidator`: en perfil `prod`, log ERROR si falta alguna de
  `android`, `dashboard`, `web-backoffice`, `web-asistencias` en `observability.api-keys.*`.
- `ObsSessionQueryService.listSessions`: une sesiones de `obs_event` y de root spans en
  `obs_span` (sesiones Android solo-HTTP visibles en `/observability/sessions`).
- `ObsSpanRepository.aggregateRecentSessions`: agregado por `sessionId` en ventana temporal.

## Prod VPS (verificado)

`OBS_API_KEY_ANDROID` presente en `/opt/gigafiber/.env` y en contenedor Tomcat; ingestión
`POST /observability/events` responde 202 con esa clave.

## Build

`./mvnw test -Dtest=ObsSessionQueryServiceListSessionsTest,ObservabilityApiKeysStartupValidatorTest`
