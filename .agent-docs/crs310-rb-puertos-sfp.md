# CRS310 RB PUERTOS SFP+ — switch L2

| Campo | Valor |
|-------|--------|
| Identity | `RB PUERTOS SFP+` |
| Modelo | CRS310-1G-5S-4S+ (chip Marvell-98DX226S) |
| ROS | 6.49.6 (stable) |
| Serial | HCX087KKA36 |
| MAC (vecino MK2) | `18:FD:74:6A:63:D7` (`LAN/sfp-sfpplus3`) |
| IP gestión | `38.224.231.8/27` en VLAN 450 `CONTROL MONITOREO` |
| Gateway mgmt | `38.224.231.1` |
| Acceso | mac-telnet desde MK2: `/tool mac-telnet 18:FD:74:6A:63:D7` (user `gigafiber2023`). SSH en el CRS quedó **enabled** tras auditoría 2026-07-22; ProxyJump aún puede fallar banner — preferir mac-telnet. |

## Rol

Switch de distribución L2 (bridge único `LAN`), no router de clientes.

**No es el switch de `ether5` de MK2.** Ese puerto (`SWITCH SFP 4 PUERTOS`) es otro equipo, del path L2 clientes/`LAN_MK1`. Este CRS310 cuelga del uplink WAN: MK2 `sfp-sfpplus1` ↔ CRS `sfp-sfpplus3` (comment `WAN CCR 2116`), en el dominio VLAN 450 / Sirion.

## Topología de puertos (comments)

| Puerto | Comment | En bridge `LAN` |
|--------|---------|-----------------|
| `sfp-sfpplus1` | ENTRADA SIRION | sí (hw-offload) |
| `sfp-sfpplus2` | SALIDA CCR1036 | sí (hw-offload) |
| `sfp-sfpplus3` | WAN CCR 2116 | sí (slave/running) |
| `sfp1` | DUNA CORP HORNO ALTO | sí |
| `sfp2` | QUIJAS IP PUBLICA | sí |
| `ether1` | — | sí (hw-offload) |
| `sfp3` | — | sí (slave, sin link) |
| `sfp4`, `sfp5`, `sfp-sfpplus4` | — | **no** en bridge |

## Auditoría “switch puro” (2026-07-22)

### Correcto (alineado a L2)

- Un solo bridge `LAN`; tráfico de datos en L2.
- `vlan-filtering=no` → puente transparente (pasa tagged/untagged sin filtrar en bridge-vlan).
- `use-ip-firewall=no`, fast-path activo (`bridge-fast-path-active=yes`).
- Puertos con `hw=yes` / flag `H` (offload al switch chip).
- Una sola IP: gestión `38.224.231.8/27` en `/interface vlan` 450.
- Solo ruta default de gestión (`0.0.0.0/0` → `38.224.231.1`).
- Sin NAT, sin filter, sin DHCP server/client, sin PPPoE server.
- RSTP (`protocol-mode=rstp`) en el bridge.
- Servicios de datos apagados (telnet/ftp/www/api); winbox (+ ssh tras auditoría).

### Aceptable / no es “router”

- IP + default route solo para administración (normal en CRS con RouterOS).
- Instancia OSPF `default` de fábrica sin redistribución ni interfaces → no enruta.

### Observaciones (no bloquean el rol L2)

1. **Sirion + MK1 WAN + MK2 WAN en el mismo bridge** — diseño de switch de distribución en el uplink. El loop visto en MK2 `ether5`/`LAN_MK1` es de **otro** switch (sectores/clientes), no de este CRS310.
2. **Puertos fuera del bridge** (`sfp4`, `sfp5`, `sfp-sfpplus4`) — OK si están vacíos; para “todos los puertos switch” conviene agregarlos a `LAN`.
3. **Winbox/SSH sin `address=`** — riesgo de gestión expuesta en la VLAN 450 pública; endurecer allowlist si se quiere.
4. **RouterOS vs SwOS** — `boot-os=router-os`. SwOS sería aún más “solo switch”; con bridge+hw-offload el comportamiento L2 ya es el de switch.

### Veredicto

**Sí está configurado como switch L2** (no como router). No hay funciones de enrutamiento de clientes. La gestión L3 en VLAN 450 es lo esperado.

## VLAN gestión

| Nombre | vlan-id | Parent | IP | Estado |
|--------|---------|--------|-----|--------|
| `CONTROL MONITOREO` | 450 | bridge `LAN` | `38.224.231.8/27` | Running |

`/interface bridge vlan` vacío (coherente con `vlan-filtering=no`).
