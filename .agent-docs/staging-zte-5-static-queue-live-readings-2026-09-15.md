# Staging #5 — STATIC_IP real, live-readings `QUEUE` (2026-09-15)

In-place. No alta FIBER oficial. Recolección lab no se apagó (`SERVICE_HEALTH_ENABLED=true`, `#5` `identity.lab=true`, ACS `last_inform` FRESH).

## Resultado

| Campo | Valor |
|-------|--------|
| Id | `#5` EEEFIBER PRUEBAZTE |
| ONU | `ZTEGDC47BFFD` / `5872C9-F6600R-ZTEGDC47BFFD` tag `lab` |
| `accessMode` | `STATIC_IP` |
| IP | `192.168.250.20` (pool MK2 `192.168.250.1/24`) |
| Cola | `*8A2` `[stg] id:5, … tipo:FIBER` `target=192.168.250.20/32` `200M/200M` |
| `/ppp/active` `gf5` | no hay |
| `<pppoe-gf5>` | no hay |
| Secret `gf5` | borrado (`*103`) |
| WAN ACS | `WANIPConnection.2` Static `192.168.250.20` VLAN 100 `Connected` |
| WAN mgmt | `WANIPConnection.1` `192.168.255.236` (no tocada) |
| `WANPPPConnection.2` | `Enable=false` `Disconnected` |

## Poll `GET /subscription/5/live-readings`

`source=QUEUE` `pppoe=null` `available=true`.

| Momento | downloadBps | uploadBps | rxBytes | txBytes |
|---------|-------------|-----------|---------|---------|
| Idle previo (cola ya existía) | 0 | 0 | 91120 | 91596 |
| Tras ping 20× (0% loss, ~2.8 ms) | cola `336/336` | | 92240 | 92716 |
| Tras ping 40× size 1400 (0% loss) | cola `11200/11200` | | 148240 | 148716 |
| API ~22:12Z (burst ya cortó) | 0 | 0 | 148240 | 148716 |

Los bps del API son el `rate` RouterOS en el instante del GET; con el ping ya terminado quedan 0/0. Los bytes de la cola sí subieron. ARP `192.168.250.20` MAC `58:72:C9:2F:4D:F8` en `vlan100-olt`.

## Cómo se aplicó la WAN

SPV NBI monolítico y `GfApplyInternetStatic` no commiteaban en el Inform (canal wifi/interval ocupado; F6600R corta sesión si el batch lleva `Enable`). Provision de un solo uso con `commit()` por pasos (hojas, luego `Enable`), disparado por task NBI + connection request. Borrado al terminar.

Se desplegaron los VirtualParameters `Gf*` en GenieACS staging (la lista NBI estaba vacía). No se reinició Tomcat. Recolección lab intacta.
