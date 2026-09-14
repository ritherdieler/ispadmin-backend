# Perfil TR-069 real de WANPPPConnection — VSOL V2804AX15T

Descargado del NBI de GenieACS (`http://127.0.0.1:7557/devices/`) sobre la ONU de
laboratorio `B46415-V2804AX15T-12345B4641531C0B6` el 2026-09-10, tras crear la
instancia con `addObject` y refrescar el subárbol `WANConnectionDevice.2`.

Sirve como fuente de verdad para `Tr069ModelProfile.buildClientPppoeWanParameterValues`.
No sustituye al CSV de importación de `tr069_model_profile`; es la verificación de
que los nombres y la escribibilidad que asume el código existen en el equipo.

## Estado previo del árbol WAN

| Ruta | Valor | Escribible |
|------|-------|------------|
| `IGD.WANDevice.1.WANConnectionDevice.1.WANPPPConnectionNumberOfEntries` | `0` | no |
| `IGD.WANDevice.1.WANConnectionDevice.2.WANPPPConnectionNumberOfEntries` | `0` | no |
| `IGD.WANDevice.1.WANConnectionDevice.2.WANPPPConnection` | (objeto) | **sí** |
| `IGD.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.AddressingType` | `Static` | sí |
| `IGD.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.Enable` | `true` | sí |

El modelo soporta PPPoE aunque no traiga ninguna instancia de fábrica: el objeto
`WANPPPConnection` es escribible, por eso `ensureClientWanSlot` puede crearlo con
`addObject`. El `WANConnectionDevice.2` es el slot de abonado (WAN de IP estática),
que es el que la rama PPPoE apaga con `Enable=false`.

## Parámetros que escribe el alta PPPoE

Todos existen en la instancia recién creada y son escribibles.

| Parámetro | Tipo | Valor de fábrica | Lo escribe |
|-----------|------|------------------|------------|
| `Enable` | `xsd:boolean` | `false` | `buildClientPppoeWanParameterValues` |
| `ConnectionType` | `xsd:string` | `IP_Routed` | idem |
| `Name` | `xsd:string` | `3_INTERNET_R_VID_100` | `connectionNameParameterValues` |
| `Alias` | `xsd:string` | `3_INTERNET_R_VID_100` | idem (`writesWanAlias` por `V2804`) |
| `Username` | `xsd:string` | vacío | `buildClientPppoeWanParameterValues` |
| `Password` | `xsd:string` | vacío | idem |
| `ConnectionTrigger` | `xsd:string` | `AlwaysOn` | `buildClientPppoeWanParameterValues` (escribible en VSOL; el alta lo reafirma) |
| `X_CT-COM_ServiceList` | `xsd:string` | `INTERNET` | idem |
| `X_ZTE-COM_ServiceList` | `xsd:string` | `INTERNET` | idem |
| `X_CT-COM_VLANIDMark` | `xsd:unsignedInt` | `100` | `vlanParameterValues` |
| `X_ZTE-COM_VLANID` | `xsd:unsignedInt` | `100` | idem |
| `X_ZTE-COM_VLANEnable` | `xsd:unsignedInt` | `1` | idem |

La instancia nace con la VLAN 100 y el `ServiceList` ya heredados del
`WANConnectionDevice.2`, pero el alta los reescribe igual para no depender de esa
herencia en otros equipos.

## Parámetros de solo lectura que condicionan la red

| Parámetro | Valor | Consecuencia |
|-----------|-------|--------------|
| `PPPAuthenticationProtocol` | `PAPandCHAP` | El `pppoe-server` de MK2 debe aceptar `pap` y `chap`. El script de fase 4 usa `authentication=pap,chap,mschap1,mschap2`, así que negocia. No se puede forzar solo CHAP desde el ACS. |
| `PossibleConnectionTypes` | `IP_Routed,PPPoE_Bridged` | `IP_Routed` es el modo correcto; el CPE hace NAT y enruta. |
| `MaxMRUSize` / `CurrentMRUSize` | `1492` | MK2 anuncia `max-mtu=1480 max-mru=1480`; PPP negocia el menor, 1480. Funciona, y el clamping de MSS lo hace el perfil PPP con `change-tcp-mss=yes`. |
| `TransportType` | `PPPoE` | Confirma que la instancia es PPPoE y no PPPoA. |
| `ConnectionStatus` | `Disconnected` | Es el parámetro que verifica el alta; pasa a `Connected` cuando MK2 autentica el secret. |
| `LastConnectionError` | `ERROR_UNKNOWN` | Valor inicial, no es un fallo. |

`PPPoEServiceName` y `PPPoEACName` están vacíos y son escribibles. Se dejan vacíos
a propósito: el CPE acepta cualquier concentrador, lo que evita atarlo al
`service-name` del servidor de MK2 y permite mover el piloto entre
`GIGAFIBER-PPPOE-STG` y `GIGAFIBER-PPPOE` sin tocar el CPE.

## Cómo volver a descargarlo

```bash
ssh "$VPS_USER@$VPS_HOST" 'python3 -c "
import urllib.request, json, urllib.parse
q=urllib.parse.quote(json.dumps({\"_id\":\"<device-id>\"}))
d=json.loads(urllib.request.urlopen(f\"http://127.0.0.1:7557/devices/?query={q}\").read())[0]
node=d[\"InternetGatewayDevice\"][\"WANDevice\"][\"1\"][\"WANConnectionDevice\"][\"2\"][\"WANPPPConnection\"][\"1\"]
for k,v in sorted(node.items()):
    if isinstance(v,dict) and \"_value\" in v:
        print(k, v.get(\"_type\"), v.get(\"_writable\"), repr(v.get(\"_value\")))
"'
```

Si la instancia no existe todavía, primero:

```json
POST /devices/<device-id>/tasks?connection_request
{"name":"addObject","objectName":"InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection"}
```

y luego un `refreshObject` sobre `InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2`.
