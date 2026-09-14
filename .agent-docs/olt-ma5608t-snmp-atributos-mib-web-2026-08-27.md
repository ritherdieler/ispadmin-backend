# HUAWEI-XPON / MA5608T — atributos SNMP documentados (scraping 2026-08-27)

**OLT nuestro:** SmartAX **MA5608T** · firmware `MA5600V800R015C00` · árbol verificado `1.3.6.1.4.1.2011.6.128` (`hwXponDeviceMIB` / HUAWEI-XPON-MIB).

**Fuentes web (no oficiales Huawei Support para XPON completo; MIB pública vía terceros):**

| Fuente | URL | Qué aporta |
|--------|-----|------------|
| Observium MIB browser | https://mibs.observium.org/mib/HUAWEI-XPON-MIB/ | **1549** objetos; detalle por OID (ej. ranging) |
| NOC Project cmib | https://github.com/nocproject/noc/blob/master/cmibs/huawei_xpon_mib.py | Mapa nombre↔OID (~1257 entradas XPON) |
| Jeremias0618 (MA5600T/08T) | https://github.com/Jeremias0618/Huawei-OLT-ONT-SNMP-MIBs | Lista práctica + service-port `2011.5.14.5.2` + códigos lastDown |
| GPON Solution | https://gponsolution.com/snmp-mib-huawei-olt-ont.html | Lista corta (distancia, temp, mac count…) |
| oid-base | https://oid-base.com/get/1.3.6.1.4.1.2011.6.128.1.1.2.46.1.15 | Descripción RunStatus |
| Pastebin MIB firmware MA5600T | https://pastebin.com/wjj68SUX | Nombres de traps/objetos extraídos de firmware |

**Índice ONT (igual que nuestro probe):** `{ifIndex}.{ontId}` con `ifIndex = 0xFA000000 + (slot<<13) + (port<<8)`.

Leyenda **Uso:** `YA` = en nuestro código · `PROBE` = documentado MIB, falta smoke en Gigafiber · `DOC` = solo documentación tercera · `WRITE` = columna de acción (no lectura ficha).

---

## Tabla `.43` — `hwGponDeviceOntConfigInfoTable` (config ONT)

| # | OID | Atributo MIB | Uso / mapeo sugerido |
|---|-----|--------------|----------------------|
| 1 | `…2.43.1.1` | `hwGponDeviceOntIndex` | índice |
| 2 | `…2.43.1.2` | `hwGponDeviceOntAuthMethod` | PROBE (Gigafiber: valor `1`) |
| 3 | `…2.43.1.3` | `hwGponDeviceOntSn` | **YA** → `sn` |
| 4 | `…2.43.1.4` | `hwGponDeviceOntPassword` | DOC (secreto) |
| 5 | `…2.43.1.5` | `hwGponDeviceOntTimeOut` | DOC |
| 6 | `…2.43.1.6` | `hwGponDeviceOntManagementMode` | PROBE → OMCI/TR069 mode |
| 7 | `…2.43.1.7` | `hwGponDeviceOntLineProfName` | **YA** → `lineProfileName` (`listConfiguredOnus`) |
| 8 | `…2.43.1.8` | `hwGponDeviceOntServiceProfName` | **YA** → `serviceProfileName` |
| 9 | `…2.43.1.9` | `hwGponDeviceOntDespt` | **YA** → `description` / `name` (importadas) |
| 10 | `…2.43.1.10` | `hwGponDeviceOntEntryStatus` | WRITE rowStatus (delete=6 en docs) |
| 11–16 | `…2.43.1.11`…`16` | protect / LOID / checkcode / auth effect | DOC |

---

## Tabla `.46` — `hwGponDeviceOntControlInfoTable` (estado / control)

| # | OID | Atributo MIB | Uso / mapeo sugerido |
|---|-----|--------------|----------------------|
| 1 | `…2.46.1.1` | `hwGponDeviceOntControlActive` | WRITE enable/disable |
| 2 | `…2.46.1.2` | `hwGponDeviceOntControlReset` | WRITE reboot |
| 3 | `…2.46.1.3` | `hwGponDeviceOntControlReRegister` | WRITE |
| 4 | `…2.46.1.4` | `hwGponDeviceOntControlReDiscovery` | WRITE |
| 15 | `…2.46.1.15` | `hwGponDeviceOntControlRunStatus` | **YA** → `runState` (`1` up / `2` down / `-1` invalid) |
| 16 | `…2.46.1.16` | `hwGponDeviceOntControlConfigStatus` | PROBE |
| 17 | `…2.46.1.17` | `hwGponDeviceOntControlDiscoveryStatus` | PROBE |
| 18 | `…2.46.1.18` | `hwGponDeviceOntControlMatchStatus` | **YA** → `matchState` (`1` match / `2` mismatch) |
| 19 | `…2.46.1.19` | `hwGponDeviceOntControlDbaStatus` | DOC |
| 20 | `…2.46.1.20` | `hwGponDeviceOntControlRanging` | **YA** → `distanceM` (m; `≤0` / `-1` invalid) |
| 21 | `…2.46.1.21` | `hwGponDeviceOntControlMacCount` | PROBE |
| 22 | `…2.46.1.22` | `hwGponDeviceOntControlLastUpTime` | PROBE |
| 23 | `…2.46.1.23` | `hwGponDeviceOntControlLastDownTime` | PROBE → `lastStatusChange` |
| 24 | `…2.46.1.24` | `hwGponDeviceOntControlLastDownCause` | **YA** → `lastDownCause` (`1`/`2`→`los`, `13`→`pwr`) |
| 25 | `…2.46.1.25` | `hwGponDeviceOntControlLastDyingGaspTime` | PROBE |
| 26 | `…2.46.1.26` | `hwGponDeviceOntControlIsolationState` | PROBE |
| 27 | `…2.46.1.27` | `hwGponDeviceOntControlBatteryCurStatus` | DOC |
| 28 | `…2.46.1.28` | `hwGponDeviceOntControlTcontNumCombined` | DOC |

---

## Tabla `.51` — `hwGponDeviceOntOpticalDdmInfoTable` (óptica ONT)

| # | OID | Atributo MIB | Uso / mapeo sugerido |
|---|-----|--------------|----------------------|
| 1 | `…2.51.1.1` | `hwGponOntOpticalDdmTemperature` | **YA** → `temperatureC` (°C o ÷100 si \|raw\|≥1000) |
| 2 | `…2.51.1.2` | `hwGponOntOpticalDdmBiasCurrent` | **YA** → `biasCurrentMa` (raw÷1000) |
| 3 | `…2.51.1.3` | `hwGponOntOpticalDdmTxPower` | DOC (en nuestro código Tx es `.5`) |
| 4 | `…2.51.1.4` | `hwGponOntOpticalDdmRxPower` | **YA** → `onuRxDbm` (÷100) |
| 5 | `…2.51.1.5` | `hwGponOntOpticalDdmVoltage` | **YA en OID Tx** en nuestro código — **ojo**: MIB NOC nombra `.5` Voltage y `.3` Tx; **nuestro cliente usa `.5`=Tx y `.6`=OLT Rx** (alineado a probe Gigafiber). Validar nombres al ampliar. |
| 6 | `…2.51.1.6` | `hwGponOntOpticalDdmOltRxOntPower` | **YA** → `oltRxDbm` |
| 7 | `…2.51.1.7` | `hwGponOntOpticalDdmAniCATVRxPower` | PROBE CATV |

> Nota: hay discrepancia de naming entre dumps MIB (NOC) y el mapping que ya validamos live en Gigafiber (Rx=.4, Tx=.5, OltRx=.6). **No cambiar OIDs de potencia sin re-probe.** Temp/bias suelen ser `.1` / `.2`.

---

## Tabla `.52` — autofind / register

| OID | Atributo | Uso |
|-----|----------|-----|
| `…2.52.1.1` | RegisterSerialNum | DOC |
| `…2.52.1.2` | RegisterSn | YA en código; **UI no** (fantasmas) |
| `…2.52.1.3` | RegisterResult | DOC |
| `…2.52.1.4` | RegisterTime | DOC |

---

## Tabla `.49` — IP config ONT (WAN)

| OID | Atributo MIB | Uso / mapeo sugerido |
|-----|--------------|----------------------|
| `…2.49.1.1` | `hwGponDeviceOntIpConfigMode` | PROBE → wanMode (DHCP/static…) |
| `…2.49.1.2` | `hwGponDeviceOntIpAddress` | PROBE → `ipAddress` |
| `…2.49.1.3` | `hwGponDeviceOntNetMask` | PROBE → `subnetMask` |
| `…2.49.1.5` | `hwGponDeviceOntNetGateway` | PROBE → `defaultGateway` |
| `…2.49.1.6` / `.7` | Master/Slave DNS | PROBE → `dns1` / `dns2` |
| `…2.49.1.8` | `hwGponDeviceOntIpConfigVlan` | PROBE → `vlan` |

Documentado también en README Jeremias (WAN DHCP/static).

---

## Tabla `.62` — Ethernet ports ONT

| OID | Atributo | Uso |
|-----|----------|-----|
| `…2.62.1.1`…`8` | portId, negotiate, duplex, speed, operate, flow, vlan default… | PROBE para ficha ETH |
| `…2.62.1.22` | `hwGponDeviceOntEthernetOnlineState` | DOC (status eth, no run ONT) |
| `…2.62.1.23`…`25` | CAR profiles / IGMP | DOC |

---

## Tabla `.21` — control puerto GPON OLT (no ONT)

Nearest/farthest distance, autofind enable, laser, status link, ont count, last down… Útil para salud de puerto PON, no ficha ONU.

---

## Service ports (fuera de XPON `6.128`)

Documentado en README MA5608T (Jeremias) bajo **`1.3.6.1.4.1.2011.5.14.5.2.1.*`**:

| Sufijo | Semántica documentada |
|--------|------------------------|
| `.2` | Shelf |
| `.3` | Slot |
| `.4` | Port |
| `.5` | ONU ID |
| `.6` | Gemport |
| `.7` | Multiservice |
| `.8` / `.12` | VLAN |
| `.9` / `.10` | Inbound / Outbound (speed profiles?) |
| `.15` | RowStatus (6=delete) |

**No está en nuestro probe Gigafiber.** Hay que `snmpbulkwalk` de `…5.14.5.2` en la OLT antes de implementarlo. Hasta entonces CLI sigue siendo el plan B.

---

## Observium (MIB completa)

https://mibs.observium.org/mib/HUAWEI-XPON-MIB/ reporta **1549 objetos** (GPON+EPON+PM+traps). Este documento lista solo las tablas relevantes a ficha ONU / inventario / señal / service-port.

Ejemplo detalle ranging: https://mibs.observium.org/object/HUAWEI-XPON-MIB/hwGponDeviceOntControlRanging — unidad **m**, `-1` = invalid.

---

## Conclusión para nosotros

1. **Distancia SÍ está en MIB:** `…2.46.1.20` → siguiente smoke live.
2. **Temp / bias SÍ están en MIB:** `…2.51.1.1` / `.2` → smoke live (sin tocar mapping Rx/Tx ya validado).
3. **Match / lastDown / perfiles / descripción** también en MIB (`.46.18`, `.46.24`, `.43.7–9`).
4. **Service ports** en otra MIB (`2011.5.14.5.2`), documentada para MA5600/08T, **sin verificar aquí**.
5. MIB oficial Huawei Support para MA5608T XPON no aparece pública completa; el catálogo usable es HUAWEI-XPON-MIB vía Observium/NOC + listas comunitarias MA5608T.
