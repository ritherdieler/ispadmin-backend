# Ficha: Diagnostics

| | |
|--|--|
| Ruta | `/diagnostics` |
| Captura | Browser 2026-07-17 — 758 ONUs, sort por last status change |

## Filtros

Search (SN, MAC, IP, name, address) | OLT | Board | Port | Zone | Splitter | ONU type | PON type | Status | Signal | Status changed From/To.

Botones: Refresh | Export.

## Listado

`initFilterActionsNoSubmit(..., '/diagnostics/get_diagnostics_list', '#diagnostics_list', 'refreshDiagnosticsData')`.

`form_data`: `page, free_text, board, port, zone_id, odb_id, olt_id, pon_type, status, signal, last_status_change_from, last_status_change_to, sort_by=last_status_change, sort_order=desc, onu_type_id`.

## Columnas

Status | Rx OLT | Rx ONU | Distance (m) | Name | SN / MAC | Zone | Splitter | ONU | Status changed

Ejemplos live: Rx OLT ~-23…-29 dBm; Rx ONU a veces `-`; distance 458–9497 m; timestamps `2026-07-16 …`.

## Otros endpoints JS

| Path | Uso |
|------|-----|
| `/api/onu/pon_refresh/` | refresh PON |
| `/diagnostics/get_odb_details/` | detalle splitter |
| `/odbs/update_nr_of_ports/`, `attach_onu_to_port/` | writes ODB |
| `/api/export` | export |

## IspManager

P1 lectura: tabla óptica + last status change desde telemetría B + gateway optical. Export CSV propio.
