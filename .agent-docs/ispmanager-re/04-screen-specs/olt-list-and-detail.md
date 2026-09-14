# Ficha: OLTs list + detail tabs

| | |
|--|--|
| Rutas | `/olt`, `/olt/olt_details/{oltId}` |
| Ejemplo | olt_id `2` HAWEI MA5608T |

## Listado

Columnas API `system/get_olts`: id, name, hardware, ip, telnet_port, snmp_port.  
Health: `olt/get_olts_uptime_and_env_temperature` (uptime, env_temp).

## Tabs detail

| Tab | Fuente lectura | Notas |
|-----|----------------|-------|
| Cards | `GET system/get_olt_cards_details/{id}` | slot, type, ports, sw, status, role |
| PON ports | `GET system/get_olt_pon_ports_details/{id}` | counts, avg signal, TX, range, autofind |
| Outage PONs | `GET system/get_outage_pons/{id}` | |
| Uplink | `GET system/get_olt_uplink_ports_details/{id}` | |
| VLANs | `GET olt/get_vlans/{id}` + UI add | sample vlan 1 scope internet |
| IP pools mgmt/internet | UI AJAX | PARCIAL |
| Remote ACLs | UI | N/A re-captura |
| Profiles | UI import line/srv | N/A |
| VoIP SIP | UI | N/A |
| Advanced | NTP, fans, rogue | N/A |
| Backups / CLI / History | UI | N/A |

## Sample cards (live)

Slots 0 H805GPFD (16p), 1 H806GPFD (16p), 3 H801MCUD1 Main R015, 4 H801MPWD.

## IspManager MVP

List + cards + PON + uptime; resto de tabs P2.
