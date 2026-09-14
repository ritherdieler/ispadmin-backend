# MikroTik CCR2 (38.224.231.4) — uplink OLT

> Hub: [infra-red-multi-mikrotik-gigafiber.md](./infra-red-multi-mikrotik-gigafiber.md)

Configuración aplicada **2026-07-19** para enlace OLT **0/3/2** → **`sfp-sfpplus2` (10G DAC)**.

## Topología física

| OLT | MK2 | Estado |
|-----|-----|--------|
| `0/3/2` | `sfp-sfpplus2` → `vlan100-olt` | VLAN 100 **tagged** desde 2026-09-10 (native del puerto: 101) |
| `0/3/3` | `sfp-sfpplus3` → `vlan1-olt` → bridge `LAN-VLAN1` | VLAN 1 **tagged** desde 2026-09-10 (native del puerto: 102) |

> **Un dominio por puerto, sin bridge unificado** — decisión evaluada y medida el 2026-09-10: [olt-mk2-uplinks-decision-2026-09-10.md](./olt-mk2-uplinks-decision-2026-09-10.md).

> Desde 2026-09-10 **ambas VLANs son responsabilidad del MK2**: la OLT ya no entrega nada untagged. Cada puerto usa una VLAN vacía de aparcamiento como native (101 y 102) porque `undo native-vlan` no existe en V800R015. Ver [checklist-mk2-vlan1-sfp-sfpplus3.md](./checklist-mk2-vlan1-sfp-sfpplus3.md).

> **MK1 ya no existe.** Los **dos** uplinks de la OLT terminan en el MK2, en puertos físicos distintos. El bridge de VLAN 1 se llama **`LAN-VLAN1`** (antes `LAN_MK1`). Contiene `vlan1-olt` (uplink `0/3/3`) más `ether4`–`ether8`, y aloja **29 subredes**. No es un enlace hacia otro router. Nombres: [mk2-nombres-canonicos.md](./mk2-nombres-canonicos.md).
>
> El lado VLAN 1 es el **mayor**: 650 service-ports y ~3,7× el tráfico de la VLAN 100.

## Arquitectura L2/L3

| Capa | Dónde | Qué hace |
|------|-------|----------|
| **L2** | OLT | Bridge VLAN 100 GPON → uplink `0/3/2` (native untagged) |
| **L3** | MK2 + CPE | Gateway `192.168.30.1/24`, NAT/firewall hacia WAN |

## Estado final (config correcta)

> ⚠️ Tabla del montaje **2026-07-19**. Vigente desde **2026-09-10**: los gateways están en **`vlan100-olt`** y el firewall usa la interface list **`OLT-VLAN100`**. Ver [cutover-vlan100-mk2-tagged-2026-09-10.md](./cutover-vlan100-mk2-tagged-2026-09-10.md).

| Elemento | 2026-07-19 | Vigente 2026-09-10 |
|----------|------------|--------------------|
| `sfp-sfpplus2` | Fuera del bridge `LAN`; comment `OLT 0/3/2 uplink 10G` | Igual, pero **sin IPs** (solo transporte del tag) |
| Gateway piloto | `192.168.30.1/24` en `sfp-sfpplus2` | **`192.168.30.1/24` en `vlan100-olt`** |
| Gateway TR-069 | `192.168.255.1/22` en `sfp-sfpplus2` | **`192.168.255.1/22` en `vlan100-olt`** |
| Gateway staging e2e | `192.168.250.1/24` en `sfp-sfpplus2` | **`192.168.250.1/24` en `vlan100-olt`** |
| `vlan100-olt` | VLAN 100 sobre `sfp-sfpplus2` — **sin IP** (reservado) | **Termina la VLAN 100 tagged con los 3 gateways** |
| `dhcp-provisioning-255` | En `sfp-sfpplus2` | **En `vlan100-olt`** |
| Bridge `LAN` | Solo `ether12` + `192.168.111.1/24` (local) | Sin cambios |
| NAT | Masquerade WAN (`WAN-VLAN SFP-SFPPLUS1`) | Sin cambios |
| Firewall | Accept forward/input en `sfp-sfpplus2` | **`in/out-interface-list=OLT-VLAN100`** |

## Native VLAN: cómo llega hoy la VLAN 100

Desde **2026-09-10** el native de `0/3/2` es la VLAN **101**, así que la **100 llega tagged** y debe terminarse en `vlan100-olt`. Poner el gateway en `sfp-sfpplus2` es ahora el error.

**Síntoma de misconfig:** `sfp-sfpplus2` sin tráfico L3, `/ip arp` con entradas **incompletas** en la interfaz equivocada.

> Al diagnosticar, contar **solo ARP completas**. Durante el cutover fallido `vlan100-olt` mostraba 305 entradas y **0 completas**: eran peticiones del propio MK2 buscando clientes inalcanzables. El total de `/ip arp` no distingue servicio vivo de servicio caído.

### Montaje anterior (2026-07-19 → 2026-09-10)

La OLT enviaba la VLAN **100** **sin etiqueta 802.1Q** hacia **0/3/2** (`native-vlan 2 vlan 100`), y el gateway tenía que estar en `sfp-sfpplus2`. Si se ponía en `vlan100-olt`, esa subinterfaz solo recibía tagged → **RX=0** y ARP fallaba.

Verificado **2026-07-19 ~23:35**: ping MK2 → `192.168.30.202` OK (~2 ms), ARP `reachable` en `sfp-sfpplus2`.

---

## Comandos RouterOS — fase 2a (uplink base)

Aplicados **2026-07-19** vía SSH. Script consolidado (estado final): `scripts/mikrotik-mk2-pilot-uplink.rsc`

```text
/interface bridge port remove [find interface=sfp-sfpplus2]
/interface ethernet set [find default-name=sfp-sfpplus2] comment="OLT 0/3/2 uplink 10G"
/interface vlan add name=vlan100-olt interface=sfp-sfpplus2 vlan-id=100 comment="OLT piloto VLAN100" l3-hw-offloading=yes
/ip firewall filter add chain=forward action=accept in-interface=sfp-sfpplus2 comment="OLT piloto VLAN100 forward"
/ip firewall filter add chain=forward action=accept out-interface=sfp-sfpplus2 connection-state=established,related comment="OLT piloto VLAN100 return"
/ip firewall filter add chain=input action=accept in-interface=sfp-sfpplus2 comment="OLT piloto VLAN100 input mgmt"
/ip address add address=192.168.30.1/24 interface=sfp-sfpplus2 comment="OLT piloto VLAN100 native untagged"
```

> **Nota histórica:** la primera versión puso la IP en `vlan100-olt`; eso falló con OLT native VLAN 100. La corrección está en la sección siguiente.

---

## Comandos RouterOS — corrección gateway (post-ONU piloto)

Aplicados **2026-07-19 ~23:35** cuando la ONU estaba online pero ARP fallaba. Script: `scripts/mikrotik-mk2-pilot-gateway-fix.rsc`

```text
/ip address remove [find interface=vlan100-olt]
/ip address add address=192.168.30.1/24 interface=sfp-sfpplus2 comment="OLT piloto VLAN100 native untagged"
/ip firewall filter set [find comment="OLT piloto VLAN100 forward"] in-interface=sfp-sfpplus2
/ip firewall filter set [find comment="OLT piloto VLAN100 return"] out-interface=sfp-sfpplus2
/ip firewall filter set [find comment="OLT piloto VLAN100 input mgmt"] in-interface=sfp-sfpplus2
```

---

## Comandos RouterOS — diagnóstico

```text
/interface print stats where name~"sfp-sfpplus2|vlan100"
/ip address print where address~"192.168.30"
/ip arp print where address=192.168.30.202
/ping 192.168.30.202 count=5
/ip firewall filter print where comment~"OLT piloto VLAN100"
/tool traceroute 8.8.8.8 src-address=192.168.30.1 count=1
```

---

## Verificación automatizada

```bash
export MIKROTIK_PASSWORD='...'
./scripts/mikrotik-mk2-pilot-verify.sh
```

Salida esperada: IP en `sfp-sfpplus2`, ping 5/5 a `192.168.30.202`, ARP `reachable`.

---

## OLT + ONU piloto

| Item | Detalle |
|------|---------|
| OLT VLAN 100 uplink | [olt-vlan100-mk2-uplink.md](./olt-vlan100-mk2-uplink.md) |
| ONU piloto | ZTE F6600R `ZTEG-DC47C169`, `0/1/6` ont **127**, service-port **551** |
| CPE | `192.168.30.202/24`, GW `192.168.30.1`, WAN VLAN **100** tagged |

---

## Relacionado

- [mikrotik-mk2-comandos-catalogo.md](./mikrotik-mk2-comandos-catalogo.md)
- [mikrotik-mk2-fase1-runbook.md](./mikrotik-mk2-fase1-runbook.md)
- [mikrotik-routers-inventario.md](./mikrotik-routers-inventario.md)
- [olt-vlan100-mk2-uplink.md](./olt-vlan100-mk2-uplink.md)
- Plan: `.cursor/plans/multi_mikrotik_gigafiber_a973760c.plan.md`
