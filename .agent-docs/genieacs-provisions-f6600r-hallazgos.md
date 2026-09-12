# GenieACS provisions: contrato, `commit()` y PPPoE F6600R / VSOL

Fecha: 2026-09-12. Solo ONUs con tag GenieACS `lab`. No aplicar a ONUs de clientes.

Fuente de verdad de lo que sí funcionó. Contrato NBI: `genieacs-nbi-provisions-task-poc-2026-09-12.md`. Script vivo: `scripts/genieacs/provisions/gf-pppoe-wan2-poc.js` (`/scripts/` está en gitignore; la copia de abajo es la que hay que copiar). NBI: `PUT /provisions/gf-pppoe-wan2-poc` HTTP 200.

Tests: `node --test scripts/genieacs/provisions/test/gf-pppoe-wan2-poc.test.js` y `GenieAcsVirtualParametersTest.pppoe_provision_maps_vsol_to_wcd2_and_f6600_to_ppp2`.

## Qué no repetir

| No hacer | Qué pasó | Hacer esto |
|---|---|---|
| `declare("...WANPPPConnection.2", {path:now}, {path:1})` | No sale `AddObject`. El índice lo asigna el CPE. El script sigue y no crea la WAN. | `declare("...WANPPPConnection.*", {path:now}, {path: size+1})` y `commit()` antes de escribir hojas. |
| Alias `[Name:…]` o `[Username:…]` para crear la instancia | Sí sale `AddObject`, pero GenieACS manda enseguida un SPV del objeto `...WANPPPConnection.2.` (punto final). La F6600R responde **9003 / 9002 Internal error**. | Crear con `.*` y `path: size+1`. Escribir solo hojas (`Name`, `Username`, …), nunca el path del objeto. |
| AddObject + parámetros + `Enable` en el mismo batch | La sesión se corta. Antes: timeout ~30 s y `session_terminated` en el SPV. El `AddObject` deja `.2` vacía. | Un `commit()` después del alta, otro después de las hojas, `Enable` en el último paso. |
| Borrar el PPP de internet en cada pasada si el script usa `commit()` | `commit()` reejecuta el script. Si al volver a empezar se borra la instancia recién creada, nunca se configura. | Borrar solo la WAN IP de **internet**. Si el PPP de internet ya existe, reusarlo y escribir las hojas. |
| Copiar paths F6600R a VSOL (o al revés) | F6600R: internet en `WCD.1` PPP/IP `.2`. VSOL: internet en `WCD.2` PPP/IP `.1`. La gestión VSOL es todo `WCD.1` (VLAN 1000 o `10.20.0.0/16`). | Ramificar por `DeviceID.ProductClass`. Modelos: `F6600R`, `V2804AX15T`, `VSOLVA74`. Otro product class: log y `return`. |
| Tocar la WAN de administración | F6600R: `WCD.1.WANIPConnection.1` (`aprovecionamientoTR069`). VSOL: `WCD.1.*` (lab `31C0B6`: `1_TR069_R_VID_1000`, `10.20.0.2`). | No nombrar esos paths. En VSOL no escribir `X_CT-COM_WANGponLinkConfig` de `WCD.1`. |
| Escribir `X_ZTE-COM_VLANEnable=true` en VSOL | 2026-09-12 19:20 UTC, lab `31C0B6` con WAN IP y PPP ya existentes: SPV **9003 / 9007 Invalid parameter value** en `...WANPPPConnection.1.X_ZTE-COM_VLANEnable`. | VSOL: solo `X_CT-COM_ServiceList`, `X_CT-COM_VLANIDMark` y VLAN GPON de WCD.2. `writeZte` solo en F6600R. |
| Add PPP bajo `WCD.2` cuando ese WCD no existe | 2026-09-12 19:46–19:55 UTC, lab `31C0B6` (solo ACS en `WCD.1` tras borrar la WAN IP y un BOOT): GPN **9005** en `...WCD.2.WANIPConnection.1` y `...WCD.2.WANPPPConnection.*`. El script loguea `add WANPPPConnection.* count=undefined -> 1` y llega a `end`. **No hay AddObject ni SPV.** HTTP 200 igual. | Primero `declare("...WANConnectionDevice.*", {path:now}, {path: size+1})` (mínimo 2) y `commit()`. Nunca `{path:1}` en `WANConnectionDevice.*` (borraría extras y amenaza `WCD.1`). Luego PPP bajo `WCD.2`. |
| Array plano del foro `["script", arg, arg]` | No encola. El POST no responde. | `provisions: [["script", arg, arg]]`. |
| Tratar HTTP 202 como éxito | 202 = encolado o sesión no cerrada. 200 = la sesión CWMP terminó el task. | Esperar 200 y confirmar el data model. |
| `declare` solo de hijos de un alias, sin `{path:1}` en la instancia | No hay `AddObject` ni SPV. El log llega a `end` y la ONU no cambia. | Ver la fila de `.*`. |
| `log()` para depurar y mirar `docker logs` o `journalctl` | No aparecen. `docker logs` del contenedor se cuelga. | `docker exec` + `grep` del access log. Sección «Ver logs». |

## Cómo funciona un provision

Un provision es JavaScript en el servidor (GenieACS 1.2.16), no un SPV. Se ejecuta en la sesión CWMP. `declare(path, timestamps, values)` compara el data model con lo deseado y arma GetParameterNames / GetParameterValues / SetParameterValues / AddObject / DeleteObject.

`commit()` no sigue en la línea siguiente de forma lineal:

1. Incrementa la revisión y lanza un corte (`COMMIT` en `lib/sandbox.ts` de v1.2.16).
2. GenieACS manda ese batch al CPE y espera la respuesta.
3. Vuelve a ejecutar el script desde la primera línea.
4. Los `commit()` ya aplicados no vuelven a cortar: la ejecución los cruza y sigue al siguiente paso.
5. `log()` solo escribe en la revisión nueva. Por eso el access log muestra un solo `start` aunque el script haya arrancado varias veces.

Por eso el script tiene que ser idempotente: en cada pasada pregunta qué hay y hace solo el paso que falta. Documentación: `docs/provisions.rst` de v1.2.16 (`commit()` sincroniza con el CPE para controlar el orden; el script se reejecuta hasta estabilizar).

`declare(path, {path: now})` refresca y sirve para saber si existe (`resultado.path`). Un wildcard (`.*`) tiene `size`. Un path concreto (`.2`) no tiene `size` (`undefined`). Existencia de `.2` es `.path`, no `.size`.

DeleteObject: `declare(instancia, null, {path: 0})`. AddObject: solo sobre el padre `.*` (o un alias, que en esta ONU no se usa; ver tabla). `{path: 1}` en un path numerado no crea.

## Layout F6600R lab

Device id: `5872C9-F6600R-ZTEGDC47BFFD`.

| Path | Rol | No escribir |
|---|---|---|
| `...WANConnectionDevice.1.WANIPConnection.1` | Administración ACS. `192.168.255.236`, nombre `aprovecionamientoTR069` | Nunca |
| `...WANPPPConnection.1` | Instancia vacía de fábrica. Enable false, Unconfigured | No es la WAN de internet |
| `...WANIPConnection.2` | WAN IP de internet, si el alta anterior fue estática | Borrar y sustituir por PPP |
| `...WANPPPConnection.2` | WAN PPPoE de internet | Crear si no existe; si existe, reescribir hojas |

VLAN de internet lab: 100. Nombre: `2_INTERNET_R_VID_100`. Usuario lab: `gflabzte`. No documentar la contraseña.

Hojas F6600R: Name, ConnectionType, ConnectionTrigger, `X_ZTE-COM_ServiceList`, NATEnabled, `X_ZTE-COM_VLANID`, `X_ZTE-COM_VLANEnable`, Username, Password, Enable. Sin Alias ni `X_CT-COM_*`.

## Layout VSOL V2804AX15T lab

Product class `V2804AX15T` (también `VSOLVA74` en el perfil ACS). Internet **no** comparte WCD con ACS.

| Path | Rol | No escribir |
|---|---|---|
| `...WANConnectionDevice.1.*` | Administración ACS. Lab `31C0B6`: VLAN 1000, `10.20.0.2`, nombre `1_TR069_R_VID_1000`. Lab `3217B6`: VLAN 100, `192.168.254.235` | Nunca. Incluye `X_CT-COM_WANGponLinkConfig` de WCD.1 |
| `...WANConnectionDevice.2` | WCD de internet. En lab `31C0B6` desapareció tras DeleteObject de la WAN IP + Inform `1 BOOT` | Si no está, crearlo con AddObject del padre `WANConnectionDevice.*` (count 1→2) **antes** de PPP |
| `...WANConnectionDevice.2.WANIPConnection.1` | WAN IP de internet | Borrar si existe y sustituir por PPP |
| `...WANConnectionDevice.2.WANPPPConnection.1` | WAN PPPoE de internet | Crear si no existe; si existe, reescribir hojas |
| `...WANConnectionDevice.2.X_CT-COM_WANGponLinkConfig.VLANIDMark` | VLAN GPON del WCD de internet | Escribir 100 en internet; no el de WCD.1 |

Lab canónica VSOL: `B46415-V2804AX15T-12345B4641531C0B6` (tag `lab`). Snapshot 2026-09-12 19:55 UTC: **solo `WCD.1`** ACS (`10.20.0.2`, VLAN 1000). `WCD.2` ya no está. Otra lab `…3217B6`: WCD.2 solo WAN IP `192.168.250.23` (caso borrar IP y crear PPP).

Hojas VSOL: Alias, `X_CT-COM_ServiceList`, `X_CT-COM_VLANIDMark`, VLAN GPON de WCD.2, NATEnabled, Username, Password, Enable. **No** `X_ZTE-COM_*` (9007).

## Secuencia F6600R que funcionó (2026-09-12 17:55 y 17:58 UTC)

Una sola tarea NBI. El access log, en orden:

1. `delete WANIPConnection.2` → `DeleteObject`
2. `add WANPPPConnection.* count=1 -> 2` → `AddObject` (sin SPV del objeto)
3. `set params without Enable` → `SetParameterValues` de las hojas
4. `set Enable` → otro `SetParameterValues`

HTTP 200. Cuando `.2` ya existía vacía y solo se escribieron hojas + Enable: `WANPPPConnection.2` Connected, `10.64.60.3`, usuario `gflabzte`, VLAN 100. `WANIPConnection.1` siguió en `192.168.255.236`.

Errores de esa misma ONU, para no reinterpretarlos:

- `session_terminated` / "The TR-069 session was unsuccessfully terminated": el CPE no contestó el SPV (~30 s). No es un 9003. En 17:30 UTC el `AddObject` sí había creado `.2` vacía y el SPV (parámetros + Enable juntos) no respondió. A los pocos segundos vino un Inform `VALUE CHANGE` con `retryCount=2`.
- `cwmp.9003` + `setParameterValuesFault` 9002 en `...WANPPPConnection.2.`: SPV del objeto (punto final), típico del alias `[Name:…]`. La WAN IP ya se había borrado; el PPP no quedó configurado.

La ramificación VSOL está cubierta por test Node. 2026-09-12 19:20 UTC en `31C0B6` (PPP ya existía): SPV **9007** en `X_ZTE-COM_VLANEnable`. El script ya no escribe `X_ZTE-COM_*` en VSOL. En el reintento 19:31, tras borrar `WANIPConnection.1`, `pppAll.size` fue `undefined` y `desired` salió `NaN` (sin AddObject). El conteo usa `(Number(size) || 0) + 1`. 19:46–19:55 UTC: `WCD.2` ausente; GPN 9005; HTTP 200 sin AddObject. El layout VSOL ahora asegura `WANConnectionDevice.2` (`wcdParent.*` count ≥ 2) y `commit()` antes del PPP. F6600R no crea WCD (internet vive en `WCD.1`).

## Script

Fuente de verdad: `scripts/genieacs/provisions/gf-pppoe-wan2-poc.js`. Forma (funciones + comentarios de porqué CWMP): `.agent-docs/genieacs-scripts-cleancode.md`.

Args: `[username, password, vlanId, connectionName, ssid24, pass24, ssid5, pass5]`. El layout sale de `DeviceID.ProductClass`. WiFi opcional: sin `ssid24` ni `ssid5` no se escribe WLAN. `pass24` y `pass5` se ignoran. Ambas bandas se escriben con passphrase **`11111111`**.

Flujo (idempotente; `commit()` reejecuta el archivo):

1. `layoutOf(productClass)` — F6600R / V2804AX15T / VSOLVA74; otro modelo: log y return.
2. `ensureInternetWcd` — VSOL: AddObject `WANConnectionDevice.*` si falta WCD.2.
3. `deleteInternetIpWan` — borra solo la WAN IP de internet.
4. `ensureInternetPpp` — AddObject `WANPPPConnection.*` si falta.
5. `setInternetPppLeaves` — hojas (VLAN, user, NAT) **sin** Enable; luego `commit()`.
6. `enableInternetPpp` — `Enable=true` en un SPV aparte.
7. `setWifi` — SSID + `KeyPassphrase` + Enable en 2.4 y 5.8, `commit()` aparte de la WAN. F6600R: 2.4 = `WLANConfiguration.1`, 5.8 = `.5`. VSOL: al revés.

WiFi va en el mismo provision (`setWifi`). F6600R: 2.4 es `WLANConfiguration.1`, 5.8 es `WLANConfiguration.5`. VSOL al revés (`.5` = 2.4, `.1` = 5.8). Se escribe `KeyPassphrase` (el leaf con valor en la lab), no `PreSharedKey`. Passphrase siempre `11111111`, no la de args. SPV aparte de la WAN.

## Invocación

`PUT /provisions/gf-pppoe-wan2-poc` con el JavaScript en el body `text/plain` (no `{script}`).

```json
{
  "name": "provisions",
  "provisions": [["gf-pppoe-wan2-poc", "<user>", "<pass>", 100, "2_INTERNET_R_VID_100", "<ssid24>", "<pass24>", "<ssid5>", "<pass5>"]]
}
```

Query: `connection_request` y `timeout` en milisegundos (45000 en la POC). Solo device id con tag `lab`.

| Modelo | Device id lab |
|---|---|
| F6600R | `5872C9-F6600R-ZTEGDC47BFFD` |
| V2804AX15T | `B46415-V2804AX15T-12345B4641531C0B6` (canónica). Alternativa IP→PPP: `…3217B6` |

## Ver logs

El `log()` del provision y el CWMP (GetParameterNames, AddObject, SPV, faults) están en el **archivo de dentro del contenedor**, no en stdout de Docker ni en systemd.

| Dónde | Valor |
|---|---|
| Host | VPS `root@212.85.13.47` |
| Contenedor | `gigafiber-genieacs` |
| Archivo | `/var/log/genieacs/genieacs-cwmp-access.log` |

`log()` del script sale en líneas `Script:`. Solo en la revisión nueva: si `commit()` reejecuta un paso ya aplicado, ese `log()` no se escribe otra vez.

HTTP 200 del curl NBI **no** está en este archivo. 200 = la sesión CWMP terminó. Confirmar aquí si hubo `AddObject` / `SetParameterValues` o un fault `9005` / `9007` / `9002` sin write.

### Qué no usar

- `docker logs` / `docker compose logs -f genieacs`: se cuelga o no muestra los `Script:` del provision.
- `journalctl -u genieacs-cwmp`: el plan viejo `genieacs-plan-implementacion-vps.md`. En el VPS actual GenieACS corre en Docker; ese unit no tiene estos logs.

### Desde la Mac (copiar)

Últimos pasos del provision:

```bash
ssh root@212.85.13.47 'docker exec gigafiber-genieacs grep -F "gf-pppoe-wan2-poc" /var/log/genieacs/genieacs-cwmp-access.log | tail -50'
```

Misma sesión de una ONU lab (sufijo del serial, no el device id entero):

```bash
ssh root@212.85.13.47 'docker exec gigafiber-genieacs grep -F "12345B4641531C0B6" /var/log/genieacs/genieacs-cwmp-access.log | tail -80'
```

AddObject, SPV y faults de esa marca:

```bash
ssh root@212.85.13.47 'docker exec gigafiber-genieacs grep -E "gf-pppoe-wan2-poc|AddObject|SetParameterValues|9002|9003|9005|9007|session_terminated" /var/log/genieacs/genieacs-cwmp-access.log | tail -60'
```

Si ya hay sesión SSH en el VPS, el `docker exec …` se corre ahí, sin el `ssh` de fuera.

Una sesión que **aplicó** muestra, en este orden, `Script:` de `add …WANConnectionDevice.*` (VSOL si faltaba WCD.2) o `add …WANPPPConnection.*`, luego `set params without Enable`, luego `set Enable`, y las líneas CWMP `AddObject` / `SetParameterValues` sin fault. Llegar a `end` sin esas líneas CWMP es el caso HTTP 200 que no cambió la ONU.
