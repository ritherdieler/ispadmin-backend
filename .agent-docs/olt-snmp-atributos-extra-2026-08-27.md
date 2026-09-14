# SNMP — atributos extra cableados (distancia, temp, match, perfiles)

Fecha: 2026-08-27

## Qué se agregó

Walks SNMP Huawei XPON ya no se limitan a SN + run + Rx/Tx/OltRx.

| Origen | OID | Persistencia / API |
|--------|-----|--------------------|
| Inventario `listConfiguredOnus` | `.43.1.7/.8/.9`, `.46.1.18/.20/.24` | `lineProfileName`, `serviceProfileName`, `name`, `matchState`, `distanceM`, `lastDownCause` |
| Óptica `listOptical` | `.51.1.1/.2`, `.46.1.18/.20` (+ potencias) | `temperatureC`, `biasCurrentMa`, `distanceM`, `matchState` en fila óptica |
| Live status | `optical()` + `onuDetail()` | Prefiere match/distancia/temp del SNMP óptico; perfiles de detail o DB |

## Código

- `HuaweiGponSnmpOids` / `HuaweiGponSnmpCodec` — decoders nuevos
- `Snmp4jOltSnmpClient` — walks ampliados
- `SnmpOpticalMerger` / `SnmpOntOptical`
- `OltInventorySyncService.applyStatus` / `applyUpdate` / `getLiveStatusByExternalId`
- `OltSignalPollService` — persiste temp + distancia
- `OltGatewayQueryService.opticalViaSnmp` — propaga campos al DTO

## Fuera de alcance (sigue PROBE)

- WAN IP/DNS (`.49`)
- ETH ports (`.62`)
- Service ports (`2011.5.14.5.2` — MIB distinta)
- Writes (reboot/enable vía SNMP)

## Smoke live e2e (2026-08-27)

ONU `ZTEGDC47DAD1` / `gigafiber-ma5608t_1_1_25` vía `GET …/status`:

| Campo | Valor observado |
|-------|-----------------|
| runState | online |
| onuRxDbm | ≈ −20.9 |
| temperatureC | **37** |
| distanceM | **795** |
| line/service profile | Generic_1_V100 (SSH detail) |
| matchState | null (pendiente decodificar valor raw) |
| Latencia | ~115 s |

Script front: `ispadmin-backoffice` → `npm run e2e:onu-detail` (API + UI Get status + History).

## Docs relacionados

- [olt-ma5608t-snmp-capabilities.md](./olt-ma5608t-snmp-capabilities.md)
- [olt-ma5608t-snmp-atributos-mib-web-2026-08-27.md](./olt-ma5608t-snmp-atributos-mib-web-2026-08-27.md)
- [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md)
- Backoffice: `ispadmin-backoffice/.agent-docs/onu-detalle-smartolt-parity-2026-08-27.md`
