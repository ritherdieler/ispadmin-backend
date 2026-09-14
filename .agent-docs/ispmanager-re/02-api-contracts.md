# Contratos API — SmartOLT (público + UI AJAX)

Base pública: `https://{subdomain}.smartolt.com/api/`  
Auth pública: header `X-Token: <api_key>` (Settings → API key; **distinta** de `config.X_TOKEN` de sesión UI).  
Instancia verificada: gigafiberperu, 2026-07-17. Tokens enmascarados.

Fuentes: Postman SmartOLT (107 ops), curl live GET, [../smartolt-reverse-engineering.md](../smartolt-reverse-engineering.md) (UI).

Envelope común frecuente:

```json
{ "status": true, "response_code": "success", "response": ... }
```

Errores típicos: `400` validación/filtro, `403` auth/permiso UI, `405` método desconocido (`{"status":false,"error":"Unknown method"}`), `429` rate limit + `Retry-After`.

---

## 1. Rate limits (resumen)

| Presupuesto | Valor |
|-------------|------:|
| General | 1000/h, ~10/s; burst &gt;15/s bloqueado |
| Full export `get_all_onus_details` sin `page` | 15/h |
| Con `page`/`page_size` (1–100) | cuenta al general |
| Heavy OLT detail (docs) | 30/10 min por OLT |
| Headers vistos | ver [03-polling-and-jobs.md](./03-polling-and-jobs.md) |

---

## 2. API pública — lectura verificada (live)

### 2.1 Catálogos / OLT

#### `GET system/get_olts`

```json
{
  "response": [
    {
      "id": "2",
      "name": "HAWEI",
      "olt_hardware_version": "Huawei-MA5608T",
      "ip": "38.***.***.***",
      "telnet_port": "2333",
      "snmp_port": "2161"
    }
  ],
  "response_code": "success",
  "status": true
}
```

No usar como heartbeat (docs Postman).

#### `GET olt/get_olts_uptime_and_env_temperature`

Limit header observado: **70**.

```json
{
  "response": [
    { "olt_id": "2", "olt_name": "HAWEI", "uptime": "7 days, 19:28", "env_temp": "43°C" }
  ],
  "response_code": "success",
  "status": true
}
```

#### `GET system/get_olt_cards_details/{olt_id}`

```json
{
  "response": [
    {
      "slot": "0",
      "type": "H805GPFD",
      "real_type": "H805GPFD",
      "ports": "16",
      "software_version": null,
      "status": "Normal",
      "role": null,
      "info_updated": "2026-07-16 23:16:04"
    },
    {
      "slot": "3",
      "type": "H801MCUD1",
      "real_type": "H801MCUD1",
      "ports": "3",
      "software_version": "MA5600V800R015C00",
      "status": "Normal",
      "role": "Main",
      "info_updated": "2026-07-16 23:16:04"
    }
  ],
  "response_code": "success",
  "status": true
}
```

#### `GET system/get_olt_pon_ports_details/{olt_id}`

Campos por puerto: `board`, `pon_port`, `pon_type`, `admin_status`, `operational_status`, `description`, `min_range`, `max_range`, `tx_power`, `onus_count`, `online_onus_count`, `average_signal`.

Ejemplo (board 0 port 0): ONUs 25, online 24, avg signal -28.7, TX 7.73 dBm, range 0–20000 m, status `Up / Autofind`.

También: `GET system/get_outage_pons/{olt_id}`, `GET system/get_olt_uplink_ports_details/{olt_id}` (200 live).

#### `GET olt/get_vlans/{olt_id}`

```json
{
  "response": [
    {
      "id": "1",
      "vlan": "1",
      "description": null,
      "olt_id": "2",
      "scope": "internet",
      "default_for_pon_ports": "",
      "default_for_pon_port_roles": ""
    }
  ],
  "response_code": "success",
  "status": true
}
```

#### `GET system/get_zones` / `GET system/get_odbs/{zone_id}` / `GET system/get_onu_types` / `GET system/get_speed_profiles`

- Zones: `{id,name,imported_date,imported_from_olt}` (muestras Zone 1…N).
- ODBs zone 1 en esta instancia: `[]`.
- ONU types: `{id,name,pon_type,capability,ethernet_ports,wifi_ports,voip_ports,catv,allow_custom_profiles}` (~49).
- Speed profiles: `{id,name,speed,direction,type}` ej. `100M` download `104800` type `internet` (~32).
- También: `GET system/get_onu_types_by_pon_type/{pon_type}`, `GET system/get_billing_details`.

Billing sample:

```json
{
  "response": {
    "olts": [
      {
        "olt_id": "2",
        "olt_name": "HAWEI",
        "olt_subscription_status": "active",
        "olt_subscription_end_date": "13-Oct-2026"
      }
    ],
    "unassigned_subscriptions": []
  },
  "response_code": "success",
  "status": true
}
```

### 2.2 ONU — inventario / telemetría

#### `GET onu/get_onus_statuses`

Query: `olt_id`, `board`, `port`, `zone` (nombre).  
Nota: query params inventados tipo `status=Offline` **no filtran** (devuelven las 758).

```json
{
  "response": [
    {
      "unique_external_id": "TPLG********",
      "sn": "TPLG********",
      "olt_id": "2",
      "board": "1",
      "port": "0",
      "onu": "2",
      "zone_id": "1",
      "name": "JEINERALVARADO",
      "address": "",
      "contact": "",
      "odb_id": null,
      "status": "Online",
      "last_status_change": "2026-07-08 11:36:33"
    }
  ]
}
```

Statuses observados en flota: `Online`, `Offline`, `Power fail`, `LOS`.

#### `GET onu/get_onus_signals`

Mismos filtros espaciales. Categorías: `Very good` | `Warning` | `Critical` | `-`.

```json
{
  "unique_external_id": "TPLG********",
  "signal_1310": "-30.46 dBm",
  "signal": "Warning",
  "signal_1490": "-24.95 dBm"
}
```

También: `get_onus_administrative_statuses`, `get_onus_catv_statuses` (200 live).

#### `GET onu/get_all_onus_details`

Query: `olt_id`, `board`, `port`, `zone`, `page`, `page_size` (1–100), `updated_since`.

Paginado:

```json
{
  "page": 1,
  "page_size": 1,
  "total_items": 758,
  "total_pages": 758,
  "onus": [ { "...81 campos..." } ]
}
```

81 campos top-level (verificado):  
`unique_external_id`, `pon_type`, `gpon_channel`, `sn`, `olt_id`, `olt_name`, `board`, `port`, `onu`, `onu_type_id`, `onu_type_name`, `zone_id`, `zone_name`, `name`, `address`, `odb_name`, `odb_port`, `mode`, `wan_mode`, `vlan`, IP/PPPoE fields, `mgmt_ip_*`, `voip_*`, `iptv_*`, `custom_template_name`, `tr069*`, `catv`, `administrative_status`, `authorization_date`, `is_synced_after_import`, `is_failed_resync_config`, `status`, `last_status_change`, `signal`, `signal_1310`, `signal_1490`, `latitude`, `longitude`, `contact`, nested `service_ports[]`, `ethernet_ports[]`, `wifi_ports[]`, `voip_ports[]`.

Detalle semántico: [../smartolt-onu-data-model.md](../smartolt-onu-data-model.md).

#### `GET onu/get_onu_details/{unique_external_id}` / `GET onu/get_onus_details_by_sn/{sn}`

Detalle 1 ONU (+ `distance` en details). by-sn envuelve `{ "onus": [ ... ] }`.

#### `GET onu/unconfigured_onus` / `GET onu/unconfigured_onus_for_olt/{olt_id}`

Schema Postman: `pon_type`, `board`, `port`, `onu`, `sn`, ONU type, `olt_id`, `disabled`, `possible_actions` (`view`,`resync_config`,`authorize`,`move_here`).  
Live 2026-07-17: `{ "response": [], "response_code": "success", "status": true }` (sin autofind pendiente).

#### Singulares

| Endpoint | Sample / notas |
|----------|----------------|
| `GET onu/get_onu_status/{id}` | `{onu_status,last_status_change,status,response_code}` |
| `GET onu/get_onu_signal/{id}` | signal + 1310/1490; limit 500 |
| `GET onu/get_onu_administrative_status/{id}` | 200 |
| `GET onu/get_onu_catv_status/{id}` | 200 |
| `GET onu/get_onu_full_status_info/{id}` | texto CLI + `full_status_json`; limit 300 |
| `GET onu/get_running_config/{id}` | string CLI Huawei |
| `GET onu/get_onu_speed_profiles/{id}` | `{upload_speed_profile_name,download_speed_profile_name}` |
| `GET onu/get_onu_*_graph/{id}/{graph_type}` | PNG; types: hourly\|daily\|weekly\|monthly\|yearly |
| `GET onu/get_all_onus_gps_coordinates` | limit **5** |

Ejemplo running config (redactado):

```text
interface gpon 0/1
 ont add 0 2 sn-auth "54504C47********" omci ont-lineprofile-id 10 ont-srvprofile-id 10 desc "JEINERALVARADO"
quit
service-port 58 vlan 1 gpon 0/1/0 ont 2 gemport 1 multi-service user-vlan 1 tag-transform translate
```

---

## 3. API pública — escrituras (solo contrato; no ejecutar)

Inventario Postman (form-data / urlencoded). IspManager debe exponer equivalentes vía tasks.

### 3.1 Authorize — `POST onu/authorize_onu`

Campos form (Postman):

| Campo | Ejemplo | Notas |
|-------|---------|-------|
| `olt_id` | 1 | requerido |
| `pon_type` | gpon \| epon | |
| `gpon_channel` / `epon_channel` | gpon / epon | |
| `board`, `port` | | vacíos = save for later |
| `sn` | | SN o MAC EPON |
| `onu_type` | nombre catálogo | |
| `custom_profile` | | opcional |
| `onu_mode` | Routing \| Bridging | |
| `vlan`, `cvlan`, `svlan` | | |
| `tag_transform_mode` | translate… | |
| `use_other_all_tls_vlan` | 0\|1 | |
| `zone`, `odb` | | zone formato alfanumérico |
| `name`, `address_or_comment` | | |
| `onu_external_id` | | |
| `upload_speed_profile_name`, `download_speed_profile_name` | | |

Backend actual WispAdmin ya postea subset: `olt_id,pon_type,board,port,sn,vlan,onu_type,zone,name,onu_mode,custom_profile`.

### 3.2 Otras escrituras (path)

`move`, `update_pon_channel`, `update_sn`, `change_onu_type`, `change_custom_profile`, `update_location_details`, `update_unique_external_id*`, `update_attached_vlans`, `update_main_vlan`, `update_onu_mode`, mgmt IP set_*, TR069 enable/disable, VoIP set_*, WAN set_*, DHCP snooping/option82/source-guard, remote access, `update_onu_speed_profiles`, `bulk_update_speed_profiles`, `update_service_port`, ethernet/wifi port modes, IPTV/CATV, `reboot`, `resync_config`, `restore_factory_defaults`, `disable`/`enable` (+ bulk), `delete`, `system/save_config`, `system/add_*` (zone/odb/onu_type), `olt/add_vlan`.

UI usa a menudo paths distintos (`/onu/rebuild/{webId}` = resync). Ver sección 4.

---

## 4. UI AJAX (sesión cookie + `X-Token: config.X_TOKEN`)

Inventario consolidado desde RE previo. Contratos: método + path; body/response donde se capturó.

### 4.1 Lectura ONU / diagnóstico

| Método | Ruta | Contrato / ejemplo |
|--------|------|--------------------|
| GET | `/api/onu/get_onu_status_and_signal/{webId}` | Ver RE; query `signal=db` |
| GET | `/api/onu/status/{webId}` | HTML CLI ~10–15 s |
| GET | `/api/onu/get_local_running_config/{webId}` | running-config |
| GET | `/api/onu/show_hw_sw/{webId}` | SW info |
| GET | `/onu/get_live_smartolt_graph/{webId}` | fragmento live; timeout 30 s |
| GET | `/traffic/onu/{id}`, `/signal/onu/{id}` | series UI |
| GET | `/onu/onu_history/{id}` | historial modal |
| GET | `/info/?onu_id={id}` | audit log |
| GET | `/onu/get_configured_list` | HTML parcial Configured — ver 4.2 |
| GET | `/diagnostics/get_diagnostics_list` | HTML parcial Diagnostics — ver 4.2b |
| GET | `/onu/get_unconfigured_for_olt/{oltId}` | HTML/JSON autofind UI |
| GET | `/api/onu/fetch_unconfigured_onus_for_olt_pon/{olt}/{ponKey}` | por PON |
| POST | `/api/onu/fetch_distinct_options/{field}` | opciones filtros |
| GET | `/api/export` | export Diagnostics (path visto en JS) |
| GET | `/api/onu/pon_refresh/` | refresh PON Diagnostics |
| GET | `/graphs_olt/get_daily_*_for_olt/{oltId}/small` | PNG graphs OLT |

### 4.2 `get_configured_list` (capturado 2026-07-17)

- Ruta página: `/onu/configured?olt_id=2`.
- Disparado por `initFilterActionsNoSubmit(getConfigured, '/onu/get_configured_list', ...)`.
- **Método real observado:** `GET` (no POST). Ejemplo:

```text
GET /onu/get_configured_list?page=1&olt_id=2&sort_by=id&sort_order=desc&_=…
→ 200 text/html (~210 ms)
```

- Objeto JS `form_data` (filtros serializados a query):

```json
{
  "page": 1,
  "free_text": "",
  "board": "",
  "port": "",
  "zone_id": "",
  "odb_id": "",
  "vlan": "",
  "vlan_id": "",
  "olt_id": "2",
  "pon_type": "",
  "status": "",
  "signal": "",
  "sort_by": "id",
  "sort_order": "desc",
  "custom_template": "",
  "custom_template_id": "",
  "line_profile_maptype": "",
  "onu_type_id": "",
  "speed_profile_id": "",
  "upload_speed": "",
  "download_speed": "",
  "voip_sip_profile_id": "",
  "extra_service_id": "",
  "tr069_profile_id": "",
  "mgmt_ip_mode": "",
  "voip_mode": "",
  "catv_enabled": "",
  "router_mode": "",
  "configuration_method": "",
  "ip_protocol": "",
  "onu_mode": "",
  "authorization_date": "",
  "last_status_change": "",
  "svlan_id": "",
  "cvlan_id": "",
  "tag_transform_mode": "",
  "should_rebuild": "",
  "all": ""
}
```

- Respuesta: **HTML parcial** (no JSON): `<input id="total-onus-count" value="758">`, paginación CodeIgniter (100/página), tabla.
- Columnas: Status | View | Name | SN / MAC | ONU | Zone | ODB | Signal | B/R | VLAN | VoIP | TV | Type | Auth date.
- Sort links: `status`, `location_name`, `sn`, `onu_descr`, `zone`, …

### 4.2b `get_diagnostics_list` (capturado)

```text
initFilterActionsNoSubmit(getConfigured, '/diagnostics/get_diagnostics_list', '#diagnostics_list', 'refreshDiagnosticsData')
```

`form_data` keys: `page, free_text, board, port, zone_id, odb_id, olt_id, pon_type, status, signal, last_status_change_from, last_status_change_to, sort_by=last_status_change, sort_order=desc, onu_type_id`.

Columnas UI: Status | Rx OLT | Rx ONU | Distance | Name | SN / MAC | Zone | Splitter | ONU | Status changed.

### 4.3 Catálogos UI

`/api/system/get_local_olts`, `get_local_onu_types_by_pon_type/{pon}`, `/api/olt/get_local_olt_boards/{oltId}`, `get_local_olt_board_ports/...`, `get_local_available_onu_ids/...`, `get_capabilities/{oltId}`, `/api/onu/fetch_zones`, `fetch_odbs_for_location/`, `fetch_available_ports_for_odb/`, `fetch_vlans*`, `fetch_mgmt_ip_addresses/`, `fetch_voip_profiles/...`, `is_duplicate_sn`, etc.

### 4.4 Batch / presets / autofind

| Ruta | Uso | Payload notes |
|------|-----|---------------|
| `POST /onu_batch_actions/execute` | Ejecutar batch | **no ejecutar**; UI muestra Permission Required si falta flag |
| `GET /onu_batch_actions/get_active_tasks` | poll 10 s / 5 min | |
| `GET /onu_batch_actions/get_dropdown_data` | dropdowns | |
| `GET /onu_batch_actions/get_move_source_ports` | move UI | |
| `POST .../preview_offline_duplicates` | preview twins | seguro si solo preview |
| `POST .../preview_move_offline_after_migration` | preview move | |
| `POST .../run_delete_offline_duplicates` | write | no llamar |
| `POST .../run_move_offline_after_migration` | write | no llamar |
| `POST .../set_change_onu_mode_enabled` | write | no llamar |
| `POST /onu_batch_actions/stop` | stop tasks | |
| `POST /onu_authorization/authorize_with_preset` | auth preset | |
| `GET /onu_authorization_presets/get_presets_for_apply` | list apply | |
| `GET /onu_authorization_presets/listing` | CRUD UI + wizard modal | |
| `GET /onu_authorization_presets/add/` | create form | |
| `POST /onu/add_task`, `update_auto_task/`, `stop_task_actions` | auto actions | |
| `GET /onu/restart_auto_authorize_task/` | Tasks UI | |

### 4.4b Graphs UI (capturado)

Tabs: `graph_type=olt|uplink|pon|traffic|signal`. Query `?olt_id=2&graph_type=olt`.

PNG examples (OLT tab):

| Path |
|------|
| `/graphs_olt/get_daily_env_temp_for_olt/{oltId}/small` |
| `/graphs_olt/get_daily_fan_speed_for_olt/{oltId}/small` |
| `/graphs_olt/get_daily_mac_for_olt/{oltId}/small` |
| `/graphs_olt/get_daily_for_board_cpu/{oltId}/{slot}/small` |
| `/graphs_olt/get_daily_for_board_mem/{oltId}/{slot}/small` |

Legacy path fragments en JS: `/graphs_olt/environment_temperature/`, `fan_speed/`, `mac_table_usage/`, `board_cpu/`, `board_mem/`.

### 4.5 Escrituras UI por modal (webId)

Ver tabla completa en RE: `change_onu_type`, `update_speed_profiles_huawei`, `update_vlan`, `update_onu_mode`, `move_onu`, `rebuild`(=resync), `reboot`, `delete`, `disable`/`enable`, etc.

---

## 5. Matriz endpoint público → uso IspManager

| Endpoint | Uso sync | MVP |
|----------|----------|-----|
| `get_onus_statuses` | poll telemetría B | fase 1 |
| `get_onus_signals` | poll señal B | fase 1 |
| `get_all_onus_details` page+updated_since | sync config A | fase 1 |
| `unconfigured_onus*` | autofind | fase 1 |
| `get_onu_details` / by_sn | detalle | fase 1 |
| `get_onu_full_status_info` | diagnóstico C | fase 1 on-demand |
| `get_olt_*_details` / cards / pon | OLT hub | fase 1 lectura |
| `get_zones` / types / speeds | catálogos | fase 1 |
| graphs PNG | ops | fase 2 |
| GPS coordinates | mapa | defer (limit 5) |
| writes authorize/move/… | provisioning | fase 1 vía gateway+tasks |

---

## 6. Cobertura

- Postman: **107/107** paths inventariados con método.
- GET críticos: **verificados live** (≥30).
- UI AJAX: `get_configured_list` + `get_diagnostics_list` + graphs PNG + batch paths + wizard presets capturados en browser chat principal 2026-07-17.
- Body `onu_batch_actions/execute`: N/A (Permission Required + no writes). CSV binario Export: N/A (no forzado).
