# Observabilidad — Hardening ingest path, replay workflowId, timestamp

**Fecha**: 2026-07-14  
**Repo**: ispadmin-backend

## Cambios

### Path de ingest en `ObservabilityApiKeyFilter`

`servletPath` vacío (string blank) hacía que el filtro no reconociera `POST …/observability/events|spans|…` y exigiera `X-Obs-Session` → **401**.

`observabilityPath()` ahora:

- usa `servletPath` solo si no está blank;
- si no, toma `requestURI` y quita `contextPath`.

Tests: `ObservabilityApiKeyFilterTest`.

### Timestamp flexible

`EventIngestRequest.timestamp` con `FlexibleEpochMillisDeserializer` (epoch Long/String o ISO-8601).

Tests: `FlexibleEpochMillisTest`.

> Requiere redeploy del backend en el proceso que esté sirviendo:8080 para aplicar el deserializer en runtime.

### Replay `workflowId`

- Columna `obs_replay.workflow_id` (Hibernate `ddl-auto=update`).
- Query param `workflowId` en `POST /observability/replays`.
- `ReplaySummaryDto.workflowId` en session detail.
- LLM context incluye workflow del replay.

Tests: `ObsReplayServiceWorkflowIdTest`.

## Criterio ingest

```bash
curl -s -X POST "http://127.0.0.1:8080/ispadmin/observability/events" \
  -H "Content-Type: application/json" \
  -H "X-Obs-Api-Key: dev-obs-android-key" \
  -d '{"events":[{"eventType":"workflow_start","message":"login","timestamp":1784043978140,"platform":"android","sessionId":"demo","tags":{"workflowId":"w1","workflowName":"login","workflowCategory":"auth"}}]}'
# → 202 {"accepted":1,...}
```

## E2e Android (2026-07-14)

Login en device wireless + `adb reverse tcp:8080`:

- Session `5417cc1d-e0dd-4421-a498-93db00ade7df`
- `workflow_start` / `workflow_end(success)` name=`login` en `obs_event`
- Span login con `workflowId` en tags
- Causa del 401 previo: BuildConfig `OBS_API_KEY` desde `local.properties` no coincidía con `observability.api-keys.android` del profile `dev`