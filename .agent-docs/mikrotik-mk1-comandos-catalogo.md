# Catálogo RouterOS — MK1 (CCR1036)

> Hub: [infra-red-multi-mikrotik-gigafiber.md](./infra-red-multi-mikrotik-gigafiber.md)  
> Inventario: [mikrotik-routers-inventario.md](./mikrotik-routers-inventario.md)

Comandos aplicados o usados en diagnóstico del MikroTik **38.224.231.2** (GIGAFIBER 1036, ROS 6.48.6).

## Acceso VPS → OLT (GRE + enrutamiento)

| Comando | Descripción | Usado en | Notas |
|---------|-------------|----------|-------|
| `/interface gre add name=gre-ispadmin-vps local-address=38.224.231.2 remote-address=212.85.13.47` | Túnel GRE con VPS | `scripts/mikrotik-mk1-olt-vps-gre.rsc` | Aplicado 2026-07-20 |
| `/ip address add address=10.255.255.1/30 interface=gre-ispadmin-vps` | IP del peer GRE | `scripts/mikrotik-mk1-olt-vps-gre.rsc` | Peer VPS `10.255.255.2` |
| `/ip firewall nat add … src-address=10.255.255.0/30 dst-address=10.11.104.0/24 action=masquerade` | SNAT túnel → LAN OLT | `scripts/mikrotik-mk1-olt-vps-gre.rsc` | OLT ve `10.11.104.88` |
| `/system ssh 10.11.104.2 user=oltadmin` | SSH anidado LAN → OLT | Manual | Alternativa sin GRE |

CloudOLT (`2322`/`2333`/`2161`) no se modifica.

## Policy IPs problemáticas (toTarazona) — deshabilitada 2026-08-19

Migrada a MK2. En MK1 quedó `disabled=yes` (rollback: `disabled=no`).

| Comando | Descripción | Usado en | Notas |
|---------|-------------|----------|-------|
| `/ip address set [find address~"8.243.126"] disabled=yes` | Desactiva bloque secundario WAN | `scripts/mikrotik-mk1-problematic-disable.rsc` | `.160–.163`; no toca `.2` |
| `/ip firewall mangle set [find comment~"Clientes problematicos"] disabled=yes` | Apaga mark-routing toTarazona | `scripts/mikrotik-mk1-problematic-disable.rsc` | |
| `/ip firewall nat set [find comment~"SNAT problematicos"] disabled=yes` | Apaga SNAT → `8.243.126.161` | `scripts/mikrotik-mk1-problematic-disable.rsc` | |
| `/ip route set [find routing-table=toTarazona] disabled=yes` | Apaga ruta marcada | `scripts/mikrotik-mk1-problematic-disable.rsc` | |
| `/ip firewall address-list set [find list="Clientes con paginas problematicas"] disabled=yes` | Apaga lista en MK1 | `scripts/mikrotik-mk1-problematic-disable.rsc` | 15 entradas; activa en MK2 |

## Relacionado

- [olt-vps-ssh-nat-build.md](./olt-vps-ssh-nat-build.md)
- [olt-ma5608t-gpon-guide.md](./olt-ma5608t-gpon-guide.md)
- [olt-ma5608t-backup.md](./olt-ma5608t-backup.md)
