# Staging OLT: alineación con SmartOLT (prod)

## Problema

Incluir `oltgateway` en el WAR no basta con hornear `olt.gateway.enabled=false`. El Tomcat compartido define `OLT_GATEWAY_ENABLED=true` (prod lab/gateway), y esa env gana a `application-staging.properties`. Entonces `OltManagerFacade` sí se crea.

`OnuService` prefería el facade: authorize iba a CLI SSH. Con `olt.gateway.writes.enabled=false` eso lanza `OltWritesDisabledException`, marca la transacción rollback-only y el E2E recibe HTTP 500 en `POST /subscription/with-facade-photo`.

## Solución

`OnuService` ya no inyecta `OltManagerFacade`. Listado, authorize, delete y reboot del contrato SmartOLT (`/onu/*`, `OnuOperationsPort`) delegan siempre a `OltService` → API SmartOLT (`RealOltService`), igual que prod.

El gateway SSH/SNMP sigue existiendo para inventario, señal y `/api/olt-gateway/*` si el bean está activo; no intercepta el registro FIBER.

## Deploy recomendado

```bash
./scripts/deploy.sh --env staging --with oltgateway,servicehealth,netdiag,traffic
```

## Verificación post-deploy

1. `GET /ispadmin-staging/onu/unconfigured_onus` → lista SmartOLT.
2. Registro FIBER → logs **sin** `OltWritesDisabledException` ni `Executing CLI command ont add`.
3. Cleanup E2E → SmartOLT encuentra la ONU autorizada.

## Relacionado

- `staging-e2e-deploy-2026-09-01.md`
