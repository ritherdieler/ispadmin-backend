# GenieACS provisions: contrato, `commit()` y PPPoE F6600R

Fecha: 2026-09-12. ONU de laboratorio `5872C9-F6600R-ZTEGDC47BFFD` (F6600R, tag `lab`). No aplicar esto a ONUs de clientes.

Fuente de verdad de lo que sí funcionó. El contrato NBI está en `genieacs-nbi-provisions-task-poc-2026-09-12.md`. El script vivo está en `scripts/genieacs/provisions/gf-pppoe-wan2-poc.js` (la carpeta `/scripts/` está en gitignore; la copia de abajo es la que hay que copiar) y subido al NBI (`PUT` 200).

## Qué no repetir

| No hacer | Qué pasó | Hacer esto |
|---|---|---|
| `declare("...WANPPPConnection.2", {path:now}, {path:1})` | No sale `AddObject`. El índice lo asigna el CPE. El script sigue y no crea la WAN. | `declare("...WANPPPConnection.*", {path:now}, {path: size+1})` y `commit()` antes de escribir hojas. |
| Alias `[Name:…]` o `[Username:…]` para crear la instancia | Sí sale `AddObject`, pero GenieACS manda enseguida un SPV del objeto `...WANPPPConnection.2.` (punto final). La F6600R responde **9003 / 9002 Internal error**. | Crear con `.*` y `path: size+1`. Escribir solo hojas (`Name`, `Username`, …), nunca el path del objeto. |
| AddObject + parámetros + `Enable` en el mismo batch | La sesión se corta. Antes: timeout ~30 s y `session_terminated` en el SPV. El `AddObject` deja `.2` vacía. | Un `commit()` después del alta, otro después de las hojas, `Enable` en el último paso. |
| Borrar `WANPPPConnection.2` en cada pasada si el script usa `commit()` | `commit()` reejecuta el script. Si al volver a empezar se borra `.2`, se destruye la instancia recién creada y nunca se configura. | Borrar solo `WANIPConnection.2` (WAN IP de internet). Si ya existe `WANPPPConnection.2`, reusarla y escribir las hojas. |
| Tocar `WANIPConnection.1` | Es la WAN de administración ACS (`aprovecionamientoTR069`, `192.168.255.236`, VLAN 100, DHCP, Connected). | No nombrarla en el script. |
| Array plano del foro `["script", arg, arg]` | No encola. El POST no responde. | `provisions: [["script", arg, arg]]`. |
| Tratar HTTP 202 como éxito | 202 = encolado o sesión no cerrada. 200 = la sesión CWMP terminó el task. | Esperar 200 y confirmar el data model. |
| `declare` solo de hijos de un alias, sin `{path:1}` en la instancia | No hay `AddObject` ni SPV. El log llega a `end` y la ONU no cambia. | Ver la fila de `.*`. |
| `log()` para depurar y mirar `docker logs` o `journalctl` | No aparecen. `docker logs` del contenedor se cuelga. | `docker exec gigafiber-genieacs grep -F <marca> /var/log/genieacs/genieacs-cwmp-access.log` |

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

| Path | Rol | No escribir |
|---|---|---|
| `InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1` | Administración ACS. `192.168.255.236`, nombre `aprovecionamientoTR069` | Nunca |
| `...WANPPPConnection.1` | Instancia vacía de fábrica. Enable false, Unconfigured | No es la WAN de internet |
| `...WANIPConnection.2` | WAN IP de internet, si el alta anterior fue estática | Borrar y sustituir por PPP |
| `...WANPPPConnection.2` | WAN PPPoE de internet | Crear si no existe; si existe, reescribir hojas |

VLAN de internet lab: 100. Nombre: `2_INTERNET_R_VID_100`. Usuario lab: `gflabzte`. No documentar la contraseña.

VSOL no usa este layout. Su gestión va en otro WCD / VLAN 1000. No copiar estos paths a V2804.

## Secuencia que funcionó (2026-09-12 17:55 y 17:58 UTC)

Una sola tarea NBI. El access log, en orden:

1. `delete WANIPConnection.2` → `DeleteObject`
2. `add WANPPPConnection.* count=1 -> 2` → `AddObject` (sin SPV del objeto)
3. `set params without Enable` → `SetParameterValues` de las hojas
4. `set Enable` → otro `SetParameterValues`

HTTP 200. Resultado anterior en el mismo día, cuando `.2` ya existía vacía y solo se escribieron hojas + Enable: `WANPPPConnection.2` Connected, `10.64.60.3`, usuario `gflabzte`, VLAN 100. `WANIPConnection.1` siguió en `192.168.255.236`.

Errores de esa misma ONU, para no reinterpretarlos:

- `session_terminated` / "The TR-069 session was unsuccessfully terminated": el CPE no contestó el SPV (~30 s). No es un 9003. En 17:30 UTC el `AddObject` sí había creado `.2` vacía y el SPV (parámetros + Enable juntos) no respondió. A los pocos segundos vino un Inform `VALUE CHANGE` con `retryCount=2`.
- `cwmp.9003` + `setParameterValuesFault` 9002 en `...WANPPPConnection.2.`: SPV del objeto (punto final), típico del alias `[Name:…]`. La WAN IP ya se había borrado; el PPP no quedó configurado.

## Script

Args: `[username, password, vlanId, connectionName]`.

```javascript
log("gf-pppoe-wan2-poc start user=" + args[0] + " vlan=" + args[2] + " name=" + args[3]);

const username = args[0];
const password = args[1];
const vlanId = args[2];
const connectionName = args[3];

const now = Date.now();
const wanRoot = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1";
const ppp2 = wanRoot + ".WANPPPConnection.2";
const ip2 = wanRoot + ".WANIPConnection.2";

const existingIp2 = declare(ip2, { path: now });
if (existingIp2.path) {
  log("gf-pppoe-wan2-poc delete WANIPConnection.2");
  declare(ip2, null, { path: 0 });
  commit();
}

const existingPpp2 = declare(ppp2, { path: now });
if (!existingPpp2.path) {
  const pppAll = declare(wanRoot + ".WANPPPConnection.*", { path: now });
  const desired = pppAll.size + 1;
  log("gf-pppoe-wan2-poc add WANPPPConnection.* count=" + pppAll.size + " -> " + desired);
  declare(wanRoot + ".WANPPPConnection.*", { path: now }, { path: desired });
  commit();
} else {
  log("gf-pppoe-wan2-poc WANPPPConnection.2 exists, skip add");
}

log("gf-pppoe-wan2-poc set params without Enable");
declare(ppp2 + ".Name", null, { value: connectionName });
declare(ppp2 + ".ConnectionType", null, { value: "IP_Routed" });
declare(ppp2 + ".ConnectionTrigger", null, { value: "AlwaysOn" });
declare(ppp2 + ".X_ZTE-COM_ServiceList", null, { value: "INTERNET" });
declare(ppp2 + ".NATEnabled", null, { value: true });
declare(ppp2 + ".X_ZTE-COM_VLANID", null, { value: vlanId });
declare(ppp2 + ".X_ZTE-COM_VLANEnable", null, { value: true });
declare(ppp2 + ".Username", null, { value: username });
declare(ppp2 + ".Password", null, { value: password });
commit();

log("gf-pppoe-wan2-poc set Enable");
declare(ppp2 + ".Enable", null, { value: true });
log("gf-pppoe-wan2-poc end");
```

WiFi de la misma ONU, en otro provision (`gf-wifi24-ssid-poc`): 2.4 es `WLANConfiguration.1`, 5 GHz es `WLANConfiguration.5`. Ese POC sí aplicó SSID y passphrase sin `commit()` intermedio. No mezclarlo con la WAN.

## Invocación

`PUT /provisions/gf-pppoe-wan2-poc` con el JavaScript en el body `text/plain` (no `{script}`).

```json
{
  "name": "provisions",
  "provisions": [["gf-pppoe-wan2-poc", "<user>", "<pass>", 100, "2_INTERNET_R_VID_100"]]
}
```

Query: `connection_request` y `timeout` en milisegundos (45000 en la POC). Solo el device id de lab.

Logs: `Script:` en `/var/log/genieacs/genieacs-cwmp-access.log` del contenedor `gigafiber-genieacs`. `log()` no sale si la revisión no es la nueva (replay de un `commit()` anterior).
