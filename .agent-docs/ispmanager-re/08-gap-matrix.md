# Gap matrix — SmartOLT → docs → MVP IspManager

Leyenda cobertura docs: **OK** completo | **PARCIAL** | **N/A** no observable / no capturado.

Prioridad MVP: **P1** fase 1 | **P2** fase 2 | **DEF** deferido.

Captura browser chat principal: 2026-07-17 (sesión autenticada gigafiberperu v3.53.0).

## Matriz

| Feature SmartOLT | Docs | Prioridad | Notas |
|------------------|------|-----------|-------|
| Configured ONUs list + filtros | OK | P1 | `GET /onu/get_configured_list` + `form_data` + columnas HTML capturados |
| Unconfigured / autofind | OK | P1 | API vacía live; schema Postman OK; UI HTML PARCIAL |
| View ONU + modales config | OK | P1 | data model + RE |
| Status/signal poll UI | OK | P1 | cadencias documentadas |
| Get status / full_status / running-config | OK | P1 | API+UI |
| Authorize manual + API | OK | P1 | form Postman + RealOltService |
| Authorization presets wizard | OK | P2 | steps 1–6 + campos Conditions/ONU/TR069/WiFi capturados (sin save) |
| Batch actions + payloads | PARCIAL | P2 | paths+preview OK; execute body N/A (Permission Required, no write) |
| Resync / rebuild | OK | P1 | contrato; no ejecutar en prod RE |
| Move / twins offline | PARCIAL | P2 | preview paths; no run |
| OLT list + cards/PON/uplink | OK | P1 | API live samples |
| OLT VLANs / IP pools / ACL / profiles UI | PARCIAL | P2 | VLAN API OK; tabs Advanced/CLI N/A |
| OLT Advanced / backups / CLI tab | N/A | DEF | no re-explorado browser |
| Graphs página `/graphs` | OK | P2 | tabs OLT/Uplink/PON/Traffic/Signal + PNG `/graphs_olt/*` |
| Events | OK | P2 | filtros severidad/status/sort; vacío live; columnas OK |
| Diagnostics | OK | P1 | `GET /diagnostics/get_diagnostics_list` + columnas Rx/Distance |
| Tasks report | OK | P1 | `/reports/tasks` filtros; vacío “No info available” |
| Authorizations report | PARCIAL | P2 | ruta+modelo |
| Export/Import CSV | OK | P1 | UI export filtros + checkbox main SP; import reubicado |
| Config mismatch scan | OK | P2 | daily auto + manual; Status Disabled; no Scan run |
| Zones / ODBs / ONU types / Speeds | OK | P1 | API |
| TR069 / VPN system_config | OK | P2 | tabs VPN/TR069 Profiles/status; tunnel types |
| General Users/API/Billing | OK | P1 | settings + Users `/auth` + API limits UI |
| Auth session vs X-Token | OK | P1 | |
| Permisos / localStorage / deep links | OK | P1 | Permission Required batch; night/wide-tables |
| Worker cloud interval exacto | N/A | DEF | fuera de alcance RE web |
| SNMP OIDs / secretos OLT | N/A | DEF | |
| i18n ES | N/A | DEF | UI EN |
| CSV binario UI original | N/A | DEF | no se forzó download export (evita ruido); schema vía UI+API |

## MVP IspManager fase 1 (recomendado)

1. DB capa A + telemetría B (statuses/signals jobs).
2. UI: Configured, Unconfigured, View ONU (lectura + authorize + resync task).
3. OLT hub lectura (cards/PON/uptime).
4. Catálogos zones/types/speeds.
5. Tasks + audit log.
6. API key auth + rate limit básico.
7. Export CSV (schema de este pack).
8. Gateway CLI (ya existe) para autofind/by-sn/optical.
9. Diagnostics lectura (Rx OLT/ONU, distance, last status change).

## Fase 2

Graphs UI, Events, presets wizard completo, batch, mismatch auto, TR069, VoIP, import CSV completo, OLT tabs avanzados.

## Criterio pack cerrado

| Criterio | Estado |
|----------|--------|
| Menú principal con ficha o N/A | OK |
| ≥90% endpoints AJAX/Postman con método+schema | OK |
| Export con sample | OK (UI + derivado API) |
| Gap matrix MVP | OK |
| Re-captura browser chat principal gaps N/A | OK (salvo DEF listados) |

## Gaps N/A / DEF restantes

- Body exacto `POST /onu_batch_actions/execute` (permiso batch ausente + no writes).
- Intervalo worker cloud→OLT.
- CSV binario descargado desde botón Export ONUs.
- Tabs OLT Advanced / backups / CLI.
- i18n ES, SNMP OIDs.
