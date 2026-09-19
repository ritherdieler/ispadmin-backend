# Deploy prod — ensure-mgmt VLAN 1000 sin `ont modify` (2026-09-19)

`develop` @ `50f2c50` (`1.0.3+50f2c50`). Preflight prod: cadena FIBER/TR-069 completa. `./scripts/deploy.sh --env prod` → `GET /ispadmin/` HTTP **200**.

## Qué entra

- `ensureMgmtServicePort` ya no hace `ont modify … ont-lineprofile-id 12`. Añade `gem mapping` VLAN 1000 al GEM de internet (VLAN 1 o 100) y abre el SP.
- Runner `retag-tr069-vlan1000.sh --prod`: ping internet (VPS/`wg-olt`) antes, tras OLT y tras CPE. Si se pierde, aborta.
- Test V52: acepta DDL idempotente (`''STATIC_IP''`) para que la suite completa pase.

## Smoke

| Check | Resultado |
|-------|-----------|
| Suite `./gradlew test` | PASS (tras el ajuste V52) |
| WAR | `ispadmin.war` en `tomcat9027` |
| `GET /ispadmin/` | 200 |
| Release | `1.0.3+50f2c50` registrado |

No se retageó ninguna ONU en este turno. El siguiente retag va contra Core prod.

Detalle del fix: [ensure-mgmt-vlan1000-sin-ont-modify-2026-09-19.md](./ensure-mgmt-vlan1000-sin-ont-modify-2026-09-19.md).
