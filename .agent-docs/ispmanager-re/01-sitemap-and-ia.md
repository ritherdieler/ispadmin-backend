# Sitemap e información architecture — SmartOLT → IspManager

Fuente: menú UI SmartOLT v3.53.0 + rutas citadas en [../smartolt-reverse-engineering.md](../smartolt-reverse-engineering.md) + DB model [../olt-manager-db-model.md](../olt-manager-db-model.md).

Base UI: `https://{subdomain}.smartolt.com`

## Menú principal (jerarquía)

```text
Dashboard / Home
├── ONUs
│   ├── Configured          /onu/configured  (GET get_configured_list)
│   ├── Unconfigured        /onu/unconfigured + autofind
│   └── View ONU            /onu/view/{webId}
├── OLTs
│   ├── List                /olt
│   └── OLT details         /olt/olt_details/{oltId}
│       ├── Cards
│       ├── PON ports
│       ├── Uplink ports
│       ├── VLANs
│       ├── IP pools (mgmt / internet)
│       ├── Remote ACLs
│       ├── Profiles (line/service)
│       ├── VoIP / SIP profiles
│       ├── Advanced (NTP, fans, rogue ONU)
│       ├── Config backups
│       ├── CLI
│       └── History
├── Graphs                  /graphs
├── Events                  /events
├── Diagnostics             /diagnostics
├── Reports
│   ├── Tasks               /reports/tasks
│   ├── Authorizations      /reports/authorizations/list
│   ├── Export              /reports/export
│   └── Import              /reports/import
├── Config comparison       /config_comparison
└── Settings
    ├── Zones               /locations/listing
    ├── Splitters / ODBs    /odbs/listing
    ├── ONU types           /onu_types/listing
    ├── Speed profiles      /speed_profiles
    ├── Authorization presets /onu_authorization_presets/listing
    ├── VPN & TR069         /system_config
    └── General             /general
        ├── Users
        ├── Notifications
        ├── API key
        ├── API Logs
        └── Billing
```

## Deep links / hashes observados

| Hash / query | Efecto UI |
|--------------|-----------|
| `#batch-actions` | Abre panel Batch actions en Configured |
| `#more-filters` | Expande filtros avanzados |
| (localStorage) `smartolt-wide-tables` | Preferencia tablas anchas |

## Identificadores

| Contexto | ID | Uso |
|----------|----|-----|
| UI View ONU | `webId` numérico (`/onu/view/3`) | Solo browser; no integración |
| API | `unique_external_id` (suele = SN) | Clave pública |
| OLT | `olt_id` string/int (`2`) | Filtros API/UI |
| Autofind | SN + `(olt, board, port)` | Unconfigured |

## Mapa pantalla → endpoints principales

| Pantalla | Lectura principal | Escritura (no ejecutar en RE) |
|----------|-------------------|-------------------------------|
| Configured | `GET /onu/get_configured_list`, tasks poll | batch execute |
| Unconfigured | `GET /onu/get_unconfigured_for_olt/{oltId}` | authorize / auto tasks |
| View ONU | status+signal, status CLI, graphs | reboot/resync/… |
| OLT details tabs | UI AJAX + API `system/get_olt_*_details` | add VLAN, save config |
| Graphs | `/graphs?olt_id=&graph_type=` + `/graphs_olt/*` PNG | — |
| Events | `/events` (filtros severity/status) | resolve |
| Diagnostics | `GET /diagnostics/get_diagnostics_list` | export / pon_refresh |
| Tasks | `/reports/tasks` | restart auto-authorize |
| Export/Import | `/reports/export`; import reubicado a Configured/Zones/ODBs | download/upload |
| Config mismatch | `/config_comparison` | manual scan (cuidado auto-fix) |
| General | `/general`, Users `/auth`, API key `/general/listing/api_key` | settings, API key rotate |
| VPN & TR069 | `/system_config` | tunnels / profiles |

## Fichas de pantalla

Ver [04-screen-specs/](./04-screen-specs/).

## Cobertura menú vs ficha

Toda ruta del menú principal tiene entrada en sitemap y ficha o nota N/A en [08-gap-matrix.md](./08-gap-matrix.md).
