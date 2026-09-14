# Alta Core staging COMPLETE — #2386 (2026-09-07)

## Resultado

`POST /subscription` en Core staging → suscripción **#2386**, DNI `98758194`, IP `192.168.250.16`.

| Fase | Estado |
|------|--------|
| OLT | COMPLETE |
| MikroTik (MK2 id 8) | COMPLETE |
| TR-069 | COMPLETE (~20 s poll) |

WiFi del alta (antes de cualquier cleanup):

| Banda | SSID | Contraseña |
|-------|------|------------|
| 2.4 GHz | `stagingcore` | `StagingCore24!` |
| 5 GHz | `stagingcore - 5G` | `StagingCore24!` |

ONU lab: `ZTEGDC47BFFD`. Sin post-cleanup (`SKIP_POST_CLEANUP=1`).

## Fallo previo (#2385)

`oltProvisionStatus=FAILED` porque `POST …/onu/activate` respondió **HTTP 500**: journal `olt_activation_operation` quedó en stage `ACS_STATUS` tras el reauth/delete de #2384, y el request nuevo (SSID distinto) chocaba con `Activation request conflicts`.

## Fix

1. **Journal**: request distinto **supersede** la operación incompleta (no 500).
2. **Delete ONU** (`SmartOltCompatController`): `clearJournal(sn)` tras delete.
3. **Hard cleanup** staging: `DELETE FROM stg_oltgateway.olt_activation_operation WHERE sn=…` antes/después del delete Gateway.

Ops inmediato que desbloqueó #2386: borrar la fila del journal + hard cleanup + re-alta.
