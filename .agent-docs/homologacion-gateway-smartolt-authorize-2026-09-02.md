# Homologación Gateway ↔ SmartOLT — unconfigured + authorize (ZTEGDC47BFFD)

Fecha: 2026-09-02  
ONU lab: **ZTEGDC47BFFD** (autofind CLI `5A544547DC47BFFD (ZTEG-DC47BFFD)`), F/S/P **0/1/6**, EquipmentID **F6600RV9.0.21**.

## 1. Unconfigured — SmartOLT real

`GET https://gigafiberperu.smartolt.com/api/onu/unconfigured_onus` (antes de authorize):

```json
{
  "board": "1",
  "olt_id": "2",
  "onu": "0",
  "onu_type_id": "80",
  "onu_type_name": "F6600RV9.0.21",
  "pon_type": "gpon",
  "port": "6",
  "sn": "ZTEGDC47BFFD"
}
```

Gateway homologado (`OltManagerFacade.unconfiguredOnus` / mapper):

| Campo | Antes Gateway | Ahora (homólogo) |
|-------|---------------|------------------|
| `olt_id` | `gigafiber-ma5608t` | `olt.gateway.smartolt-olt-id` (`2`) |
| `sn` | hex CLI crudo / con paréntesis | vendor canónico `ZTEGDC47BFFD` (`AutofindParser` + `normalizeOntSn`) |
| `onu_type_name` | EquipmentID | EquipmentID (igual SmartOLT para esta ONU) |
| `onu_type_id` | `""` | id de `OltMgrOnuType` por nombre (`80` si importado) |
| `onu` | `""` | `"0"` |
| `board` / `port` / `pon_type` | ok | sin cambio |

## 2. Authorize SmartOLT → config OLT observada

`POST onu/authorize_onu` con `vlan=100`, `onu_type=F6600RV9.0.21`, `zone=Zone 1`, `name=HOMOLOG-GW-TEST`, `custom_profile=Generic_1`, `onu_mode=Routing`.

Resultado SmartOLT: ONT-ID **16**, service-port **623**, `unique_external_id=ZTEGDC47BFFD`.

CLI OLT (`display ont info by-sn` / `display service-port 623`):

| Ítem | Valor SmartOLT en OLT |
|------|------------------------|
| Line profile | **6** `Generic_1_V100` |
| Service profile | **13** `Generic_1_V100` |
| Description | `HOMOLOG-GW-TEST_zone_Zone 1_authd_20260902` |
| Service-port | vlan **100**, gemport **1**, tag-transform **translate** |
| Traffic tables | inbound **8** (`SMARTOLT-1G-UP`), outbound **9** (`SMARTOLT-1G-DOWN`) |
| Run state | online |

Referencia vlan 1 (ONT existente en 0/1/6): line **3** / srv **2** = `Generic_1_V1`.

## 3. Homologación en Gateway (código)

- `SmartOltAuthorizeProfileResolver` — bindings `Generic_1:1=3:2,Generic_1:100=6:13` + desc SmartOLT.
- `OltGatewayCommandService` — service-port con traffic-table 8/9.
- `OltManagerFacade.authorizeOnu` — usa resolver de perfiles/desc (ya no defaults 10/10 + nombre plano).
- Props: `olt.gateway.smartolt-olt-id`, `writes.custom-profile-bindings`, `writes.inbound/outbound-traffic-table-index`.

Tests: `AutofindParserTest`, `SmartOltCompatMapperTest`, `OltGatewayCommandServiceTest`, `SmartOltAuthorizeProfileResolverTest`, `OltManagerFacadeTest`, live `OltGatewayAuthorizeHomologLiveSmokeTest`.

Implementado y verificado live 2026-09-06: ver [fix-ssh-authorize-profiles-smartolt-2026-09-06.md](./fix-ssh-authorize-profiles-smartolt-2026-09-06.md).

## 4. Estado live post-prueba

La ONU **ZTEGDC47BFFD** quedó **autorizada vía SSH Gateway** en 0/1/6 ont **16** (profiles 6/13, service-port up, Run state online) tras el live smoke con `OLT_WRITE_KEEP=1`.
