# MK2 — comentarios de gateways alineados al rol real

Fecha: **2026-09-10**

Varios `/ip address` de **clientes** llevaban el comentario `Pool de aprovisionamiento GenieACS TR-069`. Eso no era cierto: el DHCP/TR-069 pre-alta es solo **`192.168.252.0/22`** (gateway `192.168.255.1/22` en `vlan100-olt`).

## Convención

| Comentario | Qué es |
|------------|--------|
| `Gateway clientes VLAN1 {red}` | Internet abonado en `LAN-VLAN1` |
| `Gateway clientes VLAN100 {red}` | Internet abonado en `vlan100-olt` (`.30` y `.31`) |
| `Gateway aprovisionamiento TR-069 192.168.252.0/22` | DHCP pre-alta GenieACS |
| `Gateway staging e2e VLAN100 192.168.250.0/24` | Pool e2e staging |
| `Gateway gestion CPE VLAN1000 …` / `Gateway staging e2e VLAN1000 …` | Gestión CPE, no internet |

No se tocan WAN, `LAN` 192.168.111, ether3, GRE/WG ni IPs dinámicas PPPoE.

## Aplicado

Script: [`scripts/mikrotik-mk2-ip-address-comments.rsc`](../scripts/mikrotik-mk2-ip-address-comments.rsc)

21 direcciones dejaron el texto GenieACS (incl. `192.168.30.1/24` en `vlan100-olt`). 9 que decían solo `LAN-VLAN1 gateway` pasaron al mismo patrón `Gateway clientes VLAN1`. `8.243.126.162/32` (sin comment) → `SNAT problematicos`.
