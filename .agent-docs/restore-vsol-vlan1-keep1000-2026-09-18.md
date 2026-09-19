# Restore internet VLAN 1 en VSOL00872649 (2026-09-18)

Cliente sub 1794, SN `VSOL00872649`, F/S/P `0/1/1` ont 91. Tras el retag TR-069 VLAN 1000, `ensure-mgmt` hizo `ont modify … ont-lineprofile-id 12` (`Generic_1_V100M1000MGM`: gem1 VLAN 100, gem2 VLAN 1000). La WAN de internet del CPE seguía en VLAN 1 `192.168.211.145` y quedó sin GEM.

## Qué se restauró

| Pieza | Valor |
|-------|--------|
| Line profile | **13** `Generic_1_HFD44D20E` (ya existía; gem1 mapea VLAN 100, **1** y **1000**; 11 bindings previos). No se creó ni se renombró |
| Service-ports | VLAN **1** gemport **1** (idx 1984) y VLAN **1000** gemport **1** (idx 1985), ambos `up` |
| Internet | ping `192.168.211.145` 0% pérdida (~85 ms) desde el VPS por `wg-olt` |
| TR-069 | ping `10.20.0.161` 0% pérdida; CR `http://10.20.0.161:7547/tr069`; lastInform fresco |

El profile 13 no se edita: un `commit` ahí reconfigura las otras 11 ONUs.

## Por qué un SSH extra a la OLT no entra

`oltadmin` Reenter **4**. Prod (`tomcat9027`) y staging (`tomcat-staging`) abren **2** sesiones cada uno. El quinto SSH responde `Reenter times have reached the upper limit` de inmediato (no es lockout por password). Para el apply se detuvo `tomcat-staging` ~20 s, se aplicó el expect y se volvió a arrancar.

## Secuencia CLI que funcionó

1. `undo service-port port 0/1/1 ont 91`
2. `interface gpon 0/1` → `ont modify 1 91 ont-lineprofile-id 13`
3. `service-port vlan 1 … gemport 1` y `service-port vlan 1000 … gemport 1`
4. `save configuration`

`ont modify` 12→13 **con** el SP VLAN 1000 en gem 2 falla: `The GEM port cannot be deleted because there is configuration data on the GEM port`.

Script: `scripts/olt-restore-vsol-vlan1-keep1000.expect`. Test: `OltRestoreVsolVlan1Keep1000ScriptTest`.
