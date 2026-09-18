# Inventario WAN TR-069 — ZTE / VSOL (2026-09-18)

Fuente: GenieACS NBI prod (caché, no GPV live) + MK2 REST `38.224.231.4`.

## Parque

| Familia | N | Notas |
|---|---|---|
| VSOL `V2804AX15T` (HWTC/VSOL/Realtek) | 254 | Casi todas 2 WAN |
| ZTE `F6600R` | 56 | Mismo WCD, dos `WANIPConnection` |
| TP-Link | 90 | Fuera de oleada |
| Huawei HG8145X6 | 1 | Fuera |

ZTE+VSOL = **310**. CR host coincide con `ExternalIPAddress` de una WAN en **310/310**. Cero WAN con `ServiceList` TR069+INTERNET a la vez.

## Layout dominante (invertido)

| N | Fabricante | WAN TR-069 | WAN internet | Mismo WCD |
|---|---|---|---|---|
| 235 | VSOL | `WCD.2.WANIP.1` | `WCD.1.WANIP.1` | No |
| 38 | ZTE | `WCD.1.WANIP.2` | `WCD.1.WANIP.1` | Sí |
| 14 | ZTE | `WCD.1.WANIP.1` | `WCD.1.WANIP.2` | Sí (canónico del script actual) |
| 8 | VSOL | `WCD.1` | `WCD.2` | No (canónico) |

`gf-pppoe-wan2-poc` asume el canónico. Sobre el dominante **borra la WAN del ACS**.

`ServiceList` / VLAN / `AddressingType` casi no están en caché (18/310 con ServiceList). No usar como llave. Un VSOL lab tiene `ServiceList=INTERNET` en la WAN cuyo IP es `10.20.0.2`.

## DHCP MK2 (ya exclusivo TR-069)

| Servidor | Interfaz | Pool | Leases bound |
|---|---|---|---|
| `dhcp-provisioning-255` | `vlan100-olt` | `192.168.252.2–255.254` | 264 |
| `dhcp-mgmt-1000` | `vlan1000-olt` | `10.20.0.2–3.254` | 137 |

401 leases en MK2, **todos** en esos dos pools. El internet del abonado no sale de este DHCP.

CR ZTE+VSOL: 257 en `192.168.252.0/22`, 50 en `10.20.0.0/22`, 3 fuera (manual).

No crear un tercer DHCP. Identificar por CR + membresía de esos pools.

## Contrato del script de migración

1. WAN de gestión = `ExternalIP == host(ConnectionRequestURL)` **o** IP en `10.20.0.0/22` / `192.168.252.0/22`.
2. Esa WAN no se toca.
3. DeleteObject del resto.
4. AddObject `WANPPP` (internet VLAN 100). COMPLETE solo si IP `10.64.*` y el CR sigue en la WAN conservada.

Mover el parque de `192.168.252/22` a VLAN 1000 es aislamiento de plano, no prerequisito del script.
