# Export / Import — formatos

Fuentes: UI Routes `/reports/export`, `/reports/import`; API `get_all_onus_details` como export lógico; Postman; data model.

## 1. Export UI (`/reports/export`) — capturado 2026-07-17

Página “ONU database export”:

| Control | Valores vistos |
|---------|----------------|
| OLT | Any \| `2 - HAWEI` |
| Board | Any \| 0 \| 1 |
| Port | Any \| 0–15 |
| Zone | Any \| Zone 1…31 |
| Checkbox | Export only the main service port and skip additional attached VLANs |
| Acción | Export ONUs |
| Historial | Export History - Last 24h (vacío en captura) |

AJAX soporte filtros: `get_local_olts`, `fetch_distinct_onu_boards/`, `fetch_distinct_onu_ports/`, `fetch_zones`.

| Tipo | Destino IspManager | Estado RE |
|------|--------------------|-----------|
| ONUs configured | CSV inventario capa A (+ telemetría opcional) | UI OK; CSV binario no descargado a propósito |
| Zones | CSV catálogo | import reubicado a Zones |
| Splitters / ODBs | CSV | import reubicado a Splitters |
| Authorizations report | CSV desde `/reports/authorizations/list` | columnas: sn, pon, preset, user, date, source |
| Tasks | CSV opcional | type, status, onu, timestamps |

Sample canónico = proyección de `get_all_onus_details` (equivalente a “export DB ONU” en docs Postman).

## 2. Sample export ONU (derivado API, PII reducido)

Cabeceras recomendadas (1 fila ejemplo):

```csv
unique_external_id,sn,olt_id,olt_name,board,port,onu,pon_type,gpon_channel,onu_type_name,zone_name,name,address,contact,odb_name,odb_port,mode,wan_mode,vlan,administrative_status,status,signal,signal_1310,signal_1490,distance,is_synced_after_import,is_failed_resync_config,upload_speed,download_speed,service_port,latitude,longitude
TPLG********,TPLG********,2,HAWEI,1,0,2,gpon,gpon,XN020-G3v,Zone 1,JEINERALVARADO,,,,"",,Routing,Setup via ONU webpage,1,Enabled,Online,Warning,-30.46,-24.95,1447,0,0,1G,1G,58,,
```

Nested arrays en CSV plano:

- `service_ports`: repetir fila o serializar `sp:vlan:up:down;...`
- `ethernet_ports`: `eth_0/1:Enabled:LAN;eth_0/2:Enabled:LAN`

IspManager MVP: export JSON lines o CSV flat con columnas arriba; import exige subset requerido.

## 3. Import UI (`/reports/import`)

Hub (2026-07-17): “The Import functionality has moved.”

- Import ONUs location details → Configured ONUs
- Import zones → Zones
- Import Splitters → Splitters page

Campos requeridos observados en flujos de producto / authorize (mínimo para alta):

| Campo | Requerido | Notas |
|-------|-----------|-------|
| `sn` | sí | clave física |
| `olt_id` / olt name | sí | |
| `board`, `port` | sí para authorize inmediato | vacío = save later |
| `onu_type` | sí | catálogo |
| `vlan` | sí | |
| `zone` | sí | |
| `name` | sí | |
| `onu_mode` | sí | Routing/Bridging |
| speeds | recomendado | profile names |
| `unique_external_id` | opcional | default=SN |

Post-import SmartOLT: `is_synced_after_import=0` → banner Resync antes de editar. IspManager: flag equivalente + task `resync`.

## 4. API como export

| Modo | Endpoint | Límite |
|------|----------|--------|
| Full dump | `GET onu/get_all_onus_details` sin page | 15/h |
| Paginado | `?page=&page_size=` | presupuesto general |
| Incremental | `updated_since=YYYY-MM-DD HH:MM:SS` | solo cambios de **config** |

Status/señal **no** mueven `updated_since` → usar endpoints dedicados.

## 5. Zones / ODBs sample

Zones API:

```json
{ "id": "1", "name": "Zone 1", "imported_date": null, "imported_from_olt": null }
```

ODBs zone 1 (esta instancia): lista vacía. CSV propuesto:

```csv
zone_id,zone_name,odb_name,capacity,latitude,longitude
1,Zone 1,Splitter-A,8,,
```

## 6. Redacción PII

En samples del pack: SN parcialmente enmascarado, IP OLT pública enmascarada, contactos vacíos. No versionar exports reales con datos de clientes.
