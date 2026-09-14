# OLT MA5608T — Capacidades SNMP / traps (validación live)

**Fecha probe:** 2026-08-26  
**OLT:** `10.11.104.2` · Product `MA5608T` · `MA5600V800R015C00` + `SPH106 HP1013`  
**Método:** CLI solo lectura (`scripts/olt-capture-snmp.expect`) + SNMPv2c GET/GETBULK desde LAN  
**Alcance:** investigación; **sin** cliente Kotlin ni cambios de config SNMP en la OLT.

Contexto roadmap: [olt-manager-llm-contexto.md](./olt-manager-llm-contexto.md) §9.

---

## Checklist experimental

| # | Pregunta | Resultado | Evidencia |
|---|----------|-----------|-----------|
| 1 | ¿SNMP responde? | **PASS** | `sysDescr` / `sysObjectID` / `sysName` / `sysUpTime` OK en UDP/161 |
| 2 | ¿Enumerar ONTs? | **PASS** | `hwGponDeviceOntSn` → **802** filas (~1.5 s GETBULK) |
| 3 | ¿Serial + F/S/P + ONT ID? | **PASS** | Índice `ifIndex.ontId`; SN OCTET STRING 8 B; F/S/P decodificable |
| 4 | ¿Online/Offline? | **PASS** | Run status: **725** online (`1`), **77** offline (`2`) |
| 5 | ¿RX/TX / OLT RX masivo? | **PASS** | Óptica DDM GETBULK ~**104 s** / 802 ONTs (Rx); viable vs poll SSH ~10 min |
| 6 | ¿AutoFind? | **PASS** | Tabla `.52` → **4** ONTs no autorizadas en el probe |
| 7 | ¿Qué traps genera? | **PARTIAL** | Agent SNMP OK; **trap disabled**; **0** target-host; sin captura posible |
| 8 | ¿Qué falta → SSH? | Ver §Recomendación | Writes (authorize/delete/move/reboot), perfiles, service-port, alarm detail CLI |

---

## Agent SNMP (CLI)

| Campo | Valor observado |
|-------|-----------------|
| Versiones | SNMPv1 + SNMPv2c + SNMPv3 |
| Community RO | Presente (`ViewDefault`) — **no documentar valor en git**; leer con `display snmp-agent community read` |
| Community RW | Presente — no usar en gateway salvo writes SNMP futuros |
| USM (v3) | `Total number is 0` |
| `display current-configuration section snmp-agent` | `No configuration data` (communities viven fuera de esa sección en esta firmware) |
| MIB view `ViewDefault` | `internet`, `iso8802`, `ieee`, `lagMIB` included; USM/VACM excluded |
| EngineID | `800007DB0330D17EEE1D4E44` |
| Trap | **`Trap is disabled`** |
| Trap format | `private` |
| Trap source | `auto` |
| Target hosts | **0** |
| DST-NAT CloudOLT | `2161/udp → 10.11.104.2:161` ([smartolt-cloudolt-mk2.md](./smartolt-cloudolt-mk2.md)) |

Comandos CLI usados: ver [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md).

---

## Identidad SNMP

| OID | Valor |
|-----|--------|
| `1.3.6.1.2.1.1.1.0` (sysDescr) | `Huawei Integrated Access Software` |
| `1.3.6.1.2.1.1.2.0` (sysObjectID) | `1.3.6.1.4.1.2011.2.248` |
| `1.3.6.1.2.1.1.5.0` (sysName) | `MA5608T` |

Árbol GPON verificado: **`1.3.6.1.4.1.2011.6.128`** (HUAWEI-XPON / `hwXponDeviceMIB`).

---

## OIDs GPON útiles (verificados en esta OLT)

Índice común: `{ifIndex}.{ontId}`  
`ifIndex = 0xFA000000 + (slot << 13) + (port << 8)` → Fijo frame **0** en MA5608T;  
`slot = (ifIndex >> 13) & 0x3F`, `port = (ifIndex >> 8) & 0x1F`.

| Capacidad | OID (columna) | Filas / notas |
|-----------|---------------|---------------|
| Serial ONT | `…2.43.1.3` `hwGponDeviceOntSn` | 802; 8 bytes (4 ASCII vendor + 4 hex) → canónico `VSOL0086F6E9`. El CLI summary a veces muestra los mismos 8 B como 16 hex (`56534F4C…`); el sync normaliza ambos con `HuaweiGponSnmpCodec.normalizeOntSn` |
| Auth method | `…2.43.1.2` | 802; muestra `1` |
| Line / service profile | `…2.43.1.7` / `.8` | Cableado 2026-08-27 → `lineProfileName` / `serviceProfileName` |
| Description | `…2.43.1.9` | Cableado 2026-08-27 → `description` / `name` |
| Run status | `…2.46.1.15` | 802; `1`=up, `2`=down |
| Match status | `…2.46.1.18` | Cableado 2026-08-27 → `match` / `mismatch` |
| Ranging (m) | `…2.46.1.20` | Cableado 2026-08-27 → `distanceM` |
| Last down cause | `…2.46.1.24` | Cableado 2026-08-27 → `los` / `pwr` / `code_N` |
| Temperature DDM | `…2.51.1.1` | Cableado 2026-08-27 → `temperatureC` |
| Bias DDM | `…2.51.1.2` | Cableado 2026-08-27 → `biasCurrentMa` (÷1000) |
| ONT Rx power | `…2.51.1.4` | 802; raw p.ej. `-572` → interpretar como **−5.72 dBm** (÷100) |
| ONT Tx power | `…2.51.1.5` | raw p.ej. `163` → **1.63 dBm** (÷100) |
| OLT Rx (de ONT) | `…2.51.1.6` | raw p.ej. `7255` — unidad Huawei DDM; validar vs CLI óptica antes de UI |
| Autofind SN | `…2.52.1.2` | 4 filas en probe (puertos 0/1/0, 0/1/2, 0/1/6) |

### Tiempos GETBULK (LAN, `-Cr25`)

| Walk | Filas | Tiempo real |
|------|-------|-------------|
| SN `…43.1.3` | 802 | **~1.5 s** |
| Run status `…46.1.15` | 802 | **~1.1 s** |
| Rx optical `…51.1.4` | 802 | **~104 s** |

Inventario+estado SNMP cabe cómodo en cadencias de decenas de segundos. Óptica masiva full-table era ~2 min (3 columnas secuenciales × ~95 s).

**Amplificación óptica/inventario (2026-08-27):** `listConfiguredOnus` camina también match/ranging/lastDown/description/line/srv profile. `listOptical` añade temp/bias/ranging/match (full-table y per-port). Con bus MA5608T=1 el full-table crece (~7 columnas); Get status (per-port) sigue barato. Escalas de temp/bias conviene confirmar vs CLI en smoke.

**Optimización signal poll (2026-08-27):** default = **full-table GETBULK** de columnas ópticas con threads de columna. Un timeout de columna **no aborta** el poll (se persiste lo que llegó). Walk por puerto (`OPTICAL_PER_PORT=true`) es más lento en esta OLT.

**Bus SNMP (`OltSnmpBusRegistry`, 2026-08-27):** un semáforo **por OLT**. El máximo de walks concurrentes lo marca el **modelo** (`OltSnmpModelLimits` / `olt_mgr_olt_model.max_concurrent_snmp_walks`). **MA5608T = 1** (live: overlapping GETBULK pierde OLT-Rx). Otra OLT (otro modelo o la misma familia en otra caja) tiene su propio bus y puede caminar en paralelo. Con `OPTICAL_PARALLEL_COLUMNS=true` los threads de esta MA5608T esperan permit, sin saturar el agente.

**Persistencia óptica:** si una columna SNMP falla (mapa vacío / null), `OltSignalPollService.applyOpticalUpdates` **no pisa** `onu_rx` / `onu_tx` / `olt_rx` / temperatura / distancia previos en DB.

### Mapa de puertos (SN walk)

22 puertos GPON con ONTs: slots **0** (puertos 0–4, 13–14) y **1** (0–13, 15). Conteos por puerto en captura `/tmp/snmp-sn-full.txt` del probe.

### Muestra (anonimizada)

| ifIndex | F/S/P | ontId | SN (vendor+hex) | status |
|---------|-------|-------|-----------------|--------|
| 4194304000 | 0/0/0 | 0 | `VSOL` + `0086F6E9` | 1 |
| 4194304000 | 0/0/0 | 7 | `HWTC` + `15F5B736` | 1 |
| 4194304000 | 0/0/0 | 11 | (HWTC…) | 2 (offline) |

---

## Traps

| Aspecto | Estado AS-IS (prod) |
|---------|---------------------|
| Generación / enable | **Deshabilitado** (`display snmp-agent trap enable` → `Trap is disabled`) |
| Destinos | **0** (`display snmp-agent target-host`) |
| Trap format | `private` |
| Trap source | `auto` |
| Captura pasiva | No aplicable sin enable + target |
| Relación con alarmas CLI | Alarmas activas siguen vía SSH → NetDiag ([olt-ma5608t-alarms-syslog.md](./olt-ma5608t-alarms-syslog.md)) |
| NetDiag UDP `:1620` | Solo MikroTik; **no** sirve como receptor ASN.1 Huawei sin parser SNMP |

### ¿Se pueden activar?

**Sí.** El CLI en config mode admite habilitar traps y definir target-host. Hoy no hay receptor listo ni destino configurado; activar sin target no entrega eventos a WispAdmin.

| Requisito | Detalle |
|-----------|---------|
| Write en OLT | Sesión `config` (no es solo lectura) |
| Enable | Ver sintaxis abajo |
| Destino | `snmp-agent target-host …` con IP + UDP alcanzable desde `10.11.104.2` |
| Receptor | Proceso que decodifique SNMP trap (ASN.1); puerto dedicado ≠ MikroTik `1620` |
| Persistencia | `save` / confirmar logout si el equipo pide guardar datos |

### Sintaxis CLI verificada (help live 2026-08-26)

Solo documentación; **no** aplicar en prod sin ventana + receptor.

```text
# Parámetros de seguridad (v2c) — securityname = community que usará el trap
snmp-agent target-host trap-paramsname {paramsName} v2c securityname {community}

# Destino
snmp-agent target-host trap-hostname {hostName} address {A.B.C.D} udp-port {1-65535} trap-paramsname {paramsName}

# Habilitar
snmp-agent trap enable                 # enable (grupo; ver help)
snmp-agent trap enable standard        # traps SNMP estándar

# Deshabilitar
undo snmp-agent trap enable standard

# Verificar
display snmp-agent trap enable
display snmp-agent target-host
```

Help observado:

| Comando (fragmento) | Tokens siguientes |
|---------------------|-------------------|
| `snmp-agent trap ?` | `enable` \| `source` |
| `snmp-agent trap enable ?` | `standard` \| (también enable “privado” vía `trap enable` sin `standard`) |
| `snmp-agent target-host ?` | `trap-hostname` \| `trap-paramsname` \| `trap-filterprofilename` |
| `… trap-hostname {name} address {ip} ?` | `udp-port` \| `trap-paramsname` |
| `… udp-port` | `udp-portid<U><1,65535>` |
| `… trap-paramsname {p} v2c ?` | `securityname` |

Candidato destino: VPS `212.85.13.47` (ruta WG→OLT ya existe) en UDP dedicado (p.ej. `1162`), **no** reusar `1620` MikroTik.

### Incidente menor durante documentación (2026-08-26 ~21:58 −05)

Al explorar `?` en expect, se ejecutó por error `snmp-agent trap enable standard` → quedó **Trap is enabled** con **0** targets (sin envío útil).  
Revertido de inmediato con `undo snmp-agent trap enable standard`. Verificado: **Trap is disabled** otra vez. Sin `save` explícito de configuración flash en ese incidente; estado runtime restaurado.

### Plan de activación controlada

1. **DONE (código):** receptor ASN.1 en `oltgateway` — `OltSnmpTrapReceiver` (snmp4j), puerto default **1162**, off por defecto. Dump: `GET /api/olt-gateway/admin/snmp/traps/recent`. **No** reusar NetDiag `:1620`.
2. En lab/VPS: `OLT_GATEWAY_SNMP_TRAP_ENABLED=true`, firewall UDP 1162 desde `10.11.104.2`, community opcional en `OLT_GATEWAY_SNMP_TRAP_COMMUNITY`.
3. Ventana corta en OLT: `trap-paramsname` + `trap-hostname` (IP del receptor) + `snmp-agent trap enable` (standard y/o private).
4. Provocar 1 evento (ONT offline lab) → volcar PDU con el endpoint recent → mapear OID Huawei ↔ `reasonCode` NetDiag.
5. Hasta mapear: **no** sustituir el poll SSH de alarmas.

---

## Recomendación (qué baja a SNMP vs SSH)

| Función | Vía propuesta |
|---------|----------------|
| Inventario ONTs + SN + F/S/P/ONT ID | **SNMP** GETBULK `…43.1.3` |
| Online/Offline | **SNMP** `…46.1.15` |
| Autofind / pendientes | **CLI** `display ont autofind all` — API `GET /onus/autofind` (SNMP `…52.*` retiene fantasmas; no usar para la UI) |
| Óptica masiva | **SNMP** `…51.1.{4,5,6}` — sync canónico ~**5 min** (`signal-interval-ms=300000`); SSH optical **deprecado** |
| Authorize / delete / move / reboot / service-port | **SSH** (writes) |
| Alarmas ricas (Alarm ID hex, ADVICE) | **SSH** poll hasta traps validados |
| Señales ~15 s | Factible solo inventario/estado SNMP; óptica full walk ~104 s → no 15 s sin estrategia por puerto |

---

## Scripts de probe

| Script | Uso |
|--------|-----|
| `scripts/olt-capture-snmp.expect` | CLI communities / sys-info / target-host / mib-view |
| `scripts/olt-capture-snmp-traps.expect` | Trap format / target / engineid / usm |
| Manual | `snmpget` / `snmpbulkwalk -v2c -c "$OLT_SNMP_RO_COMMUNITY" 10.11.104.2 …` |

Community: exportar en local como `OLT_SNMP_RO_COMMUNITY` (no commit). Modelo DB ya contempla `snmp_ro_community_enc` ([olt-manager-db-model.md](./olt-manager-db-model.md)).

---

## Próximo trabajo de código (parcialmente hecho)

1. **DONE:** Cliente SNMP RO + sync inventario canónico (SSH inventory deprecado).  
2. **DONE:** Sync óptico SNMP → DB (`OltSignalPollService` SNMP-first; SSH optical deprecado; intervalo default **5 min**).  
3. **DONE:** `OltSnmpBusRegistry` — semáforo SNMP **por OLT**; MA5608T limitado a **1** walk (`OltSnmpModelLimits`).  
4. **PARTIAL:** Receptor ASN.1 traps (`OltSnmpTrapReceiver`, puerto 1162, default off). Falta enable/target en OLT + mapeo Huawei→NetDiag.  
5. Tests con fixtures de walks reales (anonimizados) si hace falta más cobertura.

### Activar sync SNMP inventario + señal (canónico; SSH deprecado)

Inventario (~10 min) y señal (~5 min) usan **SNMP** cuando enabled + community.  
Fallbacks SSH solo con `OLT_GATEWAY_SNMP_ALLOW_SSH_FALLBACK` / `OLT_GATEWAY_SNMP_ALLOW_SSH_SIGNAL_FALLBACK`.

```bash
OLT_GATEWAY_SNMP_ENABLED=true
OLT_GATEWAY_SNMP_RO_COMMUNITY='<community-ro>'
# espera de permit del bus de esta OLT (capacidad = modelo, MA5608T=1)
# OLT_GATEWAY_SNMP_ACQUIRE_TIMEOUT_MS=300000
# signal default ya es 300000 (5 min); override si hace falta:
# OLT_GATEWAY_SYNC_SIGNAL_INTERVAL_MS=300000

curl -X POST -H "X-Olt-Gateway-Key: $KEY" \
  http://localhost:8080/api/olt-gateway/admin/sync/inventory
curl -X POST -H "X-Olt-Gateway-Key: $KEY" \
  http://localhost:8080/api/olt-gateway/admin/sync/signal
```
