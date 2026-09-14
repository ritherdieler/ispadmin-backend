# As-built: Core sin dominio ACS (4 WARs)

Fecha: 2026-09-04.

Implementación del plan «Gateway confirmación parcial ACS».

## Runtime

- Alta FIBER: Core → Gateway `POST /onu/activate` (OLT sync, ACS async). Respuesta parcial `olt=COMPLETE`, `cpe=PENDING`.
- MikroTik (cola/IP) permanece en Core.
- Day-2 (360, reboot, wifi-refresh): Core JWT → Gateway por SN / `unique_external_id`.
- Gateway → ACS WAR (`X-Acs-Key`). ACS WAR → GenieACS. Core no habla con ACS ni GenieACS.
- Cierre ACS: Gateway XADD `cpe.provisioning`; Core `CpeProvisionFlagService` actualiza flags gruesos.
- Scan Core excluye `acs.*`, `wispadmin.service.genieacs.*` y `Tr069ModelProfileController`.

## Empaquetado staging

`./scripts/deploy.sh --env staging` construye y sube `ispadmin-staging-acs.war` junto a traffic y oltgateway.

`--with oltgateway` habilita el cliente HTTP vía perfil `subsystem` (`application-subsystem.properties` horneado; último perfil pisa `staging`/`prod`). No usar `spring.config.import` para esas claves en Boot 2.7.

Hotfix e2e 2026-09-04: [fix-staging-gateway-client-overlay-2026-09-04.md](./fix-staging-gateway-client-overlay-2026-09-04.md). El WAR gateway staging hornea `olt.gateway.writes.enabled=true`.

## E2E

Scripts HTTP (JWT, no llaman ACS WAR):

- `scripts/e2e_onu_activation_status_staging.sh`
- `scripts/e2e_onu_cpe_day2_staging.sh`

Alta Android: `e2e_register_fiber_staging_espresso.sh` + runbook [staging-fiber-e2e-runbook.md](./staging-fiber-e2e-runbook.md).

## Docs

- [arquitectura-4-wars-core-sin-acs.md](./arquitectura-4-wars-core-sin-acs.md)
- Canvas `arquitectura-4-wars-gateway-acs`
- Catálogo: [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md)
- Secreto `ACS_API_KEY`: [vps-secrets-management.md](./vps-secrets-management.md)
