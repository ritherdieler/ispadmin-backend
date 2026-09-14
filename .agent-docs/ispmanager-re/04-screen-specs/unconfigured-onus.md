# Ficha: Unconfigured ONUs (autofind)

| | |
|--|--|
| Ruta | `/onu` tab Unconfigured |
| Objetivo | SN detectados en OLT sin fila provisionada |
| Datos | `unconfigured_onu` |

## Comportamiento

- Al cargar: `fetchOltUnconfigured('/onu/get_unconfigured_for_olt/', oltId)` (UI).
- API pública: `GET onu/unconfigured_onus`, `..._for_olt/{olt_id}`.
- Scan continuo: **servidor** si Auto actions activo (no intervalo fijo en cliente).
- Tasks: `/onu/get_active_tasks_on_unconfigured` (10s/5min).

## Columnas / campos API

`pon_type`, `board`, `port`, `onu`, `sn`, ONU type name/id, `olt_id`, `disabled`, `possible_actions`: view | resync_config | authorize | move_here.

## Acciones

- Authorize (manual o preset)
- Save for later (board/port vacíos en API authorize)
- Move here / resync_config (writes — no ejecutar en RE)

## Sample live

2026-07-17: `response: []` (sin pendientes).

## IspManager

Polling autofind vía gateway CLI `display ont autofind all` + tabla; authorize crea task.
