# MK2 — nombres canónicos de interfaces

Vigente **2026-09-10**. El router único de abonados es **MK2** (`38.224.231.4`, `network_device` id **8**). MK1 ya no existe.

**VLAN 1 en desuso:** el dominio `vlan1-olt` / `LAN-VLAN1` es parque legado. El destino es llevar el internet de abonado a la VLAN 100. [vlan1-desuso-destino-vlan100.md](./vlan1-desuso-destino-vlan100.md).

El bridge se llamaba `LAN_MK1` por herencia del cutover MK1→MK2. Ese nombre daba a entender un enlace hacia otro router. Se renombró en caliente a **`LAN-VLAN1`** (IDs internos iguales: ARP, PPPoE, firewall y mangle siguieron el rename).

## Mapa vivo

```text
 MK2 CCR2116
 ├─ sfp-sfpplus1  WAN físico
 │   └─ WAN-VLAN SFP-SFPPLUS1   VLAN 450 proveedor
 ├─ sfp-sfpplus2  OLT 0/3/2 (transporte tagged)
 │   ├─ vlan100-olt            termina VLAN 100  internet abonado
 │   └─ vlan1000-olt           termina VLAN 1000 gestión CPE
 ├─ sfp-sfpplus3  OLT 0/3/3 (transporte tagged)
 │   └─ vlan1-olt ──► bridge LAN-VLAN1
 │                    + ether5 switch SFP
 │                    + ether7 AirFiber 9 Octubre
 │                    + PPPoE
 ├─ ether3        ADMINISTRACION OLT  10.11.104.89/24
 └─ ether12 ──► bridge LAN            192.168.111.0/24 local
```

| Nombre | Tipo | Qué es | No es |
|--------|------|--------|--------|
| `vlan100-olt` | VLAN 100 sobre `sfp-sfpplus2` | Terminación tagged de la VLAN 100 | El puerto físico |
| `vlan1000-olt` | VLAN 1000 sobre `sfp-sfpplus2` | Terminación tagged de la gestión de CPE (`10.20.0.1/22`, `10.20.250.1/24`) | Tráfico de internet del abonado |
| `vlan1-olt` | VLAN 1 sobre `sfp-sfpplus3` | Terminación tagged del **parque legado** VLAN 1. En desuso gradual | El destino de abonado (eso es `vlan100-olt`) |
| **`LAN-VLAN1`** | Bridge | Dominio L2 de VLAN 1: OLT + switch SFP + AirFiber + PPPoE. **29 gateways** | Un enlace a MK1 |
| `LAN` | Bridge | Solo `ether12` + `192.168.111.1/24` | Clientes GPON |
| `OLT-VLAN100` | Interface list | `sfp-sfpplus2` + `vlan100-olt` (firewall/mangle VLAN 100) | VLAN 1 |
| `OLT-VLAN1000` | Interface list | Solo `vlan1000-olt`. El puerto físico queda fuera a propósito: lo comparte con la VLAN 100 | Un dominio L2 propio |
| `WAN-VLAN SFP-SFPPLUS1` | VLAN 450 | WAN Tarazona | Uplink OLT |

## Rename 2026-09-10

| Antes | Ahora |
|-------|--------|
| `LAN_MK1` | **`LAN-VLAN1`** |

Comentarios de puerto alineados:

| Puerto | Comment |
|--------|---------|
| `sfp-sfpplus2` | `OLT 0/3/2 uplink 10G VLAN 100 tagged` |
| `sfp-sfpplus3` | `OLT 0/3/3 uplink 10G VLAN 1 tagged` |

Scripts `/system script` `vlan1-swap` / `vlan1-rollback` buscan `bridge=LAN-VLAN1`.

## VLAN 1000 (2026-09-10)

`sfp-sfpplus2` transporta ahora dos VLANs tagged: la **100** de internet y la **1000** de gestión de CPE. Cada una termina en su subinterfaz; el native del puerto en la OLT sigue siendo la 101 de aparcamiento. Detalle: [vlan1000-gestion-cpe.md](./vlan1000-gestion-cpe.md).

## Por qué VLAN 100 no está en un bridge

`vlan100-olt` no tiene ningún otro miembro L2: es tráfico puramente ruteado. `LAN-VLAN1` existe porque su dominio incluye `ether5` y `ether7`. La asimetría es deliberada: [olt-mk2-uplinks-decision-2026-09-10.md](./olt-mk2-uplinks-decision-2026-09-10.md).

## Qué no se tocó

- Nombre WAN con espacios (`WAN-VLAN SFP-SFPPLUS1`): NAT y rutas lo usan; rename aparte.
- Identity RouterOS `CCR2116 NUEVO`.
- Puertos `ether`/`sfp-sfpplus*` (nombres de hardware).
- VLANs 101/102: solo native de aparcamiento **en la OLT**, no hay interfaz en MK2.

## Docs históricos

Textos anteriores a este rename pueden seguir diciendo `LAN_MK1`. Ese era el nombre de **este mismo** bridge en MK2, no un equipo MK1.
