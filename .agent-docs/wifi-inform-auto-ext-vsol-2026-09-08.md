# Wi‑Fi Inform auto-ext (VSOL lab) — 2026-09-08

**Canónico (cómo funciona):** [wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md). Esta nota es el diagnóstico try/catch + Symbols GenieACS.

## Síntoma

Tras Connection Request en VSOL `B46415-V2804AX15T-12345B4641531C0B6`, `_lastInform` avanzaba pero no había rastro claro GenieACS `ext` → ACS. El `POST /api/acs/v1/cpe/inform-notify` manual sí dejaba Core stations en PASS.

## Causa raíz

1. **Provision `gigafiber-wifi-telemetry` envolvía `declare`/`ext` en `try/catch (e) {}`.** En GenieACS, `ext()` y `commit()` propagan Symbols (`EXT` / `COMMIT`). El `catch` vacío los traga: el script “termina” sin programar bien el ext (GenieACS documenta no usar try/catch alrededor de `declare`/`ext`).
2. **Secundario:** `GENIEACS_TO_ACS_API_KEY` en `genieacs/.env` (tras recreate) no coincidía con el valor **runtime** de `tomcat-staging` (`.env` de Tomcat actualizado sin recreate). Tras arreglar el provision, el auto-ext sí pegaba ACS pero respondía **401** hasta realinear keys.

## Fix aplicado (permanente)

- Provision: quitar try/catch; llamar `ext("wifi-inform-notify", …)` **antes** de los `declare` WLAN (así el notify no depende de GPV largos / `too_many_commits`).
- Ext: esperar respuesta HTTP antes del callback (fiabilidad del camino auto).
- VPS: re-PUT provision NBI, copiar ext al volumen `genieacs_pilot_ext`, recreate GenieACS; sync `GENIEACS_TO_ACS_API_KEY` GenieACS ↔ Tomcat live.

## Prueba

CR NBI sobre el VSOL lab → access Tomcat desde IP GenieACS (`172.18.0.2`) **sin** notify manual. **PASS** auto-path.

## Logs temporales — eliminados (2026-09-08)

Tras estabilizar el PASS auto-ext:

- Ext: se quitó `dbg()` / escritura a `/var/log/genieacs/wifi-inform-notify-debug.log` y stderr verbose.
- ACS: se quitó `log.info("inform-notify entry…")` en `WifiInformNotifyService`.
- Se mantienen `log.warn` permanentes de fallo NBI / Gateway POST.

Allowlist del preset: sin cambios (solo VSOL lab).
