# Gf* vparams — payload SET y AddObject

## Parser JSON

GenieACS 1.2 no pasa el JSON de SPV como `args[0]` string. El script del vparam corre primero en **GET** (`args[1] = {}`) y luego en **SET** (`args[1] = { value: [json, "xsd:string"] }`).

Si el GET lanza `Invalid JSON payload`, el SET nunca se ejecuta (fault `script.Error`).

`readSetJson` acepta: string JSON, string doble-escapado, `[json, xsd:string]`, objeto ya parseado, y el tuple de provision 1.2. GET sin payload retorna writable vacío (no tira).

Kotlin `VparamProvisioner` ya manda `xsd:string` con `ObjectMapper.writeValueAsString` (JSON plano). No hace falta cambiar el WAR.

## AddObject

GenieACS `Path.parse` rechaza trailing `.` (`Invalid parameter path`). AddObject usa `InternetGatewayDevice....WANPPPConnection.*` con `{path: N}`.

## Add-then-set

SPV de Username/Password/Enable sobre la instancia nueva en el mismo ciclo que AddObject deja F6600R `WANPPPConnection.2` Unconfigured / `0.0.0.0` / username vacío.

`pppInstanceReady` exige `Username` o `Enable` ya presentes. Si no: solo AddObject y `{ok:true,pending:"addObject"}`. Si sí: **no** AddObject (un AddObject extra sobre `.2` existente devolvía CWMP 9002/9003 en el objeto `WANPPPConnection.2.`); `applyPppCommon` + credenciales + Enable + `layout.ipPath.Enable=false` (F6600R `WANIPConnection.2`, nunca `.1`). F6600R no SPVea `X_CT-COM_*` (no existen en PPP.2). `slimPpp` en **dos SET**: (1) ConnectionType + Username + Password → `{pending:"credentials"}`; (2) VLAN ZTE + Enable + `WANIPConnection.2.Enable=false`. Un lote gordo (Name/Trigger/NAT/VLAN+Enable junto a credenciales) hacía 9002 en el objeto. Para pegar ese lote gordo a mano en Postman NBI (lab): [postman-genieacs-nbi-pppoe-lab.md](./postman-genieacs-nbi-pppoe-lab.md).

F6600R `slimPpp` no declara TR-181 `Device.ManagementServer.*` (si el CR IGD aún no está en cache, el `||` disparaba un GPV TR-181 → `too_many_commits` / NBI **202**). Tampoco GPV `ExternalIPAddress` de WANPPP.2 / WANIP.2 (el guard de gestión es solo prefijo `WANIPConnection.1`). El ciclo Enable omite VLAN y `WANIP.2.Enable` si ya están aplicados.

`VparamProvisioner` re-SPVea `GfApplyInternetPppoe` hasta 3 veces mientras el valor tenga `pending`. El contrato HTTP `CpeProvisionCommand` no cambia. WAR staging **sin** ese retry: un 2.º `POST /cpe/provision` cubre `pending`. SPV 202 ≠ éxito (no declarar PASS con SPV NBI a `WANPPP.*`).

Node: `node --test scripts/genieacs/virtual-parameters/test/gf-vparams.test.js` (39/39), caso `F6600R defers Username Password Enable until PPP instance exists`.

## Add-then-set

SPV de Username/Password/Enable sobre la instancia nueva **después** de que exista en el modelo. Primer ciclo sin Username/Enable: solo AddObject (`pending=addObject`). Ciclo listo: SPV PPP + `Enable=false` en el WANIP de internet (F6600R `.2`, nunca `WANIPConnection.1`). Si GenieACS no re-evalúa el vparam en el mismo SET, hace falta un segundo SPV con el mismo JSON.

Tests: `node --test scripts/genieacs/virtual-parameters/test/gf-vparams.test.js` (39/39).

## GfPppoe* (un valor por parámetro)

VirtualParameters adicionales, patrón WPA de GenieACS: `args[1].value` → `declare` del path TR-069 según `MODELS`; GET devuelve el escalar, no JSON.

| VP | Tipo | Path F6600R (`pppPath` + leaf) | Path VSOL |
|----|------|--------------------------------|-----------|
| `GfPppoeUsername` | `xsd:string` | `WCD.1.WANPPPConnection.2.Username` | `WCD.2.WANPPPConnection.1.Username` |
| `GfPppoePassword` | `xsd:string` | `…Password` | `…Password` |
| `GfPppoeVlanId` | `xsd:unsignedInt` | `X_ZTE-COM_VLANID` + `VLANEnable` (sin CT-COM) | ZTE + `X_CT-COM_VLANIDMark` + `gponVlanPath` |
| `GfPppoeConnectionName` | `xsd:string` | `…Name` (no `Alias`; `writeAlias: false`) | `…Name` + `Alias` |

AddObject solo en `GfPppoeUsername` (`ensureInternetPpp`). Los otros tres no crean instancia. `GfApplyInternetPppoe` (JSON blob) sigue igual: ACS staging aún lo usa. Curl/Postman de varios triples: [postman-genieacs-nbi-pppoe-lab.md](./postman-genieacs-nbi-pppoe-lab.md).

## Deploy

```bash
GENIEACS_NBI_URL=http://127.0.0.1:7557 ./scripts/genieacs/apply-virtual-parameters-via-nbi.sh
```

Tests: `cd scripts/genieacs && npm test`.
