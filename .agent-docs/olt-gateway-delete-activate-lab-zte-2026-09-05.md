# OLT Gateway — delete + activate lab ZTE (2026-09-05)

## Fixes aplicados antes del retest

1. **Delete idempotente**: `ont delete` con `Failure: The ONT does not exist` ya no tumba el soft-delete en DB (`OltGatewayCommandService`).
2. **Journal re-alta**: si stage=`DONE` y el request cambia, se abre operación nueva; mismo request → idempotente (`ActivationJournal`).

## Flujo gateway (alta)

Base staging: `http://127.0.0.1:8081/ispadmin-staging-oltgateway`  
Header: `X-Olt-Gateway-Key`

| Paso | Endpoint |
|------|----------|
| Delete | `POST /api/olt-gateway/onu/delete/{externalId}` |
| Unconfigured | `GET /api/olt-gateway/onu/unconfigured_onus` |
| Activate | `POST /api/olt-gateway/onu/activate` (authorize OLT + ACS provision async) |
| Poll | `GET /api/olt-gateway/onus/by-sn/{sn}/activation` |
| CR smoke | `POST /api/olt-gateway/onus/{sn}/cpe/wifi-refresh` |
| Telemetry | `GET /api/olt-gateway/onus/{sn}/cpe/telemetry` |

## Resultado staging (post-deploy)

| Paso | Resultado |
|------|-----------|
| Delete orphan `…_1_6_115` | OK tras delete idempotente (ONT already gone en OLT) |
| `POST /onu/activate` | HTTP 200 → `gigafiber-ma5608t_1_6_16`, OLT `COMPLETE`, CPE `PENDING` |
| Poll activation | CPE **`COMPLETE`** (~10 s) — WAN `192.168.250.16`, SSIDs lab |
| `wifi-refresh` vía gateway | ejecutado (CR) |

## Ciclo completo WAN + final spin (solo gateway)

Fecha: 2026-09-05 ~21:39 UTC. SN `ZTEGDC47BFFD`. Sin llamadas directas a ACS WAR/NBI para provisionar.

| Paso | Resultado |
|------|-----------|
| Delete `…_1_6_16` | HTTP 200 |
| Unconfigured | board=1 port=6 en t=1 |
| Activate name=`lab-zte-gw-full-1788644347` | HTTP 200 → `gigafiber-ma5608t_1_6_115`, OLT `COMPLETE`, CPE `PENDING` |
| Final spin (poll activation) | CPE **`COMPLETE`** en ~15 s — mensaje TR-069 |
| Telemetry vía gateway | `wanIp=192.168.250.16`, SSID 2.4/5 lab |
| `wifi-refresh` vía gateway | `accepted=true` |
| Evidencia GenieACS WAN2 (solo lectura) | Static `192.168.250.16`, Connected, GW `.1`, mask `/24` |

Marker: `GATEWAY_FULL_WAN_SPIN_OK`

WiFi lab del activate: `lab-zte-e2e-24` / `LabZteWifi24!` y `lab-zte-e2e-24 - 5G` / `LabZteWifi24!`.
