# Fix delete ONU Gateway API — 2026-09-02

## Problema

`POST /api/olt-gateway/onu/delete/{externalId}` devolvía **500**: la CLI hacía solo `ont delete` y la OLT respondía  
`Failure: This configured object has some service virtual ports`.

## Fix

`OltGatewayCommandService.delete()` / `move()` (origen):

1. `undo service-port port 0/{board}/{port} ont {ontId}` (nivel `config#`)
2. `interface gpon 0/{board}`
3. `ont delete {port} {ontId}`
4. `quit`

Además: auto-respuesta `(y/n)` en `HuaweiCliSession`; si `ont delete` falla por service-ports → `OltGatewayException`; si la ONT ya no existe → OK (idempotente).

## Deploy staging

2026-09-02 ~15:19 local: `./scripts/deploy.sh --env staging --with oltgateway,servicehealth,netdiag,traffic` → **Done**. Health 200; Gateway `/api/olt-gateway/health` UP.
