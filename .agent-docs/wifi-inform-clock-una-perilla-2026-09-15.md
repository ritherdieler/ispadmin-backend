# Reloj Wi-Fi 360 = evento Inform (2026-09-15)

Cadencia de series Wi-Fi: **una perilla** (`PeriodicInformInterval` en GenieACS). Core no copia el intervalo ni filtra por hueco.

## Qué cambió

- Lab: `isLab ? 60` en `gf-inform-interval.js`, `gigafiber-bootstrap.js` y el snippet de `configure-genieacs-pilot.sh`. `INTERVAL_VERSION` de `gf-inform-interval` subido a `1789548000000` para re-SPV.
- Flota: 1800 s + jitter, sin cambio.
- `CpeInformPersistService`: cada `cpe.inform` completo e in-scope inserta count + stations. Idempotencia solo `(deviceId, subscriptionId, informAt)`. Sin `wifiSeriesMinGapSeconds`.
- Estaciones: `observedAt = informAt` (un Inform = una columna X en WifiCharts).
- Footnote 360: «Muestras ACS (Inform)».

`periodicInformSeconds` / `labPeriodicInformSeconds` siguen para frescura de evidencia (`last_inform` STALE), no para decidir si se persiste.

Hasta `apply-provisions` en el NBI, el CPE lab puede seguir en 30 s.

Canónico: [wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md).
