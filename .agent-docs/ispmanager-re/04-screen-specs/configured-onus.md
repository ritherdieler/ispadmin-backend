# Ficha: Configured ONUs

| | |
|--|--|
| Ruta | `/onu/configured` (query `olt_id`, sort, page) |
| Objetivo | Inventario provisionado: filtros, estado, acciones batch |
| Datos | Capa A + B cache ([data model](../../smartolt-onu-data-model.md)) |
| Captura | Browser 2026-07-17 — 758 ONUs, OLT `2 - HAWEI` |

## Filtros

Básicos: Search (`free_text`), OLT, Board, Port, Zone, Splitter (ODB), VLAN, ONU type, Profile, PON type, Status, Signal, B/R.

Status: Online | Power Fail | Loss of Signal | Offline | Admin Disabled.

Signal: Good | Warning | Critical.

More filters (`#more-filters`): Mgmt IP, TR-069, VoIP, CATV, WAN mode, Configuration method, GEM mapping, WAN IP protocol, Download, Upload, Status changed before, Resync failed, SVLAN, CVLAN, Tag-transform.

Opciones dinámicas: `POST /api/onu/fetch_distinct_options/{field}`.

## Columnas (HTML parcial)

Status | View | Name | SN / MAC | ONU (`gpon-onu_0/b/p:ont`) | Zone | ODB | Signal (dBm) | B/R | VLAN | VoIP | TV/CATV | Type | Auth date

Paginación: 100 por página (`1-100 ONUs of 758 displayed`). Hidden `#total-onus-count`.

## Acciones

- Abrir View ONU (`/onu/view/{webId}`)
- Batch bar (`#batch-actions`): requiere permiso; sin él → “Permission Required — You do not have permission to perform batch actions on ONUs.”
- Import / Export link; modal “Import ONUs location details”
- Checkbox “Select All - all ONUs matching the current filters”

### Batch panel (campos UI, sin execute)

New main VLAN / SVLAN / CVLAN / Tag-transform / VLANs old→new / Download+Upload speeds / New ONU Type / Custom Profile / GEM mapping / Mode / MGMT IP pool + Mgmt VLAN / TR-069 profile + interface / Configuration method / WAN mode + WAN IP pool / IPv6 / Web user-pass / Zone / Splitter / DNS (mgmt/WAN/VoIP) / delete offline duplicates (preview) / DHCP Option 82 / PPPoE Plus / Move PONs / Move offline after migration.

Paths batch: ver [02-api-contracts.md](../02-api-contracts.md) §4.4.

## Endpoints

| | |
|--|--|
| Listado | `GET /onu/get_configured_list?...` → HTML parcial |
| Filtros JS | objeto `form_data` (ver contratos §4.2) |
| Tasks poll | `/onu_batch_actions/get_active_tasks` 10s/5min |
| Batch | `execute`, previews (**no run writes**) |

## Preferencias

`localStorage.smartolt-wide-tables`, `smartolt-night` / `smartolt-theme-local`.

## IspManager

Tabla virtualizada + filtros URL-serializables; JSON tipado en vez de HTML parcial.
