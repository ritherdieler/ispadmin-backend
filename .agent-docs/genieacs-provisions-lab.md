# Provisions de lab GenieACS

Fecha: 2026-09-12. Solo ONUs con tag `lab`. No son presets: no corren en Inform/BOOT. Se encolan a mano contra el device id.

Fuente de verdad de invocación. Contrato CWMP (`commit()`, AddObject, 9005/9007, logs): [genieacs-provisions-f6600r-hallazgos.md](./genieacs-provisions-f6600r-hallazgos.md). Forma del JS: [genieacs-scripts-cleancode.md](./genieacs-scripts-cleancode.md).

Camino vivo (staging y prod, sin flag vparams):

```text
Cliente  --HTTP JWT-->  Core  --HTTP-->  Gateway  --HTTP-->  ACS WAR  --NBI provisions-->  GenieACS  -->  ONU
```

El cliente (app Android / backoffice) habla **solo con el Core**. Solo el Gateway llama al ACS. El ACS, al arrancar, hace `PUT` de los cuatro `.js` al NBI (`src/main/resources/genieacs/provisions/`).

| Acción | Core | Gateway | ACS / NBI |
|---|---|---|---|
| Alta FIBER | `POST /subscription` (PPPoE o STATIC_IP; passphrase = `wifiPassword24` en ambas bandas) | `POST /api/olt-gateway/onu/activate` | Encola `gf-pppoe-wan2-poc` o `gf-static-wan2-poc`. COMPLETE = GPV de WAN2, no HTTP 200 |
| Retry TR-069 | `POST /subscription/{id}/acs/retry-tr069` | `POST /onus/{sn}/cpe/provision` (sin OLT) | Mismo provision PPPoE (o IP si no hay secret) |
| Migración IP→PPPoE | `AccessMigrationService` | `cpe/provision` + `cpe/access-layout` | PPPoE; el revert sigue siendo WAN IP (sin PPPoE) |
| WiFi post-alta | `POST /subscription/{id}/acs/wifi` body `{ssid24,ssid5,passphrase}` | `POST /onus/{sn}/cpe/wifi` | `gf-wifi-ssid-poc`. Passphrase menor a 8 → FAILED |
| Reboot ACS | `POST /subscription/{id}/acs/reboot` | `POST /onus/{sn}/cpe/reboot` | Encola `gf-reboot-poc`. El botón Android OLT `reboot-fiber-onu` no cambia |

Product class fuera de `F6600R` / `V2804AX15T` / `VSOLVA74` (Huawei incluido): `tr069ProvisionStatus=FAILED` sin encolar. `VSOLVA74` usa el layout VSOL.

| Id NBI | Archivo | Qué hace |
|---|---|---|
| `gf-pppoe-wan2-poc` | `scripts/genieacs/provisions/gf-pppoe-wan2-poc.js` | Sustituye la WAN IP de internet por PPPoE y, si vienen SSIDs, escribe WiFi |
| `gf-static-wan2-poc` | `scripts/genieacs/provisions/gf-static-wan2-poc.js` | Sustituye la WAN PPP leftover por STATIC y, si vienen SSIDs, escribe WiFi |
| `gf-wifi-ssid-poc` | `scripts/genieacs/provisions/gf-wifi-ssid-poc.js` | Cambia SSID 2.4 y 5.8 con una passphrase |
| `gf-reboot-poc` | `scripts/genieacs/provisions/gf-reboot-poc.js` | Reinicia la ONU (RPC CWMP `Reboot`) |

## Dispositivos de lab

| Modelo | Device id |
|---|---|
| F6600R | `5872C9-F6600R-ZTEGDC47BFFD` |
| V2804AX15T | `B46415-V2804AX15T-12345B4641531C0B6` |

`VSOLVA74` usa el mismo layout que `V2804AX15T`. Otro `ProductClass`: el script de WAN y el de WiFi loguean `unsupported productClass` y no escriben. El reboot no filtra modelo.

NBI: `http://127.0.0.1:7557` (túnel `start-genieacs-tunnel.sh`). Query `connection_request&timeout=45000`. HTTP 200 = la sesión CWMP terminó, no que la ONU aplicó el write. Confirmar en el data model o en `genieacs-cwmp-access.log` (`Script:`).

Subir el archivo (body `text/plain`):

```bash
curl -sS -o /dev/null -w '%{http_code}\n' -X PUT \
  'http://127.0.0.1:7557/provisions/<id>' \
  -H 'Content-Type: text/plain' \
  --data-binary @scripts/genieacs/provisions/<archivo>.js
```

Encolar:

```bash
curl -sS -w '\nHTTP %{http_code}\n' -X POST \
  'http://127.0.0.1:7557/devices/<deviceId>/tasks?connection_request&timeout=45000' \
  -H 'Content-Type: application/json' \
  -d '{"name":"provisions","provisions":[["<id>", ...args]]}'
```

El array de `provisions` va anidado. Un array plano no encola.

## Layout WAN y WiFi

No escribir la WAN de gestión ACS: F6600R `WANConnectionDevice.1.WANIPConnection.1`; VSOL todo `WANConnectionDevice.1.*`.

| | F6600R | VSOL (`V2804AX15T`, `VSOLVA74`) |
|---|---|---|
| Internet PPP | `WCD.1.WANPPPConnection.2` | `WCD.2.WANPPPConnection.1` |
| Internet IP a borrar | `WCD.1.WANIPConnection.2` | `WCD.2.WANIPConnection.1` |
| VLAN | `X_ZTE-COM_VLANID` + `X_ZTE-COM_VLANEnable` | `X_CT-COM_VLANIDMark` + GPON `WCD.2.X_CT-COM_WANGponLinkConfig.VLANIDMark` |
| WiFi 2.4 | `WLANConfiguration.1` | `WLANConfiguration.5` |
| WiFi 5.8 | `WLANConfiguration.5` | `WLANConfiguration.1` |

WiFi escribe `SSID`, `KeyPassphrase` y `Enable`. No escribe `PreSharedKey`.

## `gf-pppoe-wan2-poc`

Args: `[username, password, vlanId, connectionName, ssid24, pass24, ssid5, pass5]`.

`pass5` se ignora. Si vienen `ssid24` o `ssid5`, ambas bandas quedan con passphrase `pass24` (`args[5]`). Sin SSIDs, no toca WiFi. En el alta, el Core manda `wifiPassword24` como `pass24`.

Orden: asegurar WCD de internet (solo VSOL, si falta `WCD.2`) → borrar WAN IP de internet → crear PPP si no existe → hojas → `Enable` → WiFi en otro SPV. No borrar el PPP de internet si ya existe: `commit()` reejecuta el script desde la línea 1.

```json
{"name":"provisions","provisions":[["gf-pppoe-wan2-poc","<user>","<pass>",100,"2_INTERNET_R_VID_100","<ssid24>","<pass24>","<ssid5>","<pass24>"]]}
```

## `gf-static-wan2-poc`

Args: `[ip, subnetMask, gateway, dns, vlanId, connectionName, ssid24, pass24, ssid5, pass5]`.

`pass5` se ignora. Orden: WCD de internet (VSOL) → borrar leftover PPP → crear/reusar WAN IP → hojas → `Enable` → WiFi. No toca WAN1 TR-069.

```json
{"name":"provisions","provisions":[["gf-static-wan2-poc","192.168.250.10","255.255.255.0","192.168.250.1","8.8.8.8,8.8.4.4",100,"2_INTERNET_R_VID_100","<ssid24>","<pass24>","<ssid5>","<pass24>"]]}
```

## `gf-wifi-ssid-poc`

Args: `[ssid24, ssid5, passphrase]`. Una passphrase para las dos bandas. No toca WAN.

Passphrase de menos de 8 caracteres: no escribe ninguna banda.

```json
{"name":"provisions","provisions":[["gf-wifi-ssid-poc","<ssid24>","<ssid5>","<passphrase>"]]}
```

## `gf-reboot-poc`

Sin args. Una declaración:

```js
log("gf-reboot-poc");
declare("Reboot", null, { value: Date.now() });
```

`Reboot` es el timestamp del último comando de reboot de GenieACS. Un valor mayor al guardado dispara el RPC CWMP `Reboot`. No hay `commit()`: reejecutar el script programaría otro reinicio. Tras el RPC la ONU se cae; `session terminated` es el síntoma esperado, no un fallo del task.

```json
{"name":"provisions","provisions":[["gf-reboot-poc"]]}
```

## Qué no son estos cuatro

Los presets `bootstrap`, `gigafiber-bootstrap`, `inform`, `default`, `huawei-writeonly-acs-credentials` y `gigafiber-wifi-telemetry` siguen en el NBI. Borrarlos rompe ACS URL, summon y telemetría WiFi. Estos cuatro no sustituyen ese canal.
