# Fix authorize SSH ≈ SmartOLT (profiles + traffic-table) — 2026-09-06

## Problema

Con `olt.provider.authorize=GATEWAY` (SSH), el alta usaba line/srv profile **10/10** y un `service-port` **sin** traffic-table. SmartOLT cloud, con `custom_profile=Generic_1` + `vlan=100`, deja en la OLT:

| Ítem | SmartOLT |
|------|----------|
| Line / srv | **6 / 13** (`Generic_1_V100`) |
| Description | `{name}_zone_{zone}_authd_yyyyMMdd` |
| Traffic tables | inbound **8**, outbound **9** |

Sin el mapping VLAN 100 del profile `Generic_1_V100`, la ONU a menudo no obtiene WAN usable y **no informa a GenieACS**, aunque el `ont add` SSH “haya funcionado”.

## Fix

- `SmartOltAuthorizeProfileResolver` — bindings `Generic_1:1=3:2,Generic_1:100=6:13`
- `OltManagerFacade.authorizeOnu` / `planAuthorize` / `moveOnu` usan el resolver
- `OltGatewayCommandService` añade traffic-table 8/9 al `service-port`
- Props: `olt.gateway.writes.custom-profile-bindings`, `inbound/outbound-traffic-table-index`, `smartolt-olt-id`

## Verificación

Unit:

```bash
./mvnw -Dtest=SmartOltAuthorizeProfileResolverTest,OltGatewayCommandServiceTest,OltManagerFacadeTest test
```

Live (OLT `10.11.104.2`, lab SN `ZTEGDC47BFFD`):

```bash
OLT_WRITE_LIVE=true OLT_HOMOLOG_AUTHORIZE=true OLT_WRITE_KEEP=1 \
  ./mvnw -Dtest=OltGatewayAuthorizeHomologLiveSmokeTest test
```

Resultado 2026-09-06: `LIVE SSH HOMOLOG OK` — line 6 / srv 13 / Run state online / service-port up.

## Local: Gateway → SmartOLT (comparación cloud)

Requiere `OLT_SERVICE_API_KEY` en el entorno (o `application-local.properties`) y:

```bash
export OLT_PROVIDER_AUTHORIZE=SMARTOLT
# writes SSH pueden quedar true; con SMARTOLT el router no usa SSH
```

Sin API key, el camino validado es `OLT_PROVIDER_AUTHORIZE=GATEWAY` con el fix de perfiles.

## Docs relacionados

- [homologacion-gateway-smartolt-authorize-2026-09-02.md](./homologacion-gateway-smartolt-authorize-2026-09-02.md)
- [gateway-smartolt-write-toggle-2026-09-05.md](./gateway-smartolt-write-toggle-2026-09-05.md)
- [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md)
