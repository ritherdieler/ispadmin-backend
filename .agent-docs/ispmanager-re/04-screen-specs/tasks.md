# Ficha: Tasks

| | |
|--|--|
| Ruta | `/reports/tasks` (nav “Tasks”; `/tasks` → 404) |
| Captura | Browser 2026-07-17 — “No info available” |

## Filtros UI

| Control | Valores |
|---------|---------|
| OLT | Any \| `2 - HAWEI` |
| User | Any \| emails \| `API` \| `System` |
| Action | Any \| Auto-Resync (`resync`) \| AUto-Move (`move`) \| Auto-Finalize (`finalize`) |
| From / To | fechas |

## UI extra

Modal/heading “Restart Auto-Authorize Task” → path JS `/onu/restart_auto_authorize_task/`.

## Tipos (DB / UI extendido)

authorize, resync, auto_resync, move, auto_move, finalize, reboot, disable/enable, stop/start, delete, restore_defaults, firmware_upgrade, save_config, autofind_scan, status_poll, signal_poll, mismatch_scan, batch_*

## Columnas esperadas (vacío en captura)

id | type | onu/sn | olt | status | requested_by | created | started | finished | error

## Poll UI relacionado

Configured/Unconfigured refrescan tasks activas 10s / idle 5min — no la página Reports necesariamente.

## IspManager

Toda escritura → task; UI Tasks = source of truth operativa.
