# Catálogo RouterOS — MK2 piloto VLAN 100

> Hub: [infra-red-multi-mikrotik-gigafiber.md](./infra-red-multi-mikrotik-gigafiber.md) · Nombres vivos: [mk2-nombres-canonicos.md](./mk2-nombres-canonicos.md)

Comandos aplicados o usados en diagnóstico del MikroTik **38.224.231.4** (CCR2116, ROS 7.23.2) para uplink OLT **0/3/2**. Staging TR-069 (`192.168.255.0/24`) en **`vlan100-olt`** (VLAN 100 tagged), coexistiendo con gateway prod `192.168.30.1/24`.

| Comando | Descripción | Usado en | Notas |
|---------|-------------|----------|-------|
| `/interface bridge set [find name=LAN_MK1] name=LAN-VLAN1` | Renombra el bridge VLAN 1 (sin mover puertos ni IPs) | Coherencia de nombres 2026-09-10 | PPPoE/ARP/firewall siguen el `.id`. Antes se llamaba `LAN_MK1` y confundía con un enlace a MK1 |
| `/interface bridge host print where !local` | MACs aprendidas por puerto del bridge | Auditoría dominio L2 VLAN 1 | Métrica de salud del cutover VLAN 1. 2026-09-10: `vlan1-olt` 529, `ether5` 199, `ether7` 11 |
| `/interface monitor-traffic interface=vlan1-olt,vlan100-olt once` | Caudal instantáneo por VLAN | Decisión de consolidar NNI | 2026-09-10 madrugada: ~285 Mbps sumados sobre 10 Gbps |
| `/interface ethernet monitor [find] once` | Estado de link, `rate` y modelo de SFP | Inventario de capacidad | 3× DAC `FT-SFP-DAC1M` a 10 Gbps; `sfp-sfpplus4` sin módulo |
| `/interface ethernet switch print` | Switch chip del CCR | Evaluación de hardware offload | `Marvell-98DX3255` (único chip). LACP/bonding en RouterOS sale de este fast path |
| `/ip address remove [find interface=sfp-sfpplus2 disabled=yes]` | Borra las IPs de rollback del cutover VLAN 100 | Cierre de rollback 2026-09-10 | Obliga a borrar también `vlan100-rollback`: hacía `enable` de esas IPs y `disable` de las de `vlan100-olt` |
| `/system script remove [find name~"vlan100-"]` | Elimina swap/rollback/guard de la VLAN 100 | Cierre de rollback 2026-09-10 | Sin las IPs, ejecutar el rollback dejaba la VLAN 100 sin gateways |
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

## PPPoE fibra VLAN 100 (migración `PPPOE_DYNAMIC`)

Pool dinámico `10.64.0.0/18` sobre `vlan100-olt`. Perfiles `GF-{down}-{up}` derivados de `plan.downloadSpeed`/`uploadSpeed`, corte por perfil `GF-CORTE`. Detalle: [pppoe-migracion-plan.md](./pppoe-migracion-plan.md).

| Comando | Descripción | Usado en | Notas |
|---------|-------------|----------|-------|
| `/ip pool add name=PPPOE-DINAMICO ranges=10.64.0.2-10.64.47.254` | Pool dinámico de abonados fibra | `scripts/mikrotik-mk2-pppoe-vlan100.rsc` | 12 286 direcciones. Bloque verificado libre contra `/ip address`, `/ip route`, `/ip arp`, `/queue/simple`, `/ip pool` y `subscription.ip` |
| `/ip pool add name=PPPOE-STG ranges=10.64.60.2-10.64.60.254` | Pool del piloto staging | `scripts/mikrotik-mk2-pppoe-vlan100.rsc` | Aislado del pool de producción para poder purgarlo entero |
| `/ip address add address=10.64.0.1/18 interface=vlan100-olt comment="PPPoE gateway"` | Gateway PPPoE (`local-address` de todos los perfiles) | `scripts/mikrotik-mk2-pppoe-vlan100.rsc` | Convive con `192.168.255.x` de staging TR-069 en la misma VLAN |
| `/ppp profile add name=GF-200-200 local-address=10.64.0.1 remote-address=PPPOE-DINAMICO rate-limit=200M/200M change-tcp-mss=yes` | Perfil de plan (rx/tx en orden RouterOS) | `PppoeProfileCatalog.rateLimit` | `change-tcp-mss=yes` acota el clamp de MSS **al perfil**; evita el `chain=forward action=change-mss` global que tocaría a los 870 clientes existentes |
| `/ppp profile add name=GF-CORTE local-address=10.64.0.1 remote-address=PPPOE-DINAMICO rate-limit=1k/1k` | Perfil de corte por morosidad | `PppoeAccessService.cut` | El corte no borra el secret: cambia el perfil y expulsa la sesión |
| `/interface pppoe-server server add service-name=GIGAFIBER-PPPOE interface=vlan100-olt default-profile=GF-200-200 max-mtu=1480 max-mru=1480 one-session-per-host=yes` | Servidor PPPoE de la VLAN 100 | `scripts/mikrotik-mk2-pppoe-vlan100.rsc` | PPPoE usa ethertype `0x8863`/`0x8864`, así que convive con la IP estática de la misma VLAN |
| `POST /rest/ppp/secret/print {"name":"gf4321"}` | Busca el secret de un abonado | `PppoeManagerService.ensureSecret` | Idempotente: si existe hace `set`, si no `add` |
| `PUT /rest/ppp/secret` `{"name":"gf4321","profile":"GF-200-200","service":"pppoe","password":"…"}` | Alta del secret con el perfil del plan | `PppoeManagerService.ensureSecret` | **Sin** `remote-address`: el secret lo sobreescribiría y anularía el pool. Lab: perfil `GF-STG-200-200` (pool `PPPOE-STG`). HTTP 201. Desde el VPS, IP pública; no por WireGuard |
| `POST /rest/ppp/secret/set {".id":"*1","profile":"GF-CORTE"}` | Corte / reactivación / cambio de plan | `PppoeManagerService.applyProfile` | Único punto de cambio de velocidad en `PPPOE_DYNAMIC` |
| `POST /rest/ppp/active/print {"name":"gf4321"}` | Sesión viva del abonado (IP asignada, uptime, caller-id) | `PppoeManagerService.sessionOf` | Verificación de alta junto al `ConnectionStatus` del CPE |
| `POST /rest/ppp/active/remove {".id":"*3"}` | Expulsa la sesión para forzar reconexión | `PppoeManagerService.kickSession` | Necesario tras `applyProfile`: el `rate-limit` no se recalcula en caliente |
| `POST /rest/queue/simple/print` con `name` `<pppoe-gf4321>` | Cola dinámica que RouterOS crea desde el `rate-limit` del perfil | `SubscriptionTrafficPollService` | Reporta `bytes` y `rate`; la telemetría se indexa por `pppoe:<username>`, no por IP, porque la IP cambia en cada reconexión |
| `/ppp profile set [find where name=$profileName] …` | Alta/actualización idempotente de un perfil del catálogo | `scripts/mikrotik-mk2-pppoe-vlan100.rsc` | **La variable no puede llamarse `$name`.** Dentro de `find where name=$name` RouterOS resuelve `$name` contra la propiedad del ítem, el `where` da verdadero para todos y el `set` arrasa con los perfiles del PPPoE legado. Ocurrió el 2026-09-10: [incidente-mk2-ppp-profile-overwrite-2026-09-10.md](./incidente-mk2-ppp-profile-overwrite-2026-09-10.md) |
| `/ppp profile set [find where name="default"] !local-address !remote-address !dns-server !rate-limit` | Devuelve un perfil builtin a sus valores de fábrica | `scripts/mikrotik-mk2-ppp-profile-restore-20260910.rsc` | Para limpiar se usa `!propiedad`; `propiedad=""` responde `ambiguous value of pool` en `local-address` |
| `/ppp secret add … service=pppoe` | Credenciales abonados wireless | Prep PPPoE MK2 | 123 secrets importados 2026-07-21 |
| `/interface pppoe-server server add interface=LAN_MK1 service-name="PPOE CLIENTES" …` | Server PPPoE en bridge LAN_MK1 | Sync PPPoE MK1→MK2 | Sync 2026-07-21; enabled |
| `/interface pppoe-server server set [find] interface=LAN_MK1` | Reapunta server a LAN_MK1 | Sync PPPoE | |
| `/ip firewall filter add … in-interface=LAN_MK1 comment="LAN_MK1 forward"` | Forward clientes LAN_MK1 | Bridge legacy | Comments sin “VLAN” (solo WAN-VLAN 450 existe) |
| `/ip firewall filter move [find comment="CORTADO POR DEUDA - LISTA DE DEUDORES"] [find comment="OLT sfp-sfpplus2 forward"]` | Coloca el drop de deudores **antes** de los accept de subida | Corte 2026-09-10 | REST: `POST /ip/firewall/filter/move` `{".id":"*37","destination":"*11"}`. Sin esto el `accept in-interface-list=OLT-VLAN100` se come el tráfico y el corte no corta |
| `/ip firewall filter add chain=forward action=drop src-address-list=deudores place-before={id} comment="CORTADO POR DEUDA - LISTA DE DEUDORES"` | Recrea el drop en la posición correcta | `MikroTikService.createFirewallDropRule` | `{id}` = primer accept de subida sin src/dst (`in-interface` o `in-interface-list`). El `add` al final de la cadena lo vuelve a dejar inalcanzable |
| `/ip firewall filter add chain=forward action=drop src-address-list=cancelados place-before={id} comment="CORTADO POR CANCELACION - LISTA DE CANCELADOS"` | Drop de suscripciones CANCELLED, independiente del de deudores | `MikroTikService.createCutDropRule(session, CutLists.CANCELLED)` | Añadido 2026-09-10. Antes cancelados y deudores compartían la lista `deudores` y cada proceso borraba la población del otro |
| `/ip firewall address-list add list=cancelados address={ip} comment="CANCELADO: {nombre}"` | Alta en la lista de cancelados | `ServiceCutManagerService` / `AddressListManagerService` | Par lista+comment definido en `CutLists` |
| `/ip firewall address-list remove [find list=deudores\|cancelados address={ip}]` | Quita al abonado de **ambas** listas al reactivar | `MikroTikService.removeIpFromAllCutLists` | Evita que un recontratado quede cortado por el residuo de la otra lista |
| `/ip address add address=10.11.104.89/24 interface=ether3 …` | IP mgmt OLT en MK2 | SmartOLT CloudOLT | Evita conflicto con MK1 `.88` |
| `/ip firewall address-list add list=CloudOLT address=amz.smartolt.com` | Allowlist SmartOLT cloud | SmartOLT CloudOLT | Resuelve IPs dinámicas |
| `/ip firewall nat add chain=dstnat … dst-address=38.224.231.4 dst-port=2333 to-addresses=10.11.104.2 to-ports=23 …` | DNAT Telnet OLT | SmartOLT CloudOLT | Igual puertos 2322→22, 2161→161 |
| `/ip firewall filter add chain=forward action=accept connection-nat-state=dstnat comment="CloudOLT forward to OLT"` | Forward DNAT | SmartOLT CloudOLT | |
| `/import file-name=mk2-wg-vps.rsc` (contenido `scripts/mikrotik-mk2-olt-vps-wg.rsc` + claves) | WireGuard VPS ↔ MK2 + SNAT hacia LAN OLT | OLT Gateway / NetDiag | UDP 51830; OLT ve `10.11.104.89`; prod 2026-08-01. **Destructivo**: borra todos los peers. No usar en dual-run KVM4 |
| `/interface wireguard peers add … comment="ispAdmin VPS KVM4"` + SNAT `10.255.254.0/30` | Segundo peer WG hacia KVM4 `2.24.66.53:51820` | Dual-run migración VPS | Túnel `10.255.254.2/30` (prod sigue `10.255.255.0/30`). Plantilla `scripts/mikrotik-mk2-olt-vps-wg-kvm4-add-peer.rsc`. Aplicado 2026-09-20; ping OLT OK desde ambos VPS |
| `/interface wireguard peers set [find comment="ispAdmin VPS KVM4"] allowed-address=10.255.254.2/32,10.20.0.0/22` | Anuncia la red de gestión de las ONU por el peer KVM4 | TR-069 por VPN | KVM4 ya tiene la ruta inversa `10.20.0.0/22 dev wg-olt`; aplicado 2026-09-24 |
| `/ip firewall nat add chain=dstnat src-address=10.20.0.0/22 dst-address=2.24.66.53 protocol=tcp dst-port=80 action=dst-nat to-addresses=10.255.254.2 to-ports=80 comment="TR069 mgmt 1000 to KVM4 WG"` | Envía el ACS público de TR-069 al endpoint privado del KVM4 | TR-069 por VPN | Solo VLAN 1000 y TCP/80; aplicado 2026-09-24 |
| `/ip firewall nat add chain=srcnat src-address=10.20.0.0/22 dst-address=10.255.254.2 protocol=tcp dst-port=80 action=accept comment="TR069 KVM4 WG preserve ONU source"` | Evita el masquerade general de gestión para conservar la IP real de la ONU | TR-069 por VPN | Debe quedar antes de `NAT mgmt 1000 /22`; aplicado 2026-09-24 |
| `/import file-name=mk2-gre-vps.rsc` (contenido `scripts/mikrotik-mk2-olt-vps-gre.rsc`) | GRE VPS ↔ MK2 + SNAT (rollback) | OLT Gateway legacy | Peer VPS `212.85.13.47` |
| `/interface print stats where name~"sfp-sfpplus2\|vlan100"` | RX/TX uplink vs subinterfaz | Diagnóstico | `vlan100-olt` RX=0 → native untagged |
| `/ip arp print where address=192.168.30.202` | ARP abonado piloto | Diagnóstico | Esperado: `reachable` en `sfp-sfpplus2` |
| `/ping 192.168.30.202 count=5` | Ping L3 abonado piloto | Diagnóstico / verify script | |
| `/tool traceroute 8.8.8.8 src-address=192.168.30.1 count=1` | Ruta WAN desde gateway piloto | Diagnóstico | NAT masquerade OK |
| `/ip address add address=192.168.255.1/24 interface=sfp-sfpplus2 …` | Gateway staging TR-069 **VLAN 100** | `scripts/genieacs/mk2-provisioning-network-255.rsc` | Coexiste con `192.168.30.1/24` en la misma iface; DHCP `.100–.250`; DNS 8.8.8.8/8.8.4.4; migrado desde `LAN_MK1` 2026-08-20 |
| `/ip address add address=192.168.250.1/24 interface=sfp-sfpplus2 …` | Gateway pool staging e2e VLAN 100 | `scripts/genieacs/mk2-staging-pool-250.rsc` | Aplicado 2026-09-01; coexiste con `.30.1` y `.255.1`; sin DHCP; VPS `wg-olt` lleva `.250` desde 2026-09-05 |
| `/ip address add address=192.168.31.1/24 interface=vlan100-olt …` | Gateway clientes VLAN100 `192.168.31.0/24` | `scripts/mikrotik-mk2-pool-31.rsc` | Reaplicado 2026-09-10 en `vlan100-olt`; comment unificado 2026-09-10; NAT srcnat `192.168.31.0/24`; ver `mk2-pool-31-eligible-switch-2026-09-10.md` |
| `/ip address set [find address=…] comment="Gateway clientes VLAN1\|VLAN100 …"` | Alinea comments: clientes ≠ TR-069 | `scripts/mikrotik-mk2-ip-address-comments.rsc` | 2026-09-10. TR-069 real solo `192.168.252.0/22`. Detalle: `mk2-ip-address-comments-2026-09-10.md` |
| `/ip firewall nat add … src-address=192.168.250.0/24 masquerade comment="NAT staging e2e 250"` | NAT WAN cliente staging | `mk2-staging-pool-250.rsc` | Backup previo `staging-e2e-250-pre` |
| `/ip firewall filter add … src-address-list=staging-e2e-250 dst-address=192.168.22.0/24 action=drop` | Aísla `.250` de LAN_MK1 legacy | `mk2-staging-pool-250.rsc` | |
| `/ip pool add name=provisioning-255 ranges=192.168.255.100-192.168.255.250` | Pool DHCP staging | `mk2-provisioning-network-255.rsc` | Idempotente |
| `/ip dhcp-server add name=dhcp-provisioning-255 … interface=sfp-sfpplus2` | DHCP server staging VLAN100 | `mk2-provisioning-network-255.rsc` | Lease 1h; script limpia residual en `LAN_MK1` |
| `/ip firewall filter … dst-port=7547 dst-address=192.168.255.0/24` | CR GenieACS staging | `mk2-provisioning-network-255.rsc` | Desde `10.255.255.2` |
| `/ip firewall nat … src-address=192.168.255.0/24 masquerade` | NAT Inform ACS | `mk2-provisioning-network-255.rsc` | |
| `/ip firewall address-list add list=api_whitelist address=212.85.13.47` | Allowlist VPS ispAdmin | Protección API MK2 | + `192.168.0.0/16` red interna |
| `/ip service set api address=212.85.13.47/32,192.168.0.0/16,2.24.66.53/32` | API VPS prod + KVM4 + LAN | Protección API MK2 | Prod 2026-07-21; KVM4 añadido 2026-09-20 |
| `/ip service disable api-ssl` | Apaga api-ssl sin certificado | Protección API MK2 | |
| `/ip firewall filter add chain=input action=accept protocol=tcp dst-port=8728 src-address-list=api_whitelist comment="API allowlist"` | Permite API allowlist | Protección API MK2 | |
| `/ip firewall filter add chain=input action=drop protocol=tcp dst-port=8728 comment="API drop rest"` | Bloquea API desde internet | Protección API MK2 | Tras allowlist |
| `/ip service set ssh address=212.85.13.47/32,192.168.0.0/16,2.24.66.53/32` | SSH VPS prod + KVM4 + LAN | Protección gestión MK2 | KVM4 añadido 2026-09-20 |
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
| `/ip firewall nat add chain=srcnat action=masquerade src-address=10.255.255.2 dst-address=10.20.0.0/22 out-interface=vlan1000-olt comment="GenieACS CR srcnat mgmt 1000"` | El VPS origina como `10.255.255.2`; la ONU 10.20 no le responde. El masquerade lo muestra como `10.20.0.1` | `scripts/genieacs/mk2-mgmt-vlan-1000.rsc` | Ping/summon GenieACS a CR `10.20.0.0/22`. Aplicado 2026-09-19. Dual-run: espejo `src-address=10.255.254.2` comment `… KVM4` (2026-09-20) |
| `/ping 8.8.8.8 src-address=8.243.126.161 count=3` | Valida salida por IP secundaria | Diagnóstico post-migración | 0% loss verificado 2026-08-19 |
| `/ip route print where dst-address=0.0.0.0/0` | Rutas por defecto **con su `routing-table`** | Higiene L3 2026-09-10 | Hay dos: `main` y `toTarazona`, mismo GW `38.224.231.1`. **No es duplicado**: la segunda la usan 2 mangle `mark-routing`. Borrarla rompe el policy routing |
| `/routing rule print` · `/routing table print` | Reglas y tablas de policy routing | Higiene L3 2026-09-10 | `rule` vacío; tablas `main` y `toTarazona`. En ROS 7 el marcado vive en mangle, no en `routing rule` |
| `/queue simple print` con `.proplist=[".id","target","bytes","rate"]` | Contadores por cola para medir actividad real | Borrado de colas de cancelados | Dos lecturas separadas ~25 s: `delta(bytes)>0` = abonado navegando. `rate` puntual da falsos negativos |
| `/queue simple remove [find target=…]` | Borra una cola simple | `scripts/mk2-reconcile-queues.mjs --delete-cancelled` | REST `DELETE /rest/queue/simple/{id}`. **Nunca** sobre huérfanas ni sobre cancelados con tráfico (quedan sin límite) |
| `/queue simple set [find] target=… name=… max-limit=…` | Mueve una cola a la nueva IP del abonado | Acción `RETARGET` del reconciliador | Conserva contadores; evita el `already have such name` de borrar+crear |
| `/ip arp print where address~"192.169."` | ARP del prefijo no-RFC1918 | Higiene IPAM 2026-09-10 | Una entrada, `complete=false` (sin MAC). Ping a `192.169.22.170` = 100% loss |

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
| `scripts/mikrotik-mk2-olt-vps-wg-kvm4-add-peer.rsc` | ADD-only segundo peer WG KVM4 (`10.255.254.0/30`). No reemplaza el peer de prod |

## Cutover VLAN 100 a tagged (2026-09-10)

Scripts persistentes en `/system/script`, dejados para futuros cambios de uplink. Detalle: [cutover-vlan100-mk2-tagged-2026-09-10.md](./cutover-vlan100-mk2-tagged-2026-09-10.md).

| Script | Qué hace |
|--------|----------|
| `vlan100-swap` | Deshabilita IPs de `sfp-sfpplus2`, habilita las de `vlan100-olt`, mueve `dhcp-provisioning-255` |
| `vlan100-rollback` | El inverso. Idempotente: ejecutarlo en el estado original no cambia nada |
| `vlan100-guard` | Si `vlan100-olt` tiene menos de 50 ARP **completas**, lanza el rollback y se autoelimina |
| `vlan1-swap` | Habilita el bridge port `vlan1-olt` y deshabilita `sfp-sfpplus3` en `LAN_MK1` |
| `vlan1-rollback` | El inverso. Idempotente. **No basta por sí solo**: si la OLT ya está en tagged hay que revertirla también con `native-vlan 3 vlan 1` |

Patrón del lado VLAN 1: el puerto nuevo del bridge se pre-crea con `disabled=yes edge=yes`, de modo que el swap es un enable/disable simétrico y no un add/remove con carrera de STP.

```text
/interface bridge port add bridge=LAN_MK1 interface=vlan1-olt disabled=yes edge=yes pvid=1
```

Métrica de salud del bridge: **MACs aprendidas por puerto**, no ARP. Las IPs viven en `LAN_MK1`, que no cambia con el swap.

```text
/interface bridge host print where bridge=LAN_MK1 interface=vlan1-olt
```

```text
/system script run vlan100-rollback
/system scheduler add name=vlan100-guard interval=00:05:00 \
  on-event="/system script run vlan100-guard" policy=read,write,policy,test
```

### Interface list para cambios de interfaz sin corte

Permite que una regla coincida con la interfaz vieja y la nueva a la vez, así se precarga sin cambiar comportamiento:

```text
/interface list add name=OLT-VLAN100
/interface list member add list=OLT-VLAN100 interface=sfp-sfpplus2
/interface list member add list=OLT-VLAN100 interface=vlan100-olt
/ip firewall filter set [find comment~"OLT sfp-sfpplus2"] !in-interface in-interface-list=OLT-VLAN100
```

### Aplicar un `.rsc` completo por REST

La REST API no importa ficheros. Para ejecutar un script con `:local` / `:if` / `:foreach` hay que crearlo y lanzarlo:

```text
POST /rest/system/script/add   {"name":"...","source":"<contenido .rsc>","policy":"read,write,policy,test"}
POST /rest/system/script/run   {".id":"<ret del add>"}
```

Un `run` correcto devuelve `[]`. Borrar primero el script homónimo (`/system/script/remove`) para que la reejecución sea limpia.

### Terminación de una VLAN tagged adicional en un uplink compartido

```text
/interface vlan add name=vlan1000-olt interface=sfp-sfpplus2 vlan-id=1000
/ip address add address=10.20.0.1/22 interface=vlan1000-olt
/ip dhcp-server add name=dhcp-mgmt-1000 interface=vlan1000-olt address-pool=mgmt-1000
```

El `dhcp-server` va en la **subinterfaz**, nunca en el puerto físico: el puerto ya transporta otra VLAN y ahí el servidor respondería al dominio equivocado. Lo mismo para la interface list, que debe contener solo `vlan1000-olt`.

Detalle: [vlan1000-gestion-cpe.md](./vlan1000-gestion-cpe.md).

### Notas de la REST API

| Situación | Detalle |
|-----------|---------|
| Auth | Cabecera `Authorization: Basic ...` explícita. `HTTPBasicAuthHandler` de Python no preautentica y RouterOS devuelve listas vacías en vez de 401 |
| Respuestas escalares | `/system/identity` devuelve un objeto, no una lista. Normalizar antes de indexar o revienta con `KeyError: 0` |
| Borrar una propiedad | Prefijo `!` con `null`: `{"!in-interface": null}`. Mandar `""` falla con `ambiguous value of interface, more than one possible value matches input` |
| Medir salud real | Contar solo `complete=true` en `/ip arp`. El total incluye peticiones sin responder y no distingue servicio vivo de caído |
| Acceso desde el VPS | Usar la IP pública `38.224.231.4`. Por WireGuard (`10.255.255.2`) el `/ip service` corta la sesión TLS tras el handshake |
| Cadenas default-accept | No hay drop final implícito: una regla que deja de coincidir no corta tráfico, pero sí puede desactivar policy routing o drops selectivos. Un `accept` de interfaz **antes** del `drop deudores` anula el corte |

## Relacionado

- [cutover-vlan100-mk2-tagged-2026-09-10.md](./cutover-vlan100-mk2-tagged-2026-09-10.md)
- [mikrotik-mk2-config-olt-uplink.md](./mikrotik-mk2-config-olt-uplink.md)
- [olt-vlan100-mk2-uplink.md](./olt-vlan100-mk2-uplink.md)
- [auditoria-red-olt-mk2-2026-09-09.md](./auditoria-red-olt-mk2-2026-09-09.md)
