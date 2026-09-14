# Fix: deleteOnu libera F/S/P y external_id

Fecha: 2026-09-04.

## Problema

`OltManagerFacade.deleteOnu` hacía soft-delete y tombstone del SN, pero **dejaba** `board/port/onu_index/external_id` originales.

La unique `(olt_id, board, port, onu_index)` y `external_id` seguían ocupadas. El siguiente `authorizeOnu` / import fallaba con `ConstraintViolationException` y el alta Android quedaba en `olt_provision_status=PENDING`.

El sync de inventario ya parqueaba soft-deletes (`tombstoneDeletedOnu`: board=-1, `external_id=…_deleted_{id}`); delete API no.

## Fix

Tras CLI `ont delete`, además del tombstone de SN:

- `board = -1`, `port = 0`, `onu_index = id`
- `external_id = {oltId}_deleted_{id}`

## Prueba

`OltManagerFacadeTest.deleteOnu soft-delete A` exige el park de F/S/P y external_id.

## E2E staging (misma fecha)

- ONU lab `ZTEGDC47BFFD`, VLAN **100**
- Sub `2372` / DNI `98577060`: `olt=COMPLETE`, `tr069=COMPLETE`
- Espresso OK; ping MikroTik a `192.168.250.21` falló (host unreachable) — no bloquea el COMPLETE ACS
