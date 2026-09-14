# WiFi-on-Inform — cableado GenieACS → ACS → Gateway → Core (2026-09-08)

**Canónico (cómo funciona):** [wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md). Esta nota es resumen de evidencia del mismo día.

## Flujo

```text
ONU (VSOL lab) ──Inform──► GenieACS provision piloto
                              │ ext wifi-inform-notify (antes de declare WLAN)
                              ▼ HTTP X-Acs-Key
                           ACS WAR ──parse NBI + last-state──► Gateway
                                                                  │ XADD cpe.inform
                                                                  ▼
                                                                Core (samples + 360)
```

ACS **no** publica Redis. Clientes solo hablan con Core.

## Piezas

| Capa | Qué |
|------|-----|
| GenieACS | `scripts/genieacs/ext/wifi-inform-notify.js` + hook en `provisions/gigafiber-wifi-telemetry.js` (solo preset piloto VSOL) |
| ACS | `POST /api/acs/v1/cpe/inform-notify` → `WifiNbiTelemetry` → upsert last-state en `cpe_record` → POST Gateway |
| Gateway | `POST /api/olt-gateway/acs/cpe-inform` → `PlatformEventTypes.CPE_INFORM` |
| Core | `CpeInformPersistService` idempotente `subscriptionId + informAt`; poll `AcsTelemetryService` apagado (`acs-poll-enabled=false`) |

## Secretos (solo nombres)

| Variable | Uso |
|----------|-----|
| `GENIEACS_TO_ACS_API_KEY` | GenieACS ext → ACS notify (`X-Acs-Key`) |
| `GENIEACS_TO_ACS_NOTIFY_URL` | Base URL ACS vista desde GenieACS |
| `ACS_TO_GATEWAY_API_KEY` | ACS → Gateway ingest (`X-Acs-To-Gateway-Key`) |
| `ACS_GATEWAY_BASE_URL` | Base URL Gateway desde ACS (hornear por WAR; no `.env` compartido) |
| `SERVICE_HEALTH_ACS_POLL_ENABLED` | Default `false` |

Catálogo: `.agent-docs/vps-secrets-management.md`.

## Validación VSOL (staging)

1. Añadir secretos en `/opt/gigafiber/.env` y `/opt/gigafiber/genieacs/.env` (sin commitear valores).
2. Copiar `ext/wifi-inform-notify.js` al volumen `GENIEACS_EXT_DIR` y re-aplicar provision piloto.
3. Deploy WARs staging ACS + Gateway + Core.
4. Esperar Inform del CPE `12345B4641531C0B6`.
5. PASS: fila en `acs_wifi_count_sample` (Core), series 360, telemetry on-demand con last-state ACS (sin GPV al pintar).
   También: `acs_wifi_station_sample` no vacío cuando el payload trae stations, y `acs_wifi_status_current` actualizado (fix Core 2026-09-08: [wifi-on-inform-fix-stations-status-2026-09-08.md](./wifi-on-inform-fix-stations-status-2026-09-08.md)).

## Validación staging 2026-09-08

**PASS** (count sample Core + ACS last-state + Redis `cpe.inform`). Detalle:
[wifi-on-inform-validacion-staging-2026-09-08.md](./wifi-on-inform-validacion-staging-2026-09-08.md).

Piloto allowlist: [piloto-wifi-on-inform-vsol-lab-2026-09-08.md](./piloto-wifi-on-inform-vsol-lab-2026-09-08.md).
