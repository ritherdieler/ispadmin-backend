# TR-069: timeout IP/SSID en e2e ZTE (2026-09-05)

## Causa raíz

Tras OLT+MikroTik OK, el WAR ACS hace SPV+GPV con `connection_request`. GenieACS responde **HTTP 202** (~2 s) y deja las tareas en cola: el CPE no abre sesión.

Evidencia lab `ZTEGDC47BFFD` (`5872C9-F6600R-ZTEGDC47BFFD`):

- `ConnectionRequestURL` = `http://192.168.255.236:58000/...`
- Ping a `.236` falla; gateway `192.168.255.1` (wg-olt) sí responde
- `_lastInform` stale (`2026-09-05T02:18:28Z`)
- Caché ACS: IP `192.168.250.21` (vieja); el alta pedía `192.168.250.16`
- SSIDs en caché ya coincidían; fallaba la IP + CR muerto

El loop de verificación solo leía caché Mongo → timeout genérico.

## Cambio de código

En `acs` y `wispadmin` `Tr069ProvisioningService`:

- Re-GPV con CR en cada poll si no cuadra
- Log WARN con ip/status/ssid observados vs esperados + `lastInform`
- WARN si GPV vuelve HTTP 202
- Mensaje de timeout incluye valores observados (`verificationTimeoutMessage`)

Tests: `Tr069ProvisioningServiceTest` (incl. `verification timeout includes observed ACS values`).

## Operación e2e

Sin reachability CR / inform fresco del CPE, el e2e seguirá en PENDING aunque el código reporte mejor el mismatch. Despertar la ONU (reboot OLT / energía) hasta que GenieACS actualice `_lastInform` y el CR responda.
