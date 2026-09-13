# Scripts GenieACS — clean code

Ámbito: JavaScript en `scripts/genieacs/` (provisions, virtual-parameters y sus tests). El sandbox de GenieACS (`declare` / `commit` / `log` / `args`) no es licencia para un script lineal.

Contrato CWMP (`commit()`, AddObject `.*`, layouts F6600R/VSOL, 9005/9007): `.agent-docs/genieacs-provisions-f6600r-hallazgos.md`. Catálogo de `gf-pppoe-wan2-poc`, `gf-wifi-ssid-poc` y `gf-reboot-poc`: `.agent-docs/genieacs-provisions-lab.md`. Este doc cubre **forma del código**. `gf-reboot-poc` es la excepción: un `declare("Reboot")` en el top-level, sin `commit()`, porque reejecutar el script volvería a reiniciar.

## Criterio de terminado

Un script nuevo o tocado no está listo si el flujo principal no se lee como una secuencia de llamadas con nombre de intención (`ensureInternetWcd`, `deleteInternetIpWan`, `ensureInternetPpp`, `setInternetPppLeaves`, `enableInternetPpp`). Un `declare`/`commit` suelto en el top-level, duplicado entre pasos, no cumple.

Referencia de forma: `scripts/genieacs/virtual-parameters/lib/gf-vparams-core.js` (`layoutOf`, funciones por intención).

## Layout es dato; el flujo es función

Los paths y flags por `ProductClass` viven en un objeto de layout (`pppPath`, `wcdPath`, `writeZte`, …). El mismo algoritmo corre para F6600R y VSOL. Un modelo nuevo es una fila de datos, no un `if (productClass === …)` que copia la secuencia.

## Un trabajo por función

Cada función hace **un** paso CWMP y se nombra por el efecto:

| Función | Trabajo |
|---|---|
| `layoutOf(productClass)` | Elegir layout o `null` |
| `pathExists(path)` | `declare(path, {path: now}).path` |
| `addUntilCount(wildcard, desired)` | AddObject `.*` + `commit()` |
| `ensureInternetWcd(layout)` | Crear `WANConnectionDevice` de internet si falta |
| `deleteInternetIpWan(layout)` | DeleteObject de la WAN IP de internet |
| `ensureInternetPpp(layout)` | AddObject PPP si falta |
| `setInternetPppLeaves(layout, creds)` | Hojas sin `Enable` + `commit()` |
| `enableInternetPpp(layout)` | `Enable=true` |
| `setWifi(layout, wifi)` | SSID + `KeyPassphrase` + Enable de 2.4 y 5.8, `commit()` aparte |

El archivo del provision queda en este orden: layouts → helpers → flujo. Nombres en inglés, como el resto del código.

## Helpers de `declare`

La comparación con el data model, el conteo `size` y el `commit()` después de AddObject/DeleteObject se escriben **una vez**. Quien llama pasa el path y el tamaño deseado.

`addUntilCount` usa `size` actual + 1 (si `size` no es un entero > 0, en VSOL WCD el mínimo deseado es 2). `{path: 1}` sobre `WANConnectionDevice.*` no es un atajo: reduce instancias.

## Idempotencia

`commit()` reejecuta el script desde la primera línea. Cada función pregunta el data model y solo actúa si falta el paso. Borrar el PPP de internet en cada pasada no es idempotente.

## Límites del sandbox (siguen siendo clean code)

- Un provision NBI es **un** archivo: sin `require`/`import`. Funciones locales sí.
- Virtual-parameters: lógica compartida en `lib/gf-vparams-core.js`, no copiada en cada entry.
- Comentarios en el JS: el **porqué** CWMP (por qué `commit()` aquí, por qué no tocar `WCD.1`, por qué `Enable` al final). No repetir el nombre de la función. Cabecera del archivo: args, idempotencia de `commit()`, WAN de gestión.
- `log()` con marca estable (`gf-pppoe-wan2-poc …`) para el access log, no para explicar el código. Cómo leerlo: «Ver logs» en `.agent-docs/genieacs-provisions-f6600r-hallazgos.md` (`docker exec gigafiber-genieacs grep` del archivo; no `docker logs` ni `journalctl`).
- Tests Node del mismo comportamiento (`scripts/genieacs/**/test/*.test.js`) y, si cambia el mapeo de paths, el test Kotlin de scripts.

## Forma del flujo principal

```javascript
const layout = layoutOf(productClass);
if (!layout) {
  log("gf-pppoe-wan2-poc unsupported productClass=" + productClass);
  return;
}
log("gf-pppoe-wan2-poc start user=" + args[0] + " vlan=" + args[2] + " product=" + productClass);
ensureInternetWcd(layout);
deleteInternetIpWan(layout);
ensureInternetPpp(layout);
setInternetPppLeaves(layout, {
  username: args[0],
  password: args[1],
  vlanId: args[2],
  connectionName: args[3],
});
enableInternetPpp(layout);
log("gf-pppoe-wan2-poc end");
```
