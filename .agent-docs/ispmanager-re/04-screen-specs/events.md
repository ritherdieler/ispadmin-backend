# Ficha: Events

| | |
|--|--|
| Ruta | `/events` (título “Network Events”) |
| Captura | Browser 2026-07-17 — vacío: “No network events yet.” |

## Filtros UI

| Control | Valores |
|---------|---------|
| Search | OLT, source, rule, message |
| OLT | All OLTs \| `2 - HAWEI` |
| Type | (checkbox) PON outage |
| Severity | All \| Critical (`danger`) \| Warning \| Info |
| Status | Active \| History \| All |
| From / To | fechas |
| Sort | Newest \| Oldest \| Severity \| Source |

## Contadores

Total | Critical | Warning | PON outages (todos 0 en captura).

## Columnas tabla

Time | Severity | Type | Status | Source | Event

## Modelo (desde DB pack)

| Campo | Valores |
|-------|---------|
| severity | critical \| warning \| info |
| event_type | pon_outage \| olt_unreachable \| onu_flood… |
| status | active \| resolved |
| source | ej. `gpon 0/1/0` |

## IspManager

Generar eventos desde jobs `olt_health_poll` / outage PONs API.
