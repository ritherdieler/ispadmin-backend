# Staging: VSOL `#11` FIBER STATIC_IP — 2026-09-15

Alta lab de la ONU **VSOL** `VSOL0031C0B6` como cliente IP legado. No se tocó la ZTE `#9`.

## Emparejamiento vivo

| Id | ONU | Alta | Tráfico 360 | OLT / MK / TR-069 |
|----|-----|------|-------------|-------------------|
| **9** | `ZTEGDC47BFFD` | FIBER `PPPOE_DYNAMIC` `gf9` | XOR `pppoe:gf9` cola `*8AA` | COMPLETE / COMPLETE / COMPLETE |
| **11** | `VSOL0031C0B6` | FIBER `STATIC_IP` `192.168.250.11` | XOR IP `192.168.250.11` cola `*8AC` | COMPLETE / COMPLETE / FAILED |
| 10 | — | WIRELESS `STATIC_IP` `192.168.250.10` | leftover, no ACS | NA / COMPLETE / NA |

`#10` no es la VSOL.

## Alta

`POST /subscription` contra `https://api.gigafiberperu.cloud/ispadmin-staging` con `installationType=FIBER`, `accessMode=STATIC_IP`, `hostDeviceId=8`, `vlan=100`, NAP `NO-001`, place `9 de octubre`.

| Campo | Valor |
|-------|--------|
| id | **11** |
| DNI | `99523453` |
| Nombre | LAB VSOLSTATIC |
| IP | `192.168.250.11` (pool MK2) |
| PPPoE | ninguno |
| Wi‑Fi 2.4 | `lab-vsol-e2e-24` / `LabVsolWifi24!` |
| Wi‑Fi 5 | `lab-vsol-e2e-24 - 5G` / `LabVsolWifi24!` |
| `pilot_enabled` | `true` |

## Recolección

El directorio de tráfico ya resuelve XOR por `accessMode`: `#9` usuario PPPoE, `#11` IP `/32`. `GET /subscription/{id}/traffic/latest` ve ambas colas en MK2.

TR-069 de `#11` falló porque el alta STATIC_IP caía al provisioner de **perfiles CSV importados** (ya no se usan). El camino vivo es HTTP NBI a GenieACS: ACS encola el script `gf-wifi-ssid-poc` (STATIC_IP) o `gf-pppoe-wan2-poc` (PPPoE); el JS orquesta F6600R vs VSOL. Tras el fix, `POST /subscription/{id}/acs/retry-tr069` debe encolar ese provision, no pedir perfiles. `#9` ya tenía TR-069 COMPLETE por el script PPPoE.

## No tocar

Cleanup y writes solo SN `VSOL0031C0B6`. `#9` ZTE se deja.
