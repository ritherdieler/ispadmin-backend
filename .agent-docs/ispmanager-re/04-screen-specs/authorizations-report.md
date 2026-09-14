# Ficha: Authorizations report

| | |
|--|--|
| Ruta | `/reports/authorizations/list` |
| Estado | PARCIAL |

## Columnas esperadas

sn | pon_type | olt | board/port | preset | user | source (ui\|api\|auto) | authorized_at

Export CSV alineado a `authorization_log`.

## Flujo

Autofind → preset/manual → authorize → fila aquí.
