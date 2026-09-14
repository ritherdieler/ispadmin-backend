# Ficha: Graphs

| | |
|--|--|
| Ruta UI | `/graphs` → `?olt_id=2&graph_type=olt` |
| Captura | Browser 2026-07-17 |

## Tabs “Graphs for”

| Tab | `graph_type` |
|-----|--------------|
| OLT | `olt` |
| Uplink | `uplink` |
| PON | `pon` |
| Traffic | `traffic` |
| Signal | `signal` |

Filtro: OLTs (Any | `2 - HAWEI`).

## OLT tab — imágenes PNG observadas

| Endpoint |
|----------|
| `/graphs_olt/get_daily_env_temp_for_olt/{oltId}/small` |
| `/graphs_olt/get_daily_fan_speed_for_olt/{oltId}/small` |
| `/graphs_olt/get_daily_mac_for_olt/{oltId}/small` |
| `/graphs_olt/get_daily_for_board_cpu/{oltId}/{slot}/small` (slots 0,1,3,4) |
| `/graphs_olt/get_daily_for_board_mem/{oltId}/{slot}/small` |

## API ONU (verificado antes)

| Endpoint | Response |
|----------|----------|
| `GET onu/get_onu_traffic_graph/{external_id}/{graph_type}` | `image/png` 484×190 |
| `GET onu/get_onu_signal_graph/{external_id}/{graph_type}` | `image/png` 484×190 |

`graph_type` API ONU: `hourly` \| `daily` \| `weekly` \| `monthly` \| `yearly`.

UI View ONU: `/traffic/onu/{id}`, `/signal/onu/{id}`, `get_live_smartolt_graph`.

## IspManager

Preferir series JSON propias (`onu_metric_sample`) + chart client; PNG solo compat.
