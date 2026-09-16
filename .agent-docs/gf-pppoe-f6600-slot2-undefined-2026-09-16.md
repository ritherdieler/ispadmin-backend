# F6600 PPPoE: slot `.2` cuando `wildcardSize` es undefined

Fecha: 2026-09-16. Lab `ZTEGDC47BFFD` / alta staging #17 `gf17`.

## Causa

`gf-pppoe-wan2-poc` hacía `nextCount(size, 0)`. Si GenieACS devolvía `size=undefined`, el AddObject pedía **1** instancia: creaba `WANPPPConnection.1` vacía y escribía hojas en `.2` (inexistente). WiFi sí se aplicaba. `NamedCpeProvisioner` espera GPV de `.2.ExternalIPAddress` = `10.64.*` → **PENDING** a los 90 s.

ACS `cpe_record.wan_ip=10.64.0.21` era leftover de #14, no WAN viva (solo ACS `192.168.255.236`).

## Cambio

- `desiredInstanceCount(size, slot)`: si `size` no es entero > 0, usa el índice del path de internet (`WANPPPConnection.2` / `WANIPConnection.2` en F6600).
- Borra leftover `WANIP.3` y `WANPPP.3`. No toca TR-069 (`WANIP.1`).
- Mismo hueco cerrado en `gf-static-wan2-poc`.
- Tests Node: `scripts/genieacs/provisions/test/gf-*-wan2-poc.test.js` (22 pass).
- Classpath ACS copiado. **PUT NBI** `gf-pppoe-wan2-poc` y `gf-static-wan2-poc` HTTP 200. Sin deploy WAR.

Un restart del ACS VPS volvería a PUT el JS del WAR viejo; rehacer el PUT o desplegar ACS.

## Verificación

```bash
node --test scripts/genieacs/provisions/test/gf-pppoe-wan2-poc.test.js \
  scripts/genieacs/provisions/test/gf-static-wan2-poc.test.js
```
