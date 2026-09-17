# MikroTik solo detrás de Traffic (2026-09-16)

Traffic es el único runtime que abre RouterOS. Core, NetDiag y la consola de equipo no inyectan `MikrotikClient` ni `RouterOs7RestAdapter`. Lecturas y escrituras van por HTTP a Traffic (`X-Traffic-Key`). ACS, OLT y Genie no abrían MikroTik y no se rediseñaron. Live e histórico 360 no cambiaron de contrato. `traffic.poll.enabled` sigue en `true`.

Parent: `af7c810` (live-readings Core→Traffic). Este commit no pisa esa fachada.

## Inventario que se sacó de Core

- `MikrotikClientAccessor` + `NetworkDeviceConnectionConfig` que cableaba el adapter REST.
- `MikroTikConnectionService` (consola, filters, address-list) ya no usa `MikrotikClient`.
- `NetworkDeviceConnection.executeCommand` (altas, colas, PPPoE secret, cortes, IP) abre `TrafficRouterOsSession`.
- Bean `netDiagMikrotikClient` (`RouterOs7RestAdapter`) en NetDiag.
- `MikrotikPollAdapter` ahora pide sesión a `RouterOsSessionFactory` (Traffic HTTP).

Shared sigue siendo la librería de transporte (`RouterOs7RestAdapter`). El bean `trafficPollMikrotikClient` vive para Traffic (poll, live 360, gateway). Si Core y Traffic comparten JVM, ese bean existe para los servicios de Traffic, no para dominio Core.

## Gateway RouterOS en Traffic

`RouterOsCommandUseCase` (`Result` + `runCatching`) resuelve `TrafficRouter` por `hostDeviceId` y ejecuta print/add/set/remove/call.

| Método | Ruta | Uso |
|--------|------|-----|
| POST | `/api/traffic/v1/routeros/{hostDeviceId}/print` | Lectura de path RouterOS |
| POST | `/api/traffic/v1/routeros/{hostDeviceId}/add` | Alta (cola, secret, address-list, profile, filter) |
| POST | `/api/traffic/v1/routeros/{hostDeviceId}/set` | Update (secret, address-list, filter, profile) |
| POST | `/api/traffic/v1/routeros/{hostDeviceId}/remove` | Baja (cola, secret, sesión PPPoE activa, address-list) |
| POST | `/api/traffic/v1/routeros/{hostDeviceId}/call` | Comandos (`/interface/monitor-traffic`) |

Core: `TrafficRouterOsCommandUseCase` + `TrafficRouterOsSession`. Domain (`IMikroTikService`, `PppoeManagerService`, `IQueueManager`) no se rediseñó: recibe la misma `MikrotikSession`, ahora HTTP-backed.

## Lecturas Traffic que ya existían (sin romper)

| Método | Ruta |
|--------|------|
| GET | `/api/traffic/v1/by-subscription/{id}/live-readings` |
| GET | `/api/traffic/v1/by-subscription/{id}/latest` |
| GET | `/api/traffic/v1/by-subscription/{id}/series` |
| GET | `/api/traffic/v1/by-subscription/{id}/summary` |
| GET | `/api/traffic/v1/by-subscription/{id}/today` |
| GET | `/api/traffic/v1/by-subscription/{id}/day` |
| GET | `/api/traffic/v1/by-ip/{ip}/latest` |
| GET | `/api/traffic/v1/by-ip/{ip}/series` |
| GET | `/api/traffic/v1/network` `/overview` `/series` `/sources` `/subscriptions` `/subscriptions/{id}` |
| GET | `/api/traffic/v1/network/hourly-profile` `/daily-trend` `/insights` |
| GET | `/api/traffic/v1/anomalies` `/anomalies/changes` `/routers/{id}/latest-run` `/config` |
| POST | `/api/traffic/v1/admin/poll` `/admin/aggregation/catch-up` |

Fachada pública 360: `GET /subscription/{id}/live-readings` sigue igual.

## Grep

En `core/src/main`, `oltgateway/src/main` y `acs/src/main` no hay `MikrotikClient`, `RouterOs7RestAdapter` ni `MikrotikClientAccessor`. Los únicos `MikrotikClient` de runtime están en Traffic (live-readings, poll, live monitor, `RouterOsCommandUseCase`).

## Tests

```
./gradlew :shared:test :traffic:test :core:test
```

BUILD SUCCESSFUL. shared 83 (5 skipped), traffic 106, core 1568. Cero failures.
