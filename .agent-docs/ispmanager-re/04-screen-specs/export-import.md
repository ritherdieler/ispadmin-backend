# Ficha: Export / Import

| | |
|--|--|
| Export | `/reports/export` |
| Import | `/reports/import` (hub; uploads reubicados) |
| Detalle formatos | [../05-export-import-formats.md](../05-export-import-formats.md) |
| Captura | Browser 2026-07-17 |

## Export UI

Título: “ONU database export”.

Filtros: OLT | Board (0,1) | Port (0–15) | Zone (Zone 1…31).

Checkbox: “Export only the main service port and skip additional attached VLANs”.

Botón: “Export ONUs”.

Sección: “Export History - Last 24h” (vacío: “No exports found”).

Dependencias AJAX filtros: `/api/system/get_local_olts`, `fetch_distinct_onu_boards/`, `fetch_distinct_onu_ports/`, `fetch_zones`.

## Import UI

Mensaje: “The Import functionality has moved.”

| Acción | Dónde |
|--------|-------|
| Import ONUs location details | Configured ONUs page |
| Import zones | Zones page |
| Import Splitters | Splitters / ODBs page |

## Seguridad RE

No subir imports de prueba a producción. No se forzó download CSV en RE (schema vía UI+API).
