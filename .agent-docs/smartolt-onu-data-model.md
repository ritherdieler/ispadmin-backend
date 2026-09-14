# Modelo de datos ONU (SmartOLT) — inventario para diseño

Fuente: UI `https://gigafiberperu.smartolt.com/onu/view/{webId}` + API
`onu/get_onu_details/{unique_external_id}` y `onu/get_onu_full_status_info/{unique_external_id}`.

Ejemplo revisado: **JEINERALVARADO** → web id `3`, SN / external id `TPLG2A561D98`.

Relacionado: [olt-gateway-read-mvp.md](./olt-gateway-read-mvp.md).

## Separación conceptual (importante para el diseño)

SmartOLT no guarda un único blob. Hay **tres capas**:

| Capa | Qué es | Persistencia | Caducidad |
|------|--------|--------------|-----------|
| **A. Inventario / config deseada** | Lo que el operador quiere en DB | DB SmartOLT | Estable; cambia con authorize / edit / resync |
| **B. Telemetría live** | Status, señal, tráfico, distancia | Cache + series/gráficos | Segundos–minutos; poll continuo |
| **C. Snapshot CLI OLT** | Output crudo Huawei (Get status) | Bajo demanda | Instantánea; no es el modelo de negocio |

El listado *Configured ONUs* pinta **A + B cacheada**. *View ONU* mezcla A + B + acciones que escriben A→OLT. *Get status* es **C**.

El `id` de la URL (`/onu/view/3`) es **solo web**. La API usa `unique_external_id` (aquí = SN).

---

## A. Inventario / config deseada (guardar en DB propia)

### Identidad y posición PON

| Campo UI | API | Notas |
|----------|-----|--------|
| OLT | `olt_id`, `olt_name` | FK a catálogo OLT |
| Board | `board` | Slot Huawei |
| Port | `port` | Puerto PON |
| ONU | `onu` | ONT-ID en el puerto |
| GPON channel | `pon_type`, `gpon_channel` | gpon / xg / xgs |
| SN | `sn` | Clave física |
| ONU external ID | `unique_external_id` | Clave lógica API; suele = SN |
| ONU type | `onu_type_id`, `onu_type_name` | Catálogo + modo mapping |
| Custom / Line profile | (modales Change ONU type) | Relación a profiles OLT |

### Ubicación / CRM ligero

| Campo UI | API | Notas |
|----------|-----|--------|
| Zone | `zone_id`, `zone_name` | Catálogo Zones |
| Splitter / ODB | `odb_name` | UI: Splitter; API: odb |
| Splitter port | (modal location) | |
| Name | `name` | Nombre cliente / etiqueta |
| Address or comment | `address` | |
| Contact | `contact` | |
| Latitude / Longitude | `latitude`, `longitude` | |

En OLT Huawei a menudo se embebe parte en `description` (ej. `NAME_zone_Zone1_authd_...`); SmartOLT lo separa en columnas.

### WAN / modo de servicio

| Campo UI | API |
|----------|-----|
| Attached VLANs | `vlan` (+ listas en modal) |
| ONU mode | `mode` (Routing / Bridging) |
| WAN setup mode | `wan_mode` |
| WAN IP / mask / gw / DNS | `ip_address`, `subnet_mask`, `default_gateway`, `dns1`, `dns2` |
| PPPoE user/pass | `username`, `password` |
| Config method | OMCI / TR069 (modales) |

### Mgmt / TR069 / VoIP / IPTV / CATV

| Campo UI | API |
|----------|-----|
| TR069 Profile | `tr069`, `tr069_profile`, `tr069_device_id` |
| Mgmt IP mode + IP/VLAN… | `mgmt_ip_*` |
| VoIP | `voip_*`, `voip_ports[]` |
| IPTV | `iptv_*` |
| CATV | `catv` |
| Administrative status | `administrative_status` |

### Service ports (speed profiles)

Tabla UI *Speed profiles* ↔ `service_ports[]`:

- `service_port`, `vlan`, `cvlan`, `svlan`, `tag_transform_mode`
- `upload_speed`, `download_speed`

### Ethernet / WiFi (config deseada)

`ethernet_ports[]`: `port`, `admin_state`, `mode` (LAN/Access/Hybrid/Trunk/Transparent), `dhcp`, `vlan`, `allowed_vlans`.

`wifi_ports[]` (vacío en el ejemplo).

### Flags de sync / import

| Campo | API | UI |
|-------|-----|-----|
| Importado sin resync | `is_synced_after_import` | Banner: *settings were imported… use Resync* |
| Resync fallido | `is_failed_resync_config` | Filtro listado *Resync failed* |
| Authorization date | `authorization_date` | Link *History* (auditoría, no solo fecha) |

### Historial de acciones (DB)

Modal History (ejemplo):

| Action | User | IP | Date |
|--------|------|-----|------|
| Imported from OLT config | (vacío) | 207.246.72.245 | 24-Feb-2023 19:33 |

Es **audit log** de la DB SmartOLT, distinto del historial up/down óptico de la OLT.

### Acciones que escriben config (no son datos, pero definen el modelo)

Reboot, Resync config (= recreate OLT from DB), Restore defaults, Disable/Enable ONU, Delete, Move, Change ONU ID, Replace by SN, Configure speeds/ports/VLANs/location/mode.

---

## B. Telemetría (poll; no tratar como config)

| Campo UI | API | Notas |
|----------|-----|--------|
| Status | `status` | Online / Offline / LOS / Power Fail / Admin Disabled |
| Last status change | `last_status_change` | |
| Signal category | `signal` | Good / Warning / Critical |
| ONU Rx 1490 | `signal_1490` | dBm |
| OLT Rx 1310 | `signal_1310` | dBm |
| Distance | `distance` | metros |
| Traffic graphs | (solo UI / series) | current/max upload-download; no en get_onu_details |
| Auto-refresh | UI | “Online (1 week ago) auto-refresh…” |

Endpoint dedicado de statuses recomendado para sync incremental (status cambia mucho; config poco).

---

## C. Snapshot CLI (Get status / full_status)

`get_onu_full_status_info` — útil para diagnóstico, **no** como esquema primario de inventario.

Óptica: Rx/Tx ONU, temp, OLT Rx, CATV Rx.

Estado ONT: control flag, run state, **match state** (ej. mismatch), distance, SN hex+vendor, management mode, description, last down cause, last up/down/dying-gasp, online duration, line/srv profile names, FEC, QoS, service mapping, ports definidos, loopback, multicast.

History up/down OLT: auth time, offline time, down reason.

LAN link state live (speed/duplex/link) — distinto de config ethernet.

MACs en OLT: service port, MAC, VLAN.

También UI: Show running-config, SW info, LIVE! (sesión live).

---

## Catálogos globales (Settings) que referencian la ONU

No viven en la fila ONU, pero el diseño debe tenerlos:

- Zones, ODBs/Splitters, ONU types, Speed profiles, OLTs, VPN & TR069, Authorization presets.

---

## Implicaciones para olt-gateway / inventario propio

1. **Hoy el gateway solo tiene capa C parcial** (`display ont info` → frame/slot/port/ontId/sn/run/config/match/description) + óptica puntual. No tiene zone/type/VLAN/speeds/name separados.
2. **Para reemplazar SmartOLT UI** hace falta DB propia de capa **A** + jobs de poll capa **B** + CLI on-demand capa **C**.
3. Claves recomendadas: `sn` (única física), `unique_external_id` (API), posición `(olt_id, board, port, onu)`.
4. Parsear `description` OLT puede hidratar name/zone/authd de forma barata, pero zone/type/VLAN “de negocio” deben vivir en DB.
5. `is_synced_after_import` / mismatches DB vs OLT son features de producto, no de la OLT sola.
6. El web id numérico de `/onu/view/N` **no** debe usarse como clave de integración.

## Ejemplo compacto (JEINERALVARADO)

```text
A: olt=2 HAWEI, 0/1/0 ont=2, sn=TPLG2A561D98, type=XN020-G3v,
   zone=Zone 1, name=JEINERALVARADO, mode=Routing, vlan=1,
   wan=Setup via ONU webpage, mgmt/tr069=Inactive,
   sp=58 vlan1 1G/1G, eth_0/1+0/2 Enabled LAN,
   imported (is_synced_after_import=0), history Import 2023-02-24

B: Online, signal Warning, rx1490=-24.95, rx1310≈-30.5, dist=1447m,
   traffic ~0.1/3 Mbps

C: match=mismatch, dying-gasp history, line-profile_10, srv-profile_10,
   LAN links down, MAC e4:c3:2a:56:1d:9f on sp 58 vlan 1
```
