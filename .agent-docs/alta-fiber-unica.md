# Alta FIBER — un solo camino

El módulo OLT Gateway arranca con el WAR. El campo `olt.gateway.enabled` y la variable `OLT_GATEWAY_ENABLED` se eliminaron: no apagan el módulo. Los schedulers (inventario, SNMP, señal) siguen con sus propios flags. El SSH real arranca cuando `olt.gateway.mock.enabled=false`.

Fecha: 2026-09-24.

El alta FIBER ya no elige versión. `POST /subscription` con `installationType=FIBER` siempre abre el pipeline de etapas (OLT, OMCI/VLAN 1000, MikroTik, contacto ACS, WAN y Wi-Fi). No existe selector de flujo ni alta FIBER por `onu/activate`.

## Contrato

- No enviar `provisioningFlowVersion`. El campo se eliminó.
- Autorización OLT interna: `POST /api/olt-gateway/onus/provisioning/authorize` y `.../compensate`.
- OMCI: `POST /api/olt-gateway/onus/{sn}/omci/management`.
- Progreso del pipeline: `GET /subscription/{id}/provisioning`.
- La pantalla de alta y el e2e siguen leyendo `oltProvisionStatus` y `tr069ProvisionStatus` en `GET /subscription/{id}/registration-progress`. Esos campos pasan a `COMPLETE` cuando la etapa OLT y la operación completa quedan `SUCCEEDED`.

## Variables

Nombres vigentes. Si el proceso todavía exporta el nombre anterior, el properties lo acepta como respaldo hasta rotar el entorno.

| Vigente | Respaldo temporal |
|---|---|
| `PROVISIONING_ENABLED` | `PROVISIONING_V2_ENABLED` |
| `PROVISIONING_WORKER_ENABLED` | `PROVISIONING_V2_WORKER_ENABLED` |
| `PROVISIONING_TR069_PROFILE_ID` | `PROVISIONING_V2_TR069_PROFILE_ID` |
| `PROVISIONING_ACS_BASELINE_KEY` | `PROVISIONING_V2_ACS_BASELINE_KEY` |
| `PROVISIONING_TELEMETRY_ENABLED` | `PROVISIONING_V2_TELEMETRY_ENABLED` |
| `PROVISIONING_OBS_BASE_URL` | `PROVISIONING_V2_OBS_BASE_URL` |
| `PROVISIONING_OBS_API_KEY` | `PROVISIONING_V2_OBS_API_KEY` |
| `PROVISIONING_OUTBOX_DELAY_MS` | `PROVISIONING_V2_OUTBOX_DELAY_MS` |

Wireless y solo TV no usan este pipeline. El journal MySQL conserva tablas `provisioning_v2_*` ya migradas; no renombrarlas en caliente.
