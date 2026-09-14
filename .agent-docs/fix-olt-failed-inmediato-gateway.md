# Fix: `olt=FAILED` inmediato si Gateway falla

Fecha: 2026-09-04.

## Problema

Si `POST /onu/activate` fallaba (`oltStatus=FAILED` o excepción), Core guardaba `oltProvisionStatus=PENDING` y reintentaba hasta `MAX_ATTEMPTS`. La app/e2e Espresso esperaba minutos en PENDING en lugar de fallar enseguida. `RegistrationProgressMapper` ya trata `FAILED` como `done=true`.

## Cambio

`SubscriptionProvisionService.mapOltStatus`:

| Resultado instalación | Status OLT |
|-----------------------|------------|
| `onuAuthorized` | `COMPLETE` |
| `oltError` no vacío | `FAILED` |
| resto | `PENDING` |

Aplica a FIBER y ONLY_TV_FIBER con ONU.

## Pruebas

- `SubscriptionProvisionServiceTest.applyInstallationResult marks olt FAILED when fiber onu has oltError`
- `… ONLY_TV onu has oltError`
- `… keeps olt PENDING when fiber onu unauthorized without oltError`
- `SubscriptionServiceIdempotencyTest.registerSubscription keeps saved fiber when OLT fails but MikroTik succeeds` (espera `FAILED`)
