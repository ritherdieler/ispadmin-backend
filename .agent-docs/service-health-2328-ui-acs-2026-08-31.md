# Service-health #2328 — UI local y ACS — 2026-08-31

Backoffice local (Vite `:3002`, API prod). `:3000` es un túnel SSH al VPS, no Vite.

## Qué muestra la 360 (`/subscriptions/2328/service-health`)

| Bloque | Estado |
|--------|--------|
| Banner | Piloto activo · sin notificaciones nuevas |
| GPON / Internet / ACS | ONLINE / ACTIVE / **FRESH** (`last_inform` ~18:15 UTC) |
| Diagnóstico | `TELEMETRY_GAP` — recolección incompleta |
| Óptica · dBm | **19 lecturas** ~−21 RX ONU / ~−25.5 RX OLT (07:09–13:08 Lima). Fix UTC OK. Evidencia `onu_rx_dbm` **Antiguo** porque `opticalFreshSeconds=600` y el collector ~17 min |
| Dispositivos asociados | Serie histórica 04:13–07:30 Lima (último persistido 12:30 UTC). Evidencia **Antiguo** |
| RSSI | Scatter 09:07–10:50 Lima (−75…−100 dBm). Evidencia **Sin dato**: estaciones 15:50 UTC cuelgan de `count_sample` 10:50 UTC, fuera de la ventana de evidencia 6 h |
| `WIFI_REFRESH` | PENDING desde 10:50 Lima; `confirmPending` espera un `acs_wifi_status_current` FRESH posterior a esa hora |

ACS card FRESH = inform del CPE, no Wi‑Fi persistido.

## ACS en prod (release `1.0.3+2b15333`)

- Runs: `written=0`, `missing=2`, a ratos `ACS_CACHE_READ_FAILED`
- Cursors `acs-gpv:*` sí se actualizan (GPV encolado)
- GPV NBI pedía `TotalAssociations` **y** hojas `AssociatedDevice.2..6` fantasma

## Cambio de código (pendiente de deploy)

Conteos: GPV solo `TotalAssociations` + `countProjection`.  
RSSI: GPV `AssociatedDevice.1..N` según el conteo fresco, persistido en el sample nuevo (`acs-gpv-sta:*`).

Detalle: [acs-wifi-gpv-auto-refresh-2026-08-31.md](./acs-wifi-gpv-auto-refresh-2026-08-31.md).
