# MK2: GRE tunnel con VPS ispAdmin + enrutamiento a OLT (sin DST-NAT)
# VPS 212.85.13.47 <-> MK2 38.224.231.4
# Tunnel: 10.255.255.0/30 (peer VPS 10.255.255.2)
# SSH destino real: 10.11.104.2:22 — SNAT origen GRE hacia LAN OLT (mgmt MK2 ether3 10.11.104.89)

/interface gre remove [find name=gre-ispadmin-vps]
/interface gre add name=gre-ispadmin-vps local-address=38.224.231.4 remote-address=212.85.13.47 \
    allow-fast-path=no comment="ispAdmin VPS GRE -> OLT LAN (MK2)"

/ip address remove [find interface=gre-ispadmin-vps]
/ip address add address=10.255.255.1/30 interface=gre-ispadmin-vps comment="ispAdmin VPS GRE MK2"

/ip firewall address-list remove [find list=IspAdminVPS address=212.85.13.47]
/ip firewall address-list add list=IspAdminVPS address=212.85.13.47 comment="ispAdmin VPS"
/ip firewall address-list remove [find list=IspAdminVPS address=10.255.255.2]
/ip firewall address-list add list=IspAdminVPS address=10.255.255.2 comment="ispAdmin VPS GRE peer"

/ip firewall nat remove [find comment="IspAdmin VPS GRE -> OLT LAN SNAT"]
/ip firewall nat add chain=srcnat action=masquerade src-address=10.255.255.0/30 \
    dst-address=10.11.104.0/24 comment="IspAdmin VPS GRE -> OLT LAN SNAT"

/ip firewall filter remove [find comment="IspAdmin VPS GRE accept"]
/ip firewall filter add chain=input protocol=gre src-address=212.85.13.47 action=accept \
    comment="IspAdmin VPS GRE accept" place-before=0
/ip firewall filter remove [find comment="IspAdmin VPS GRE forward"]
/ip firewall filter add chain=forward in-interface=gre-ispadmin-vps src-address=10.255.255.0/30 \
    dst-address=10.11.104.0/24 action=accept comment="IspAdmin VPS GRE forward" place-before=0
