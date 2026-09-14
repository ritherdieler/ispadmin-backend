# Ingeniería inversa SmartOLT (Gigafiber) — v3.53.0

Documento de comportamiento observado en `https://gigafiberperu.smartolt.com`
(sesión UI + API pública + inyección JS de hooks XHR/timers). Complementa:

- [smartolt-onu-data-model.md](./smartolt-onu-data-model.md) — campos por capa
- [olt-manager-db-model.md](./olt-manager-db-model.md) — propuesta DB propia
- [olt-gateway-read-mvp.md](./olt-gateway-read-mvp.md) — gateway CLI actual
- [ispmanager-re/00-index.md](./ispmanager-re/00-index.md) — pack RE completo para diseñar IspManager

Fecha de captura: 2026-07-17. Instancia: OLT `2 - HAWEI` (MA5608T), ~758 ONUs.

## Método

1. Recorrido UI de pantallas y modales (nombres de inputs = esquema interno).
2. Extracción de scripts inline (constantes `*_MS`, URLs, funciones).
3. Inyección JS: parche de `XMLHttpRequest` / `setInterval` / `setTimeout` +
   `Page.addScriptToEvaluateOnNewDocument` para persistir en navegaciones.
4. Captura live en `/onu/view/3` (~60 s) y clicks Get status / running-config / SW info.
5. API con header `X-Token` (API key del backend; distinta del `X_TOKEN` de sesión UI).

**No documentamos tokens reales.** El UI inyecta `config.X_TOKEN` en JS; la API
usa otra clave en Settings → API key.

---

## Arquitectura observada

```text
┌─────────────┐   HTTP session / X-Token   ┌──────────────────┐
│ Browser UI  │ ──────────────────────────►│ SmartOLT cloud   │
│ (jQuery)    │   reads DB + triggers jobs │  DB + workers      │
└─────────────┘                            └────────┬─────────┘
                                                    │ SSH/Telnet/SNMP
                                                    ▼
                                            ┌──────────────┐
                                            │ OLT Huawei   │
                                            │ 10.x / public│
                                            └──────────────┘
```

- Listados y filtros leen **DB cloud** (`/onu/get_configured_list`).
- Status/señal en View ONU hacen poll al **backend cloud**, que a veces lee OLT
  live y a veces sirve cache DB (`?signal=db`).
- Escrituras (authorize, resync, move…) encolan **tasks** server-side.
- Helper `SmartOLTPolling`: pausa timers cuando `document.visibilityState === 'hidden'`.

---

## Cadencias de actualización (evidencia JS + XHR)

### A) Vista ONU `/onu/view/{webId}` — status/señal

Endpoint: `GET /api/onu/get_onu_status_and_signal/{webId}`  
Auth UI: header `X-Token: config.X_TOKEN`.

Constantes extraídas del script inline:

| Constante | Valor | Rol |
|-----------|------:|-----|
| `STATUS_FAST_WINDOW_MS` | 30000 | Ventana rápida tras load / refresh manual |
| `STATUS_FAST_POLL_MS` | 5000 | Intervalo en ventana rápida |
| `STATUS_RELAXED_POLL_MS` | 15000 | Online después de la ventana rápida |
| `ONU_ONLINE_POLL_MS` | 30000 | Cadencia “flat” online en otro tramo |
| `ONLINE_MAX_POLLS` | 10 | Tope online ≈ 5 min a 30 s |
| `GetOnuStatusIterationsCountdown` | 30 | Tope total de polls por carga de página |
| `ONU_OFFLINE_GRACE_SECONDS` | 180 | Offline reciente |
| `ONU_TRANSITION_REFRESH_MS` | 10000 | Offline &lt; 3 min |
| `ONU_OFFLINE_REFRESH_MS` | 30000 | Offline estable |
| `STATUS_COUNTDOWN_LABEL` | `"auto-refresh in"` | Texto UI |

Reglas (comentarios del propio JS):

1. Primeros **30 s** (o tras click en countdown): poll cada **5 s**.
2. Después, Online: **15 s** (y/ o **30 s** flat según rama).
3. Offline &lt; 3 min del cambio: **10 s**; luego **30 s**.
4. Online: máximo **10** polls (~5 min) y para.
5. Cap global: **30** polls/página (~15 min offline a 30 s) y para.
6. Señal: **primera** lectura Online → OLT live; siguientes Online →
   `?signal=db` (cache) para no hacer SNMP walk cada poll.
7. Si el tab está oculto, el countdown no avanza / no martilla.
8. Click en “auto-refresh in…” = `doManualStatusRefresh()` (reinicia ventana 5 s + contadores).

**Captura XHR real** (ONU web id 3, Online):

| t (ms) | Endpoint | dt | Query | Notas |
|-------:|----------|---:|-------|-------|
| 7116 | `get_onu_status_and_signal/3` | 452 ms | `signal=db` | Cache |
| 18423 | `status/3` | **11637 ms** | — | Get status (CLI) |
| 22399 | `get_onu_status_and_signal/3` | 281 ms | `signal=db` | Gap ≈ **15.3 s** |
| 42910 | `get_local_running_config/3` | — | — | Show running-config |
| 47821 | `show_hw_sw/3` | 4911 ms | — | SW info |

Respuesta típica status+signal:

```json
{
  "status": true,
  "onu_status_available": true,
  "onu_status": "Online",
  "last_status_change": "(1 week ago)",
  "last_status_change_unix": 1783528593,
  "onu_signal": "Warning",
  "onu_signal_value": "-24.95 dBm / -30.46 dBm (1447m)",
  "onu_signal_rx1310": -30.46,
  "signal_limit_warning": "-30",
  "signal_limit_critical": "-32",
  "distance": "1447",
  "response_code": "success"
}
```

Umbrales señal observados (OLT Rx 1310): Warning ≤ **-30 dBm**, Critical ≤ **-32 dBm**.
Categorías UI/API: `Very good` | `Warning` | `Critical` (listado dice Good/Warning/Critical).

### B) LIVE! (tarjeta señal)

- Click `#soc-live` → `startLive()`.
- Duración fija: **60 s** (`live.until = now + 60000`).
- Durante LIVE refresca más agresivo vía mismo status endpoint (código `fetch` en tarjeta).

### C) Gráficos

- Tráfico: `/traffic/onu/{id}`
- Señal histórica: `/signal/onu/{id}`
- Live graph HTML: `GET /onu/get_live_smartolt_graph/{id}` (timeout 30 s)
- UI: “Back to 24h view” / “More graphs”.

### D) Listado Configured + Unconfigured (tasks)

No refrescan el inventario ONU cada X segundos. Solo refrescan **tareas activas**:

| Constante | Valor | Cuándo |
|-----------|------:|--------|
| `ACTIVE_REFRESH_MS` | 10000 | Hay ≥1 batch/auto task running |
| `IDLE_REFRESH_MS` | 300000 | Lista de tasks vacía (5 min) |

Configured: `SmartOLTPolling.register(...)` → `/onu_batch_actions/get_active_tasks`.  
Unconfigured: `/onu/get_active_tasks_on_unconfigured`.  
Listado ONUs: `POST/GET` vía `initFilterActionsNoSubmit(..., '/onu/get_configured_list', ...)`.  
Autofind UI: `fetchOltUnconfigured('/onu/get_unconfigured_for_olt/', oltId)` al cargar (no intervalo fijo en cliente; el scan continuo lo hace el **servidor** cuando Auto actions está activo).

### E) Config mismatch

UI: “Daily Auto Configuration Check” + Manual Scan.  
Auto-fix opcional: DB config → OLT (admin-state, CATV).  
Intervalo exacto del cron cloud **no** visible en JS del browser.

### F) Poll cloud→OLT (servidor)

**No expuesto** en General settings. Inferencia:

- Status/señal de las 758 ONUs se sirven desde DB (`get_onus_statuses` / `get_onus_signals` responden en &lt;3 s) → hay job server-side periódico.
- El browser **no** es ese job; solo consume cache + lecturas on-demand en View/Get status.

---

## Endpoints UI (sesión) descubiertos

### Lectura ONU / diagnóstico

| Método | Ruta | Uso |
|--------|------|-----|
| GET | `/api/onu/get_onu_status_and_signal/{id}` | Poll status+señal (`?signal=db` opcional) |
| GET | `/api/onu/status/{id}` | Get status (HTML CLI, ~10–15 s) |
| GET | `/api/onu/get_local_running_config/{id}` | Show running-config |
| GET | `/api/onu/show_hw_sw/{id}` | SW info (vendor, versions, ports) |
| GET | `/onu/get_live_smartolt_graph/{id}` | Fragmento gráfico live |
| GET | `/traffic/onu/{id}` | Serie tráfico |
| GET | `/signal/onu/{id}` | Serie señal |
| GET | `/onu/onu_history/{id}` | Historial modal |
| GET | `/info/?onu_id={id}` | Audit log filtrado |
| POST | `/onu/get_configured_list` | Tabla Configured (filtros) |
| GET | `/onu/get_unconfigured_for_olt/{oltId}` | Autofind por OLT |
| GET | `/api/onu/fetch_unconfigured_onus_for_olt_pon/{olt}/{ponKey}` | Autofind por PON |
| POST | `/api/onu/fetch_distinct_options/{field}` | Opciones de filtros |

### Catálogos locales (AJAX)

| Ruta | Uso |
|------|-----|
| `/api/system/get_local_olts` | OLTs |
| `/api/system/get_local_onu_types_by_pon_type/{pon}` | Tipos ONU |
| `/api/olt/get_local_olt_boards/{oltId}` | Boards |
| `/api/olt/get_local_olt_board_ports/{oltId}/{board}` | Ports |
| `/api/olt/get_local_available_onu_ids/...` | ONU IDs libres |
| `/api/olt/get_capabilities/{oltId}` | Capacidades OLT |
| `/api/onu/fetch_zones` | Zones |
| `/api/onu/fetch_odbs_for_location/` | Splitters por zone |
| `/api/onu/fetch_available_ports_for_odb/` | Puertos splitter |
| `/api/onu/fetch_vlans/{oltId}` | VLANs |
| `/api/onu/fetch_vlans_mgmt/{oltId}` | VLANs mgmt |
| `/api/onu/fetch_vlans_ethernet/{onuId}` | VLANs eth |
| `/api/onu/fetch_vlans_for_extra/{oltId}` | Extra VLANs |
| `/api/onu/fetch_all_vlans_for_default/{onuId}` | Default VLAN |
| `/api/onu/fetch_mgmt_ip_addresses/` | IPs mgmt pool |
| `/api/onu/fetch_voip_profiles/{pon}/{oltId}` | SIP profiles |
| `/api/onu/is_duplicate_sn` | Validación SN |
| `/onu/is_phone_number_used` | Validación VoIP |
| `/onu/normalize_coords` | Geo |
| `/onu/validate_static_ip_config/{id}` | Validación WAN static |

### Escrituras ONU (form `action`)

| Ruta | Modal / acción |
|------|----------------|
| `/onu/change_onu_type/{id}` | Change ONU type (+ custom profile, line-profile map) |
| `/onu/update_client_external_id/{id}` | External ID |
| `/onu/update_speed_profiles_huawei/{id}` | Service-port / speeds |
| `/api/onu/update_vlan` | Ethernet port VLAN/mode |
| `/onu/update_onu_mode/{id}` | Routing/Bridging + WAN |
| `/onu/update_mgmt_ip/{id}` | Mgmt IP + TR069 |
| `/onu/move_onu/{id}` | Move board/port |
| `/onu/change_allocated_onu/{id}` | Change ONT-ID |
| `/onu/update_gpon_type/{id}` | GPON/XG/XGS + ONU ID range |
| `/onu/update_epon_type/{id}` | EPON channel |
| `/onu/update_sn/{id}` | Replace by SN |
| `/onu/update_extra_vlans/{id}` | Attached VLANs |
| `/onu/update_location_details/{id}` | Zone/ODB/name/geo |
| `/onu/enable_voip/{id}` / `disable_voip` / `clear_config_voip` | VoIP |
| `/onu/reboot/{id}` | Reboot |
| `/onu/rebuild/{id}` | Resync (= recreate OLT from DB) |
| `/onu/restore_factory_defaults/{id}` | Factory |
| `/onu/firmware_upgrade/{id}` | Firmware + reset |
| `/onu/disable/{id}` / `enable` / `stop` / `start` | Admin / O7 emergency |
| `/onu/delete/{id}` | Delete |
| `/onu/save_catv_status/{id}` | CATV |
| `/onu/save_allow_remote_access/{id}` | Remote access |
| `/onu/sync_wifi_from_tr069_cache/{id}` | WiFi TR069 |
| `/onu/wifi_section/{id}` | Sección WiFi |

### Batch / autofind / auth

| Ruta | Uso |
|------|-----|
| `/onu_batch_actions/execute` | Ejecutar batch |
| `/onu_batch_actions/get_active_tasks` | Poll tasks (10 s / 5 min) |
| `/onu_batch_actions/get_dropdown_data` | Dropdowns batch |
| `/onu_batch_actions/preview_offline_duplicates` | Twins offline |
| `/onu_batch_actions/run_delete_offline_duplicates` | Borrar twins |
| `/onu_batch_actions/preview_move_offline_after_migration` | Preview move |
| `/onu_batch_actions/run_move_offline_after_migration` | Move offline |
| `/onu_batch_actions/get_move_source_ports` | Mapear PONs |
| `/onu_batch_actions/stop` | Stop batch |
| `/onu/add_task` | Auto actions modal |
| `/onu/update_auto_task/` | Config auto actions |
| `/onu/stop_task_actions` | Stop auto |
| `/onu/get_active_tasks_on_unconfigured` | Tasks autofind |
| `/onu_authorization/authorize_with_preset` | Auth con preset |
| `/onu_authorization/continue_authorize` | Continuar auth |
| `/onu_authorization_presets/get_presets_for_apply` | Presets aplicables |
| `/onu_authorization_presets/listing` | CRUD presets UI |

### OLT settings (navegación)

`/olt`, `/olt/olt_details/{id}` + tabs: cards, PON ports, uplink, VLANs,
ONU IP pools (`.../ip_pools/mgmt|internet`), Remote ACLs, Profiles,
VoIP profiles (`.../sip_profiles`), Advanced (NTP, fans, rogue ONU),
Config backups, Cli, history.

### Plataforma

`/locations/listing` (Zones), `/odbs/listing` (Splitters), `/onu_types/listing`,
`/speed_profiles`, `/system_config` (TR069), `/general` (+ Users, Notifications,
API key, API Logs, Billing), `/events`, `/diagnostics`, `/graphs`,
`/reports/tasks`, `/reports/authorizations/list`, `/reports/export|import`,
`/config_comparison`, `/api/system/write`, `/api/export`.

---

## API pública (X-Token API key)

Base: `https://{subdomain}.smartolt.com/api/`

Límites (UI General + docs Postman):

- General: **1000**/hora, **10**/s; burst &gt;15/s bloqueado.
- Heavy OLT detail: **30**/10 min por OLT (`get_olt_cards_details`,
  `get_olt_pon_ports_details`, `get_olt_uplink_ports_details`).
- `get_all_onus_details` sin `page`: export completo, **15**/hora.
- Con `page`/`page_size` (1–100): cuenta al presupuesto general.
- Sync incremental: `updated_since=` en details; status/señal **no** lo tocan.

Endpoints verificados en esta instancia:

| Endpoint | n (olt 2) | Rol |
|----------|----------:|-----|
| `GET onu/get_onus_statuses?olt_id=2` | 758 | Status + last_status_change |
| `GET onu/get_onus_signals?olt_id=2` | 758 | signal + 1310/1490 |
| `GET onu/get_all_onus_details?page=1` | 100/page, 81 campos | Inventario |
| `GET onu/get_onu_details/{external_id}` | 1 | Detalle |
| `GET onu/get_onu_full_status_info/{external_id}` | 1 | Snapshot CLI JSON/HTML |
| `GET onu/unconfigured_onus` | — | Autofind |

Clave API: `unique_external_id` (aquí = SN). El id web `/onu/view/3` **no** sirve en API.

Campos `get_all_onus_details` (81): ver lista en captura; incluye
`is_synced_after_import`, `is_failed_resync_config`, `service_ports`,
`ethernet_ports`, `wifi_ports`, `voip_ports`, mgmt/voip/iptv nested, etc.

---

## Flujos de negocio clave

### Autorización

1. Autofind (servidor + UI Unconfigured) → SN no provisionado.
2. Manual o **Authorization preset** (condiciones: OLT/board/port/SN pattern/ONU type;
   settings JSON: mode, VLAN, speeds, zone, TR069, mgmt, WiFi…).
3. `authorize_with_preset` / authorize manual → escribe OLT + fila DB.
4. Report: `/reports/authorizations/list`.

### Resync

- UI: “Resync config” → `/onu/rebuild/{id}`.
- Texto modal: *reset OLT config and rebuild from SmartOLT database (~30 s)*.
- Banner import: `is_synced_after_import=0` → pedir Resync antes de editar.
- Mismatch diario puede auto-fix DB→OLT.

### Move / twins

- Move: cambia board/port; opción `move_to_default_vlan` si VLAN PON ≠ main VLAN.
- Twins: offline duplicate con twin Online; o missing from OLT; opción
  “Refresh live status first on OLTs with no recent status data”.

### Get status vs poll

| | Poll status+signal | Get status |
|--|-------------------|------------|
| Endpoint | `get_onu_status_and_signal` | `status` |
| Latencia | ~0.3–0.5 s (DB) | ~10–15 s (CLI) |
| Contenido | status, dBm, distance, limits | Óptica + details + history up/down + LAN + MACs |
| Automático | Sí (cadencia arriba) | Solo botón |

---

## Acciones → efecto (mapa para clonar)

| Acción UI | Efecto DB | Efecto OLT | Task |
|-----------|-----------|------------|------|
| Edit location/type/VLAN/speeds… | Update config deseada | No hasta Resync (si importado) / inmediato según acción | a menudo sí |
| Resync / Rebuild | — | Reaplica DB→OLT | resync |
| Reboot / Disable / Stop | Flags admin | Comando OLT | yes |
| Authorize | Insert ONU | Provision | authorize |
| Delete | Soft/hard delete | Remove ONT | delete |
| Save config | — | `save` startup OLT | save_config |
| Batch | N updates | N comandos | batch_* |

---

## Implicaciones para reemplazo propio

1. **Separar** jobs server (status/signal/autofind) del poll del browser (solo UX).
2. Replicar cadencia View ONU solo en UI detail; no martillar OLT a 5 s para 758 ONUs.
3. Status list: endpoint liviano tipo `get_onus_statuses` (DB).
4. Details: paginado + `updated_since`; señales aparte.
5. Toda escritura → `task` + `audit_log`.
6. Umbrales señal configurables (default -30 / -32 dBm Rx OLT).
7. `SmartOLTPolling`-like: pausar UI polls con tab oculto.
8. External id = clave API; web id interno opcional.

---

## Script de hook (referencia)

Inyectado vía CDP `Runtime.evaluate` / `Page.addScriptToEvaluateOnNewDocument`:

- Parchea `XMLHttpRequest.open/send` → `window.__smartoltRE.xhr[]`
- Parchea `setInterval` / `setTimeout(≥1s)` → `timers[]`
- Campos: `{t, dt, method, url, status, body, respPreview}`

Para reutilizar: instalar hook → navegar/esperar →
`JSON.stringify(window.__smartoltRE)`.

---

## Límites de lo no observable desde el browser

- Intervalo exacto del worker cloud que rellena statuses/señales de las 758 ONUs.
- Implementación interna SSH vs SNMP (solo indicios: comentarios “SNMP walk”,
  Get status ~12 s = CLI, status poll 0.3 s = DB).
- Cola interna de tasks y prioridad.
- Reglas exactas del “dead-OLT guard” server-side (mencionado en comentarios JS).

Esos puntos requieren logs del servidor SmartOLT o instrumentar la OLT (sesiones SSH)
mientras corre el cloud — fuera de alcance de esta captura UI/API.
