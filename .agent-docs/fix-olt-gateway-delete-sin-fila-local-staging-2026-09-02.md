# Fix delete ONU sin fila local (staging) — 2026-09-02

## Problema

En staging, `DELETE`/`POST` delete por `externalId` (`gigafiber-ma5608t_{board}_{port}_{ontId}`) devolvía **OnuNotFound** porque:

- Staging usa BD `ispadmin_staging`.
- Inventario `olt_mgr_onu` vive en prod (`ispadmin`); sync SNMP/SSH de inventario está apagado en staging (OLT compartida).
- Tras un alta e2e (o residual en OLT), la fila puede existir solo en prod o solo en la OLT.

## Fix

`OltManagerFacade.deleteOnu(externalId)`:

1. Busca fila local por `externalId`.
2. Si no hay fila → parsea `{oltId}_{board}_{port}_{ontId}` con `OltOnuExternalId`.
3. Ejecuta CLI `commandService.delete` (undo service-port + ont delete).
4. Soft-delete en BD solo si había fila; task/audit con `onu` nullable.

Si el id no es parseable y no hay fila → `OnuNotFoundException` (404 en `OltGatewayExceptionHandler`).

## Tests

- `OltOnuExternalIdTest`
- `OltManagerFacadeTest` — delete con fila; sin fila (CLI por parse); id inválido → 404

## Deploy

Tras build: `./scripts/deploy.sh --env staging --with oltgateway,servicehealth,netdiag,traffic`
