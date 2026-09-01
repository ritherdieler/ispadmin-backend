# Catálogo RouterOS — MK2 piloto VLAN 100

> Hub: [infra-red-multi-mikrotik-gigafiber.md](./infra-red-multi-mikrotik-gigafiber.md)

Comandos aplicados o usados en diagnóstico del MikroTik **38.224.231.4** (CCR2116, ROS 7.23.2) para uplink OLT **0/3/2**. Staging TR-069 (`192.168.255.0/24`) en **`sfp-sfpplus2`** (VLAN 100), coexistiendo con gateway prod `192.168.30.1/24`.

| Comando | Descripción | Usado en | Notas |
|---------|-------------|----------|-------|
| `/interface bridge port remove [find interface=sfp-sfpplus2]` | Saca uplink OLT del bridge LAN | `scripts/mikrotik-mk2-pilot-uplink.rsc` | Prerrequisito L3 piloto |
| `/interface ethernet set [find default-name=sfp-sfpplus2] comment="OLT 0/3/2 uplink 10G"` | Etiqueta uplink OLT | `scripts/mikrotik-mk2-pilot-uplink.rsc` | |
| `/interface vlan add name=vlan100-olt interface=sfp-sfpplus2 vlan-id=100 …` | Subinterfaz VLAN 100 | `scripts/mikrotik-mk2-pilot-uplink.rsc` | **No** llevar gateway si OLT native untagged |
| `/ip address add address=192.168.30.1/24 interface=sfp-sfpplus2 …` | Gateway piloto | `scripts/mikrotik-mk2-pilot-uplink.rsc` | Interfaz **padre** con OLT native VLAN 100 |
| `/ip address remove [find interface=vlan100-olt]` | Quita IP errónea de subinterfaz | `scripts/mikrotik-mk2-pilot-gateway-fix.rsc` | Fix post-ONU piloto |
| `/ip firewall filter add chain=forward action=accept in-interface=sfp-sfpplus2 comment="OLT sfp-sfpplus2 forward"` | Permite forward desde uplink piloto | Piloto `sfp-sfpplus2` | Comments renombrados 2026-07-21 (sin VLAN iface) |
| `/ip firewall filter add chain=forward action=accept out-interface=sfp-sfpplus2 connection-state=established,related comment="OLT sfp-sfpplus2 return"` | Retorno establecido | Piloto `sfp-sfpplus2` | |
| `/ip firewall filter add chain=input action=accept in-interface=sfp-sfpplus2 comment="OLT sfp-sfpplus2 input mgmt"` | Input mgmt uplink | Piloto `sfp-sfpplus2` | |
| `/ip firewall filter set [find comment="OLT piloto VLAN100 forward"] in-interface=sfp-sfpplus2` | Reapunta regla forward | `scripts/mikrotik-mk2-pilot-gateway-fix.rsc` | Tras mover IP |
| `/ip firewall filter add chain=forward action=accept in-interface=vlan1-olt comment="OLT VLAN1 forward"` | Permite forward desde VLAN1 | Cutover VLAN1 en `sfp-sfpplus3` | IPs en `vlan1-olt` (tagged); aplicado 2026-07-21 |
| `/ip firewall filter add chain=forward action=accept out-interface=vlan1-olt connection-state=established,related comment="OLT VLAN1 return"` | Retorno establecido VLAN1 | Cutover VLAN1 | Espejo de regla VLAN100 |
| `/ip firewall filter add chain=input action=accept in-interface=vlan1-olt comment="OLT VLAN1 input mgmt"` | Input mgmt VLAN1 | Cutover VLAN1 | ARP/ping a gateways en `vlan1-olt` |
| `/ip pool add name="PPOE CLIENTES" ranges=192.168.26.2-192.168.26.254` | Pool PPPoE wireless | Prep PPPoE MK2 | Espejo MK1 |
| `/ip pool add name="PPOE GAMERS" ranges=192.168.55.2-192.168.55.254` | Pool PPPoE gamers | Prep PPPoE MK2 | |
| `/ppp profile add name="PLAN …" local-address=192.168.26.1 remote-address="PPOE CLIENTES" …` | Profiles de velocidad | Prep PPPoE MK2 | 8 planes + CORTE |
| `/ppp secret add … service=pppoe` | Credenciales abonados wireless | Prep PPPoE MK2 | 123 secrets importados 2026-07-21 |
| `/interface pppoe-server server add interface=LAN_MK1 service-name="PPOE CLIENTES" …` | Server PPPoE en bridge LAN_MK1 | Sync PPPoE MK1→MK2 | Sync 2026-07-21; enabled |
| `/interface pppoe-server server set [find] interface=LAN_MK1` | Reapunta server a LAN_MK1 | Sync PPPoE | |
| `/ip firewall filter add … in-interface=LAN_MK1 comment="LAN_MK1 forward"` | Forward clientes LAN_MK1 | Bridge legacy | Comments sin “VLAN” (solo WAN-VLAN 450 existe) |
| `/ip address add address=10.11.104.89/24 interface=ether3 …` | IP mgmt OLT en MK2 | SmartOLT CloudOLT | Evita conflicto con MK1 `.88` |
| `/ip firewall address-list add list=CloudOLT address=amz.smartolt.com` | Allowlist SmartOLT cloud | SmartOLT CloudOLT | Resuelve IPs dinámicas |
| `/ip firewall nat add chain=dstnat … dst-address=38.224.231.4 dst-port=2333 to-addresses=10.11.104.2 to-ports=23 …` | DNAT Telnet OLT | SmartOLT CloudOLT | Igual puertos 2322→22, 2161→161 |
| `/ip firewall filter add chain=forward action=accept connection-nat-state=dstnat comment="CloudOLT forward to OLT"` | Forward DNAT | SmartOLT CloudOLT | |
| `/import file-name=mk2-wg-vps.rsc` (contenido `scripts/mikrotik-mk2-olt-vps-wg.rsc` + claves) | WireGuard VPS ↔ MK2 + SNAT hacia LAN OLT | OLT Gateway / NetDiag | UDP 51830; OLT ve `10.11.104.89`; prod 2026-08-01 |
| `/import file-name=mk2-gre-vps.rsc` (contenido `scripts/mikrotik-mk2-olt-vps-gre.rsc`) | GRE VPS ↔ MK2 + SNAT (rollback) | OLT Gateway legacy | Peer VPS `212.85.13.47` |
| `/interface print stats where name~"sfp-sfpplus2\|vlan100"` | RX/TX uplink vs subinterfaz | Diagnóstico | `vlan100-olt` RX=0 → native untagged |
| `/ip arp print where address=192.168.30.202` | ARP abonado piloto | Diagnóstico | Esperado: `reachable` en `sfp-sfpplus2` |
| `/ping 192.168.30.202 count=5` | Ping L3 abonado piloto | Diagnóstico / verify script | |
| `/tool traceroute 8.8.8.8 src-address=192.168.30.1 count=1` | Ruta WAN desde gateway piloto | Diagnóstico | NAT masquerade OK |
| `/ip address add address=192.168.255.1/24 interface=sfp-sfpplus2 …` | Gateway staging TR-069 **VLAN 100** | `scripts/genieacs/mk2-provisioning-network-255.rsc` | Coexiste con `192.168.30.1/24` en la misma iface; DHCP `.100–.250`; DNS 8.8.8.8/8.8.4.4; migrado desde `LAN_MK1` 2026-08-20 |
| `/ip address add address=192.168.250.1/24 interface=sfp-sfpplus2 …` | Gateway pool staging e2e VLAN 100 | `scripts/genieacs/mk2-staging-pool-250.rsc` | Aplicado 2026-09-01; coexiste con `.30.1` y `.255.1`; sin DHCP |
| `/ip firewall nat add … src-address=192.168.250.0/24 masquerade comment="NAT staging e2e 250"` | NAT WAN cliente staging | `mk2-staging-pool-250.rsc` | Backup previo `staging-e2e-250-pre` |
| `/ip firewall filter add … src-address-list=staging-e2e-250 dst-address=192.168.22.0/24 action=drop` | Aísla `.250` de LAN_MK1 legacy | `mk2-staging-pool-250.rsc` | |
| `/ip pool add name=provisioning-255 ranges=192.168.255.100-192.168.255.250` | Pool DHCP staging | `mk2-provisioning-network-255.rsc` | Idempotente |
| `/ip dhcp-server add name=dhcp-provisioning-255 … interface=sfp-sfpplus2` | DHCP server staging VLAN100 | `mk2-provisioning-network-255.rsc` | Lease 1h; script limpia residual en `LAN_MK1` |
| `/ip firewall filter … dst-port=7547 dst-address=192.168.255.0/24` | CR GenieACS staging | `mk2-provisioning-network-255.rsc` | Desde `10.255.255.2` |
| `/ip firewall nat … src-address=192.168.255.0/24 masquerade` | NAT Inform ACS | `mk2-provisioning-network-255.rsc` | |
| `/ip firewall address-list add list=api_whitelist address=212.85.13.47` | Allowlist VPS ispAdmin | Protección API MK2 | + `192.168.0.0/16` red interna |
| `/ip service set api address=212.85.13.47/32,192.168.0.0/16` | API solo VPS + LAN | Protección API MK2 | Aplicado 2026-07-21 |
| `/ip service disable api-ssl` | Apaga api-ssl sin certificado | Protección API MK2 | |
| `/ip firewall filter add chain=input action=accept protocol=tcp dst-port=8728 src-address-list=api_whitelist comment="API allowlist"` | Permite API allowlist | Protección API MK2 | |
| `/ip firewall filter add chain=input action=drop protocol=tcp dst-port=8728 comment="API drop rest"` | Bloquea API desde internet | Protección API MK2 | Tras allowlist |
| `/ip service set ssh address=212.85.13.47/32,192.168.0.0/16` | SSH solo VPS + LAN | Protección gestión MK2 | |
| `/ip service set winbox address=212.85.13.47/32,192.168.0.0/16` | Winbox solo VPS + LAN | Protección gestión MK2 | |
| `/ip firewall filter add … dst-port=22 … comment="SSH allowlist\|SSH drop rest"` | Allowlist/drop SSH | Protección gestión MK2 | Misma lista `api_whitelist` |
| `/ip firewall filter add … dst-port=8291 … comment="Winbox allowlist\|Winbox drop rest"` | Allowlist/drop Winbox | Protección gestión MK2 | |
| `/tool torch interface=sfp-sfpplus2 duration=30` | Monitoreo tráfico uplink | Validación internet ONU | Detectar flujos ONU→Internet |
| `/ip firewall connection print where src-address~"192.168.30"` | Conexiones NAT abonados piloto | Validación internet | Sin conns externas = CPE no sale |
| `/tool fetch url="http://192.168.30.202/" mode=http` | Acceso UI CPE desde MK2 | Diagnóstico ZTE | HTTP 200 = L3 bidireccional |
| `/routing table add name=toTarazona fib` | Tabla de ruteo para policy clientes problemáticos | `scripts/mikrotik-mk2-problematic-routing.rsc` | ROS 7; aplicado 2026-08-19 |
| `/ip address add address=8.243.126.161/32 interface="WAN-VLAN SFP-SFPPLUS1" …` | IP secundaria SNAT problemáticos (también `.160`/`.162`) | `scripts/mikrotik-mk2-problematic-routing.rsc` | **No** tocar `38.224.231.4/27` |
| `/ip firewall address-list add list="Clientes con paginas problematicas" …` | 14 clientes migrados desde MK1 | `scripts/mikrotik-mk2-problematic-address-list.rsc` | Excluye `0.0.0.0` inválida |
| `/ip firewall mangle add … new-routing-mark=toTarazona in-interface=LAN_MK1` | Mark-routing clientes problemáticos VLAN1 | `scripts/mikrotik-mk2-problematic-routing.rsc` | + regla espejo `sfp-sfpplus2` VLAN100 |
| `/ip route add … gateway=38.224.231.1 routing-table=toTarazona` | Default marcada → Tarazona | `scripts/mikrotik-mk2-problematic-routing.rsc` | Mismo GW que main; SNAT cambia IP origen |
| `/ip firewall nat add chain=srcnat action=src-nat routing-mark=toTarazona to-addresses=8.243.126.161` | SNAT problemáticos | `scripts/mikrotik-mk2-problematic-routing.rsc` | Antes del masquerade WAN; 2026-08-19 |
| `/ping 8.8.8.8 src-address=8.243.126.161 count=3` | Valida salida por IP secundaria | Diagnóstico post-migración | 0% loss verificado 2026-08-19 |

## Scripts

| Script | Propósito |
|--------|-----------|
| `scripts/mikrotik-mk2-pilot-uplink.rsc` | Config uplink + gateway (estado final correcto) |
| `scripts/mikrotik-mk2-pilot-gateway-fix.rsc` | Solo corrección si la IP quedó en `vlan100-olt` |
| `scripts/mikrotik-mk2-pilot-verify.sh` | Verificación ping/ARP/stats |
| `scripts/mikrotik-mk2-phase1-verify.py` | Verificación API + DB id=8 |
| `scripts/mikrotik-mk2-problematic-address-list.rsc` | Address-list clientes problemáticos (14 IPs) |
| `scripts/mikrotik-mk2-problematic-routing.rsc` | Routing table + IPs secundarias + mangle + ruta + SNAT |
| `scripts/mikrotik-mk1-problematic-disable.rsc` | Deshabilitar policy/IPs en MK1 tras cutover |
| `scripts/genieacs/mk2-provisioning-network-255.rsc` | Staging TR-069 `192.168.255.0/24` en `sfp-sfpplus2` (VLAN 100) + limpieza `LAN_MK1` |
| `scripts/genieacs/mk2-staging-pool-250.rsc` | Gateway + NAT pool staging e2e `192.168.250.0/24` en `sfp-sfpplus2` |

## Relacionado

- [mikrotik-mk2-config-olt-uplink.md](./mikrotik-mk2-config-olt-uplink.md)
- [olt-vlan100-mk2-uplink.md](./olt-vlan100-mk2-uplink.md)
