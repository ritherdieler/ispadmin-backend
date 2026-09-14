# Postman → GenieACS NBI: SET `GfApplyInternetPppoe` (lab)

Prueba **manual** contra **GenieACS NBI** (no ACS, no Core). **Una** `POST` `setParameterValues` cuyo único elemento es el VirtualParameter. El JSON interno lleva todos los campos PPPoE que ACS envía hoy. El JS (`gf-vparams-core.js`) mapea a paths TR-069 según product class.

Solo ONUs con tag GenieACS **`lab`**. Esta guía es para pegar en Postman; no implica writes desde el agente.

Gobierno ACS / vparams: [tr069-perfiles-gobierno-acs.md](./tr069-perfiles-gobierno-acs.md). Parser SET / slim interno: [gf-vparams-set-json-addobject.md](./gf-vparams-set-json-addobject.md). Túneles: [pruebas-local-gateway-acs-lab.md](./pruebas-local-gateway-acs-lab.md) §1.

## Túnel Mac → VPS

Canónico (un SSH, dos forwards):

```bash
ssh -f -N \
  -o StrictHostKeyChecking=accept-new \
  -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=30 \
  -L 7557:127.0.0.1:7557 \
  -L 8091:127.0.0.1:8081 \
  -p 22 root@<VPS_HOST>
```

`<VPS_HOST>` / usuario: `VPS_HOST` y `VPS_USER` en `scripts/deploy.config.local` (no commitear). Password: `DEPLOY_SSH_PASSWORD` / `sshpass -e`. No pegar el valor aquí.

| Local | Remoto VPS | Qué es |
|-------|------------|--------|
| `127.0.0.1:7557` | `127.0.0.1:7557` | GenieACS **NBI** ← Postman |
| `127.0.0.1:8091` | `127.0.0.1:8081` | ACS staging (opcional) |

Comprobar: `nc -z 127.0.0.1 7557`. Si ya hay **un** SSH con esos `-L`, no matar ni duplicar.

**Postman base:** `http://127.0.0.1:7557`

NBI en localhost vía túnel está **open** (GET 200, sin `Authorization`). URL: `GENIEACS_NBI_BASE_URL`. `X-Acs-Key` / `ACS_API_KEY` es ACS `:8091`, no NBI.

## Lab

| | F6600R (principal) | VSOL (mismo JSON) |
|--|--------------------|-------------------|
| SN | `ZTEGDC47BFFD` | `VSOL0031C0B6` |
| `_id` | `5872C9-F6600R-ZTEGDC47BFFD` | `B46415-V2804AX15T-12345B4641531C0B6` |
| Encoded | igual (`-` no se percent-encodea) | igual |
| Tag | `lab` | `lab` |
| User PPP | `gflabzte` | mismo secret de lab o el de esa ONU |
| VLAN internet | `100` | `100` (nunca 1000) |
| Gestión — el JS no la toca | `WANIPConnection.1` `192.168.255.236` | todo `WCD.1` / VLAN 1000 |

Password: **no está en el repo**. `{{pppoePass}}` = secret PPP de lab `gflabzte` / perfil MK2 `GF-STG-200-200`. `/ppp/active` no es NBI.

## Contrato JSON (`VparamProvisioner` + parser JS)

ACS manda **un** path `VirtualParameters.GfApplyInternetPppoe` (`GfVirtualParameters.APPLY_INTERNET_PPPOE`) tipo `xsd:string`. Valor = `ObjectMapper.writeValueAsString` de:

| Campo | Obligatorio | Origen ACS |
|-------|-------------|------------|
| `username` | sí (`!payload.username` → error) | `request.pppoeUsername` |
| `password` | sí | `request.pppoePassword` |
| `vlanId` | no (JS default **100**) | `request.wanVlanId` |
| `connectionName` | no (si falta, JS no escribe `Name`/`Alias`) | `request.connectionName` o `2_INTERNET_R_VID_{vlan}` |

No hay campo `enable` en el JSON. El JS pone `Enable=true` en el PPP de internet y `Enable=false` solo en `layout.ipPath` (F6600R `WANIPConnection.2`, VSOL `WCD.2.WANIPConnection.1`). **No** deshabilita WAN de gestión.

GenieACS 1.2: el script ve GET (`args` vacío → writable vacío) y luego SET (`args[1].value = [json, "xsd:string"]`). `readSetJson` / `unwrapSetValue` aceptan string JSON, `[json, tipo]`, u objeto. GET sin payload no tira.

WiFi no va en este SET (`GfSetWifi` es otro vparam).

## Collection Postman

| Variable | Valor |
|----------|--------|
| `nbi` | `http://127.0.0.1:7557` |
| `deviceId` | `5872C9-F6600R-ZTEGDC47BFFD` |
| `deviceIdVsol` | `B46415-V2804AX15T-12345B4641531C0B6` |
| `pppoeUser` | `gflabzte` |
| `pppoePass` | secret PPP de lab (no en git) |
| `vlan` | `100` |
| `connectionName` | `2_INTERNET_R_VID_100` |

JSON interno (campos reales; el mismo para F6600R y VSOL):

```json
{
  "username": "gflabzte",
  "password": "{{pppoePass}}",
  "vlanId": 100,
  "connectionName": "2_INTERNET_R_VID_100"
}
```

### 1) GET device (confirmar `lab`)

```
GET http://127.0.0.1:7557/devices/?query=%7B%22_id%22%3A%225872C9-F6600R-ZTEGDC47BFFD%22%7D&projection=_id%2C_tags
```

**Parar** si `_tags` no contiene `lab`.

### 2) POST principal — un VirtualParameter

```
POST http://127.0.0.1:7557/devices/5872C9-F6600R-ZTEGDC47BFFD/tasks?timeout=30000&connection_request
Content-Type: application/json
```

```json
{
  "name": "setParameterValues",
  "parameterValues": [
    [
      "VirtualParameters.GfApplyInternetPppoe",
      "{\"username\":\"gflabzte\",\"password\":\"{{pppoePass}}\",\"vlanId\":100,\"connectionName\":\"2_INTERNET_R_VID_100\"}",
      "xsd:string"
    ]
  ]
}
```

Una sola petición. No mandar tandas A/B/C de paths TR-069 ni tres SET `pending`. El JS internamente puede devolver `{ok:true}` o `{ok:true,pending:"addObject"|"credentials"}` en el valor del vparam; ACS (`applyPppoeUntilSettled`) reintenta solo si el caller es el WAR. En Postman se envía **una** vez.

### 2b) POST — un VirtualParameter por atributo PPPoE (GfPppoe*)

Forma documentada de GenieACS (WPA): varios triples en `parameterValues`, un VP por campo. El script elige el path TR-069 por ProductClass. **No** sustituye a `GfApplyInternetPppoe` (ACS staging sigue usando el blob).

```
POST http://127.0.0.1:7557/devices/5872C9-F6600R-ZTEGDC47BFFD/tasks?timeout=30000&connection_request
Content-Type: application/json
```

```json
{
  "name": "setParameterValues",
  "parameterValues": [
    ["VirtualParameters.GfPppoeUsername", "gflabzte", "xsd:string"],
    ["VirtualParameters.GfPppoePassword", "{{pppoePass}}", "xsd:string"],
    ["VirtualParameters.GfPppoeVlanId", 100, "xsd:unsignedInt"],
    ["VirtualParameters.GfPppoeConnectionName", "2_INTERNET_R_VID_100", "xsd:string"]
  ]
}
```

`GfPppoeVlanId` puede ir como number `100` en el JSON NBI. F6600R: Username/Password/Name/VLAN ZTE sobre `WANPPPConnection.2`; nunca `WANIPConnection.1`. VSOL: mismos VPs sobre `WCD.2.WANPPPConnection.1`; nunca `WCD.1`. ConnectionName = leaf `Name` (VSOL también `Alias` si `writeAlias`). AddObject solo si `GfPppoeUsername` y la instancia PPP aún no existe.

Curl (una línea, Import → Raw text). Sustituir `{{pppoePass}}` por el secret PPP de lab MK2:

```
curl -sS -X POST 'http://127.0.0.1:7557/devices/5872C9-F6600R-ZTEGDC47BFFD/tasks?timeout=30000&connection_request' -H 'Content-Type: application/json' -d '{"name":"setParameterValues","parameterValues":[["VirtualParameters.GfPppoeUsername","gflabzte","xsd:string"],["VirtualParameters.GfPppoePassword","{{pppoePass}}","xsd:string"],["VirtualParameters.GfPppoeVlanId",100,"xsd:unsignedInt"],["VirtualParameters.GfPppoeConnectionName","2_INTERNET_R_VID_100","xsd:string"]]}'
```

GPV de username y/o status (después del SET, no en el mismo POST):

```
curl -sS -X POST 'http://127.0.0.1:7557/devices/5872C9-F6600R-ZTEGDC47BFFD/tasks?timeout=30000&connection_request' -H 'Content-Type: application/json' -d '{"name":"getParameterValues","parameterNames":["VirtualParameters.GfPppoeUsername","VirtualParameters.GfInternetStatus"]}'
```

`connection_request` + `timeout=30000`: CR al CPE y espera 30 s (`GENIEACS_TASK_TIMEOUT_MS`). Sin el query, espera el próximo Inform.

### 3) GET / GPV `GfInternetStatus` (opcional)

Caché NBI:

```
GET http://127.0.0.1:7557/devices/?query=%7B%22_id%22%3A%225872C9-F6600R-ZTEGDC47BFFD%22%7D&projection=VirtualParameters.GfInternetStatus,_id,_tags
```

GPV (dispara refresh del vparam):

```
POST http://127.0.0.1:7557/devices/5872C9-F6600R-ZTEGDC47BFFD/tasks?timeout=30000&connection_request
Content-Type: application/json
```

```json
{
  "name": "getParameterValues",
  "parameterNames": ["VirtualParameters.GfInternetStatus"]
}
```

Valor típico (string JSON): `{"connected":true,"ip":"10.64.x.x","productClass":"F6600R"}`. ACS da COMPLETE PPPoE si `connected` y `ip` empieza por `10.64.`.

---

## VSOL lab — mismo JSON, otro device

```
POST http://127.0.0.1:7557/devices/B46415-V2804AX15T-12345B4641531C0B6/tasks?timeout=30000&connection_request
Content-Type: application/json
```

Mismo body que F6600R (`GfApplyInternetPppoe` + el mismo string). El JS usa `MODELS.V2804AX15T` (WCD.2, nunca WCD.1). GET tags antes:

```
http://127.0.0.1:7557/devices/?query=%7B%22_id%22%3A%22B46415-V2804AX15T-12345B4641531C0B6%22%7D&projection=_id%2C_tags
```

---

## HTTP 200 vs 202 / `too_many_commits`

| Código | Significado |
|--------|-------------|
| **200** | La task corrió en la sesión (CR o Inform abierto). Único éxito inmediato del NBI. |
| **202** | Encolada. En F6600R el SET del vparam **a menudo es 202** (CR lento, Inform, o `too_many_commits` si el script declara de más). No es COMPLETE. |
| 4xx/5xx o `202` + `fault` | Fallo. Body: `fault` / `setParameterValuesFault`. |

`too_many_commits`: GenieACS corta el script del vparam si hay demasiados `declare`/commits en un ciclo (p. ej. GPV TR-181 extra). El ACS WAR trata SPV **202** como no-éxito (`VparamProvisioner`). Poll `GfInternetStatus` o GET device; no declarar PASS solo por 202.

Tasks: `GET {{nbi}}/tasks/?query={"device":"5872C9-F6600R-ZTEGDC47BFFD"}`.

---

## Qué no tocar

| Modelo | Prohibido |
|--------|-----------|
| F6600R | `…WANIPConnection.1.*` (gestión). El JSON no la nombra; el JS tampoco la deshabilita. |
| VSOL | `…WANConnectionDevice.1.*` / VLAN 1000. |
| Ambos | Devices sin tag `lab`. WiFi. Paths TR-069 crudos en el request principal. `.env` / passwords en git. |

---

## Checklist

1. Túnel `:7557` up; Postman → `http://127.0.0.1:7557`.
2. GET device → `_tags` contiene `lab`.
3. Un POST: `GfApplyInternetPppoe` + JSON completo, **o** §2b varios `GfPppoe*` (un triple por campo).
4. 200 = script ejecutado; 202 = encolado / típico en lab F6600R.
5. Opcional: GPV `GfPppoeUsername` y/o `GfInternetStatus`.
6. No commitear secretos.

Push de scripts Gf* (no es esta prueba): `GENIEACS_NBI_URL=http://127.0.0.1:7557 ./scripts/genieacs/apply-virtual-parameters-via-nbi.sh`.

---

## Apéndice — lote crudo TR-069 (no usar como request principal)

El ACS con `genieacs.vparams.enabled=true` **no** manda estos paths. Solo diagnóstico si se quiere ver el árbol sin pasar por el vparam. F6600R `writeCtCom: false`. Un lote gordo histórico daba CWMP 9002.

```
POST {{nbi}}/devices/{{deviceId}}/tasks?timeout=30000&connection_request
```

```json
{
  "name": "setParameterValues",
  "parameterValues": [
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2.ConnectionType", "IP_Routed", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2.ConnectionTrigger", "AlwaysOn", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2.X_ZTE-COM_ServiceList", "INTERNET", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2.NATEnabled", true, "xsd:boolean"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2.X_ZTE-COM_VLANID", 100, "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2.X_ZTE-COM_VLANEnable", 1, "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2.Username", "{{pppoeUser}}", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2.Password", "{{pppoePass}}", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2.Enable", true, "xsd:boolean"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2.Enable", false, "xsd:boolean"]
  ]
}
```
