# Activate: si la ONU existe, borrarla (2026-09-17)

## Problema

`POST /subscription` FIBER vía Gateway (`OnuActivationService.activate`) trataba `already authorized` como éxito: reutilizaba el `unique_external_id` y saltaba el `ont add`. Caso #2372 JEISON / `VSOL00323AF6`: inventario importado (ONT 19, perfil viejo, sin VLAN 1000). OLT quedó `COMPLETE` y ACS en timeout.

Autofind solo lista ONUs **ya liberadas**. Si el SN está autorizado, hay que borrarlo.

## Comportamiento

En `authorizeOlt`:

1. `externalIdBySn(sn)` → si hay id, `deleteOnu`.
2. Poll `unconfiguredOnus` hasta 90 s (5 s). Match de SN con `OnuSerialMatcher` (hex CLI, `VSOL-00…`, sufijo 6 hex).
3. Si no vuelve al autofind → `oltStatus=FAILED` (`ONU deleted but not back in autofind`). No authorize.
4. Si está en autofind (o nunca existió) → `authorizeOnu` (VLAN 100 + 1000 si `vlan=100`).

No se reutiliza un authorize viejo. `CancelledOnuReuseService` sigue solo en el fallback SmartOLT.

## Tests

`./gradlew :oltgateway:test --tests "com.dscorp.wispadmin.oltgateway.service.OnuSerialMatcherTest" --tests "com.dscorp.wispadmin.oltgateway.service.OnuActivationServiceTest" --tests "com.dscorp.wispadmin.oltgateway.service.ActivationRecoveryTest" --tests "com.dscorp.wispadmin.oltgateway.service.ActivationDurabilityTest"` — OK (2026-09-17).

## Archivos

- `oltgateway/.../OnuActivationService.kt`
- `oltgateway/.../OnuSerialMatcher.kt`
- tests `OnuSerialMatcherTest`, `OnuActivationServiceTest`
