# POC: task NBI `provisions` (no documentado)

Fecha: 2026-09-12. Solo ONU lab `ZTEGDC47BFFD` (`5872C9-F6600R-ZTEGDC47BFFD`, F6600R, tag `lab`). Sin commit, sin deploy.

## Contrato (no está en la API reference)

Foro: https://forum.genieacs.com/t/non-documented-functionality-in-the-tasks-api/7428  
Código: `case "provisions"` en `lib/api-functions.ts` y `lib/cwmp.ts` (commit `bf9517c`). GenieACS 1.2.13 lo tiene igual.

El foro muestra un array plano. El código no: `task.provisions` es `[scriptName, ...args][]`. El primer elemento de **cada** array interno es el nombre del script; el resto son `args`, no otros scripts.

```http
POST /devices/{id}/tasks?connection_request&timeout=45000
Content-Type: application/json
```

```json
{
  "name": "provisions",
  "provisions": [["gf-pppoe-poc", "<username>", "<password>", 100]]
}
```

- `timeout` va en milisegundos (mismo query param que el resto de tasks).
- HTTP 200: la sesión CWMP terminó el task. HTTP 202: encolado; con `connection_request` también significa que no llegó a commit.
- El array plano del foro (`["script", arg, arg]`) no encola. En este NBI el POST no responde (timeout del cliente) y no queda task. No usar esa forma.
- PUT del script: `PUT /provisions/{id}` con el JavaScript en el body (`text/plain`), no `{ "script": "..." }`. Así está en la API reference 1.2 y así lo hace `scripts/genieacs/apply-provisions-via-nbi.sh`. GET `/provisions/{id}` responde 405; el documento vive en `GET /provisions/`.

## Resultado en la ONU lab

- PUT `gf-pppoe-poc`: HTTP 200. Script local: `scripts/genieacs/provisions/gf-pppoe-poc.js` (la carpeta `/scripts/` está en gitignore).
- Declaraciones solo en `WANPPPConnection.2`: Username, Password, `X_ZTE-COM_VLANID`, `X_ZTE-COM_VLANEnable` (`true`, el tipo vivo es `xsd:boolean`, no entero 1), Enable. No se escribió `WANIPConnection.1`. No se tocó WiFi. No se declaró `WANIPConnection.2` (no había instancia en el data model).
- Task anidado con `connection_request` sobre la instancia recreada: HTTP **202**. Fault `cwmp.9003` / `setParameterValuesFault` **9002** Internal error en `...WANPPPConnection.2.` (el path del objeto, con punto final, no un leaf). Task y fault borrados para que el inform no reintente.
- Antes de ese 202, un task anidado sin `connection_request` fue HTTP 202 y se consumió en una sesión (GPV con connection request HTTP 200, sin fault almacenado). Tras `refreshObject`, `WANPPPConnection.2` (antes Connected, username lab, `10.64.60.2`) ya no estaba. Quedó solo `WANPPPConnection.1` Unconfigured.
- `AddObject` de `WANPPPConnection` (HTTP 200) recreó la instancia 2 vacía. El provision no aplicó username ni VLAN. GPV posterior: Username vacío, Enable false, ConnectionStatus Unconfigured, ExternalIPAddress `0.0.0.0`, VLANID 0.
- `WANIPConnection.1` (gestión) sigue Enable true y ExternalIPAddress `192.168.255.236`. No se escribió ese path.

No repetir el SPV de este provision sobre F6600R tal cual: GenieACS manda el objeto `WANPPPConnection.2.` y el CPE responde 9002, y la instancia de internet que ya existía desapareció del data model.

## POC manual: solo SSID 2.4

Script: `scripts/genieacs/provisions/gf-wifi24-ssid-poc.js`. F6600R: SSID y `KeyPassphrase` de `WLANConfiguration.1` (2.4) y `WLANConfiguration.5` (5). Args: ssid24, password24, ssid5, password5. Si `ProductClass` no es `F6600R`, no escribe. No toca PPP ni WAN.

`gf-pppoe-poc` se borró del NBI y del repo (2026-09-12): no se usa. `PUT /provisions/gf-wifi24-ssid-poc` HTTP 200, body `text/plain`. Task: `provisions: [["gf-wifi24-ssid-poc", "<ssid>"]]`. El SSID actual de la lab es `lab-zte-e2e-24`; restaurarlo al terminar.

## POC PPPoE F6600R (cerrado)

El alias `[Username:…]` / `[Name:…]` y el `declare` de `WANPPPConnection.2` con `{path:1}` no sirven. Secuencia que sí funcionó, errores y script: `.agent-docs/genieacs-provisions-f6600r-hallazgos.md`.
