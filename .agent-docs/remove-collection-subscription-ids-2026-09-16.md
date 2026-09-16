# Lista de recolección 360 eliminada — 2026-09-16

`collectionSubscriptionIds()` / `HealthLabScopePort` era un passthrough a `directory.allIds()` desde que se quitó el gate (2026-09-15). Ya no filtraba.

## Qué se fue

- `HealthLabScopePort` y `collectionSubscriptionIds()`
- `ServiceHealthScope` se eliminó (2026-09-16 cleanup); `HealthEvidenceReader` usa `AcsSubscriptionPort.isLab()`

## Qué queda

- `AcsSubscriptionPort.isLab()` → `identity.lab` (GenieACS / `subscription_acs.lab`)
- `HealthEvaluationService.evaluate()` itera `subscriptions.allIds()` → `findEvaluationIds()` (FIBER/ONLY_TV_FIBER operativas con ONU o ACS). Ver `optimize-360-staging-sweep-2026-09-16.md`.

## Tests

```bash
./gradlew :core:test --tests "com.dscorp.wispadmin.servicehealth.config.ServiceHealthDeadSurfaceTest" --tests "com.dscorp.wispadmin.servicehealth.*"
```

Diagramas: `.agent-docs/archify-360-recoleccion-staging/`, `.agent-docs/archify-360-recoleccion-prod/`.
