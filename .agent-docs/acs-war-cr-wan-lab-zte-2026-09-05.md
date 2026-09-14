# ACS WAR — CR + WAN lab ZTE (2026-09-05)

Prueba directa contra `ispadmin-staging-acs` (Tomcat `:8081`), SN `ZTEGDC47BFFD` (`5872C9-F6600R-ZTEGDC47BFFD`). Autorización OLT a cargo del operador.

## Endpoints

Base: `http://127.0.0.1:8081/ispadmin-staging-acs`  
Header: `X-Acs-Key: $ACS_API_KEY`

| Método | Path | Uso |
|--------|------|-----|
| GET | `/api/acs/v1/health` | Health |
| POST | `/api/acs/v1/cpe/{sn}/wifi-refresh` | Connection Request (`refreshObject` WLAN) |
| POST | `/api/acs/v1/cpe/{sn}/reboot` | Connection Request (`reboot`) |
| POST | `/api/acs/v1/cpe/provision` | SPV WAN cliente + WiFi + verificación |
| GET | `/api/acs/v1/cpe/{sn}/status` | Estado provision |
| GET | `/api/acs/v1/cpe/{sn}/telemetry` | Snapshot ACS |

## Resultado (tras OLT habilitada)

| Paso | Resultado |
|------|-----------|
| Baseline `_lastInform` | fresco `2026-09-05T21:12:29Z` |
| `wifi-refresh` | HTTP 200 `accepted=true` |
| `reboot` | HTTP 200 `accepted=true`; Inform `21:12:45Z` |
| `provision` IP `192.168.250.16` /24 VLAN 100 + SSIDs lab | **`COMPLETE`** |
| GenieACS WANIPConnection.2 | Static `192.168.250.16`, `Connected` |

Sin reachability CR (ONU no autorizada en OLT) los mismos calls encolan HTTP 202 / timeout de verificación.
