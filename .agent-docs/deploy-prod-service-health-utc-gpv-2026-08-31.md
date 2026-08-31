# Deploy prod — service-health series UTC + ACS GPV — 2026-08-31

## Backend

| Campo | Valor |
|-------|--------|
| Commit | `2b15333` |
| Release | `1.0.3+2b15333` |
| Deploy | `./scripts/deploy.sh --deploy` OK |
| Smoke | `GET https://api.gigafiberperu.cloud/ispadmin/` → HTTP 200 |
| Observability | Deploy event registrado |

## Qué incluye

- Series 360 con ventana UTC (`UtcInstantText` / queries nativas) — óptica/Wi‑Fi dejan de quedar fuera por JDBC Lima.
- Watcher ACS: GPV+CR automático si `PARAMETERS_NOT_REFRESHED_FOR_INFORM` (cooldown 900 s).
- Docs/catálogo: `SERVICE_HEALTH_ACS_GPV_COOLDOWN_SECONDS`.

## Verificación post-deploy sugerida

1. UI `#2328` / `#2310`: óptica en «Demanda»/360 con puntos en 24 h.
2. Tras ~2–15 min: runs ACS con `written_count>0` o cursor `acs-gpv:*` actualizado.
3. Conteos/RSSI FRESH si GenieACS responde al GPV.

## Referencias

- [service-health-series-utc-window-2026-08-31.md](./service-health-series-utc-window-2026-08-31.md)
- [acs-wifi-gpv-auto-refresh-2026-08-31.md](./acs-wifi-gpv-auto-refresh-2026-08-31.md)
