# Ficha: View ONU

| | |
|--|--|
| Ruta | `/onu/view/{webId}` |
| Ejemplo | webId `3` ↔ external `TPLG********` |
| Objetivo | Detalle A+B+acciones; C on-demand |

## Secciones UI

1. Identidad PON: OLT, board/port/onu, SN, type, external id
2. Status + last change + countdown auto-refresh
3. Señal (Rx 1490 / OLT 1310) + LIVE! (60s) + umbrales
4. Tráfico (current/max) + links graphs 24h
5. Location: zone, splitter, name, address, geo
6. WAN/mode/VLAN/speeds/service-ports
7. Ethernet / WiFi / VoIP / CATV / TR069 / mgmt
8. Acciones: Get status, running-config, SW info, reboot, resync, disable, delete, move…

## Endpoints clave

Ver [../02-api-contracts.md](../02-api-contracts.md) §4.1 y [../../smartolt-reverse-engineering.md](../../smartolt-reverse-engineering.md).

Poll status+signal: cadencia en [../03-polling-and-jobs.md](../03-polling-and-jobs.md).

## Banner import

Si `is_synced_after_import=0`: pedir Resync antes de editar.

## IspManager

UDF: state machine Online/Offline; no martillar OLT; Get status = botón.
