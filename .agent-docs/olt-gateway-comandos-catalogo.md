# Catálogo de comandos OLT Gateway

Fuente de verdad de CLI SSH y HTTP del gateway usados en diagnóstico o código. Detalle largo: [olt-ma5608t-gpon-guide.md](./olt-ma5608t-gpon-guide.md).

## HTTP (OLT Gateway)

Prefijo staging: `/ispadmin-staging-oltgateway/api/olt-gateway`. Header `X-Olt-Gateway-Key`.

| Comando | Descripción | Usado en | Notas |
|---------|-------------|----------|-------|
| `GET /onus/configured?q={sn}` | Inventario + status SNMP de ONUs; `q` busca SN/nombre | Health ONU 360 identidad PON | Paginación `page`/`size` hasta 200; Health recorre todas las páginas y conserva `polledAt` original. Un fallo intermedio invalida la descarga. |
| `POST /onu/activate` | Autoriza ONU en OLT y dispara ACS async; responde parcial (`olt=COMPLETE`, `cpe=PENDING`) | `OnuActivationService` / Core `GatewayOnuActivationClient` | Diario durable en DB propia, request cifrado, deduplicación por SN y contenido. Recuperación programada de operaciones y outbox Redis; sin wait TR-069; OLT fail → `cpe=NA`. Core HTTP read timeout 180s. Staging Gateway hornea `writes.enabled=true`. |
| `GET /onus/by-sn/{sn}/activation` | Estados gruesos olt/cpe por SN | Core poll flags / `retryTr069` | También `GET /onus/by-external-id/{id}/activation`: busca el ID externo en el diario; no lo utiliza como SN para consultar ACS. |
| `POST /onus/{sn}/cpe/reboot` | Reboot CPE vía ACS WAR | Service-health 360 / Core BFF | Header `X-Olt-Gateway-Key`; Core no habla GenieACS |
| `POST /onus/{sn}/cpe/wifi-refresh` | Refresh WiFi GPV vía ACS WAR | Service-health `WIFI_REFRESH` | |
| `GET /onus/{sn}/cpe/telemetry` | Cache CPE (SSID, WAN IP, last inform, product class) | 360 / `GET /subscription/{id}/acs` | Identidad device solo detrás de Gateway |
| `GET /onus/{sn}/service-ports` | VLANs de service-port de una ONU | `OltServicePortService` / Core `GatewayOnuActivationClient.servicePorts` | Migración IP→PPPoE: exige VLAN 100 |
| `POST /onus/{sn}/service-port/remove` body `{"vlan":1}` | `undo service-port` de una VLAN | `OltServicePortService.removeVlan` / cuarentena PPPoE | Core no habla CLI; retira VLAN 1 al cerrar cuarentena |
| `POST /onus/{sn}/service-port/ensure-mgmt` body `{"vlan":1000}` | Pre-registra SP VLAN 1000 **sin** cambiar el lineprofile de la ONT | `OltServicePortService.ensureMgmtVlan` / Core `POST /onu/{sn}/service-port/ensure-mgmt` / `retag-tr069-vlan1000.sh` | Idempotente. Skip si el display ya lista la VLAN. Si el profile actual ya mapea 1000, solo abre el SP en ese GEM. Si no, `gem mapping {hostGem} {next} vlan 1000` + `commit` + SP en el mismo GEM que internet (VLAN 1 o 100). **Nunca** `ont modify`. Un `commit` de profile afecta a todas las ONUs vinculadas (aditivo). Core es la fachada; el runner no llama al Gateway ni abre SSH. Incidente `ont modify` 12: [restore-vsol-vlan1-keep1000-2026-09-18.md](./restore-vsol-vlan1-keep1000-2026-09-18.md) |
| `GET /onus/by-sn/{sn}` | `display ont info by-sn` + estado administrativo | Autofind / alta Android | `administrative_status` puede diferir de SNMP `run_state` |
| `POST /onu/authorize_onu` | Alias SmartOLT: autoriza ONU (form) | `SmartOltCompatController` → `OnuWriteRouter` | Default SSH (`olt.provider.authorize=GATEWAY`); cloud si `SMARTOLT` |
| `POST /onu/delete/{externalId}` | Alias SmartOLT: borra ONU | `SmartOltCompatController` → `OnuWriteRouter` | Toggle `olt.provider.delete` |
| `POST /onu/reboot/{externalId}` | Alias SmartOLT: reboot ONU | `SmartOltCompatController` → `OnuWriteRouter` | Toggle `olt.provider.reboot` |
| `POST /onu/move/{sn}` | Alias SmartOLT: mueve ONU (form board/port) | `SmartOltCompatController` → `OnuWriteRouter` | Toggle `olt.provider.move` |
| `GET /optical-samples?oltId={id}&since={iso}&limit={n}` | Snapshot Rx/Tx/OLT-Rx de `olt_mgr_onu_status_current` más nuevo que `since` | `HealthOltPullService` modo `samples` | Incluye ONU con `run_state=offline` si hay última DDM. Diagnóstico polls/logs: [optical-snmp-core-dedup-and-gateway-logs.md](./optical-snmp-core-dedup-and-gateway-logs.md) |
| `POST /admin/sync/signal` | Poll SNMP óptica forzado | `OltSignalPollService.pollSignals` | Scheduler + logs `SNMP_OPTICAL_*` — [optical-snmp-core-dedup-and-gateway-logs.md](./optical-snmp-core-dedup-and-gateway-logs.md) |
| `POST /onus/optical` body `{"sns":["{sn}"]}` | Poll óptica live de seriales | `HealthOltPullService` modo `live-sns` | Timeout largo (SSH/SNMP); no usar `serials` |

## HTTP (ACS WAR)

Prefijo staging: `/ispadmin-staging-acs/api/acs/v1`. Header `X-Acs-Key` (`ACS_API_KEY`). Lo llaman el Gateway y el Core (`wispadmin/acsclient`). Clientes externos no.

| Comando | Descripción | Usado en | Notas |
|---------|-------------|----------|-------|
| `POST /cpe/provision` | Provision TR-069 (body con SN) | `CpeFacadeService` | Gateway no espera. Core lo usa en migración PPPoE **sin WiFi** |
| `GET /cpe/{sn}/access-layout` | CR URL, lastInform, rutas WANIP/WANPPP y si comparten slot | Core `AccessMigrationService` | Abortar si `wanIpSharesPppSlot=false` |
| `GET /cpe/{sn}/status` | Estado CPE grueso | Gateway GET activation | `PENDING`/`COMPLETE`/`FAILED`/`NA` |
| `GET /cpe/{sn}/telemetry` | Cache device/WiFi/WAN | Gateway telemetry | deviceId no sale al Core |
| `POST /cpe/{sn}/reboot` | Reboot CPE vía GenieACS | Gateway day-2 | |
| `POST /cpe/{sn}/wifi-refresh` | GPV WLAN | Gateway wifi-refresh | |

## HTTP (SmartOLT cloud)

Prefijo típico: `https://gigafiberperu.smartolt.com/api/`. Header `X-Token` (`OLT_SERVICE_API_KEY`). Solo lo llama el WAR Gateway cuando `olt.provider.*=SMARTOLT`.

| Comando | Descripción | Usado en | Notas |
|---------|-------------|----------|-------|
| `POST onu/authorize_onu` | Autoriza ONU en cloud SmartOLT (form) | `RestSmartOltWriteClient` | Tras éxito, Gateway persiste `olt_mgr_onu.unique_external_id` |
| `POST onu/delete/{id}` | Borra ONU en cloud | `RestSmartOltWriteClient` | Luego soft-delete local |
| `POST onu/reboot/{id}` | Reboot ONU en cloud | `RestSmartOltWriteClient` | No toca inventario local |
| `POST onu/move/{sn}` | Mueve ONU en cloud (form board/port) | `RestSmartOltWriteClient` | Actualiza board/port en `olt_mgr_onu` |

## SSH CLI (MA5608T)

| Comando | Descripción | Usado en | Notas |
|---------|-------------|----------|-------|
| `display ont optical-info {port} {ontId}` | Rx/Tx/OLT Rx de una ONU (dentro de `interface gpon 0/{slot}`) | `LabOpticalSshPollService` | Lab SSH; overlay `OLT_GATEWAY_SYNC_LAB_OPTICAL_SSH_ENABLED` |
| `display ont info by-sn {sn}` | FSP + estado administrativo | `GET /onus/by-sn/{sn}` | ~1–3 s |
| `display ont info {port} {ontId}` | FSP + `Line profile ID` de una ONT (dentro de `interface gpon 0/{slot}`) | `OltGatewayCommandService.ensureMgmtServicePort` | Para decidir el GEM de VLAN 1000 **sin** `ont modify` |
| `display ont autofind all` | ONUs no confirmadas (autofind) | `GET /onu/unconfigured_onus` / `OltGatewayQueryService.autofindParsed` | SSH a demanda, job `UNCONFIGURED` (P2). No usa el scheduler ni SNMP (`…52.*` retiene fantasmas). Singleflight si dos técnicos piden a la vez. |
| `ont add {port} {ontId} sn-auth {sn} omci ont-lineprofile-id {line} ont-srvprofile-id {srv} desc "{desc}"` | Autoriza ONT en `interface gpon 0/{slot}` | `OltGatewayCommandService.authorize` / `planAuthorize` | VLAN 100 → line **12** / srv 13 (`labAcsLineProfileId`) + SP VLAN 100 gem 1 y VLAN 1000 gem 2. Live 2026-09-13 `ZTEGDC47BFFD` `0/1/6` ont 16: line 12, srv 13, VLANs 100 y 1000. Desc `{name}_zone_{zone}_authd_yyyyMMdd` |
| `service-port vlan {vlan} gpon 0/{slot}/{port} ont {ontId} gemport 1 multi-service user-vlan {vlan} tag-transform translate inbound traffic-table index {in} outbound traffic-table index {out}` | Crea service-port + traffic tables | `OltGatewayCommandService.authorize` / `move` | Defaults inbound **8** / outbound **9** (`SMARTOLT-1G-UP/DOWN`). En ONU lab ACS se emite **además** el SP VLAN 1000 gemport 2 (misma tabla). |
| `ont delete {port} {ontId}` | Borra ONT en `interface gpon 0/{slot}` | `OltManagerFacade.deleteOnu` | Tras undo; OK si CLI `success: ≥1` **o** `The ONT does not exist` (idempotente); soft-delete DB tombstonea SN `#del#{id}` |

### Uplink / VLAN de placa MCU

Verificados en vivo el **2026-09-10** durante el cutover de la VLAN 100. Detalle: [cutover-vlan100-mk2-tagged-2026-09-10.md](./cutover-vlan100-mk2-tagged-2026-09-10.md).

| Comando | Descripción | Usado en | Notas |
|---------|-------------|----------|-------|
| `display port vlan 0/{slot}/{port}` | VLANs miembro del puerto y cuál es la native | Diagnóstico de uplink | Salida: lista de VLANs, `Total: N`, `Native VLAN: X` |
| `native-vlan {port} vlan {vlan}` | Fija la VLAN untagged del puerto (dentro de `interface mcu 0/{slot}`) | Cutover VLAN 100 | El puerto **debe** ser ya miembro de esa VLAN o falla con `The port is not in the VLAN`. Las demás VLANs miembro pasan a **tagged** |
| `undo native-vlan {port}` | **No existe en V800R015** | — | Devuelve `% Unknown command`; el eco muestra `undo native-vlan{port}`. Para quitar el untagged de una VLAN hay que **mover el native a otra VLAN miembro** |
| `port vlan {vlan} 0/{slot} {port}` | Añade el puerto como miembro de la VLAN | Cutover / preparación de trunk | Idempotente |
| `undo port vlan {vlan} 0/{slot} {port}` | Saca el puerto de la VLAN | Limpieza | Falla con `VLAN has been configured as native VLAN of the ports` si es la native; reasignar el native primero |
| `interface vlanif {vlan}` + `ip address {ip} {mask}` | Crea interfaz L3 de la OLT en una VLAN | Validación de transporte tagged | `display interface vlanif {vlan}` confirma `VLAN Encap-mode : single-tag`. Útil para probar un trunk sin depender de una ONU |
| `undo interface vlanif {vlan}` | Elimina la interfaz L3 | Limpieza | Tras borrarla, el `display` responde `% Parameter error` |
| `save configuration` | Persiste la config en flash | Cierre de cualquier cambio de uplink | **Tarda varios minutos**; imprime `It will take several minutes...` y el prompt vuelve antes de terminar. Esperar antes de lanzar otro comando pesado |

> **Trampa de automatización:** estos comandos devuelven el prompt aunque fallen. Un runner que solo espere el prompt da por bueno un `% Unknown command`. Validar siempre la salida contra `failure|error|unknown command|parameter error`.
>
> **Banco de pruebas seguro:** `0/3/0` y `0/3/1` son GE sin óptica, offline y sin tráfico. Sirven para verificar sintaxis de VLAN de placa sin tocar producción.

### ONT line profiles y segundo service-port (multi-VLAN)

Verificados en vivo el **2026-09-10** al habilitar la VLAN 1000 de gestión. Detalle: [vlan1000-gestion-cpe.md](./vlan1000-gestion-cpe.md).

| Comando | Descripción | Usado en | Notas |
|---------|-------------|----------|-------|
| `display ont-lineprofile gpon all` | Lista de profiles con `Binding times` | Diagnóstico antes de tocar un profile | El binding count dice a cuántas ONUs afecta un `commit`. `Generic_1_V100` (id 6) tenía **82** |
| `display ont-lineprofile gpon profile-id {id}` | T-CONTs, GEM ports y mapeo VLAN→gem | Diseño de perfiles multi-VLAN | `Mapping mode: VLAN`. Un profile sin la VLAN mapeada a un gem no transporta esa VLAN aunque exista el service-port |
| `ont-lineprofile gpon profile-id {id}` | Entra al profile (vista `config-gpon-lineprofile-{id}`) | `OltGatewayCommandService.ensureMgmtServicePort` / alta VLAN 1000 | Sin `profile-name`: no renombra. Crear uno nuevo en vez de editar uno con bindings si el cambio no es aditivo: el `commit` reconfigura por OMCI a **todas** las ONUs vinculadas |
| `tcont {n} dba-profile-id {id}` | Asigna DBA al T-CONT | Clonado de profile | `tcont 0` es el de gestión OMCI. Un profile nuevo lo deja en DBA **1**; si el original usaba otro (p. ej. 2), igualarlo o la ONU arranca con otro perfil de banda |
| `gem add {gem} eth tcont {n}` | Crea el GEM port | Clonado de profile | — |
| `gem mapping {gem} {index} vlan {vlan}` | Mapea una VLAN de usuario a un GEM | `OltGatewayCommandService.ensureMgmtServicePort` (VLAN 1000 aditiva) | Puede ir en el **mismo** GEM que internet (VLAN 1 o 100). Un GEM extra solo si el profile ya mapea 1000 ahí (p. ej. profile 12 gem 2). El `commit` reconfigura OMCI a todas las ONUs del profile |
| `commit` | Persiste el profile | Cierre de edición | Obligatorio antes de `quit` |
| `ont modify {port} {ontId} ont-lineprofile-id {id}` | Revincula la ONT a otro line profile (dentro de `interface gpon 0/{slot}`) | `scripts/olt-restore-vsol-vlan1-keep1000.expect` (restore puntual). **No** lo usa `ensureMgmtServicePort` | Falla `The GEM port cannot be deleted…` si un SP sigue en un GEM que el destino no tiene: `undo service-port port …` **antes**. Live 2026-09-18 `VSOL00872649` `0/1/1` ont 91: 12→**13** (`Generic_1_HFD44D20E`, gem1 VLAN 100+1+1000). Rebind a profile 12 **corta** internet VLAN 1 |
| `service-port vlan {vlan} gpon 0/{slot}/{port} ont {ontId} gemport {gem} multi-service user-vlan {vlan} tag-transform translate inbound traffic-table index 8 outbound traffic-table index 9` | Service-port de gestión VLAN 1000 | `OltGatewayCommandService.ensureMgmtServicePort` / alta VLAN 1000 | `gemport` = GEM que el line profile **actual** mapea a esa VLAN (host gem si el mapping es nuevo; gem 2 solo si el profile ya lo tiene). Convive con el SP de internet (VLAN 1 o 100). Idempotente si el CLI responde `already exists` y el display ya lista la VLAN |
| `display service-port port 0/{slot}/{port} ont {ontId}` | Service-ports de una ONT concreta | `OltGatewayCommandService.displayServicePorts` / verificación | Columna `VCI` = GEM index, `VPI` = ONT ID |
| `undo service-port vlan {vlan} gpon 0/{slot}/{port} ont {ontId}` | Elimina el service-port de una VLAN | `OltGatewayCommandService.removeServicePort` | En MA5608T live 2026-09-18 quedó `Incomplete command`; para borrar todos los SP de una ONT usar la fila siguiente |
| `undo service-port port 0/{slot}/{port} ont {ontId}` | Borra **todos** los service-ports de esa ONT | `OltGatewayCommandService` delete/move / restore VLAN 1 `VSOL00872649` | Obligatorio antes de un `ont modify` que elimine un GEM con SP. Live 2026-09-18: luego SP VLAN 1 y 1000 **gemport 1** (profile 13 no tiene gem 2) |

## SNMP v2c GETBULK (MA5608T, `hwXponDeviceMIB`)

Índice de todas las columnas: `{ifIndex}.{ontId}` (`HuaweiGponSnmpCodec.encodeIfIndex`). Walker: `SnmpMultiColumnWalk` — **una** `PDU.GETBULK` por página con **N varbinds** (`nonRepeaters=0`, `maxRepetitions=25`), cursor independiente por columna y reparto posicional (`index % nVarbinds`) validado por prefijo de raíz.

| Petición | Descripción | Usado en | Notas |
|----------|-------------|----------|-------|
| GETBULK 8 vb full-table: `43.1.3` SN, `46.1.15` runState, `46.1.18` matchState, `46.1.20` ranging, `46.1.24` lastDownCause, `43.1.9` desc, `43.1.7` lineProf, `43.1.8` srvProf | Inventario completo de ONTs configuradas | `Snmp4jOltSnmpClient.listConfiguredOnus` | 817 filas medidas entre **3.5 s** (ventana buena) y **137 s** (ventana con drops: mediana de página 833 ms, máx 41.6 s). Corre bajo el lock `olt-snmp-poll` |
| GETBULK 7 vb scoped a `{col}.{ifIndex}`: `51.1.4` Rx, `51.1.5` Tx, `51.1.6` OLT Rx, `51.1.1` temp, `51.1.2` bias, `46.1.20` ranging, `46.1.18` matchState | Óptica DDM de un puerto GPON | `Snmp4jOltSnmpClient.listOptical(ports)` (default) | **~273 s** los 22 puertos, serial (`optical-parallel-ports=1`); página máx 12.4 s. Requiere `retries=2` + `timeout=20 s`: el agente descarta ~2-8% de páginas sin PDU de error |
| GETBULK 7 vb full-table (mismas columnas, sin `ifIndex`) | Óptica DDM de toda la OLT | `Snmp4jOltSnmpClient.listOptical(null)` con `optical-per-port-walks=false` | ~255 s pero página máx 50 s y un fallo se lleva el dataset completo. Fallback, no default |
| **GETBULK 13 vb scoped a `{col}.{ifIndex}`** (unión de las 8 de inventario + `51.1.4` Rx, `51.1.5` Tx, `51.1.6` OLT Rx, `51.1.1` temp, `51.1.2` bias; `ranging` y `matchState` se deduplican) | Pasada fusionada: inventario y óptica de un puerto en la misma página | `Snmp4jOltSnmpClient.listInventoryAndOptical(ports)` (default con `fused-inventory-optical=true`) | **198.3 s** los 32 puertos con 817 filas por columna, 0 puertos fallidos. Sustituye al par inventario+óptica (407.9 s). Siempre serial (`parallelism=1`); ignora `PER_PORT`/`PARALLEL_PORTS`. 325 bindings/página, nunca `tooBig` |
| GETBULK 1 vb: `46.1.15` runState scoped a `{ifIndex}` | Probe barato: ¿este puerto tiene ONTs? | `Snmp4jOltSnmpClient.portHasOnts` (pasada fusionada) | **51 ms** por puerto vacío frente a 2806 ms si se le lanza la página de 13 vb. Vacío seguro = `endOfMibView` / OID fuera de subárbol. PDU vacío o probe inconcluso **falla el puerto** (`portsFailed`), no lo salta |
| GETBULK 1 vb: `52.1.2` | Autofind (ONTs no configuradas) | `Snmp4jOltSnmpClient.listAutofind` | Mismo walker con una sola columna. **No** cubre ONTs ya configuradas en puertos vírgenes: eso lo resuelve el barrido de 32 puertos de la pasada fusionada |
| GET `1.3.6.1.2.1.1.2.0` | `sysObjectID` para identificar el modelo | `Snmp4jOltSnmpClient.probeSysObjectId` | Espera `1.3.6.1.4.1.2011.2.248` |

Coste del agente: **una lectura OMCI por ONT**, no por varbind — 13 columnas cuestan lo mismo o menos que 1. Detalle y bench: [olt-snmp-multivarbind-getbulk-bench-2026-09-09.md](./olt-snmp-multivarbind-getbulk-bench-2026-09-09.md). Contención y lock: [olt-snmp-optical-contention.md](./olt-snmp-optical-contention.md).
