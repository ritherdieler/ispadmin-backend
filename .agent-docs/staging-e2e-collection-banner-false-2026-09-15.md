# Staging e2e: banner «Recolección desactivada» — 2026-09-15

## Qué se ve

Tras un alta Espresso (p. ej. VSOL `#12` PPPoE) el 360 muestra **Recolección desactivada** con hora del alta (`15/09/26, 9:56 p. m.`). Wi‑Fi sí llega (2 dispositivos). Tráfico dice «sin IP» (PPPoE XOR, otro tema).

El e2e **no apaga** un flag. El banner es `HealthSummary.pilot_enabled == false`.

## Causa

1. `DiagnosisEngine` puede calcular `pilotEnabled` con `GigafiberEnvironmentProperties()` **vacío** (ctor default) y `identity.lab == "true"`. Sin tag, el gate de prod **excluye lab**.
2. `ServiceHealthScope` sí tiene `tag=stg` y recolecta todo: el Inform persiste (por eso hay estaciones).
3. `HealthSummaryQueryService` guarda/devuelve ese snapshot. `decorate()` no pisaba `pilotEnabled` con el scope. El GET del 360 reutiliza el JSON del alta → banner off.
4. `#12` no tenía fila `subscription_acs` (lab no persistido). El gate viejo dependía de esa fila.

API minutos después ya podía devolver `pilot_enabled=true`; la UI seguía en el snapshot de las 9:56 si no se recalcula.

## Fix

- `decorate` / persist aplican `scope.collects(id)`: staging (`stg`) → siempre on.
- `DiagnosisEngine` usa `HealthLabScopePort` cuando Spring lo inyecta.
- Script Android `e2e_register_fiber_staging_espresso.sh` falla si el 360 no trae `pilot_enabled=true`.

Tests: `HealthSummaryQueryServiceTest`, `DiagnosisEngineTest`, `E2ePlaceLocationFixtureTest`.

## Deploy

Staging Tomcat tiene este decorate (`1.0.3+61fdeef`). `#12` GET `pilot_enabled=true` con `identity.lab=false`. Detalle: [deploy-staging-360-collect-banner-2026-09-15.md](./deploy-staging-360-collect-banner-2026-09-15.md).
