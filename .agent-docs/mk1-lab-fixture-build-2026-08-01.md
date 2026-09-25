# MK1 lab fixture + Android E2E (2026-08-01)

## Backend

- `GeoLocationConverter`: `null`/blank en columna `location` ya no rompe `findById` (test `GeoLocationConverterTest`).
- `scripts/lab/mk1-e2e-seed.sql`: `location` JSON válido + `equipment_condition=LOAN` para suscripción `900001`.
- `scripts/mk1-lab-reset.sh`: validación API con `GET /subscription/{id}` (evita `find/ip` y filas ajenas).

## Verificación

```bash
export MYSQL_PASSWORD='…' ADMIN_PASS='nohacker'
./scripts/mk1-lab-reset.sh   # → LAB_RESET_OK
```

## Android

El script `scripts/e2e_mk1_payment_lab.sh` fue eliminado.

- Sin `hide_keyboard` (KEYCODE_BACK cerraba login/buscador).
- Código de suscripción vía `type_digits` (keyevents) para Compose.
- Menú / método de pago por texto cuando el dropdown no expone testTag en `android layout`.

Corrida exitosa local: login `labmk1` → buscar `900001` → registrar pago `900001` → `192.168.250.1` fuera de `deudores` (`E2E_MK1_PAYMENT_LAB_OK`).

Pruebas: `./mvnw test -Dtest=GeoLocationConverterTest`; Android `./gradlew :presentation:assembleDevDebug`.

## Re-ejecución 2026-08-01 (14:28–14:31 UTC-5)

| Paso | Resultado |
|------|-----------|
| `./scripts/mk1-lab-reset.sh` | LAB_RESET_OK |
| `./scripts/mk1-mikrotik-smoke.sh` | PASS (lecturas MK1) |
| `./scripts/mk1-mikrotik-e2e-full.sh` (`RUN_MASS=false RUN_PAYMENT=false`) | 9/9 PASS |
| `./mvnw -Plive-mk1 test` RouterOs7 + MikrotikPoll live | BUILD SUCCESS |
| `./mvnw test` GeoLocationConverter + RouterOsEntryId | PASS |
| Android `compileDevDebugSources` + RegisterPaymentViewModelTest | BUILD SUCCESS |
| `e2e_mk1_payment_lab.sh` (eliminado) | E2E_MK1_PAYMENT_LAB_OK el 2026-08-01 |

Ver también `mk1-lab-all-flows-build-2026-08-01.md` (cancel/reactivate/plan/ip-pool + masivos MK1).
