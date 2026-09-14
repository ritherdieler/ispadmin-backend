# Alta de suscripción: TR-069 async + polling

## Contrato

1. `POST /subscription` y `POST /subscription/with-facade-photo` persisten OLT/MikroTik y **encolan** TR-069 (`Tr069AsyncApplicator.schedule`). Responden en segundos con `tr069ProvisionStatus=PENDING` (si aplica).
2. `GET /subscription/{id}/registration-progress` deriva el paso UX de los estados persistidos (`RegistrationProgressMapper`).
3. `POST /subscription/{id}/acs/retry-tr069` usa `applyExclusive` (lock por `subscriptionId`).

## Pasos (`RegistrationStep`)

| Step | Condición |
|------|-----------|
| AUTHORIZING_ONU | OLT PENDING |
| PROVISIONING_MIKROTIK | OLT settled + MikroTik PENDING |
| WAITING_ACS / APPLYING_WIFI / VERIFYING | TR-069 PENDING (mensaje de fase) |
| DONE | COMPLETE / MANUAL_REQUIRED / NA (OLT+MK settled) |
| FAILED | OLT o MikroTik FAILED |

Durante GenieACS, `Tr069ProvisioningService` invoca `onPhase` y se persiste en `tr069LastError`/`tr069Message` como PENDING.

## Cliente Android

Tras el POST, poll cada ~2s a `registration-progress` hasta `done=true` o timeout ~120s. El overlay muestra `message` real (no cronómetro).
