# Catálogo de comandos OLT Gateway

Fuente de verdad de CLI SSH y HTTP del gateway usados en diagnóstico o código. Detalle largo: [olt-ma5608t-gpon-guide.md](./olt-ma5608t-gpon-guide.md).

Prueba por CLI en la OLT lab (`10.11.104.2`) sin usar la sesión del Gateway: usuario `root`, clave `admin`. `oltadmin` queda reservado al Gateway.

## HTTP (OLT Gateway)

Prefijo del dueño SSH: `/ispadmin/api/olt-gateway` (stopgap, embebido en prod) o `/ispadmin-oltgateway/api/olt-gateway` (sibling, aún no desplegado). Header `X-Olt-Gateway-Key`. El Core envía `X-Gigafiber-Env` (`prod`, `stg` o `lpstg`).

| Header / key | Quién | Efecto |
|---|---|---|
| `X-Olt-Gateway-Key` = `OLT_GATEWAY_API_KEY` | Core prod | Lectura y escrituras de flota |
| `X-Olt-Gateway-Key` = `OLT_GATEWAY_STAGING_API_KEY` | Core staging / prestaging | Lecturas de inventario solo si el SN termina en `olt.gateway.writes.lab-acs-sn-suffixes` (`0031C0B6`, `12345B4641531C0B6`, `ZTEGDC47BFFD`). Listado configurado, detalle, estado, historial y autofind omiten el resto (el detalle responde 404). Escrituras de un SN fuera de esa lista: **403** |
| `X-Gigafiber-Env` distinto de la key | cualquiera | **400**. Sin env y `olt.gateway.acs.require-caller=true`, el day-2 ACS responde **400** (no cae en prod) |
| `X-Acs-To-Gateway-Key` | ACS | Solo `POST /acs/cpe-inform` |

| Comando | Descripción | Usado en | Notas |
|---------|-------------|----------|-------|
| `GET /onus/configured?q={sn}` | Inventario + status SNMP de ONUs; `q` busca SN/nombre | Health ONU 360 identidad PON | Paginación `page`/`size` hasta 200; Health recorre todas las páginas y conserva `polledAt` original. Un fallo intermedio invalida la descarga. Con la key de staging el total y las filas quedan solo en seriales lab; la key de prod no filtra. |
| `GET /onus/configured/{externalId}` | Detalle cacheado de una ONU configurada | Verificación de alta y limpieza desde Core `/onu/configured/{externalId}` | Lee el inventario Gateway; devuelve 404 después de purgar la ONU. |
| `GET /onus/configured/{externalId}/status` | Consulta live de estado GPON y potencia óptica de una ONU configurada | Verificación física desde Core `/onu/configured/{externalId}/status` | Ejecuta las consultas live de estado/óptica del Gateway; requiere que la ONU siga en el inventario configurado. |
| `GET /onus/pon?oltName={name}&board={board}&port={port}` | Inventario cacheado de ONUs de un puerto GPON | Verificación LT del PON `0/{board}/{port}` | Consulta de lectura; permite confirmar que un SN ya no aparece en el inventario del puerto después de `ont delete`. |
| `POST /onu/activate` | Autoriza ONU en OLT y dispara ACS async; responde parcial (`olt=COMPLETE`, `cpe=PENDING`) | `OnuActivationService` / Core `GatewayOnuActivationClient` | Diario durable en DB propia, request cifrado, deduplicación por SN y contenido. Recuperación programada de operaciones y outbox Redis; sin wait TR-069; OLT fail → `cpe=NA`. Core HTTP read timeout 180s. Staging Gateway hornea `writes.enabled=true`. |
| `POST /onus/provisioning/compensate` body `{"operationId":"…","sn":"…","expectedExternalId":"…"}` | Revierte la autorización ONU de una operación v2 y confirma que dejó de estar configurada | Core `OltProvisioningStageHandler.compensate` | Si existe reserva, debe pertenecer a la operación; si falta, solo elimina cuando el ID externo configurado coincide con la evidencia Core. Sin reserva y sin ONU configurada es idempotente. Identidad distinta responde 409; borrado no confirmado responde 502; la reserva se libera tras confirmar limpieza. |
| `GET /onus/by-sn/{sn}/activation` | Estados gruesos olt/cpe por SN | Core poll flags / `retryTr069` | En un reintento, Core toma de aquí el `uniqueExternalId` durable y no lo sustituye por el ID numérico de la suscripción. También `GET /onus/by-external-id/{id}/activation`: busca el ID externo en el diario; no lo utiliza como SN para consultar ACS. |
| `POST /onus/{sn}/cpe/provision` | Reaplica el aprovisionamiento TR-069 a una ONU ya autorizada | Core `POST /subscription/{id}/acs/retry-tr069` | El body conserva el `uniqueExternalId` de la activación y las credenciales PPPoE de la suscripción. `X-Gigafiber-Env: stg` enruta al ACS staging. Un rechazo HTTP se traduce en 502 saneado; la acción queda `FAILED` y puede reintentarse de inmediato con una request key nueva. |
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
| `ont wifi-config` | No existe en esta OLT | CLI manual 2026-09-21 | `MA5600V800R015C00`: `% Unknown command`. El SSID no se escribe por este comando. La guía oficial V800R015C00 manda el WiFi del gateway ONT a la web de la ONT o a U2000 |
| `ont wan-config` | No existe en esta OLT | CLI manual 2026-09-22 | `% Unknown command` en `interface gpon 0/1`. En recetas de F660 sobre MA5600 **V800R017** este comando liga el `ip-index` a un WAN profile. Aquí no está |
| `ont internet-config` | No existe en esta OLT | CLI manual 2026-09-22 | `% Unknown command` en `interface gpon 0/1`. En recetas posteriores marca el `ip-index` como WAN de internet |
| `ont wan-profile` | No existe en esta OLT | CLI manual 2026-09-22 | `% Unknown command` en modo `config`. En recetas posteriores crea el perfil de WAN enrutada con NAT |
| `ont ipconfig {port} {ontId}` | Help del IP host: `dhcp`, `pppoe`, `static`, `ip-index`, `dscp-mapping-table` | CLI manual 2026-09-22 | En `interface gpon 0/{slot}`. `dhcp` expone `<cr>`, `reset` y `vlan`; `pppoe` expone `<cr>`, `user-account` y `vlan`; `static` expone `<cr>`, `gateway`, `ip-address`, `pri-dns`, `slave-dns` y `vlan`; `dscp-mapping-table` recibe índice `0..49`. |
| `ont port native-vlan {port} {ontId}` | VLAN nativa por tipo de puerto | CLI manual 2026-09-22 | Help: `eth`, `iphost`, `moca`, `vdsl`. `ont port vlan {port} {ontId}` acepta `eth`, `iphost`, `vdsl` |
| `undo xpon ont-interoperability-mode gpon vendor ZTEG equipment F6600RV9.0.21` | Quita la regla CTC de la F6600R | CLI manual 2026-09-22 | La regla se había creado con `… ctc activemode immediate`. Tras el undo, `display … vendor ZTEG` responde `The specified ONT interoperability rule does not exist`. `ont re-register 6 16` dejó `ZTEGDC47BFFD` online con `Interoperability-mode: ITU-T` (last up `2026-09-22 01:04:08-05:00`) |
| `display xpon ont-interoperability-mode gpon [vendor {vendor-id} [equipment {equipment-id}]]` | Consulta modo global o regla de interoperabilidad por fabricante/equipo | Diagnóstico live 2026-09-29 `VSOL-0031C0B6` | MA5608T `MA5600V800R015C00 SPH106 HP1013`: modo global `ITU-T`; no existe regla para `HWTC` ni `HWTC/VSOLVA74`. La ONU recién confirmada reportó `Interoperability-mode: ITU-T`; no se creó override |
| `ont home-gateway-config {port} {ontId} profile-id {id}` | Liga un perfil home-gateway ya creado a la ONT | CLI manual 2026-09-21 | Dentro de `interface gpon 0/{slot}`. Help en vivo: solo `profile-id` o `profile-name`. No acepta `ssid-name` ni clave |
| `ont tr069-server-config {port} {ontId} profile-id {id}` | Asocia un perfil de servidor TR-069 a una ONT | CLI manual 2026-09-21 `HWTC15F5AFE6` | Dentro de `interface gpon 0/{slot}`. `undo ont tr069-server-config {port} {ontId}` lo quita. No usar profile 1 (`bore.pub`) |
| `ont tr069-server-profile modify profile-id {id} url {url}` | Cambia la URL del perfil TR-069 sin tocar usuario ni password | CLI manual 2026-09-21 profile 2 | Falla si el perfil está ligado: hay que desligar antes. Profile 2 `GIGAFIBER_GENIEACS` quedó en `http://acs.gigafiberperu.cloud/` (puerto 80; `:7547` no está publicado). Solo estaba ligado `0/0/0` ont 27; se reasoció |
| `tr069-management enable` | Activa TR-069 en un line profile (afecta a todas las ONT vinculadas al `commit`) | CLI manual 2026-09-21 profile **30** `GF_V100_M1000_TR069` | Profile 12 sigue `Disable` (4 ONT). Profile 30 es clon de los GEM 1=VLAN 100 y 2=VLAN 1000, con `tr069-management ip-index 0`. `ont modify 0 27 ont-lineprofile-id 30` dejó `HWTC15F5AFE6` con TR069 **Enable**, IP index 0 y server profile 2 |
| `service-port vlan {vlan} gpon 0/{slot}/{port} ont {ontId} gemport 1 multi-service user-vlan {vlan} tag-transform translate inbound traffic-table index {in} outbound traffic-table index {out}` | Crea service-port + traffic tables | `OltGatewayCommandService.authorize` / `move` | Defaults inbound **8** / outbound **9** (`SMARTOLT-1G-UP/DOWN`). En ONU lab ACS se emite **además** el SP VLAN 1000 gemport 2 (misma tabla). |
| `ont add {port} {ontId} sn-auth {sn} omci ont-lineprofile-id {lineProfileId} ont-srvprofile-id {serviceProfileId} desc "{description}"` | Registra la ONU por número de serie y la administra mediante OMCI | `OltGatewayCommandService.authorize` / `move` | El modo de gestión `omci` se especifica en la OLT; vincula perfiles de línea y servicio. |
| `ont delete {port} {ontId}` | Borra ONT en `interface gpon 0/{slot}` | `OltManagerFacade.deleteOnu` | Tras undo; OK si CLI `success: ≥1` **o** `The ONT does not exist` (idempotente); soft-delete DB tombstonea SN `#del#{id}` |
| `display ont-srvprofile gpon all` | Lista perfiles de servicio GPON y cantidad de ONT vinculadas | Diagnóstico live de perfiles ZTE, 2026-09-23 | Encontrados perfil 8 `ZTE-F6608` y perfil 11 `F6600RV9.0.21`; el segundo coincide con el equipment ID de la ZTE de laboratorio |
| `display ont-srvprofile gpon profile-id {id}` | Muestra el detalle de un perfil de servicio GPON | Diagnóstico live de perfiles ZTE, 2026-09-23 | Perfil 11 `F6600RV9.0.21`: access type GPON, ETH 2 |
| `display vlan {vlan}` | Muestra la definición de una VLAN y sus puertos/service-ports asociados | Diagnóstico live de transporte de la ONU lab | Lectura en modo `config`; útil para confirmar que VLAN 100 y VLAN 1000 tienen uplink y service-ports activos |
| `display ont tr069-server-profile profile-id {id}` | URL, usuario y realm de un perfil TR-069. No incluye intervalo de Inform ni la contraseña en claro | CLI manual 2026-09-21 profile 2 | Profile 2: URL `http://acs.gigafiberperu.cloud/`, user `gigafiberacs`, realm `-`, binding 1 |
| `display ont tr069-server-profile bound-info profile-id {id}` | ONT ligadas a un perfil TR-069 | CLI manual 2026-09-21 profile 2 | Solo `0/0/0` ont 27 |
| `display ont-lineprofile gpon bound-info profile-id {id}` | ONT ligadas a un line profile | CLI manual 2026-09-21 profile 12 | Profile 12: `0/0/0` 27, `0/0/3` 18, `0/1/5` 104, `0/1/6` 115. Por eso no se hizo `tr069-management enable` ahí |
| `ont-lineprofile gpon profile-id {id} profile-name {name}` | Crea o edita un line profile. `gem add` y `gem mapping` piden `<cr>` | CLI manual 2026-09-21 profile **30** | `tcont 1 dba-profile-id 11`, `gem add 1 eth tcont 1`, `gem add 2 eth tcont 1`, `gem mapping 1 1 vlan 100`, `gem mapping 2 1 vlan 1000`, `tr069-management ip-index 0`, `tr069-management enable`, `commit`. Nombre `GF_V100_M1000_TR069` |
| `ont modify {port} {ontId} ont-lineprofile-id {id}` | Cambia el line profile de una ONT. Pide `<cr>` | CLI manual 2026-09-21 ont 27 | `0 27` de profile 12 a **30**. Quedó online, TR069 Enable, IP `10.20.1.160/22`. El profile destino tiene que conservar los GEM con service-port |
| `ont modify {port} {ontId} ont-srvprofile-id {id}` | Cambia el service profile de una ONT. Pide `<cr>` | CLI manual 2026-09-28 `VSOL0031C0B6` `0/1/6` ont 16 | De servicio **13** (`Generic_1_V100`, ETH 0) a **12** (`VSOLVA74`, ETH 4). Line profile 30 no se tocó |
| `display ont version {port} {ontId}` | Vendor, equipment y firmware | CLI manual 2026-09-28 ont 16 | Vendor `HWTC`, equipment `VSOLVA74`, software `V1.1.00-260422` |
| `scroll 512` | Desactiva la paginación de la sesión | CLI manual 2026-09-28 | En modo privilegiado, antes de `config` |

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
| `gem add {gem} eth tcont {n}` | Crea el GEM port | Clonado de profile | — |
| `commit` | Persiste el profile | Cierre de edición | Obligatorio antes de `quit` |
| `ont modify {port} {ontId} ont-lineprofile-id {id}` | Revincula la ONT a otro line profile (dentro de `interface gpon 0/{slot}`) | `scripts/olt-restore-vsol-vlan1-keep1000.expect` (restore puntual). **No** lo usa `ensureMgmtServicePort` | Falla `The GEM port cannot be deleted…` si un SP sigue en un GEM que el destino no tiene: `undo service-port port …` **antes**. Live 2026-09-18 `VSOL00872649` `0/1/1` ont 91: 12→**13** (`Generic_1_HFD44D20E`, gem1 VLAN 100+1+1000). Rebind a profile 12 **corta** internet VLAN 1 |
| `service-port vlan {vlan} gpon 0/{slot}/{port} ont {ontId} gemport {gem} multi-service user-vlan {vlan} tag-transform translate inbound traffic-table index 8 outbound traffic-table index 9` | Service-port de gestión VLAN 1000 | `OltGatewayCommandService.ensureMgmtServicePort` / alta VLAN 1000 | `gemport` = GEM que el line profile **actual** mapea a esa VLAN (host gem si el mapping es nuevo; gem 2 solo si el profile ya lo tiene). Convive con el SP de internet (VLAN 1 o 100). Idempotente si el CLI responde `already exists` y el display ya lista la VLAN. Live 2026-09-22 `ZTEGDC47BFFD` `0/1/6` ont 16 profile 12: index **2268** VLAN 1000 gem 2, state up. El lease DHCP (`10.20.1.163/22`) apareció en un `display ont ipconfig` posterior, no en la misma lectura |
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
