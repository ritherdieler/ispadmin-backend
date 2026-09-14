# Service-health lab + e2e WiFi — VSOL0031C0B6

**Fecha:** 2026-09-07

## WiFi canónico (GenieACS / prod #2349)

| Banda | SSID | Contraseña |
|-------|------|------------|
| 2.4 GHz | `lab-vsol-e2e-24` | `LabVsolWifi24!` |
| 5 GHz | `lab-vsol-e2e-24 - 5G` | `LabVsolWifi24!` |

## E2E scripts

Los scripts eligen WiFi por SN:

- `VSOL*` / `56534F4C*` → `lab-vsol-e2e-24` / `LabVsolWifi24!`
- default staging ZTE → `lab-zte-e2e-24` / `LabZteWifi24!`
- default local/prod (no VSOL) → `mimiwifi` / `MimiWifi24pass`

Archivos: `IpsAdmin-android app/scripts/e2e_register_fiber_{staging,local,_}espresso.sh`.

```bash
E2E_ONU_SN=VSOL0031C0B6 SKIP_POST_CLEANUP=1 ./scripts/e2e_register_fiber_staging_espresso.sh
```

## Recolección staging

| Pieza | Valor |
|-------|--------|
| Membresía lab | `subscription_acs.lab=1` (tag GenieACS `lab`; **sin** `SERVICE_HEALTH_LAB_*`) |
| ZTE lab | `#2387` (`ZTEGDC47BFFD`, device `5872C9-F6600R-ZTEGDC47BFFD`) |
| VSOL lab | `#2389` (`VSOL0031C0B6`, device `B46415-V2804AX15T-12345B4641531C0B6`, IP `192.168.250.21`) |
| Provision | OLT + TR-069 `COMPLETE` |

Detalle: `service-health-lab-membership-db-only-2026-09-07.md`.

Verificado: `GET /subscription/2389/service-health` → `identity.lab=true`, `ONU=VSOL0031C0B6`, `acs=FRESH`, `onu_rx_dbm≈-19.46` FRESH (serie Core tras pull desde Gateway `status_current`). Tras recreate de `tomcat-staging`, restaurar WARs desde `/opt/gigafiber/*.war` (host `8081`).

Duplicado `#2388` (OLT FAILED / ACS MISSING, misma SN) eliminado con hard-cleanup; conflicto `ONU:VSOL0031C0B6` resuelto a `#2389`.

## Óptica vs Gateway / tráfico vivo (2026-09-07)

| Capa | Rol |
|------|-----|
| Gateway `olt_mgr_onu_status_current` | Solo estado **actual** (dBm / run_state); no historial óptico |
| Core `olt_mgr_onu_optical_sample` | Serie temporal para `GET .../service-health/series` → OpticalChart |
| Actividad en vivo | SockJS/STOMP al **Core** `https://api.gigafiberperu.cloud/ispadmin-staging/ws` (relay). No usar `…/ispadmin-staging-traffic/ws` (nginx 404) |

Backoffice staging: `VITE_TRAFFIC_WS_URL` → Core `/ispadmin-staging/ws`; deploy `npm run build:staging` + rsync → `/var/www/gigafiber/backoffice-staging/`.

## Incidente cleanup MK (corregido)

`tr069-e2e-hard-cleanup.sh` usaba `ip in target` (substring). Al limpiar `192.168.30.18` borró colas `.180`–`.189`. Se restauraron en MK2 y el match pasó a host exacto (`scripts/lib/mikrotik_queue_match.py` + `scripts/test_mikrotik_queue_ip_match.py`).
