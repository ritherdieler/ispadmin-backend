# ensure-mgmt VLAN 1000 sin rebind de lineprofile (2026-09-19)

Tras el retag de `VSOL00872649`, `ensureMgmtServicePort` hacía `ont modify … ont-lineprofile-id 12`. El profile 12 solo mapea VLAN 100 (gem 1) y 1000 (gem 2). Internet en VLAN 1 quedó sin GEM y el cliente se quedó sin acceso.

## Comportamiento nuevo

`POST /onu/{sn}/service-port/ensure-mgmt` (Core → Gateway):

1. Si `display service-port` ya lista la VLAN, no escribe.
2. `display ont info {port} {ontId}` → lineprofile **actual**.
3. `display ont-lineprofile gpon profile-id {id}` → mappings GEM.
4. Si VLAN 1000 ya está mapeada: SP en ese GEM (p. ej. gem 2 del profile 12).
5. Si no: `gem mapping {hostGem} {nextIndex} vlan 1000` + `commit` en el GEM de internet (el de menor índice, que lleva VLAN 1 y/o 100) y SP en ese mismo GEM.
6. **Nunca** `ont modify`. La WAN de internet (VLAN 1 o 100) no se rebindea.

El provision GenieACS `gf-tr069-vlan1000` solo escribe la WAN TR-069 identificada por VLAN 100 + pool `192.168.252.0/22`.

## Tests

```bash
./gradlew :oltgateway:test --tests com.dscorp.wispadmin.oltgateway.parser.OntLineProfileGemParserTest --tests com.dscorp.wispadmin.oltgateway.service.OltGatewayCommandServiceTest --tests com.dscorp.wispadmin.oltgateway.service.OltServicePortServiceTest
```

## Parque

El primer `commit` de un profile compartido (p. ej. `Generic_1_HF291F96D` id 5, ~273 ONUs) empuja OMCI a todas las vinculadas. El mapping es aditivo; no quita VLAN 1 ni 100.

**Prod 2026-09-19:** WAR `1.0.3+50f2c50` en `tomcat9027`. `GET /ispadmin/` HTTP 200. El runner del parque va contra Core prod. Detalle: [deploy-prod-ensure-mgmt-vlan1000-2026-09-19.md](./deploy-prod-ensure-mgmt-vlan1000-2026-09-19.md).

Por cada ONU: ping de la WAN de internet (IP fuera de `192.168.252.0/22` y `10.20.0.0/22`) desde el VPS por `wg-olt` **antes**, tras ensure-mgmt y tras el retag CPE. Si estaba reachable y deja de estarlo, el runner aborta y no sigue con GenieACS.

```bash
./scripts/genieacs/retag-tr069-vlan1000.sh --prod --device-id ... --sn VSOL...
```

Relacionado: [restore-vsol-vlan1-keep1000-2026-09-18.md](./restore-vsol-vlan1-keep1000-2026-09-18.md), [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md), [fase1-olt-vlan1000-parque-2026-09-18.md](./fase1-olt-vlan1000-parque-2026-09-18.md).
