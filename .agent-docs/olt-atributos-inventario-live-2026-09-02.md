# Inventario live — atributos propios OLT/ONU (MA5608T)

Fecha: 2026-09-02 · Host: `10.11.104.2` · Solo lectura CLI · Una sesión SSH  
Script: `scripts/olt-attribute-inventory.py`  
Captura cruda: `/tmp/olt-attribute-inventory-raw.txt` (local, no commit)

Objetivo: separar lo que **expone la OLT** de atributos de catálogo/ops/SmartOLT que el gateway no debería modelar como “del equipo”.

## 1. OLT (chasis / sistema)

Fuente: `display version`, `display board 0`, `display snmp-agent sys-info`, `display time`.

| Atributo CLI | Valor observado | Notas |
|--------------|-----------------|-------|
| PRODUCT | `MA5608T` | Identidad de modelo de equipo |
| VERSION | `MA5600V800R015C00` | Software activo |
| PATCH | `SPH106 HP1013` | Parches |
| Uptime | 15d 5h 36m | Runtime |
| Program/Data Area A/B | `MA5600V800R015C00` | Dual bank |
| Time | `2026-09-02 23:34:56-05:00` | Reloj OLT |
| Board SlotID / BoardName / Status / SubType | ver tabla | Chassis frame 0 |
| SNMP contact / location / versions | Huawei defaults; v1/v2c/v3 | Sys-info |

### Boards (`display board 0`)

| Slot | BoardName | Status | SubType |
|------|-----------|--------|---------|
| 0 | H805GPFD | Normal | — |
| 1 | H806GPFD | Normal | — |
| 3 | H801MCUD1 | Active_normal | CPCB |
| 4 | H801MPWD | Normal | — |

`display board 1` → `% Parameter error` (igual que docs previas).

### No disponibles / incompletos en este firmware (enable)

- `display user-interface maximum-vty` → unknown
- `display users` → unknown
- `display device` → unknown
- `display patch-information` → unknown
- `display temperature` / `display power` / `display vlan summary` → incomplete/parameter error (necesitan args)

**Conclusión:** el “modelo” físico no expone `max_concurrent_cli_sessions`, `max_concurrent_snmp_walks`, `max_slot_probe`, `default_ports_per_gpon_board`, `family`, `notes` ni `vendor` como campos de catálogo. Esos son **límites/ops del gateway**, no atributos OLT.

## 2. Puerto GPON (módulo óptico OLT)

Fuente: `interface gpon 0/0` → `display port state all`.

| Atributo | Ejemplo 0/0/0 |
|----------|----------------|
| F/S/P | 0/0/0 |
| Optical Module status | Online |
| Port state | Online |
| Laser state | Normal |
| Available bandwidth(Kbps) | 1130972 |
| Temperature(C) | 38 |
| TX Bias current(mA) | 47 |
| Supply Voltage(V) | 3.21 |
| TX power(dBm) | 7.71 |
| Illegal rogue ONT | Inexistent |
| Max Distance(Km) | 60 |
| Wave length(nm) | 1490 |
| Fiber type | Single Mode |

GPFD = 16 puertos por board (slots 0 y 1).

## 3. Catálogos en la OLT (no “atributos de ONU”, pero sí del equipo)

| Comando | Qué lista |
|---------|-----------|
| `display ont-lineprofile gpon all` | Profile-ID, Profile-name, Binding times (12 perfiles) |
| `display ont-srvprofile gpon all` | Profile-ID, Profile-name, Binding times (15 perfiles) |
| `display dba-profile all` | ID, type, Fix/Assure/Max kbps, Bind times |
| `display ont autofind all` | ONUs no confirmadas (si hay) |

## 4. ONU — ficha (`display ont info`)

Muestra live `by-sn ZTEGDC47DAD1` (0/1/1:25) y `gpon 0/0` ont 0.

### Identidad / estado

| Atributo CLI | Ejemplo |
|--------------|---------|
| F/S/P | 0/1/1 |
| ONT-ID | 25 |
| Control flag | active |
| Run state | online |
| Config state | normal |
| Match state | mismatch / match |
| DBA type | SR |
| ONT distance(m) | 795 |
| ONT battery state | holding / not support |
| Memory / CPU occupation | - |
| Temperature | - (en info; sí en optical) |
| Authentic type | SN-auth |
| SN | hex + ASCII (`ZTEG-DC47DAD1`) |
| Management mode | OMCI |
| Software work mode | normal |
| Isolation state | normal |
| Description | texto libre |
| Last down cause | dying-gasp |
| Last up / down / dying gasp time | timestamps |
| ONT online duration | duración |
| Type C support | Not support |
| Interoperability-mode | ITU-T |
| VoIP configure method | Default |

### Perfiles / mapping

| Atributo CLI | Ejemplo |
|--------------|---------|
| Line profile ID / name | 6 / Generic_1_V100 |
| Service profile ID / name | 13 / Generic_1_V100 |
| FEC upstream / OMCC encrypt / Qos / Mapping mode | Disable / Off / PQ / VLAN |
| TR069 management / IP index | Disable / 0 |
| T-CONT → DBA Profile-ID | 0→2, 1→11 |
| GEM Serv-Type / Encrypt / VLAN mapping | ETH / off / VLAN 100 |
| Port-type counts (POTS/ETH/…) | 0 o adaptive |
| Alarm policy profile ID/name | 0 / alarm-policy_0 |

### Versión ONU (`display ont version`)

Vendor-ID, ONT Version, Product-ID, Equipment-ID, Main/Standby Software Version, OntProductDescription, Support XML Version.

### Capability (`display ont capability`)

Equipment ID, nº puertos PON/ETH/POTS/CATV/GEM/T-CONT, IP configuration, ONT type (HGU), etc.

### Óptica ONU (`display ont optical-info`)

| Atributo CLI | Ejemplo 0/0/0 |
|--------------|----------------|
| Rx optical power(dBm) | -5.72 |
| Tx optical power(dBm) | 32.17 (valor anómalo en esta muestra) |
| Laser bias current(mA) | 5600 (anómalo; validar escala) |
| Temperature(C) | 45 |
| Voltage(V) | 0.163 |
| OLT Rx ONT optical power(dBm) | -27.45 |
| CATV Rx / thresholds / vendor module fields | varios `-` |

### ETH port (`display ont port attribute … eth 1`)

Auto-neg, Speed, Duplex, Port switch, Flow control, Native VLAN, Priority.

### Fallos en muestra

- `display ont wan-info 0 0` → `Failure: ONT process failed`
- `display ont info 0 0 0` desde enable (sin `interface gpon`) → Incomplete command

## 5. Qué NO es atributo de OLT/ONU (candidatos a limpiar del modelo gateway)

Atributos/campos del WAR gateway que **no salen** de la OLT y pertenecen a ops, SmartOLT o CRM:

### `olt_mgr_olt_model` (catálogo artificial)

| Campo JPA | Origen real |
|-----------|-------------|
| `maxConcurrentCliSessions` | Límite ops / Reenter (no CLI `display`) |
| `maxConcurrentSnmpWalks` | Límite agente medido por nosotros |
| `maxSlotProbe` | Heurística discovery gateway |
| `defaultPortsPerGponBoard` | Heurística (GPFD=16 se deduce del board, no de un campo “modelo”) |
| `family` / `notes` | Metadata nuestra |
| `vendor` / `product` / `code` | Derivables de `display version` PRODUCT (+ naming) |

### `olt_mgr_onu` (legado SmartOLT / negocio)

No expuestos por la OLT como campos propios: `zone`, `splitter*`, `address`, `contact`, `latitude`, `longitude`, `mode`, `wanMode`, `customTemplate`, `authorizedByUserId`, `externalId` (formato SmartOLT), `signalCategory` (cálculo nuestro), flags `importedFromOlt` / `syncedAfterImport` / `lastResync*`, etc.

### API `OltInfoDto`

Hoy mezcla live OLT (`product`, `version`, `patch`, `uptime`, `boards`) con catálogo (`modelCode`, `maxConcurrentCliSessions`). Para gateway aislado, lo propio del equipo es solo el bloque live.

## 6. Mínimo canónico sugerido (solo OLT/ONU)

**OLT:** product, version, patch, uptime, time, boards\[slot,name,status,subtype\], snmp sys-info opcional, ports GPON state, line/srv/dba profile catalogs.

**ONU:** F/S/P, ontId, sn, controlFlag, run/config/match state, distanceM, auth type, management mode, description, lastDownCause + timestamps, online duration, line/service profile id+name, optical Rx/Tx/oltRx/temp/bias/voltage, version/equipmentId, eth port attrs si se necesitan.

Todo lo demás → config del WAR, no columnas “del modelo OLT”.

## 7. Cómo repetir

```bash
python3 scripts/olt-attribute-inventory.py 10.11.104.2 root '<pass>' '<enable-pass>'
```

Una sola sesión; no paralelizar (límite Reenter OLT).
